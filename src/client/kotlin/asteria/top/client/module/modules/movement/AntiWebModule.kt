package asteria.top.client.module.modules.movement

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.FloatSetting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

class AntiWebModule : Module(
    name = "AntiWeb",
    category = ModuleCategory.MOVEMENT,
    description = "Applies controlled motion while you are stuck in cobwebs.",
    enabledByDefault = false,
) {
    private val yMotion = setting(FloatSetting("Y Motion", 0.995f, 0.0f, 2.0f, 0.01f))
    private val xzMotion = setting(FloatSetting("XZ Motion", 0.19175f, 0.0f, 1.0f, 0.005f))

    fun tick() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!enabled) return
        if (!isInWeb(mc)) return
        if (!hasMotionKeyDown(mc)) return

        val horizontal = horizontalMotion(mc, xzMotion.value.toDouble())
        player.deltaMovement = Vec3(horizontal.x, verticalMotion(mc), horizontal.z)
    }

    private fun verticalMotion(mc: Minecraft): Double {
        val speed = yMotion.value.coerceAtLeast(0.0f).toDouble()
        return when {
            mc.options.keyJump.isDown -> speed
            mc.options.keyShift.isDown -> -speed
            else -> 0.0
        }
    }

    private fun horizontalMotion(mc: Minecraft, speed: Double): Vec3 {
        val player = mc.player ?: return Vec3.ZERO
        val forward = movementMultiplier(mc.options.keyUp.isDown, mc.options.keyDown.isDown)
        val strafe = movementMultiplier(mc.options.keyLeft.isDown, mc.options.keyRight.isDown)
        if (forward == 0.0f && strafe == 0.0f) return Vec3.ZERO

        var direction = player.yRot
        if (forward < 0.0f) direction += 180.0f
        val forwardFactor = when {
            forward < 0.0f -> -0.5f
            forward > 0.0f -> 0.5f
            else -> 1.0f
        }
        if (strafe > 0.0f) direction -= 90.0f * forwardFactor
        if (strafe < 0.0f) direction += 90.0f * forwardFactor

        val radians = Math.toRadians(direction.toDouble())
        return Vec3(-sin(radians) * speed, 0.0, cos(radians) * speed)
    }

    private fun hasMotionKeyDown(mc: Minecraft): Boolean {
        return mc.options.keyJump.isDown ||
            mc.options.keyShift.isDown ||
            mc.options.keyUp.isDown ||
            mc.options.keyDown.isDown ||
            mc.options.keyLeft.isDown ||
            mc.options.keyRight.isDown
    }

    private fun isInWeb(mc: Minecraft): Boolean {
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        val center = player.blockPosition()

        for (x in center.x - 2..center.x + 2) {
            for (y in center.y - 1..center.y + 4) {
                for (z in center.z - 2..center.z + 2) {
                    val pos = BlockPos(x, y, z)
                    if (player.boundingBox.intersects(AABB(pos)) && level.getBlockState(pos).`is`(Blocks.COBWEB)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun movementMultiplier(positive: Boolean, negative: Boolean): Float {
        if (positive == negative) return 0.0f
        return if (positive) 1.0f else -1.0f
    }
}
