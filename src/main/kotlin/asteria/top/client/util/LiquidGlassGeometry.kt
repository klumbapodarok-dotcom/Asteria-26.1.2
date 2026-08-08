package asteria.top.client.util

import kotlin.math.sqrt

object LiquidGlassGeometry {
    data class Offset(val x: Float, val y: Float)

    @JvmStatic
    fun stableRadius(width: Float, height: Float, requestedRadius: Float): Float {
        if (!width.isFinite() || !height.isFinite() || !requestedRadius.isFinite()) return 0.0f
        return requestedRadius.coerceIn(0.0f, minOf(width.coerceAtLeast(0.0f), height.coerceAtLeast(0.0f)) * 0.5f)
    }

    @JvmStatic
    fun radialDisplacement(
        pixelX: Float,
        pixelY: Float,
        boxX: Float,
        boxY: Float,
        boxWidth: Float,
        boxHeight: Float,
        fresnel: Float,
        strength: Float,
    ): Offset {
        val localX = pixelX - (boxX + boxWidth * 0.5f)
        val localY = pixelY - (boxY + boxHeight * 0.5f)
        val length = sqrt(localX * localX + localY * localY)
        if (!length.isFinite() || length <= 0.000001f) return Offset(0.0f, 0.0f)

        val magnitude = minOf(boxWidth.coerceAtLeast(0.0f), boxHeight.coerceAtLeast(0.0f)) *
            fresnel.coerceIn(0.0f, 1.0f) * strength.coerceAtLeast(0.0f)
        return Offset(localX / length * magnitude, localY / length * magnitude)
    }

    @JvmStatic
    fun selectTopmostBox(pixelX: Float, pixelY: Float, boxes: FloatArray, boxCount: Int): Int {
        val safeCount = boxCount.coerceIn(0, boxes.size / 4)
        var selected = -1
        for (index in 0 until safeCount) {
            val offset = index * 4
            val x = boxes[offset]
            val y = boxes[offset + 1]
            val width = boxes[offset + 2]
            val height = boxes[offset + 3]
            if (pixelX >= x && pixelY >= y && pixelX <= x + width && pixelY <= y + height) {
                selected = index
            }
        }
        return selected
    }
}
