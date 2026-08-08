package asteria.top.client.mixin

import asteria.top.client.gui.AsteriaClickGui
import asteria.top.client.gui.CosmeticsMenu
import asteria.top.client.gui.HudOverlay
import asteria.top.client.module.ModuleManager
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyboardHandler
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(KeyboardHandler::class)
class KeyboardHandlerMixin {
    @Inject(method = ["keyPress"], at = [At("HEAD")], cancellable = true)
    private fun glacierOnKey(window: Long, action: Int, event: KeyEvent, ci: CallbackInfo) {
        val mc = Minecraft.getInstance()
        if (CosmeticsMenu.keyPressed(event.key(), action)) {
            ci.cancel()
            return
        }
        if (action == GLFW.GLFW_PRESS && mc.screen == null && event.key() == GLFW.GLFW_KEY_INSERT && HudOverlay.toggleEditor()) {
            ci.cancel()
            return
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && AsteriaClickGui.visible) {
            // When a vanilla screen (most commonly the pause menu) is open, let it
            // consume Escape first. Once the player is back in-game, the next Escape
            // closes ClickGUI instead of immediately opening the pause menu again.
            if (action == GLFW.GLFW_PRESS && mc.screen == null) {
                AsteriaClickGui.toggle()
                ci.cancel()
            }
            return
        }
        if (action == GLFW.GLFW_PRESS && event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (mc.screen == null || AsteriaClickGui.visible) {
                AsteriaClickGui.toggle()
                ci.cancel()
                return
            }
        }

        if (AsteriaClickGui.keyPressed(event.key(), action)) {
            ci.cancel()
            return
        }

        if (action == GLFW.GLFW_PRESS && mc.screen == null && ModuleManager.handleKey(event.key())) {
            ci.cancel()
        }
    }
}
