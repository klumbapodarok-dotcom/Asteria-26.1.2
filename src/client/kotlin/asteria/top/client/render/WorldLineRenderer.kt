package asteria.top.client.render

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max

/**
 * Port of Asteria12111's WorldLineRenderer.
 *
 * Draws real world lines as thin quad ribbons billboarded toward the camera, because GL line
 * layers don't feed a line width to the shader (thickness 0 -> invisible). The quad path ignores
 * shader width and renders reliably. World coordinates are converted to camera-relative inside.
 */
object WorldLineRenderer {

    /** Pixel -> world half-width at 1 block distance (constant on-screen thickness). */
    const val PIXEL_TO_WORLD = 0.00075

    /** Minimum half-width so close-up lines don't vanish. */
    const val MIN_HALF_WIDTH = 0.0025

    /** Box edges (12 lines). worldBox is in world coordinates. */
    fun box(emitter: WorldGeometryEmitter, worldBox: AABB, cameraPos: Vec3, color: Int, px: Double) {
        val x0 = worldBox.minX - cameraPos.x
        val y0 = worldBox.minY - cameraPos.y
        val z0 = worldBox.minZ - cameraPos.z
        val x1 = worldBox.maxX - cameraPos.x
        val y1 = worldBox.maxY - cameraPos.y
        val z1 = worldBox.maxZ - cameraPos.z

        rel(emitter, x0, y0, z0, x1, y0, z0, color, color, px)
        rel(emitter, x1, y0, z0, x1, y0, z1, color, color, px)
        rel(emitter, x1, y0, z1, x0, y0, z1, color, color, px)
        rel(emitter, x0, y0, z1, x0, y0, z0, color, color, px)

        rel(emitter, x0, y1, z0, x1, y1, z0, color, color, px)
        rel(emitter, x1, y1, z0, x1, y1, z1, color, color, px)
        rel(emitter, x1, y1, z1, x0, y1, z1, color, color, px)
        rel(emitter, x0, y1, z1, x0, y1, z0, color, color, px)

        rel(emitter, x0, y0, z0, x0, y1, z0, color, color, px)
        rel(emitter, x1, y0, z0, x1, y1, z0, color, color, px)
        rel(emitter, x1, y0, z1, x1, y1, z1, color, color, px)
        rel(emitter, x0, y0, z1, x0, y1, z1, color, color, px)
    }

    /** A single line between two world points (solid colour). */
    fun line(emitter: WorldGeometryEmitter, aWorld: Vec3, bWorld: Vec3, cameraPos: Vec3, color: Int, px: Double) {
        line(emitter, aWorld, bWorld, cameraPos, color, color, px)
    }

    /** A line with a colour gradient from point a to point b. */
    fun line(
        emitter: WorldGeometryEmitter,
        aWorld: Vec3,
        bWorld: Vec3,
        cameraPos: Vec3,
        colorA: Int,
        colorB: Int,
        px: Double,
    ) {
        rel(
            emitter,
            aWorld.x - cameraPos.x, aWorld.y - cameraPos.y, aWorld.z - cameraPos.z,
            bWorld.x - cameraPos.x, bWorld.y - cameraPos.y, bWorld.z - cameraPos.z,
            colorA, colorB, px,
        )
    }

    /**
     * Draws a whole path as one continuous camera-facing ribbon. Adjacent segments share their
     * edge vertices, preventing twists and width spikes when a trajectory runs almost directly
     * away from the camera.
     */
    fun polyline(emitter: WorldGeometryEmitter, worldPoints: List<Vec3>, cameraPos: Vec3, color: Int, px: Double) {
        polyline(emitter, worldPoints, cameraPos, null, color, px)
    }

    /** Draws a continuous ribbon with an independently coloured vertex at every path point. */
    fun gradientPolyline(
        emitter: WorldGeometryEmitter,
        worldPoints: List<Vec3>,
        cameraPos: Vec3,
        pointColors: IntArray,
        px: Double,
    ) {
        require(pointColors.size == worldPoints.size) { "Each polyline point must have one colour" }
        polyline(emitter, worldPoints, cameraPos, pointColors, 0, px)
    }

    private fun polyline(
        emitter: WorldGeometryEmitter,
        worldPoints: List<Vec3>,
        cameraPos: Vec3,
        pointColors: IntArray?,
        uniformColor: Int,
        px: Double,
    ) {
        if (worldPoints.size < 2) return

        val count = worldPoints.size
        val points = arrayOfNulls<Vec3>(count)
        val offsets = arrayOfNulls<Vec3>(count)
        var previousSide: Vec3? = null

        for (i in 0 until count) {
            points[i] = worldPoints[i].subtract(cameraPos)
        }

        for (i in 0 until count) {
            val before = points[max(0, i - 1)]!!
            val after = points[minOf(count - 1, i + 1)]!!
            var tangent = after.subtract(before)
            if (tangent.lengthSqr() < 1.0e-12) {
                val stableSide = previousSide ?: Vec3(1.0, 0.0, 0.0)
                val halfWidth = max(px * points[i]!!.length() * PIXEL_TO_WORLD, MIN_HALF_WIDTH)
                offsets[i] = stableSide.scale(halfWidth)
                continue
            }
            tangent = tangent.normalize()

            val distance = points[i]!!.length()
            val toCamera = if (distance < 1.0e-6) Vec3(0.0, 0.0, 1.0) else points[i]!!.scale(-1.0 / distance)
            var side = tangent.cross(toCamera)

            // When the path aims almost exactly at the camera, reuse the previous
            // orientation by parallel transport instead of selecting a random axis.
            if (side.lengthSqr() < 1.0e-10 && previousSide != null) {
                side = previousSide.subtract(tangent.scale(previousSide.dot(tangent)))
            }
            if (side.lengthSqr() < 1.0e-10) {
                val reference = if (abs(tangent.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
                side = tangent.cross(reference)
            }
            side = side.normalize()
            if (previousSide != null && side.dot(previousSide) < 0.0) {
                side = side.scale(-1.0)
            }

            val halfWidth = max(px * distance * PIXEL_TO_WORLD, MIN_HALF_WIDTH)
            offsets[i] = side.scale(halfWidth)
            previousSide = side
        }

        for (i in 1 until count) {
            val offsetA = offsets[i - 1] ?: continue
            val offsetB = offsets[i] ?: continue
            val from = points[i - 1]!!
            val to = points[i]!!

            // A segment pointing almost straight at the camera has no meaningful billboard
            // orientation left: its ribbon degenerates into a wide cross-shaped smear. Those
            // segments cover barely a pixel on screen anyway, so skip them instead.
            val delta = to.subtract(from)
            val segmentLength = delta.length()
            if (segmentLength < 1.0e-6) continue
            val midDistance = from.add(to).scale(0.5).length()
            if (midDistance > 1.0e-6) {
                val alongView = abs(delta.scale(1.0 / segmentLength).dot(from.add(to).scale(0.5).scale(1.0 / midDistance)))
                if (alongView > 0.995) continue
            }

            val colorA = pointColors?.get(i - 1) ?: uniformColor
            val colorB = pointColors?.get(i) ?: uniformColor
            emitter.emitQuad(
                from.add(offsetA),
                to.add(offsetB),
                to.subtract(offsetB),
                from.subtract(offsetA),
                colorA, colorB, colorB, colorA,
            )
        }
    }

    /** Billboarded ribbon from already camera-relative coordinates (camera = origin). */
    private fun rel(
        emitter: WorldGeometryEmitter,
        ax: Double, ay: Double, az: Double,
        bx: Double, by: Double, bz: Double,
        colorA: Int, colorB: Int, px: Double,
    ) {
        val a = Vec3(ax, ay, az)
        val b = Vec3(bx, by, bz)
        val diff = b.subtract(a)
        val len = diff.length()
        if (len < 1.0e-9) return

        val dir = diff.scale(1.0 / len)
        val mid = a.add(b).scale(0.5)
        val dist = mid.length()
        val toCam = if (dist < 1.0e-6) Vec3(0.0, 0.0, 1.0) else mid.scale(-1.0 / dist)

        var side = dir.cross(toCam)
        var sideLen = side.length()
        if (sideLen < 1.0e-6) {
            side = dir.cross(Vec3(0.0, 1.0, 0.0))
            sideLen = side.length()
            if (sideLen < 1.0e-6) {
                side = Vec3(1.0, 0.0, 0.0)
                sideLen = 1.0
            }
        }
        val half = max(px * dist * PIXEL_TO_WORLD, MIN_HALF_WIDTH)
        side = side.scale(half / sideLen)

        val c0 = a.add(side)
        val c1 = b.add(side)
        val c2 = b.subtract(side)
        val c3 = a.subtract(side)
        // gradient: side a -> colorA, side b -> colorB
        emitter.emitQuad(c0, c1, c2, c3, colorA, colorB, colorB, colorA)
    }
}
