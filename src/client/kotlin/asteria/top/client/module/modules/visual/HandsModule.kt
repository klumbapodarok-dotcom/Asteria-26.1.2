package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.render.HandsRenderer

class HandsModule : Module(
    name = "Hands",
    category = ModuleCategory.VISUALS,
    description = "Animated client gradient or space effect on first-person hands and items.",
    enabledByDefault = false,
) {
    enum class Mode(val label: String) {
        GRADIENT("Gradient"),
        SPACE("Space"),
    }

    val mode = setting(EnumSetting("Mode", Mode.entries.toTypedArray(), Mode.GRADIENT) { it.label })
    val fillAlpha = setting(FloatSetting("Fill Alpha", 0.8f, 0.0f, 1.0f, 0.05f))
    val keepShading = setting(BooleanSetting("Keep Shading", true))
    val shadingStrength = setting(FloatSetting("Shading Strength", 0.3f, 0.0f, 1.0f, 0.05f).apply {
        visibleWhen { keepShading.value }
    })

    val glowEnabled = setting(BooleanSetting("Glow", true))
    val glowRadius = setting(FloatSetting("Glow Radius", 4.0f, 1.0f, 6.0f, 1.0f).apply {
        visibleWhen { glowEnabled.value }
    })
    val glowExposure = setting(FloatSetting("Glow Exposure", 1.45f, 0.5f, 5.0f, 0.1f).apply {
        visibleWhen { glowEnabled.value }
    })
    val turbulenceStrength = setting(FloatSetting("Turbulence Strength", 1.2f, 0.0f, 9.0f, 0.05f).apply {
        visibleWhen { glowEnabled.value }
    })
    val turbulenceSpeed = setting(FloatSetting("Turbulence Speed", 0.72f, 0.05f, 3.0f, 0.05f).apply {
        visibleWhen { glowEnabled.value }
    })

    val spaceSpeed = setting(FloatSetting("Space Speed", 0.45f, 0.05f, 2.0f, 0.05f).apply {
        visibleWhen { mode.value == Mode.SPACE }
    })
    val starDensity = setting(FloatSetting("Star Density", 1.0f, 0.25f, 2.5f, 0.05f).apply {
        visibleWhen { mode.value == Mode.SPACE }
    })
    val nebulaStrength = setting(FloatSetting("Nebula Strength", 1.0f, 0.0f, 2.0f, 0.05f).apply {
        visibleWhen { mode.value == Mode.SPACE }
    })

    override fun onDisable() {
        HandsRenderer.resetCapture()
    }
}
