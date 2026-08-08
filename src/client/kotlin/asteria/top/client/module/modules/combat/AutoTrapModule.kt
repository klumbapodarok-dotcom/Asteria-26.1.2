package asteria.top.client.module.modules.combat

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.module.setting.IntSetting
import asteria.top.client.module.setting.MultiBooleanSetting
import asteria.top.client.util.combat.CombatRotation
import asteria.top.client.util.combat.CombatRotationManager
import asteria.top.client.util.combat.CombatRotationUtil
import asteria.top.client.util.combat.TargetManager
import asteria.top.client.util.combat.TrapPlaceUtil
import asteria.top.client.util.player.InventoryUtil
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import kotlin.math.floor

class AutoTrapModule : Module(
    name = "Auto Trap",
    category = ModuleCategory.COMBAT,
    description = "Places configured blocks around a nearby player using shared trap placement infrastructure.",
    enabledByDefault = false,
) {
    enum class TrapMode(val label: String) {
        FULL("Full"),
        LEGS("Legs"),
        HEAD("Head"),
    }

    enum class RotateMode(val label: String) {
        DEFAULT("Default"),
        NONE("None"),
    }

    enum class PlaceMode(val label: String) {
        PACKET("Packet"),
        NORMAL("Normal"),
    }

    enum class PreferredHand(val label: String) {
        MAIN("Main"),
        OFFHAND("Offhand"),
    }

    private val range = setting(IntSetting("Target Range", 5, 1, 7, 1))
    private val placeDelay = setting(IntSetting("Place Delay", 0, 0, 10, 1))
    private val blocksPerTick = setting(IntSetting("Blocks Per Tick", 20, 1, 20, 1))
    private val trapMode = setting(EnumSetting("Mode", TrapMode.entries.toTypedArray(), TrapMode.FULL) { it.label })
    private val rotate = setting(EnumSetting("Rotate", RotateMode.entries.toTypedArray(), RotateMode.DEFAULT) { it.label })
    private val maxYawSpeed = setting(FloatSetting("Max yaw speed", 180.0f, 1.0f, 180.0f, 1.0f))
    private val maxPitchSpeed = setting(FloatSetting("Max pitch speed", 90.0f, 1.0f, 90.0f, 1.0f))
    private val placeMode = setting(EnumSetting("Place Mode", PlaceMode.entries.toTypedArray(), PlaceMode.NORMAL) { it.label })
    private val silentSwap = setting(BooleanSetting("Silent Swap", false))
    private val preferredHand = setting(EnumSetting("Preferred hand", PreferredHand.entries.toTypedArray(), PreferredHand.MAIN) { it.label })
    private val blocks = setting(
        MultiBooleanSetting(
            "Blocks",
            BooleanSetting("Obsidian", true),
            BooleanSetting("Ender Chest", true),
            BooleanSetting("Crying Obsidian", true),
            BooleanSetting("Cobblestone", true),
            BooleanSetting("Mossy Cobblestone", true),
            BooleanSetting("Stone", true),
            BooleanSetting("Smooth Stone", true),
            BooleanSetting("Andesite", true),
            BooleanSetting("Diorite", true),
            BooleanSetting("Granite", true),
            BooleanSetting("Tuff", true),
            BooleanSetting("Calcite", true),
            BooleanSetting("Deepslate", true),
            BooleanSetting("Cobbled Deepslate", true),
            BooleanSetting("Blackstone", true),
        )
    )
    private val swing = setting(BooleanSetting("Swing", true))
    private val raycast = setting(BooleanSetting("Raycast", false))
    private val throughWalls = setting(BooleanSetting("Through Walls", false))
    private val autoSwapBack = setting(BooleanSetting("Auto Swap Back", false))
    private val renderOverlay = setting(BooleanSetting("Render", true))

    private val targetManager = TargetManager()
    var target: Player? = null
        private set

    private var currentPlaceQueue = mutableListOf<BlockPos>()
    private var currentQueueIndex = 0
    private var placeDelayCounter = 0
    private var previousSlot = -1
    private var offhandRestoreSlot = -1
    private var placementTickAge = -1
    private var placedThisTick = 0
    private var pendingPlace: BlockPos? = null
    private var pendingHitResult: BlockHitResult? = null
    private var pendingRotation: CombatRotation? = null
    private var pendingAge = 0
    private val deferredRotationPlacements = mutableSetOf<BlockPos>()

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        val player = Minecraft.getInstance().player
        if (player != null && previousSlot != -1 && player.inventory.selectedSlot != previousSlot) {
            InventoryUtil.swapToSlot(previousSlot)
        }
        restoreManagedOffhandState(clearWhenBlocked = true)
        resetState()
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        val player = mc.player
        val level = mc.level
        if (!enabled || player == null || level == null || mc.gameMode == null) {
            if (!enabled) resetState()
            return
        }

        syncPlacementTickBudget(player)
        if (preferredHand.value != PreferredHand.OFFHAND) {
            restoreManagedOffhandState(clearWhenBlocked = false)
        }

        if (placeDelayCounter > 0) {
            placeDelayCounter -= 1
            return
        }

        target = findTarget()
        if (target == null) {
            clearPlacementState()
            swapBack()
            restoreManagedOffhandState(clearWhenBlocked = false)
            return
        }

        rebuildPlaceQueue()
        if (currentPlaceQueue.isEmpty()) {
            clearPlacementState()
            swapBack()
            restoreManagedOffhandState(clearWhenBlocked = false)
            return
        }

        val blockSlot = findTrapBlockSlot()
        if (blockSlot == -1) {
            clearPlacementState()
            swapBack()
            restoreManagedOffhandState(clearWhenBlocked = false)
            return
        }

        val maxBlocksPerTick = blocksPerTick.value
        if (placedThisTick >= maxBlocksPerTick) {
            clearPendingPlacement(releaseRotation = false)
            placeDelayCounter = placeDelay.value
            return
        }

        val pending = pendingPlace
        val hit = pendingHitResult
        if (pending != null && hit != null) {
            pendingRotation = updatePlacementRotation(player, hit) ?: pendingRotation
            pendingAge += 1
            if (!level.getBlockState(pending).canBeReplaced()) {
                clearPendingPlacement(releaseRotation = false)
            } else if (shouldWaitForRotationPacket()) {
                return
            } else if (!canRaycastPendingPlacement(pending)) {
                deferPendingPlacement(pending)
            } else if (TrapPlaceUtil.tryAcquireDefaultPlacementWindow(this)) {
                prepareNormalSwapBack(blockSlot)
                if (TrapPlaceUtil.place(pending, hit, blockSlot, getSwitchMode(), getPlaceMode(), swing.value)) {
                    placedThisTick += 1
                    placeDelayCounter = placeDelay.value
                    deferredRotationPlacements.remove(pending)
                }
                clearPendingPlacement(releaseRotation = false)
            } else {
                return
            }
        }

        if (placedThisTick >= maxBlocksPerTick) {
            placeDelayCounter = placeDelay.value
            return
        }

        val nextPos = advanceToNextValid()
        if (nextPos != null) {
            val nextHit = findPlacementInfo(nextPos)
            pendingPlace = if (nextHit != null) nextPos else null
            pendingHitResult = nextHit
            pendingRotation = if (nextHit != null) updatePlacementRotation(player, nextHit) else null
            pendingAge = 0
        } else {
            clearPendingPlacement(releaseRotation = true)
        }
    }

    fun shouldOverrideAuraRotation(): Boolean {
        return enabled && rotate.value == RotateMode.DEFAULT && target != null && pendingPlace != null && pendingHitResult != null && CombatRotationManager.hasRotation()
    }

    fun hasPacketRotation(): Boolean {
        return shouldOverrideAuraRotation()
    }

    fun packetYaw(playerYaw: Float): Float {
        return if (shouldOverrideAuraRotation()) CombatRotationManager.packetYaw(playerYaw) else playerYaw
    }

    fun packetPitch(playerPitch: Float): Float {
        return if (shouldOverrideAuraRotation()) CombatRotationManager.packetPitch(playerPitch) else playerPitch
    }

    fun renderGizmos() {
        val mc = Minecraft.getInstance()
        if (!enabled || !renderOverlay.value || mc.player == null || mc.level == null) return
        val placements = getRenderablePlacements()
        if (placements.isEmpty()) return

        mc.levelRenderer.collectPerFrameGizmos().use {
            placements.forEachIndexed { index, pos ->
                val fill = if (index == 0) 0x5088FF82 else 0x3088FF82
                val stroke = if (index == 0) 0xFF88FF82.toInt() else 0xAA88FF82.toInt()
                Gizmos.cuboid(AABB(pos), GizmoStyle.strokeAndFill(stroke, 1.75f, fill), false).setAlwaysOnTop()
            }
        }
    }

    private fun findTarget(): Player? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        targetManager.searchTargets(level.entitiesForRendering(), range.value.toFloat())
        targetManager.validateTarget { entity ->
            entity is Player &&
                entity !== player &&
                entity.isAlive &&
                !entity.isRemoved
        }
        return targetManager.currentTarget as? Player
    }

    private fun rebuildPlaceQueue() {
        val player = Minecraft.getInstance().player ?: return
        val level = Minecraft.getInstance().level ?: return
        val locked = target ?: return
        val playerBox = player.boundingBox
        currentPlaceQueue = getTrapPositions(locked)
            .asSequence()
            .filter { level.getBlockState(it).canBeReplaced() }
            .filter { !playerBox.intersects(AABB(it)) }
            .filter { findPlacementInfo(it) != null }
            .distinct()
            .sortedWith(
                compareBy<BlockPos> { deferredRotationPlacements.contains(it) }
                    .thenByDescending { placementDistanceSq(player, it) }
            )
            .toMutableList()
        currentQueueIndex = 0
    }

    private fun advanceToNextValid(): BlockPos? {
        val player = Minecraft.getInstance().player ?: return null
        val level = Minecraft.getInstance().level ?: return null
        val playerBox = player.boundingBox
        while (currentQueueIndex < currentPlaceQueue.size) {
            val pos = currentPlaceQueue[currentQueueIndex++]
            if (!level.getBlockState(pos).canBeReplaced()) continue
            if (playerBox.intersects(AABB(pos))) continue
            if (findPlacementInfo(pos) == null) continue
            return pos
        }
        return null
    }

    private fun findPlacementInfo(pos: BlockPos): BlockHitResult? {
        return TrapPlaceUtil.getPlacementHitResult(
            pos = pos,
            checkSpaceEmpty = true,
            raycast = raycast.value && !throughWalls.value,
            allowAwaitingSupport = false,
        )
    }

    private fun getTrapPositions(entity: LivingEntity): List<BlockPos> {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return emptyList()
        val player = mc.player ?: return emptyList()
        val foundation = getPlayerFoundation(entity)
        if (foundation.isEmpty()) return emptyList()

        val surround = linkedSetOf<BlockPos>()
        foundation.forEach { pos ->
            Direction.Plane.HORIZONTAL.forEach { dir ->
                surround += pos.relative(dir)
            }
        }
        surround.removeAll(foundation.toSet())
        val surroundList = surround.toList()
        val positions = mutableListOf<BlockPos>()

        positions += surroundList.map { it.below() }
        if (trapMode.value == TrapMode.FULL || trapMode.value == TrapMode.LEGS) {
            positions += surroundList
        }
        positions += surroundList.map { it.above() }

        val torsoLayerComplete = surroundList.none { level.getBlockState(it.above()).canBeReplaced() }
        val playerIsElevated = player.y > entity.y || player.onGround()
        if ((trapMode.value == TrapMode.FULL || trapMode.value == TrapMode.HEAD) && torsoLayerComplete && playerIsElevated) {
            positions += foundation.map { it.above(2) }
            surroundList.maxByOrNull { player.distanceToSqr(Vec3Compat.centerOf(it)) }?.let { positions += it.above(2) }
        }
        return positions.distinct()
    }

    private fun getPlayerFoundation(entity: LivingEntity): List<BlockPos> {
        val box = entity.boundingBox
        val y = Mth.floor(entity.y)
        val positions = mutableListOf<BlockPos>()
        var x = floor(box.minX).toInt()
        while (x < box.maxX) {
            var z = floor(box.minZ).toInt()
            while (z < box.maxZ) {
                positions += BlockPos(x, y, z)
                z += 1
            }
            x += 1
        }
        return positions.distinct()
    }

    private fun findTrapBlockSlot(): Int {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return -1
        if (preferredHand.value == PreferredHand.OFFHAND) {
            if (hasAllowedTrapBlock(player.offhandItem)) return TrapPlaceUtil.OFFHAND_SLOT
            return if (prepareOffhandTrapBlock()) TrapPlaceUtil.OFFHAND_SLOT else -1
        }

        if (hasAllowedTrapBlock(player.mainHandItem)) return player.inventory.selectedSlot
        return (0..8).firstOrNull { hasAllowedTrapBlock(player.inventory.getItem(it)) } ?: -1
    }

    private fun prepareOffhandTrapBlock(): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (mc.screen != null || !player.containerMenu.carried.isEmpty) return false
        val sourceSlot = findPreferredOffhandTrapSourceSlot()
        if (sourceSlot == -1) return false
        if (offhandRestoreSlot == -1) offhandRestoreSlot = sourceSlot
        return InventoryUtil.swapToOffhand(sourceSlot)
    }

    private fun findPreferredOffhandTrapSourceSlot(): Int {
        val player = Minecraft.getInstance().player ?: return -1
        val selectedScreenSlot = InventoryUtil.toScreenSlot(player.inventory.selectedSlot)
        if (selectedScreenSlot != offhandRestoreSlot && hasAllowedTrapBlock(player.mainHandItem)) {
            return selectedScreenSlot
        }

        for (slot in 0..8) {
            val screenSlot = InventoryUtil.toScreenSlot(slot)
            if (screenSlot != offhandRestoreSlot && hasAllowedTrapBlock(player.inventory.getItem(slot))) {
                return screenSlot
            }
        }
        for (slot in 9..35) {
            val screenSlot = InventoryUtil.toScreenSlot(slot)
            if (screenSlot != offhandRestoreSlot && hasAllowedTrapBlock(player.inventory.getItem(slot))) {
                return screenSlot
            }
        }
        return -1
    }

    private fun restoreManagedOffhandState(clearWhenBlocked: Boolean) {
        val restoreSlot = offhandRestoreSlot
        if (restoreSlot == -1) return

        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player == null || mc.gameMode == null || mc.screen != null || !player.containerMenu.carried.isEmpty) {
            if (clearWhenBlocked) offhandRestoreSlot = -1
            return
        }

        offhandRestoreSlot = -1
        InventoryUtil.swapToOffhand(restoreSlot)
    }

    private fun prepareNormalSwapBack(slot: Int) {
        val player = Minecraft.getInstance().player ?: return
        if (getSwitchMode() != TrapPlaceUtil.SwitchMode.NORMAL || TrapPlaceUtil.isOffhandSlot(slot)) {
            previousSlot = -1
            return
        }
        val currentSlot = player.inventory.selectedSlot
        if (previousSlot == -1 && currentSlot != slot) {
            previousSlot = currentSlot
        }
    }

    private fun swapBack() {
        val player = Minecraft.getInstance().player ?: run {
            previousSlot = -1
            return
        }
        if (previousSlot != -1 && autoSwapBack.value && player.inventory.selectedSlot != previousSlot) {
            InventoryUtil.swapToSlot(previousSlot)
        }
        previousSlot = -1
    }

    private fun hasAllowedTrapBlock(stack: ItemStack): Boolean {
        val item = stack.item as? BlockItem ?: return false
        return !stack.isEmpty && isAllowedTrapBlock(item.block)
    }

    private fun isAllowedTrapBlock(block: Block): Boolean {
        return allowedBlockLabels[block]?.let { blocks.enabled(it) } == true
    }

    private fun getSwitchMode(): TrapPlaceUtil.SwitchMode {
        return if (silentSwap.value) TrapPlaceUtil.SwitchMode.SILENT else TrapPlaceUtil.SwitchMode.NORMAL
    }

    private fun getPlaceMode(): TrapPlaceUtil.PlaceMode {
        return if (placeMode.value == PlaceMode.PACKET) TrapPlaceUtil.PlaceMode.PACKET else TrapPlaceUtil.PlaceMode.VANILLA
    }

    private fun getRenderablePlacements(): List<BlockPos> {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return emptyList()
        val level = mc.level ?: return emptyList()
        val locked = target ?: return emptyList()
        val rangeSq = range.value * range.value.toDouble()
        val playerBox = player.boundingBox
        return getTrapPositions(locked)
            .asSequence()
            .filter { player.position().distanceToSqr(Vec3Compat.centerOf(it)) < rangeSq }
            .filter { level.getBlockState(it).canBeReplaced() }
            .filter { !playerBox.intersects(AABB(it)) }
            .filter { findPlacementInfo(it) != null }
            .distinct()
            .sortedByDescending { placementDistanceSq(player, it) }
            .take(16)
            .toList()
    }

    private fun clearPlacementState() {
        currentPlaceQueue.clear()
        currentQueueIndex = 0
        clearPendingPlacement(releaseRotation = true)
    }

    private fun clearPendingPlacement(releaseRotation: Boolean = false) {
        pendingPlace = null
        pendingHitResult = null
        pendingRotation = null
        pendingAge = 0
        if (releaseRotation) {
            CombatRotationManager.clear(this)
        }
    }

    private fun deferPendingPlacement(pos: BlockPos) {
        deferredRotationPlacements += pos.immutable()
        clearPendingPlacement(releaseRotation = false)
    }

    private fun updatePlacementRotation(player: Player, hitResult: BlockHitResult): CombatRotation? {
        if (rotate.value != RotateMode.DEFAULT) return null
        val desired = applyPlacementRotationVariance(
            CombatRotationUtil.rotationAt(hitResult.location).clamped(),
            hitResult,
        )
        return CombatRotationManager.requestRotation(
            owner = this,
            priority = 100,
            desired = desired,
            maxYawSpeed = maxYawSpeed.value,
            maxPitchSpeed = maxPitchSpeed.value,
            aimPosition = hitResult.location,
            targetCenter = target?.boundingBox?.center,
        )
    }

    private fun applyPlacementRotationVariance(rotation: CombatRotation, hitResult: BlockHitResult): CombatRotation {
        val hash = Mth.murmurHash3Mixer(
            hitResult.blockPos.x * 73428767 xor
                hitResult.blockPos.y * 91227153 xor
                hitResult.blockPos.z * 42317861 xor
                hitResult.direction.ordinal * 187739,
        )
        val yawOffset = (((hash and 0xF) - 7.5f) * PLACEMENT_ROTATION_VARIANCE_STEP)
        val pitchOffset = ((((hash ushr 4) and 0xF) - 7.5f) * PLACEMENT_ROTATION_VARIANCE_STEP)
        return CombatRotation(
            rotation.yaw + yawOffset,
            rotation.pitch + pitchOffset,
        ).clamped()
    }

    private fun canRaycastPendingPlacement(pos: BlockPos): Boolean {
        if (!raycast.value) return true
        if (throughWalls.value) return true
        val rotation = if (rotate.value == RotateMode.DEFAULT) {
            pendingRotation ?: return false
        } else {
            val player = Minecraft.getInstance().player ?: return false
            CombatRotation(player.yRot, player.xRot)
        }
        return TrapPlaceUtil.canRaycastPlacementRotation(
            pos = pos,
            rotation = rotation,
            allowAwaitingSupport = true,
            requireExactFace = false,
        )
    }

    private fun shouldWaitForRotationPacket(): Boolean {
        return rotate.value == RotateMode.DEFAULT && placeDelay.value > 0 && pendingAge <= 1
    }

    private fun syncPlacementTickBudget(player: Player) {
        val currentTick = player.tickCount
        if (placementTickAge != currentTick) {
            placementTickAge = currentTick
            placedThisTick = 0
        }
    }

    private fun placementDistanceSq(player: Player, pos: BlockPos): Double {
        return player.position().distanceToSqr(Vec3Compat.centerOf(pos))
    }

    private fun resetState() {
        target = null
        targetManager.releaseTarget()
        currentPlaceQueue.clear()
        currentQueueIndex = 0
        placeDelayCounter = 0
        previousSlot = -1
        offhandRestoreSlot = -1
        placementTickAge = -1
        placedThisTick = 0
        deferredRotationPlacements.clear()
        clearPendingPlacement(releaseRotation = true)
    }

    private object Vec3Compat {
        fun centerOf(pos: BlockPos) = net.minecraft.world.phys.Vec3.atCenterOf(pos)
    }

    private companion object {
        private const val PLACEMENT_ROTATION_VARIANCE_STEP = 0.0035f

        private val allowedBlockLabels = mapOf(
            Blocks.OBSIDIAN to "Obsidian",
            Blocks.ENDER_CHEST to "Ender Chest",
            Blocks.CRYING_OBSIDIAN to "Crying Obsidian",
            Blocks.COBBLESTONE to "Cobblestone",
            Blocks.MOSSY_COBBLESTONE to "Mossy Cobblestone",
            Blocks.STONE to "Stone",
            Blocks.SMOOTH_STONE to "Smooth Stone",
            Blocks.ANDESITE to "Andesite",
            Blocks.DIORITE to "Diorite",
            Blocks.GRANITE to "Granite",
            Blocks.TUFF to "Tuff",
            Blocks.CALCITE to "Calcite",
            Blocks.DEEPSLATE to "Deepslate",
            Blocks.COBBLED_DEEPSLATE to "Cobbled Deepslate",
            Blocks.BLACKSTONE to "Blackstone",
        )
    }
}
