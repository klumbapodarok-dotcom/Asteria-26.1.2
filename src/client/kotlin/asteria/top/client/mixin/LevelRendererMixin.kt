package asteria.top.client.mixin

import asteria.top.client.util.ProjectionUtil
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(LevelRenderer::class)
class LevelRendererMixin {
    // Captured during the extract phase, not renderLevel: GameRenderer.extract() runs
    // extractCamera -> extractLevel -> extractGui, while renderLevel happens afterwards in the
    // render phase. Capturing in renderLevel left the GUI pass projecting with the previous
    // frame's camera, which made screen-space tags slide opposite to camera rotation.
    @Suppress("UNUSED_PARAMETER")
    @Inject(method = ["extractLevel"], at = [At("TAIL")])
    private fun asteriaCaptureProjectionState(
        deltaTracker: DeltaTracker,
        camera: Camera,
        partialTick: Float,
        ci: CallbackInfo,
    ) {
        val gameRenderState = Minecraft.getInstance().gameRenderer.gameRenderState
        ProjectionUtil.capture(
            gameRenderState.levelRenderState.cameraRenderState,
            camera.fov,
            gameRenderState.optionsRenderState.bobView,
        )
    }
}
