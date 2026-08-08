package asteria.top.client.mixin

import asteria.top.client.gui.AsteriaClickGui
import asteria.top.client.gui.HudOverlay
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Gui::class)
class GuiMixin {
    @Inject(method = ["extractRenderState"], at = [At("HEAD")], cancellable = true)
    private fun glacierExtractClickGui(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker, ci: CallbackInfo) {
        HudOverlay.extract(graphics)
        asteria.top.client.module.ModuleManager.nameTags.onRenderWithEntities(graphics, deltaTracker.getGameTimeDeltaPartialTick(false))
        asteria.top.client.module.ModuleManager.arrows.onRenderWithEntities(graphics, deltaTracker.getGameTimeDeltaPartialTick(false))
        asteria.top.client.module.ModuleManager.predictions.onRenderWithEntities(graphics, deltaTracker.getGameTimeDeltaPartialTick(false))
        asteria.top.client.module.ModuleManager.trapEsp.onRenderWithEntities(graphics, deltaTracker.getGameTimeDeltaPartialTick(false))
        if (!AsteriaClickGui.shouldRender()) return
        // Vanilla screens (pause menu, options, chat, etc.) own the single
        // blur boundary allowed by GuiRenderState for this frame.
        if (Minecraft.getInstance().screen != null) return
        // Draw HUD first, then blur the framebuffer before ClickGUI strata.
        graphics.nextStratum()
        graphics.blurBeforeThisStratum()
        AsteriaClickGui.extract(graphics)
        ci.cancel()
    }

    @Inject(method = ["extractItemHotbar"], at = [At("HEAD")], cancellable = true)
    private fun glacierCancelVanillaHotbar(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker, ci: CallbackInfo) {
        if (HudOverlay.customHotbarActive()) ci.cancel()
    }
}
