package asteria.top.client.mixin

import com.mojang.blaze3d.vertex.PoseStack
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.HandsRenderer
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ItemInHandRenderer::class)
abstract class ItemInHandRendererMixin {
    @Inject(method = ["renderHandsWithItems"], at = [At("HEAD")])
    private fun asteriaBeginHandsCapture(
        tickDelta: Float,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        player: net.minecraft.client.player.LocalPlayer,
        light: Int,
        ci: CallbackInfo,
    ) {
        HandsRenderer.beginCapture()
    }

    @Inject(method = ["renderHandsWithItems"], at = [At("RETURN")])
    private fun asteriaEndHandsCapture(
        tickDelta: Float,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        player: net.minecraft.client.player.LocalPlayer,
        light: Int,
        ci: CallbackInfo,
    ) {
        HandsRenderer.endCapture()
    }

    @Inject(
        method = ["renderArmWithItem"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            ),
        ],
    )
    private fun glacierApplyViewModelTransform(
        player: AbstractClientPlayer,
        tickDelta: Float,
        pitch: Float,
        hand: InteractionHand,
        swingProgress: Float,
        stack: ItemStack,
        equipProgress: Float,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        light: Int,
        ci: CallbackInfo,
    ) {
        ModuleManager.viewModel.applyTransform(poseStack, hand)
    }
}
