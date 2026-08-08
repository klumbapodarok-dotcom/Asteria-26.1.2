package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.module.setting.IntSetting
import asteria.top.client.render.RoundingType

class PostProcessingModule : Module(
    name = "Post Processing",
    category = ModuleCategory.VISUALS,
    description = "Controls ClickGUI blur post-processing.",
    enabledByDefault = true,
) {
    enum class BlurMode(val label: String) {
        DOUBLE_KAWASE("Double Kawase"),
        GAUSSIAN("Gaussian"),
    }

    val liquidGlass = setting(BooleanSetting("Liquid Glass", false))
    val glassOpacity = setting(FloatSetting("Glass Opacity", 100.0f, 0.0f, 100.0f, 1.0f))
    val glassRefraction = setting(FloatSetting("Glass Refraction", 50.0f, 0.0f, 100.0f, 1.0f))
    val glassChromaticAberration = setting(FloatSetting("Glass Chromatic", 0.0f, 0.0f, 100.0f, 1.0f))
    val glassSaturation = setting(FloatSetting("Glass Saturation", 78.0f, 0.0f, 200.0f, 1.0f))
    val glassHighlight = setting(FloatSetting("Glass Highlight", 22.0f, 0.0f, 100.0f, 1.0f))
    val blurMode = setting(EnumSetting("Blur", BlurMode.entries.toTypedArray(), BlurMode.DOUBLE_KAWASE) { it.label })
    val roundingType = setting(EnumSetting("Rounding", RoundingType.entries.toTypedArray(), RoundingType.CURRENT) { it.displayName })
    val kawasePasses = setting(IntSetting("Kawase Passes", 6, 1, 6, 1))
    val kawaseOffset = setting(FloatSetting("Kawase Offset", 1.65f, 0.5f, 4.0f, 0.15f))
    val gaussianRadius = setting(IntSetting("Gaussian Radius", 12, 1, 32, 1))
    val tintStrength = setting(FloatSetting("Tint", 45.0f, 0.0f, 100.0f, 1.0f))
    val shadow = setting(BooleanSetting("Shadow", true))
    val shadowRadius = setting(IntSetting("Shadow Radius", 28, 0, 80, 1))

    fun gaussianSigma(): Float {
        return (gaussianRadius.value * 0.45f).coerceAtLeast(0.5f)
    }
}
