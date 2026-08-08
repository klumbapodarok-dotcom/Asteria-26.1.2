package asteria.top.client.module.setting

import kotlin.math.roundToInt

class IntSetting(
    name: String,
    initialValue: Int,
    val min: Int,
    val max: Int,
    val step: Int,
    private val suffix: String = "",
) : ModuleSetting<Int>(name, initialValue.coerceIn(min, max)) {
    override fun displayValue(): String {
        return "$value$suffix"
    }

    override fun adjust(direction: Int) {
        value = (value + step * direction).coerceIn(min, max)
    }

    override fun setRaw(value: Int) {
        this.value = value.coerceIn(min, max)
    }

    fun setByPercent(percent: Float) {
        val raw = min + (max - min) * percent.coerceIn(0.0f, 1.0f)
        value = raw.roundToInt().coerceIn(min, max)
    }
}
