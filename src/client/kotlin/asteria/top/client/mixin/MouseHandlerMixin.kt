package asteria.top.client.mixin

import asteria.top.client.gui.AsteriaClickGui
import asteria.top.client.gui.HudOverlay
import net.minecraft.client.MouseHandler
import net.minecraft.client.input.MouseButtonInfo
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(MouseHandler::class)
class MouseHandlerMixin {
    @Inject(method = ["onButton"], at = [At("HEAD")], cancellable = true)
    private fun glacierOnButton(window: Long, buttonInfo: MouseButtonInfo, action: Int, ci: CallbackInfo) {
        if (HudOverlay.mouseClicked(buttonInfo.button(), action)) {
            ci.cancel()
            return
        }
        if (HudOverlay.mouseReleased(action)) {
            ci.cancel()
            return
        }
        if (AsteriaClickGui.mouseClicked(buttonInfo.button(), action)) {
            ci.cancel()
            return
        }
        AsteriaClickGui.mouseReleased(buttonInfo.button(), action)
    }

    @Inject(method = ["onScroll"], at = [At("HEAD")], cancellable = true)
    private fun glacierOnScroll(window: Long, horizontal: Double, vertical: Double, ci: CallbackInfo) {
        if (AsteriaClickGui.mouseScrolled(vertical)) {
            ci.cancel()
        }
    }
}
