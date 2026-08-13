package asteria.top.client.mixin

import asteria.top.client.gui.hud.HudStyle
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.modules.visual.AmbienceModule
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.client.renderer.fog.FogRenderer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.material.FogType
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

        val mode = ambience.fogMode.value
        if (mode == AmbienceModule.FogMode.NOTHING || shouldKeepVanillaFog(camera)) return

        val data = cir.returnValue
        if (mode == AmbienceModule.FogMode.CLEAR) {
            val far = renderDistance * 16.0f * 4.0f
            applyDistances(data, far, far)
            return
        }

        val start = ambience.fogStart.value * 64.0f
        val end = maxOf(ambience.fogEnd.value * 128.0f, start + 10.0f)
        applyDistances(data, start, end)

        val color = if (ambience.fogColorMode.value == AmbienceModule.FogColorMode.INTERFACE) {
            HudStyle.ACCENT
        } else {
            ambience.fogColor.value
        }
        data.color.x = ((color shr 16) and 0xFF) / 255.0f
        data.color.y = ((color shr 8) and 0xFF) / 255.0f
        data.color.z = (color and 0xFF) / 255.0f
    }

    private fun applyDistances(data: FogData, start: Float, end: Float) {
        data.renderDistanceStart = start
        data.renderDistanceEnd = end
        data.environmentalStart = start
        data.environmentalEnd = end
    }

    private fun shouldKeepVanillaFog(camera: Camera): Boolean {
        val fluid = camera.fluidInCamera
        if (fluid == FogType.WATER || fluid == FogType.LAVA) return true
        val entity = camera.entity()
        return entity is LivingEntity && entity.hasEffect(MobEffects.BLINDNESS)
    }
}
