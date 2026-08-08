package asteria.top.client.util

object ControlGeometry {
    const val MODULE_SWITCH_WIDTH = 30.0f
    const val MODULE_SWITCH_HEIGHT = 16.0f
    const val SETTING_SWITCH_WIDTH = 28.0f
    const val SETTING_SWITCH_HEIGHT = 15.0f

    data class PillSegments(val radius: Float, val centerWidth: Float)

    @JvmStatic
    fun pillSegments(width: Float, height: Float): PillSegments {
        val radius = minOf(width, height) * 0.5f
        return PillSegments(radius, (width - radius * 2.0f).coerceAtLeast(0.0f))
    }
}
