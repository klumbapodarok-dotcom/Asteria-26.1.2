package asteria.top.client.module.setting

class BooleanSetting(name: String, initialValue: Boolean) : ModuleSetting<Boolean>(name, initialValue) {
    override fun displayValue(): String {
        return if (value) "On" else "Off"
    }

    override fun adjust(direction: Int) {
        value = !value
    }
}
