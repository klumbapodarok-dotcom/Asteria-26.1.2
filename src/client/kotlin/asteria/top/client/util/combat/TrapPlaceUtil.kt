package asteria.top.client.util.combat

import asteria.top.client.util.player.InventoryUtil
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.max

object TrapPlaceUtil {
    const val OFFHAND_SLOT = -2

    private const val FACE_SAMPLE_RADIUS = 0.32
    private const val FACE_RAYCAST_INSIDE_OFFSET = 0.03
    private const val OVERHEAD_SUPPORT_DISTANCE_PENALTY = 64.0
    private const val AWAITING_EXPIRE_MS = 750L

    private val awaiting = mutableMapOf<BlockPos, Long>()
    private var defaultPlacementSyncTick = Long.MIN_VALUE
    private var defaultPlacementSyncOwner: Any? = null

    private val shiftBlocks = setOf(
        Blocks.ENDER_CHEST,
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.CRAFTING_TABLE,
        Blocks.ANVIL,
        Blocks.BREWING_STAND,
        Blocks.HOPPER,
        Blocks.DROPPER,
        Blocks.DISPENSER,
        Blocks.ENCHANTING_TABLE,
        Blocks.WHITE_SHULKER_BOX,
        Blocks.ORANGE_SHULKER_BOX,
        Blocks.MAGENTA_SHULKER_BOX,
        Blocks.LIGHT_BLUE_SHULKER_BOX,
        Blocks.YELLOW_SHULKER_BOX,
        Blocks.LIME_SHULKER_BOX,
        Blocks.PINK_SHULKER_BOX,
        Blocks.GRAY_SHULKER_BOX,
        Blocks.CYAN_SHULKER_BOX,
        Blocks.PURPLE_SHULKER_BOX,
        Blocks.BLUE_SHULKER_BOX,
        Blocks.BROWN_SHULKER_BOX,
        Blocks.GREEN_SHULKER_BOX,
        Blocks.RED_SHULKER_BOX,
        Blocks.BLACK_SHULKER_BOX,
    )

    enum class RotateMode {
        NONE,
        DEFAULT,
        GRIM,
    }

    enum class SwitchMode {
        SILENT,
        NORMAL,
    }

    enum class PlaceMode {
        PACKET,
        VANILLA,
    }

    fun isOffhandSlot(slot: Int): Boolean = slot == OFFHAND_SLOT

    fun isHotbarSlot(slot: Int): Boolean = slot in 0..8

    fun resolvePlacementHand(slot: Int): InteractionHand {
        return if (isOffhandSlot(slot)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
    }

    fun findHotbarBlock(block: Block): Int {
        val player = Minecraft.getInstance().player ?: return -1
        return (0..8).firstOrNull { slot ->
            val stack = player.inventory.getItem(slot)
            stack.item is BlockItem && (stack.item as BlockItem).block == block
        } ?: -1
    }

    fun tryAcquireDefaultPlacementWindow(owner: Any?): Boolean {
        val level = Minecraft.getInstance().level ?: return true
        if (owner == null) return true
        val tick = level.gameTime
        if (defaultPlacementSyncTick != tick) {
            defaultPlacementSyncTick = tick
            defaultPlacementSyncOwner = owner
            return true
        }
        return defaultPlacementSyncOwner === owner
    }

    fun canPlaceAt(pos: BlockPos, checkSpaceEmpty: Boolean = true): Boolean {
        if (!isReplaceableForPlacement(pos)) return false
        if (checkSpaceEmpty && hasBlockingEntity(pos)) return false
        return findPlaceData(pos, allowAwaitingSupport = true, requireRaycast = false) != null
    }

    fun getPlacementHitResult(pos: BlockPos, checkSpaceEmpty: Boolean, raycast: Boolean): BlockHitResult? {
        return getPlacementHitResult(pos, checkSpaceEmpty, raycast, allowAwaitingSupport = true)
    }

    fun getPlacementHitResult(pos: BlockPos, checkSpaceEmpty: Boolean, raycast: Boolean, allowAwaitingSupport: Boolean): BlockHitResult? {
        if (!isReplaceableForPlacement(pos)) return null
        if (checkSpaceEmpty && hasBlockingEntity(pos)) return null
        return findPlaceData(pos, allowAwaitingSupport = allowAwaitingSupport, requireRaycast = raycast)?.hitResult
    }

    fun getPlacementRotation(pos: BlockPos, checkSpaceEmpty: Boolean = true, raycast: Boolean = false): CombatRotation? {
        val hitResult = getPlacementHitResult(pos, checkSpaceEmpty, raycast) ?: return null
        return CombatRotationUtil.rotationAt(hitResult.location).clamped()
    }

    fun canRaycastPlacementRotation(pos: BlockPos, rotation: CombatRotation, allowAwaitingSupport: Boolean, requireExactFace: Boolean): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val level = Minecraft.getInstance().level ?: return false
        val eye = player.eyePosition
        val reach = max(player.blockInteractionRange(), eye.distanceTo(Vec3.atCenterOf(pos)) + 1.0)
        val end = eye.add(vectorFromRotation(rotation).scale(reach))
        val result = level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        if (result.type != HitResult.Type.BLOCK) return false
        if (result.blockPos == pos) return true
        val data = findPlaceData(pos, allowAwaitingSupport, requireRaycast = false) ?: return false
        return result.blockPos == data.supportPos && (!requireExactFace || result.direction == data.hitResult.direction)
    }

    fun place(
        pos: BlockPos,
        slot: Int,
        rotateMode: RotateMode = RotateMode.NONE,
        switchMode: SwitchMode = SwitchMode.SILENT,
        placeMode: PlaceMode = PlaceMode.VANILLA,
        swing: Boolean = true,
        raycast: Boolean = false,
    ): Boolean {
        val hitResult = getPlacementHitResult(pos, true, raycast) ?: return false
        return place(pos, hitResult, slot, switchMode, placeMode, swing)
    }

    fun place(
        pos: BlockPos,
        hitResult: BlockHitResult,
        slot: Int,
        switchMode: SwitchMode,
        placeMode: PlaceMode,
        swing: Boolean,
    ): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        val level = mc.level ?: return false
        if (!CombatActionSync.canPlaceThisTick(player)) return false
        if (!level.getBlockState(pos).canBeReplaced()) return false

        val hand = resolvePlacementHand(slot)
        val useOffhand = hand == InteractionHand.OFF_HAND
        val currentSlot = player.inventory.selectedSlot
        val silent = switchMode == SwitchMode.SILENT
        if (!useOffhand && slot !in 0..8) return false

        if (!useOffhand) {
            InventoryUtil.swapToSlot(slot)
        }

        val result: InteractionResult = gameMode.useItemOn(player, hand, hitResult)
        val placed = result.consumesAction() || result == InteractionResult.SUCCESS
        if (placed) {
            CombatActionSync.markPlacement(player)
            awaiting[pos.immutable()] = System.currentTimeMillis()
            if (swing) player.swing(hand)
        }

        if (!useOffhand && silent && currentSlot != slot) {
            InventoryUtil.swapToSlot(currentSlot)
        }
        return placed
    }

    private fun findPlaceData(pos: BlockPos, allowAwaitingSupport: Boolean, requireRaycast: Boolean): PlaceData? {
        pruneAwaiting()
        val player = Minecraft.getInstance().player ?: return null
        val supports = Direction.entries
            .mapNotNull { side ->
                val supportPos = pos.relative(side.opposite)
                if (isSolidSupport(supportPos, allowAwaitingSupport)) SupportData(supportPos, side) else null
            }
        if (supports.isEmpty()) return null

        val eye = player.eyePosition
        return supports
            .asSequence()
            .flatMap { support -> faceHitCandidates(support.position, support.side).map { support to it }.asSequence() }
            .filter { (support, hit) ->
                !requireRaycast || matchesPlacementSupport(raycastFacePoint(eye, hit, support.side), support)
            }
            .map { (support, hit) ->
                PlaceData(
                    BlockHitResult(hit, support.side, support.position, false),
                    support.position,
                    eye.distanceToSqr(hit) + supportSelectionPenalty(support),
                )
            }
            .minByOrNull { it.distanceSq }
    }

    private fun supportSelectionPenalty(support: SupportData): Double {
        return if (support.side == Direction.DOWN) OVERHEAD_SUPPORT_DISTANCE_PENALTY else 0.0
    }

    private fun matchesPlacementSupport(result: BlockHitResult?, support: SupportData): Boolean {
        return result != null && result.type == HitResult.Type.BLOCK && result.blockPos == support.position && result.direction == support.side
    }

    private fun raycastFacePoint(start: Vec3, candidate: Vec3, side: Direction): BlockHitResult? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val player = mc.player ?: return null
        val insideFace = candidate.add(
            -side.stepX * FACE_RAYCAST_INSIDE_OFFSET,
            -side.stepY * FACE_RAYCAST_INSIDE_OFFSET,
            -side.stepZ * FACE_RAYCAST_INSIDE_OFFSET,
        )
        return level.clip(ClipContext(start, insideFace, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
    }

    private fun faceHitCandidates(supportPos: BlockPos, side: Direction): List<Vec3> {
        val center = Vec3.atCenterOf(supportPos).add(side.stepX * 0.5, side.stepY * 0.5, side.stepZ * 0.5)
        val (axisU, axisV) = when (side.axis) {
            Direction.Axis.Y -> Vec3(FACE_SAMPLE_RADIUS, 0.0, 0.0) to Vec3(0.0, 0.0, FACE_SAMPLE_RADIUS)
            Direction.Axis.X -> Vec3(0.0, FACE_SAMPLE_RADIUS, 0.0) to Vec3(0.0, 0.0, FACE_SAMPLE_RADIUS)
            Direction.Axis.Z -> Vec3(FACE_SAMPLE_RADIUS, 0.0, 0.0) to Vec3(0.0, FACE_SAMPLE_RADIUS, 0.0)
        }
        return listOf(
            center,
            center.add(axisU),
            center.subtract(axisU),
            center.add(axisV),
            center.subtract(axisV),
            center.add(axisU).add(axisV),
            center.add(axisU).subtract(axisV),
            center.subtract(axisU).add(axisV),
            center.subtract(axisU).subtract(axisV),
        ).distinctBy { Triple(it.x, it.y, it.z) }
    }

    private fun hasBlockingEntity(pos: BlockPos): Boolean {
        val level = Minecraft.getInstance().level ?: return true
        return level.getEntities(null, AABB(pos)) { entity ->
            entity !is ItemEntity && entity !is ExperienceOrb
        }.isNotEmpty()
    }

    private fun isReplaceableForPlacement(pos: BlockPos): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        return level.getBlockState(pos).canBeReplaced()
    }

    private fun isSolidSupport(pos: BlockPos, allowAwaitingSupport: Boolean): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val state = level.getBlockState(pos)
        return (!state.isAir && state.isSolid) || (allowAwaitingSupport && awaiting.containsKey(pos) && !state.canBeReplaced())
    }

    private fun pruneAwaiting() {
        val now = System.currentTimeMillis()
        awaiting.entries.removeIf { now - it.value > AWAITING_EXPIRE_MS }
    }

    private fun vectorFromRotation(rotation: CombatRotation): Vec3 {
        val yaw = Math.toRadians(rotation.yaw.toDouble())
        val pitch = Math.toRadians(rotation.pitch.toDouble())
        val x = -kotlin.math.sin(yaw) * kotlin.math.cos(pitch)
        val y = -kotlin.math.sin(pitch)
        val z = kotlin.math.cos(yaw) * kotlin.math.cos(pitch)
        return Vec3(x, y, z)
    }

    private data class PlaceData(val hitResult: BlockHitResult, val supportPos: BlockPos, val distanceSq: Double)

    private data class SupportData(val position: BlockPos, val side: Direction)
}
