package asteria.top.client.module.setting

class EnumSetting<T : Enum<T>>(
    name: String,
    val values: Array<T>,
    initialValue: T,
    val label: (T) -> String = { it.name },
) : ModuleSetting<T>(name, initialValue) {
    override fun displayValue(): String {
        return label(value)
    }

    override fun adjust(direction: Int) {
        val current = values.indexOf(value).coerceAtLeast(0)
        val next = Math.floorMod(current + direction, values.size)
        value = values[next]
    }

    fun setOption(option: T) {
        value = option
    }

    fun optionCount(): Int = values.size

    fun optionDisplay(index: Int): String = label(values[index])

    fun selectIndex(index: Int) {
        value = values[index]
    }
}
