package asteria.top.client.module.setting

import java.util.Locale

class FloatSetting(
    name: String,
    initialValue: Float,
    val min: Float,
    val max: Float,
    val step: Float,
    private val suffix: String = "",
) : ModuleSetting<Float>(name, initialValue.coerceIn(min, max)) {
    override fun displayValue(): String {
        return String.format(Locale.ROOT, "%.2f%s", value, suffix)
    }

    override fun adjust(direction: Int) {
        value = (value + step * direction).coerceIn(min, max)
    }

    override fun setRaw(value: Float) {
        this.value = value.coerceIn(min, max)
    }

    fun setByPercent(percent: Float) {
        value = (min + (max - min) * percent.coerceIn(0.0f, 1.0f)).coerceIn(min, max)
    }
}
