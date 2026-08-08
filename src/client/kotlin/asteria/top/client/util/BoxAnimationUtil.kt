package asteria.top.client.util

import kotlin.math.max
import kotlin.math.min

object BoxAnimationUtil {
    fun progress(startMs: Double, durationMs: Float): Float {
        val elapsed = ((System.nanoTime() / 1_000_000.0 - startMs) / durationMs).toFloat()
        return AnimationUtil.clamp01(elapsed)
    }

    fun fade(progress: Float): Float {
        return AnimationUtil.apply(AnimationUtil.Mode.FADE, progress)
    }

    fun bounce(progress: Float): Float {
        return AnimationUtil.apply(AnimationUtil.Mode.BOUNCE, progress)
    }

    fun size(from: Float, to: Float, progress: Float, mode: AnimationUtil.Mode = AnimationUtil.Mode.FADE): Float {
        val t = AnimationUtil.apply(mode, progress)
        return from + (to - from) * t
    }

    fun lerp(from: Float, to: Float, progress: Float, mode: AnimationUtil.Mode = AnimationUtil.Mode.FADE): Float {
        val t = AnimationUtil.apply(mode, progress)
        return from + (to - from) * t
    }

    fun centerScale(left: Float, top: Float, width: Float, height: Float, scale: Float): FloatArray {
        val safeScale = max(0.0f, min(scale, 1.25f))
        val scaledWidth = width * safeScale
        val scaledHeight = height * safeScale
        return floatArrayOf(
            left + (width - scaledWidth) * 0.5f,
            top + (height - scaledHeight) * 0.5f,
            scaledWidth,
            scaledHeight,
        )
    }
}
