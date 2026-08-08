package asteria.top.client.util.combat

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.hypot

data class CombatRotation(val yaw: Float, val pitch: Float) {
    fun clamped(): CombatRotation {
        return CombatRotation(Mth.wrapDegrees(yaw), pitch.coerceIn(-89.0f, 89.0f))
    }
}

object CombatRotationUtil {
    fun rotationAt(point: Vec3): CombatRotation {
        val player = Minecraft.getInstance().player ?: return CombatRotation(0.0f, 0.0f)
        return fromVector(point.subtract(player.eyePosition))
    }

    fun fromVector(vector: Vec3): CombatRotation {
        val yaw = Mth.wrapDegrees(Math.toDegrees(atan2(vector.z, vector.x)).toFloat() - 90.0f)
        val pitch = Mth.wrapDegrees((-Math.toDegrees(atan2(vector.y, hypot(vector.x, vector.z)))).toFloat())
        return CombatRotation(yaw, pitch).clamped()
    }

    fun spot(entity: Entity): Vec3 {
        val eye = Minecraft.getInstance().player?.eyePosition ?: return entity.position()
        val box = entity.boundingBox
        return Vec3(
            Mth.clamp(eye.x, box.minX, box.maxX),
            Mth.clamp(eye.y, box.minY, box.maxY),
            Mth.clamp(eye.z, box.minZ, box.maxZ),
        )
    }
}
