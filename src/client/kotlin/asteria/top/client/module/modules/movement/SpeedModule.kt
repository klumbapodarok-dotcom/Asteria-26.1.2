package asteria.top.client.module.modules.movement

import com.mojang.blaze3d.platform.InputConstants
import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.util.combat.CombatRotationUtil
import asteria.top.client.util.combat.TrapPlaceUtil
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin

class SpeedModule : Module(
    name = "Speed",
    category = ModuleCategory.MOVEMENT,
    description = "Movement bypasses for Polar and FunTime.",
    enabledByDefault = false,
) {
    enum class Mode(val label: String) {
        POLAR("Polar"),
        FUNTIME_ICE("Funtime Ice"),
    }

    private val mode = setting(EnumSetting("Mode", Mode.entries.toTypedArray(), Mode.POLAR) { it.label })
    private var iceLayerY = Int.MIN_VALUE
    private var lastIcePos: BlockPos? = null
    private var lastIceTick = Int.MIN_VALUE
    private var visualIcePitch = Float.NaN
    private var visualIcePitchTick = Int.MIN_VALUE

    fun tick() {
        if (!enabled) return

        when (mode.value) {
            Mode.POLAR -> {
                resetIceState()
                tickPolar()
            }
            Mode.FUNTIME_ICE -> tickFuntimeIce()
        }
    }

    override fun onDisable() {
        resetIceState()
    }

    fun modelPitch(original: Float): Float {
        val player = Minecraft.getInstance().player ?: return original
        val recentlyAimed = visualIcePitchTick != Int.MIN_VALUE &&
            player.tickCount - visualIcePitchTick <= ICE_PLACE_DELAY_TICKS
        return if (enabled && mode.value == Mode.FUNTIME_ICE && recentlyAimed && !visualIcePitch.isNaN()) {
            visualIcePitch
        } else {
            original
        }
    }

    private fun tickPolar() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        var collisions = 0
        level.entitiesForRendering().forEach { entity ->
            if (entity is LivingEntity && entity !== player && entity !is ArmorStand &&
                player.boundingBox.intersects(entity.boundingBox)
            ) {
                collisions++
            }
        }

        if (collisions > 0) {
            val movement = calculateDirection(mc, 0.08 * collisions)
            player.addDeltaMovement(Vec3(movement.first, 0.0, movement.second))
        }
    }

    private fun tickFuntimeIce() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return resetIceState()
        val level = mc.level ?: return resetIceState()
        if (!player.isAlive || player.isSpectator) return resetIceState()

        val inputDirection = calculateDirection(mc, 1.0)
        val inputLength = hypot(inputDirection.first, inputDirection.second)
        if (inputLength < 0.001) return

        val iceSlot = findIceSlot() ?: return
        if (player.onGround()) player.jumpFromGround()
        val velocity = player.deltaMovement
        val horizontalSpeed = hypot(velocity.x, velocity.z)
        val velocityWeight = if (horizontalSpeed > 0.035) 0.68 else 0.0
        var directionX = inputDirection.first / inputLength
        var directionZ = inputDirection.second / inputLength
        if (velocityWeight > 0.0) {
            directionX = directionX * (1.0 - velocityWeight) + velocity.x / horizontalSpeed * velocityWeight
            directionZ = directionZ * (1.0 - velocityWeight) + velocity.z / horizontalSpeed * velocityWeight
            val blendedLength = hypot(directionX, directionZ).coerceAtLeast(0.001)
            directionX /= blendedLength
            directionZ /= blendedLength
        }

        initializeIceLayer(player.blockPosition().y, player.boundingBox.minY)
        val lead = (0.62 + horizontalSpeed * 1.75).coerceIn(0.62, 1.28)
        val lateralX = -directionZ * 0.24
        val lateralZ = directionX * 0.24
        val horizontalCandidates = listOf(
            directionX * lead to directionZ * lead,
            directionX * (lead + 0.36) to directionZ * (lead + 0.36),
            directionX * lead + lateralX to directionZ * lead + lateralZ,
            directionX * lead - lateralX to directionZ * lead - lateralZ,
            directionX * (lead - 0.28).coerceAtLeast(0.45) to directionZ * (lead - 0.28).coerceAtLeast(0.45),
        )
        val feetY = Mth.floor(player.boundingBox.minY + 0.001)
        val layerCandidates = linkedSetOf(iceLayerY, feetY, feetY - 1)

        val placement = horizontalCandidates
            .asSequence()
            .map { (offsetX, offsetZ) ->
                BlockPos.containing(player.x + offsetX, 0.0, player.z + offsetZ)
            }
            .flatMap { horizontal ->
                layerCandidates.asSequence().map { y -> BlockPos(horizontal.x, y, horizontal.z) }
            }
            .distinct()
            .filter { pos ->
                val repeatedTooSoon = pos == lastIcePos && player.tickCount - lastIceTick <= 3
                !repeatedTooSoon && !player.boundingBox.inflate(-0.02).intersects(AABB(pos))
            }
            .mapNotNull { pos ->
                val hit = TrapPlaceUtil.getPlacementHitResult(
                    pos = pos,
                    checkSpaceEmpty = true,
                    raycast = false,
                    allowAwaitingSupport = true,
                )
                if (hit == null) null else pos to hit
            }
            .firstOrNull() ?: return

        if (lastIceTick != Int.MIN_VALUE && player.tickCount - lastIceTick < ICE_PLACE_DELAY_TICKS) return
        if (!TrapPlaceUtil.tryAcquireDefaultPlacementWindow(this)) return
        sendSilentIceRotation(placement.second.location)
        if (TrapPlaceUtil.place(
                pos = placement.first,
                hitResult = placement.second,
                slot = iceSlot,
                switchMode = TrapPlaceUtil.SwitchMode.SILENT,
                placeMode = TrapPlaceUtil.PlaceMode.VANILLA,
                swing = true,
            )
        ) {
            iceLayerY = placement.first.y
            lastIcePos = placement.first.immutable()
            lastIceTick = player.tickCount
        }
    }

    /**
     * FunTime checks the placement against the server-side look direction. Send the
     * steep downward rotation only over the wire so neither the camera nor the local
     * player model snaps down when the perspective is changed.
     */
    private fun sendSilentIceRotation(hitPosition: Vec3) {
        val player = Minecraft.getInstance().player ?: return
        val target = CombatRotationUtil.rotationAt(hitPosition)
        val pitch = ICE_PITCH
        visualIcePitch = pitch
        visualIcePitchTick = player.tickCount
        player.connection.send(
            ServerboundMovePlayerPacket.Rot(
                target.yaw,
                pitch,
                player.onGround(),
                player.horizontalCollision,
            ),
        )
    }

    private fun initializeIceLayer(feetBlockY: Int, minY: Double) {
        if (iceLayerY != Int.MIN_VALUE) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        val below = BlockPos.containing(player.x, minY - 0.05, player.z)
        iceLayerY = if (level.getBlockState(below).block in ICE_BLOCKS) below.y else feetBlockY
    }

    private fun findIceSlot(): Int? {
        val player = Minecraft.getInstance().player ?: return null
        val selected = player.inventory.selectedSlot
        if (isIceBlock(player.inventory.getItem(selected).item as? BlockItem)) return selected
        return (0..8).firstOrNull { slot -> isIceBlock(player.inventory.getItem(slot).item as? BlockItem) }
    }

    private fun isIceBlock(item: BlockItem?): Boolean = item?.block in ICE_BLOCKS

    private fun resetIceState() {
        iceLayerY = Int.MIN_VALUE
        lastIcePos = null
        lastIceTick = Int.MIN_VALUE
        visualIcePitch = Float.NaN
        visualIcePitchTick = Int.MIN_VALUE
    }

    private fun calculateDirection(mc: Minecraft, distance: Double): Pair<Double, Double> {
        var forward = 0.0f
        var sideways = 0.0f
        val window = mc.window

        if (InputConstants.isKeyDown(window, mc.options.keyUp.defaultKey.value)) forward++
        if (InputConstants.isKeyDown(window, mc.options.keyDown.defaultKey.value)) forward--
        if (InputConstants.isKeyDown(window, mc.options.keyLeft.defaultKey.value)) sideways++
        if (InputConstants.isKeyDown(window, mc.options.keyRight.defaultKey.value)) sideways--

        var yaw = mc.player?.yRot ?: return 0.0 to 0.0
        if (forward != 0.0f) {
            if (sideways > 0.0f) yaw += if (forward > 0.0f) -45.0f else 45.0f
            else if (sideways < 0.0f) yaw += if (forward > 0.0f) 45.0f else -45.0f
            sideways = 0.0f
            forward = if (forward > 0.0f) 1.0f else -1.0f
        }

        val radians = Math.toRadians((yaw + 90.0f).toDouble())
        val sinYaw = sin(radians)
        val cosYaw = cos(radians)
        val x = forward * distance * cosYaw + sideways * distance * sinYaw
        val z = forward * distance * sinYaw - sideways * distance * cosYaw
        return x to z
    }

    private companion object {
        const val ICE_PITCH = 90.0f
        const val ICE_PLACE_DELAY_TICKS = 4
        val ICE_BLOCKS: Set<Block> = setOf(Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE)
    }
}
