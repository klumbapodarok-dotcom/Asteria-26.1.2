package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.render.LiquidFogRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

class LiquidFogModule : Module(
    name = "Liquid Fog",
    category = ModuleCategory.VISUALS,
    description = "Renders animated depth-aware fluid fog over the world.",
) {
    val fallout = setting(FloatSetting("Fallout", 0.03f, 0.0f, 1.0f, 0.01f))
    val density = setting(FloatSetting("Density", 0.75f, 0.0f, 1.0f, 0.01f))
    val hsvMode = setting(BooleanSetting("HSV Mode", false))

    fun render(context: LevelRenderContext) {
        if (!enabled) return
        LiquidFogRenderer.render(context, this)
    }

    override fun onDisable() {
        LiquidFogRenderer.release()
    }
}
