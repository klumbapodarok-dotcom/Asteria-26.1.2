package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.module.setting.IntSetting
import asteria.top.client.module.setting.MultiBooleanSetting

class ChamsModule : Module(
    name = "Chams",
    category = ModuleCategory.VISUALS,
    description = "Applies a custom shader effect to entities",
    enabledByDefault = false,
) {
    enum class ShaderPreset(val label: String) {
        DEFAULT("Default"),
        PLASMA("Plasma"),
    }

    val shaderPreset = setting(EnumSetting("Shader Preset", ShaderPreset.entries.toTypedArray(), ShaderPreset.DEFAULT) { it.label })
    val targets = setting(
        MultiBooleanSetting(
            "Targets",
            BooleanSetting("Self", true),
            BooleanSetting("Players", true),
            BooleanSetting("Mobs", false),
            BooleanSetting("Animals", false),
        )
    )
    val throughWalls = setting(BooleanSetting("Through Walls", true))

    val waveSpeed = setting(FloatSetting("Wave Speed", 1.2f, 0.1f, 5.0f, 0.1f))
    val waveScale = setting(FloatSetting("Wave Scale", 1.0f, 1.0f, 3.0f, 0.1f))
    val glow = setting(FloatSetting("Glow", 1.0f, 0.0f, 5.0f, 0.1f))
    val filled = setting(IntSetting("Filled", 60, 0, 100, 1))
    val alphaSetting = setting(FloatSetting("Alpha", 1.0f, 0.0f, 1.0f, 0.01f))
}
