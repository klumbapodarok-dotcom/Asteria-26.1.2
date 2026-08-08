package asteria.top.client.mixin

import com.mojang.blaze3d.buffers.GpuBufferSlice
import asteria.top.client.render.DoubleKawaseBlurRenderer
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.renderer.GameRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(GuiRenderer::class)
class GuiRendererMixin {
    @Inject(method = ["render"], at = [At("HEAD")])
    private fun glacierRenderDoubleKawaseBlur(gpuBufferSlice: GpuBufferSlice, ci: CallbackInfo) {
        DoubleKawaseBlurRenderer.renderHudPass()
    }

    @Redirect(
        method = ["draw"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V",
        ),
    )
    private fun glacierRenderClickGuiBlur(gameRenderer: GameRenderer) {
        if (!DoubleKawaseBlurRenderer.renderClickGuiPass()) gameRenderer.processBlurEffect()
    }
}
