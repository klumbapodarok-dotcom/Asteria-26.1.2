package asteria.top.client.module

import asteria.top.client.gui.hud.NotificationManager
import asteria.top.client.module.setting.ModuleSetting

abstract class Module(
    val name: String,
    val category: ModuleCategory,
    val description: String,
    val enabledByDefault: Boolean = false,
    val defaultBind: Int = -1,
) {
    val settings = mutableListOf<ModuleSetting<*>>()
    var enabled = enabledByDefault
        private set
    var bind = defaultBind

    fun toggle() {
        setEnabled(!enabled)
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled
            if (enabled) {
                onEnable()
            } else {
                onDisable()
            }
            NotificationManager.moduleStateChanged(this, enabled)
        }
    }

    protected open fun onEnable() {}
    protected open fun onDisable() {}

    open fun onBindPressed(): Boolean {
        toggle()
        return true
    }

    protected fun <T : ModuleSetting<*>> setting(setting: T): T {
        settings += setting
        return setting
    }

    fun reset() {
        enabled = enabledByDefault
        bind = defaultBind
        settings.forEach { it.reset() }
    }
}
