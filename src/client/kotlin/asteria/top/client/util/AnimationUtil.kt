package asteria.top.client.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.pow

object AnimationUtil {
    enum class Mode {
        LINEAR,
        FADE,
        BOUNCE,
        BACK_IN,
    }

    fun clamp01(value: Float): Float {
        return max(0.0f, min(1.0f, value))
    }

    fun apply(mode: Mode, value: Float): Float {
        val t = clamp01(value)
        return when (mode) {
            Mode.LINEAR -> t
            Mode.FADE -> t * t * (3.0f - 2.0f * t)
            Mode.BOUNCE -> easeOutBack(t)
            Mode.BACK_IN -> easeInBack(t)
        }
    }

    fun easeOutBack(value: Float): Float {
        val t = clamp01(value)
        val c1 = 1.70158f
        val c3 = c1 + 1.0f
        return 1.0f + c3 * (t - 1.0f).pow(3) + c1 * (t - 1.0f).pow(2)
    }

    fun easeOutSoftBack(value: Float): Float {
        val t = clamp01(value)
        val c1 = 0.72f
        val c3 = c1 + 1.0f
        return 1.0f + c3 * (t - 1.0f).pow(3) + c1 * (t - 1.0f).pow(2)
    }

    fun easeOutQuad(value: Float): Float {
        val t = clamp01(value)
        return t * (2.0f - t)
    }

    fun easeInBack(value: Float): Float {
        val t = clamp01(value)
        val c1 = 1.70158f
        val c3 = c1 + 1.0f
        return c3 * t * t * t - c1 * t * t
    }

    fun easeInSine(value: Float): Float {
        val t = clamp01(value)
        return 1.0f - cos(t * Math.PI.toFloat() * 0.5f)
    }

    fun easeOutSine(value: Float): Float {
        val t = clamp01(value)
        return kotlin.math.sin(t * Math.PI.toFloat() * 0.5f)
    }

    class TimedAnimation(initialValue: Float = 0.0f) {
        var value = initialValue
            private set
        private var from = initialValue
        private var target = initialValue
        private var startMillis = 0L
        private var durationMillis = 1L
        private var easing: (Float) -> Float = { it }

        fun snap(next: Float) {
            value = next
            from = next
            target = next
            startMillis = 0L
            durationMillis = 1L
            easing = { it }
        }

        fun run(next: Float, duration: Long, easing: (Float) -> Float, safe: Boolean = false) {
            update()
            if (safe && next == target && !isAlive()) return
            if (safe && isAlive() && (next == from || next == target || next == value)) return
            from = value
            target = next
            startMillis = System.currentTimeMillis()
            durationMillis = duration.coerceAtLeast(1L)
            this.easing = easing
        }

        fun update(): Boolean {
            if (!isAlive()) {
                value = target
                startMillis = 0L
                return false
            }
            val elapsed = (System.currentTimeMillis() - startMillis).toFloat() / durationMillis.toFloat()
            value = from + (target - from) * easing(clamp01(elapsed))
            return true
        }

        fun isAlive(): Boolean {
            return startMillis > 0L && System.currentTimeMillis() - startMillis < durationMillis
        }
    }
}
