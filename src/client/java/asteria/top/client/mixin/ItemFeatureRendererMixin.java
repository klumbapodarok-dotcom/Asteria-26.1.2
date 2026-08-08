package asteria.top.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import asteria.top.client.module.ModuleManager;
import asteria.top.client.render.HandShaderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;
import java.util.HashSet;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {
    private final Set<RenderType> asteria$usedRenderTypes = new HashSet<>();

    @WrapOperation(
        method = "renderItem(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/OutlineBufferSource;Lnet/minecraft/client/renderer/SubmitNodeStorage$ItemSubmit;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V")
    )
    private void asteria_replaceItemWithShader(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        BakedQuad quad,
        QuadInstance quadInstance,
        Operation<Void> original,
        @Local(argsOnly = true) MultiBufferSource.BufferSource bufferSource,
        @Local(argsOnly = true) SubmitNodeStorage.ItemSubmit itemSubmit,
        @Local RenderType renderType
    ) {
        if (itemSubmit.displayContext().firstPerson() && ModuleManager.INSTANCE.getHandShader().getEnabled()) {
            Identifier texture = null;
            if (quad != null) {
                BakedQuad.MaterialInfo matInfo = quad.materialInfo();
                if (matInfo != null) {
                    TextureAtlasSprite sprite = matInfo.sprite();
                    if (sprite != null) {
                        texture = sprite.atlasLocation();
                    }
                }
            }
            if (texture == null) {
                texture = Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png");
            }

            RenderType shaderRenderType = HandShaderRenderer.INSTANCE.getItemHandRenderType(texture, ModuleManager.INSTANCE.getHandShader().getShaderPreset().getValue());
            asteria$usedRenderTypes.add(shaderRenderType);
            VertexConsumer shaderConsumer = bufferSource.getBuffer(shaderRenderType);
            shaderConsumer.putBakedQuad(pose, quad, quadInstance);
        } else {
            original.call(consumer, pose, quad, quadInstance);
        }
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/OutlineBufferSource;Lnet/minecraft/client/renderer/SubmitNodeStorage$ItemSubmit;)V",
        at = @At("RETURN")
    )
    private void asteria_flushShaderBuffer(CallbackInfo ci,
        @Local(argsOnly = true) MultiBufferSource.BufferSource bufferSource,
        @Local(argsOnly = true) SubmitNodeStorage.ItemSubmit itemSubmit) {
        if (itemSubmit.displayContext().firstPerson() && ModuleManager.INSTANCE.getHandShader().getEnabled()) {
            for (RenderType type : asteria$usedRenderTypes) {
                bufferSource.endBatch(type);
            }
            asteria$usedRenderTypes.clear();
        }
    }
}
