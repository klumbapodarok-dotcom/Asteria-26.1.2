package asteria.top.client.gui.hud

import net.minecraft.client.gui.GuiGraphicsExtractor

fun drawSplitHudPanel(graphics: GuiGraphicsExtractor, x: Float, y: Float, width: Float, headerHeight: Float, bodyHeight: Float) {
    HudStyle.panel(graphics, HudBounds(x, y, width, headerHeight + 1.0f), 5.0f)
    HudStyle.panel(graphics, HudBounds(x, y + headerHeight, width, bodyHeight), 5.0f)
}
