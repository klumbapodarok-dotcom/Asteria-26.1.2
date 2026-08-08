package asteria.top.client.util

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object RoundedRectangleGeometry {
    private const val MIN_SEGMENTS = 4
    private const val MAX_SEGMENTS = 18

    const val DEFAULT_AA_WIDTH = 0.75f

    @JvmStatic
    fun stableRadius(width: Float, height: Float, requestedRadius: Float): Float {
        if (!width.isFinite() || !height.isFinite() || !requestedRadius.isFinite()) return 0.0f

        val maxRadius = min(max(0.0f, width), max(0.0f, height)) * 0.5f
        return requestedRadius.coerceAtLeast(0.0f).coerceAtMost(maxRadius)
    }

    @JvmStatic
    fun segmentCount(radius: Float): Int {
        if (!radius.isFinite() || radius <= 0.0f) return MIN_SEGMENTS

        return ceil(radius * 0.9f + 4.0f)
            .toInt()
            .coerceIn(8, MAX_SEGMENTS)
    }

    @JvmStatic
    fun antiAliasWidth(width: Float, height: Float, requestedWidth: Float = DEFAULT_AA_WIDTH): Float {
        if (!width.isFinite() || !height.isFinite() || !requestedWidth.isFinite()) return 0.0f

        val smallestAxis = min(max(0.0f, width), max(0.0f, height))
        if (smallestAxis <= 0.0f) return 0.0f

        return min(requestedWidth.coerceAtLeast(0.0f), smallestAxis * 0.25f)
    }
}
