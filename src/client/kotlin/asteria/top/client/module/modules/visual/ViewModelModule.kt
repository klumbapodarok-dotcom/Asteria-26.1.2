package asteria.top.client.module.modules.visual

import com.mojang.blaze3d.vertex.PoseStack
import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.FloatSetting
import net.minecraft.world.InteractionHand

class ViewModelModule : Module(
    name = "View Model",
    category = ModuleCategory.VISUALS,
    description = "Adjusts first-person item position and scale.",
    enabledByDefault = false,
) {
    private val x = setting(FloatSetting("X", 0.0f, -1.0f, 1.0f, 0.025f))
    private val y = setting(FloatSetting("Y", 0.0f, -1.0f, 1.0f, 0.025f))
    private val z = setting(FloatSetting("Z", 0.0f, -1.0f, 1.0f, 0.025f))
    private val scale = setting(FloatSetting("Scale", 1.0f, 0.5f, 1.5f, 0.025f))

    fun applyTransform(poseStack: PoseStack, hand: InteractionHand) {
        if (!enabled) return

        val side = if (hand == InteractionHand.OFF_HAND) -1.0 else 1.0
        poseStack.translate(x.value.toDouble() * side, y.value.toDouble(), z.value.toDouble())
        poseStack.scale(scale.value, scale.value, scale.value)
    }
}
