package asteria.top.client.mixin

import asteria.top.client.module.ModuleManager
import asteria.top.client.module.modules.visual.AmbienceModule
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.client.renderer.fog.FogRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(FogRenderer::class)
class FogRendererMixin {
    @Inject(method = ["setupFog"], at = [At("RETURN")])
    private fun onSetupFog(
        camera: Camera,
        renderDistance: Int,
        deltaTracker: DeltaTracker,
        partialTick: Float,
        level: ClientLevel,
        cir: CallbackInfoReturnable<FogData>,
    ) {
        val ambience = ModuleManager.ambience
        if (!ambience.enabled) return

        val data = cir.returnValue
        val mode = ambience.fogMode.value

        if (mode != AmbienceModule.FogMode.NOTHING) {
            val renderDistanceBlocks = renderDistance * 16f
            if (mode == AmbienceModule.FogMode.CLEAR) {
                val far = renderDistanceBlocks * 8f + 512f
                data.renderDistanceStart = far
                data.renderDistanceEnd = far
                data.environmentalStart = far
                data.environmentalEnd = far
            } else {
                val start = renderDistanceBlocks * ambience.fogStart.value
                val end = renderDistanceBlocks * ambience.fogEnd.value
                data.renderDistanceStart = start
                data.renderDistanceEnd = end
                data.environmentalStart = start
                data.environmentalEnd = end
            }
        }

        if (ambience.fogColorEnabled.value) {
            data.color.x = ambience.fogColorRed.value / 255.0f
            data.color.y = ambience.fogColorGreen.value / 255.0f
            data.color.z = ambience.fogColorBlue.value / 255.0f
        }
    }
}
