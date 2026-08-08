package asteria.top.client.util.combat

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object MultiPointUtil {
    private const val EDGE_INSET = 0.03

    fun aimPoint(entity: LivingEntity, currentRotation: CombatRotation? = null): Vec3 {
        val player = Minecraft.getInstance().player ?: return CombatRotationUtil.spot(entity)
        val eye = player.eyePosition
        val candidates = candidates(entity)
        val visible = candidates.filter { visibleFromEye(eye, it) }
        val pool = if (visible.isNotEmpty()) visible else candidates

        return pool.minWithOrNull(
            compareBy<Vec3> { rotationScore(currentRotation, eye, it) }
                .thenBy { it.distanceToSqr(eye) }
        ) ?: CombatRotationUtil.spot(entity)
    }

    fun clampInside(entity: LivingEntity, point: Vec3): Vec3 {
        return clampInside(entity, point, EDGE_INSET)
    }

    fun clampInside(entity: LivingEntity, point: Vec3, edgeInset: Double): Vec3 {
        val box = entity.boundingBox
        val insetX = min(edgeInset, (box.xsize * 0.25).coerceAtLeast(0.0))
        val insetY = min(edgeInset, (box.ysize * 0.25).coerceAtLeast(0.0))
        val insetZ = min(edgeInset, (box.zsize * 0.25).coerceAtLeast(0.0))

        return Vec3(
            clamp(point.x, box.minX + insetX, box.maxX - insetX),
            clamp(point.y, box.minY + insetY, box.maxY - insetY),
            clamp(point.z, box.minZ + insetZ, box.maxZ - insetZ),
        )
    }

    private fun candidates(entity: LivingEntity): List<Vec3> {
        val box = entity.boundingBox
        val centerX = (box.minX + box.maxX) * 0.5
        val centerZ = (box.minZ + box.maxZ) * 0.5
        val height = max(box.ysize, 0.01)
        val radiusX = min(box.xsize * 0.28, 0.18)
        val radiusZ = min(box.zsize * 0.28, 0.18)
        val lowY = box.minY + height * 0.32
        val midY = box.minY + height * 0.52
        val highY = box.minY + height * 0.74

        return listOf(
            CombatRotationUtil.spot(entity),
            Vec3(centerX, midY, centerZ),
            Vec3(centerX, highY, centerZ),
            Vec3(centerX, lowY, centerZ),
            Vec3(centerX + radiusX, midY, centerZ),
            Vec3(centerX - radiusX, midY, centerZ),
            Vec3(centerX, midY, centerZ + radiusZ),
            Vec3(centerX, midY, centerZ - radiusZ),
            Vec3(centerX + radiusX, highY, centerZ + radiusZ),
            Vec3(centerX - radiusX, highY, centerZ - radiusZ),
        ).map { clampInside(entity, it) }.distinctBy { PointKey(it) }
    }

    private fun visibleFromEye(eye: Vec3, point: Vec3): Boolean {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return true
        val player = mc.player ?: return true
        val result = level.clip(ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        return result.type == HitResult.Type.MISS || result.location.distanceToSqr(point) < 0.04
    }

    private fun rotationScore(currentRotation: CombatRotation?, eye: Vec3, point: Vec3): Float {
        val current = currentRotation ?: return 0.0f
        val desired = CombatRotationUtil.fromVector(point.subtract(eye))
        val yaw = abs(Mth.wrapDegrees(desired.yaw - current.yaw))
        val pitch = abs(Mth.wrapDegrees(desired.pitch - current.pitch))
        return yaw + pitch * 1.35f
    }

    private fun clamp(value: Double, minValue: Double, maxValue: Double): Double {
        return if (minValue > maxValue) (minValue + maxValue) * 0.5 else value.coerceIn(minValue, maxValue)
    }

    private data class PointKey(val x: Int, val y: Int, val z: Int) {
        constructor(point: Vec3) : this(
            (point.x * 1000.0).toInt(),
            (point.y * 1000.0).toInt(),
            (point.z * 1000.0).toInt(),
        )
    }
}
