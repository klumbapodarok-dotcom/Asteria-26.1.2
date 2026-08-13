package asteria.top.client.render.cosmetic

import asteria.top.client.module.ModuleManager
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier

class RocketBackRenderLayer(parent: RenderLayerParent<AvatarRenderState, PlayerModel>) :
    RenderLayer<AvatarRenderState, PlayerModel>(parent) {

    private val model = RocketBackModel.create()

    override fun submit(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        packedLight: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        if (!ModuleManager.kasmetikatest.enabled || state.isInvisible) return
        if (state.id != Minecraft.getInstance().player?.id) return

        poseStack.pushPose()
        if (state.isCrouching) {
            poseStack.translate(0.0f, 0.2f, 0.05f)
        }
        collector.submitModel(
            model,
            state,
            poseStack,
            RenderTypes.entityTranslucent(TEXTURE),
            packedLight,
            OverlayTexture.NO_OVERLAY,
            state.outlineColor,
            null,
        )
        poseStack.popPose()
    }

    companion object {
        private val TEXTURE = Identifier.fromNamespaceAndPath("asteria", "cosmetics/rocket/rocket.png")
    }
}
