package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.IntSetting

class InterfaceModule : Module(
    name = "Interface",
    category = ModuleCategory.VISUALS,
    description = "Controls the client interface.",
    enabledByDefault = false,
) {
    val watermark = setting(BooleanSetting("Watermark", true))
    val keybinds = setting(BooleanSetting("Keybinds", false))
    val potions = setting(BooleanSetting("Potions", false))
    val cooldowns = setting(BooleanSetting("Cooldowns", false))
    val targetHud = setting(BooleanSetting("Target HUD", false))
    val notifications = setting(BooleanSetting("Notifications", true))
    val hudSize = setting(EnumSetting("HUD Size", HudSize.entries.toTypedArray(), HudSize.NORMAL) { it.label })
    val customHudSize = setting(IntSetting("Custom HUD Size", 100, 50, 150, 1, "%").visibleWhen { hudSize.value == HudSize.CUSTOM })
    val shadowSize = setting(IntSetting("Shadow Size", 22, 0, 60, 1))
    val shadowOpacity = setting(IntSetting("Shadow Opacity", 38, 0, 100, 1, "%"))
    val shadowSmoothness = setting(IntSetting("Shadow Smoothness", 60, 0, 100, 1, "%"))

    fun hudScaleMultiplier(): Float {
        return if (hudSize.value == HudSize.CUSTOM) {
            customHudSize.value / 100.0f
        } else {
            hudSize.value.multiplier
        }
    }

    enum class HudSize(val multiplier: Float, val label: String) {
        SMALL(0.75f, "75%"),
        NORMAL(1.0f, "100%"),
        LARGE(1.25f, "125%"),
        CUSTOM(1.0f, "Custom"),
    }
}
