package asteria.top.client.util.combat

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object PolarRotationUtil {
    private val random = Random()
    private var targetId = Int.MIN_VALUE
    private var currentJitter = Vec3(0.0, 0.0, 0.0)
    private var targetJitter = Vec3(0.0, 0.0, 0.0)
    private var nextJitterTick = Int.MIN_VALUE
    private var speedOffset = 0.0f
    private var targetSpeedOffset = 0.0f

    fun aimPoint(entity: LivingEntity, currentRotation: CombatRotation): Vec3 {
        updateState(entity)
        currentJitter = lerp(currentJitter, targetJitter, 0.20)
        speedOffset = lerp(speedOffset, targetSpeedOffset, 0.18f)
        return MultiPointUtil.clampInside(entity, MultiPointUtil.aimPoint(entity, currentRotation).add(currentJitter), safeInset(entity))
    }

    fun yawSpeed(current: CombatRotation, desired: CombatRotation): Float {
        val delta = abs(Mth.wrapDegrees(desired.yaw - current.yaw))
        return (16.0f + delta * 0.42f + speedOffset).coerceIn(18.0f, 62.0f)
    }

    fun pitchSpeed(current: CombatRotation, desired: CombatRotation): Float {
        val delta = abs(Mth.wrapDegrees(desired.pitch - current.pitch))
        return (8.0f + delta * 0.34f + speedOffset * 0.45f).coerceIn(9.0f, 34.0f)
    }

    private fun updateState(entity: LivingEntity) {
        val tick = Minecraft.getInstance().player?.tickCount ?: 0
        if (entity.id != targetId) {
            targetId = entity.id
            random.setSeed(System.nanoTime() xor entity.uuid.leastSignificantBits xor entity.uuid.mostSignificantBits)
            currentJitter = Vec3(0.0, 0.0, 0.0)
            targetJitter = Vec3(0.0, 0.0, 0.0)
            speedOffset = 0.0f
            targetSpeedOffset = 0.0f
            nextJitterTick = tick
        }

        if (tick < nextJitterTick) return

        val horizontal = min(max(entity.bbWidth.toDouble() * 0.055, 0.018), 0.052)
        val vertical = min(max(entity.bbHeight.toDouble() * 0.024, 0.012), 0.042)
        targetJitter = Vec3(
            centeredRandom() * horizontal,
            centeredRandom() * vertical,
            centeredRandom() * horizontal,
        )
        targetSpeedOffset = (centeredRandom() * 4.0).toFloat()
        nextJitterTick = tick + 2 + random.nextInt(5)
    }

    private fun safeInset(entity: LivingEntity): Double {
        val horizontal = entity.bbWidth.toDouble() * 0.16
        val vertical = entity.bbHeight.toDouble() * 0.06
        return min(max(min(horizontal, vertical), 0.045), 0.11)
    }

    private fun centeredRandom(): Double {
        return random.nextDouble() * 2.0 - 1.0
    }

    private fun lerp(from: Vec3, to: Vec3, progress: Double): Vec3 {
        val t = progress.coerceIn(0.0, 1.0)
        return Vec3(
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
            from.z + (to.z - from.z) * t,
        )
    }

    private fun lerp(from: Float, to: Float, progress: Float): Float {
        val t = progress.coerceIn(0.0f, 1.0f)
        return from + (to - from) * t
    }
}
