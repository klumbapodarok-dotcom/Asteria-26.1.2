package asteria.top.client.module.setting

class MultiBooleanSetting(
    name: String,
    vararg values: BooleanSetting,
) : ModuleSetting<List<BooleanSetting>>(name, values.toList()) {
    override fun displayValue(): String {
        return enabledNames().size.toString()
    }

    override fun adjust(direction: Int) {
    }

    fun enabled(name: String): Boolean {
        return value.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value == true
    }

    fun toggle(name: String) {
        value.firstOrNull { it.name.equals(name, ignoreCase = true) }?.adjust(1)
    }

    fun enabledNames(): List<String> {
        return value.filter { it.value }.map { it.name }
    }

    override fun reset() {
        value.forEach { it.reset() }
    }
}
