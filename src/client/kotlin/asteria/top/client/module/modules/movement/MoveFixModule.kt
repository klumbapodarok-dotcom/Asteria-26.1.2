package asteria.top.client.module.modules.movement

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.EnumSetting
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2

class MoveFixModule : Module(
    name = "MoveFix",
    category = ModuleCategory.MOVEMENT,
    description = "Keeps movement aligned while combat rotations are active.",
    enabledByDefault = false,
) {
    enum class Mode(val label: String) {
        FOCUS("Focus"),
        FREE("Free"),
    }

    data class CorrectedInput(
        val forward: Boolean,
        val backward: Boolean,
        val left: Boolean,
        val right: Boolean,
    ) {
        val forwardImpulse: Float = if (forward == backward) 0.0f else if (forward) 1.0f else -1.0f
        val sidewaysImpulse: Float = if (left == right) 0.0f else if (left) 1.0f else -1.0f

        fun withPassthrough(original: Input): Input {
            return Input(forward, backward, left, right, original.jump(), original.shift(), original.sprint())
        }

        companion object {
            fun fromImpulses(forward: Float, strafe: Float): CorrectedInput {
                return CorrectedInput(
                    forward = forward > 0.0f,
                    backward = forward < 0.0f,
                    left = strafe > 0.0f,
                    right = strafe < 0.0f,
                )
            }
        }
    }

    val mode = setting(EnumSetting("Mode", Mode.entries.toTypedArray(), Mode.FOCUS) { it.label })

    private var auraTargeting = false
    private var auraYaw = 0.0f
    private var auraAimPosition: Vec3? = null
    private var auraTargetCenter: Vec3? = null
    private var movementCameraYaw: Float? = null

    fun activeForAura(): Boolean {
        return enabled && auraTargeting
    }

    fun freeCorrection(): Boolean {
        return enabled && mode.value == Mode.FREE
    }

    fun updateAuraRotation(targeting: Boolean, yaw: Float, aimPosition: Vec3?, targetCenter: Vec3?) {
        auraTargeting = targeting
        auraYaw = Mth.wrapDegrees(yaw)
        auraAimPosition = aimPosition
        auraTargetCenter = targetCenter
    }

    fun correctedYaw(playerYaw: Float): Float {
        return Mth.wrapDegrees(if (activeForAura()) auraYaw else playerYaw)
    }

    fun beginMovementCorrection(playerYaw: Float): Float {
        movementCameraYaw = playerYaw
        return correctedYaw(playerYaw)
    }

    fun endMovementCorrection() {
        movementCameraYaw = null
    }

    fun auraTargetCenter(): Vec3? {
        return auraTargetCenter
    }

    fun correctedInput(input: Input, playerYaw: Float): CorrectedInput? {
        if (!enabled) return null
        val targetYaw = correctedYaw(playerYaw)
        val forward = movementMultiplier(input.forward(), input.backward())
        val strafe = movementMultiplier(input.left(), input.right())

        return when (mode.value) {
            Mode.FOCUS -> {
                if (activeForAura() && forward == 1.0f && strafe == 0.0f) {
                    solveForTarget(targetYaw, auraAimPosition)
                } else {
                    null
                }
            }
            Mode.FREE -> correctFreeInput(playerYaw, targetYaw, forward, strafe)
        }
    }

    private fun correctFreeInput(playerYaw: Float, targetYaw: Float, forward: Float, strafe: Float): CorrectedInput? {
        if (!activeForAura()) return null
        if (forward == 0.0f && strafe == 0.0f) return null
        val desired = movementDirection(movementCameraYaw ?: playerYaw, forward, strafe)
        return resolveDirectionalInputForAngle(targetYaw, desired)
    }

    private fun solveForTarget(yaw: Float, targetPosition: Vec3?): CorrectedInput? {
        val player = Minecraft.getInstance().player ?: return null
        val position = targetPosition ?: return null
        val desired = Mth.wrapDegrees(Math.toDegrees(atan2(position.z - player.z, position.x - player.x)).toFloat() - 90.0f)
        return resolveDirectionalInputForAngle(yaw, desired)
    }

    private fun resolveDirectionalInputForAngle(yaw: Float, desiredAngle: Float): CorrectedInput {
        var bestForward = 0.0f
        var bestStrafe = 0.0f
        var bestDifference = Float.MAX_VALUE

        for (forward in -1..1) {
            for (strafe in -1..1) {
                if (forward == 0 && strafe == 0) continue
                val moveAngle = movementDirection(yaw, forward.toFloat(), strafe.toFloat())
                val difference = abs(Mth.wrapDegrees(desiredAngle - moveAngle))
                if (difference < bestDifference) {
                    bestDifference = difference
                    bestForward = forward.toFloat()
                    bestStrafe = strafe.toFloat()
                }
            }
        }

        return CorrectedInput.fromImpulses(bestForward, bestStrafe)
    }

    private fun movementDirection(yaw: Float, forward: Float, strafe: Float): Float {
        var direction = yaw
        if (forward < 0.0f) direction += 180.0f
        val forwardFactor = when {
            forward < 0.0f -> -0.5f
            forward > 0.0f -> 0.5f
            else -> 1.0f
        }
        if (strafe > 0.0f) direction -= 90.0f * forwardFactor
        if (strafe < 0.0f) direction += 90.0f * forwardFactor
        return Mth.wrapDegrees(direction)
    }

    companion object {
        private fun movementMultiplier(positive: Boolean, negative: Boolean): Float {
            if (positive == negative) return 0.0f
            return if (positive) 1.0f else -1.0f
        }
    }
}
