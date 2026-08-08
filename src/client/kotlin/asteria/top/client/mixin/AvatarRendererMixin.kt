package asteria.top.client.mixin

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.player.AvatarRenderer
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.HandShaderRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyArg

@Mixin(AvatarRenderer::class)
abstract class AvatarRendererMixin {
    @ModifyArg(
        method = ["renderHand"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
        ),
        index = 2
    )
    private fun handShaderRenderType(original: RenderType): RenderType {
        val handShader = ModuleManager.handShader
        if (!handShader.enabled) return original
        return HandShaderRenderer.getHandRenderType(handShader.shaderPreset.value)
    }
}
