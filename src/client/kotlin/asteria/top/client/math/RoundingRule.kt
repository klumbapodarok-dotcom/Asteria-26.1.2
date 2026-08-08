package asteria.top.client.math

import kotlin.math.ceil
import kotlin.math.floor

enum class RoundingRule(val displayName: String, val glslId: Int) {
    TO_NEAREST_OR_AWAY_FROM_ZERO("toNearestOrAwayFromZero", 0) {
        override fun round(value: Double): Double {
            return if (value >= 0.0) {
                floor(value + 0.5)
            } else {
                ceil(value - 0.5)
            }
        }
    },
    TOWARD_ZERO("towardZero", 1) {
        override fun round(value: Double): Double {
            return if (value >= 0.0) floor(value) else ceil(value)
        }
    },
    UP("up", 2) {
        override fun round(value: Double): Double {
            return ceil(value)
        }
    },
    DOWN("down", 3) {
        override fun round(value: Double): Double {
            return floor(value)
        }
    };

    abstract fun round(value: Double): Double
}
