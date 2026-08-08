package asteria.top.client.mixin

import asteria.top.client.render.SkyShaderRenderer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SkyRenderer
import net.minecraft.world.level.MoonPhase
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(SkyRenderer::class)
class SkyRendererMixin {
    @Inject(method = ["renderSkyDisc"], at = [At("HEAD")], cancellable = true)
    private fun asteriaRenderSkyDisc(color: Int, ci: CallbackInfo) {
        if (!SkyShaderRenderer.shouldRender()) return
        SkyShaderRenderer.render()
        ci.cancel()
    }

    @Inject(method = ["renderEndSky"], at = [At("HEAD")], cancellable = true)
    private fun asteriaRenderEndSky(ci: CallbackInfo) {
        if (!SkyShaderRenderer.shouldRender()) return
        SkyShaderRenderer.render()
        ci.cancel()
    }

    @Inject(method = ["renderDarkDisc"], at = [At("HEAD")], cancellable = true)
    private fun asteriaCancelDarkDisc(ci: CallbackInfo) {
        if (SkyShaderRenderer.shouldRender()) ci.cancel()
    }

    @Inject(method = ["renderSunriseAndSunset"], at = [At("HEAD")], cancellable = true)
    private fun asteriaCancelSunriseAndSunset(matrices: PoseStack, angle: Float, color: Int, ci: CallbackInfo) {
        if (SkyShaderRenderer.shouldRender()) ci.cancel()
    }

    @Inject(method = ["renderSunMoonAndStars"], at = [At("HEAD")], cancellable = true)
    private fun asteriaCancelSunMoonAndStars(
        matrices: PoseStack,
        sunAngle: Float,
        moonAngle: Float,
        starAngle: Float,
        moonPhase: MoonPhase,
        rainBrightness: Float,
        starBrightness: Float,
        ci: CallbackInfo,
    ) {
        if (SkyShaderRenderer.shouldRender()) ci.cancel()
    }

    @Inject(method = ["renderEndFlash"], at = [At("HEAD")], cancellable = true)
    private fun asteriaCancelEndFlash(
        matrices: PoseStack,
        intensity: Float,
        xAngle: Float,
        yAngle: Float,
        ci: CallbackInfo,
    ) {
        if (SkyShaderRenderer.shouldRender()) ci.cancel()
    }
}
