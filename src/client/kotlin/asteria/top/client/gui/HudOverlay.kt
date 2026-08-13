package asteria.top.client.gui

import asteria.top.client.gui.hud.HudManager
import asteria.top.client.gui.hud.NotificationManager
import net.minecraft.client.gui.GuiGraphicsExtractor

object HudOverlay {
    fun extract(graphics: GuiGraphicsExtractor) {
        HudManager.extract(graphics)
        NotificationManager.extract(graphics)
    }

    fun shouldPostProcess(): Boolean = HudManager.shouldPostProcess()

    fun customHotbarActive(): Boolean = HudManager.customHotbarActive()

    fun blurBoxes(guiScale: Float): List<AsteriaOverlay.BlurBox> =
        HudManager.blurBoxes(guiScale) + NotificationManager.blurBoxes(guiScale)

    fun toggleEditor(): Boolean = HudManager.toggleEditor()

    fun mouseClicked(button: Int, action: Int): Boolean =
        NotificationManager.mouseClicked(button, action) || HudManager.mouseClicked(button, action)

    fun mouseReleased(button: Int, action: Int): Boolean =
        NotificationManager.mouseReleased(action) || HudManager.mouseReleased(button, action)
}
