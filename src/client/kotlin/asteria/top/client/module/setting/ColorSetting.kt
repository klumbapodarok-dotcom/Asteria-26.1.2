package asteria.top.client.module.setting

import java.awt.Color

/** A single opaque RGB colour exposed to the ClickGUI as an HSV colour picker. */
class ColorSetting(
    name: String,
    initialValue: Int,
    val legacyRgbNames: Triple<String, String, String>? = null,
) : ModuleSetting<Int>(name, normalize(initialValue)) {
    override fun displayValue(): String = "#%06X".format(value and 0xFFFFFF)

    override fun adjust(direction: Int) {
        val hsv = hsv()
        setHsv(hsv[0] + direction * 0.01f, hsv[1], hsv[2])
    }

    override fun setRaw(value: Int) {
        this.value = normalize(value)
    }

    fun hsv(): FloatArray = Color.RGBtoHSB(red, green, blue, null)

    fun setHsv(hue: Float, saturation: Float, brightness: Float) {
        val wrappedHue = ((hue % 1.0f) + 1.0f) % 1.0f
        value = Color.HSBtoRGB(
            wrappedHue,
            saturation.coerceIn(0.0f, 1.0f),
            brightness.coerceIn(0.0f, 1.0f),
        )
    }

    val argb: Int get() = 0xFF000000.toInt() or value
    val red: Int get() = (value ushr 16) and 0xFF
    val green: Int get() = (value ushr 8) and 0xFF
    val blue: Int get() = value and 0xFF

    companion object {
        private fun normalize(color: Int): Int = color and 0xFFFFFF
    }
}
