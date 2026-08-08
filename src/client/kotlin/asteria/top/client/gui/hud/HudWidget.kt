package asteria.top.client.gui.hud

import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.AsteriaGuiRenderer
import asteria.top.client.util.GuiBoxUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

data class HudBounds(
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
)

abstract class HudWidget(
    val id: String,
    val title: String,
    val iconGlyph: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    enabledByDefault: Boolean = true,
    val movable: Boolean = true,
) {
    val bounds = HudBounds(x, y, width, height)
    var enabled = enabledByDefault
    var visibilityAlpha = 1.0f

    open fun update(mc: Minecraft, preview: Boolean) = Unit

    abstract fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean)

    open fun visible(mc: Minecraft, preview: Boolean): Boolean = enabled || preview

    open fun blurBoxes(guiScale: Float, tintStrength: Float): List<AsteriaOverlay.BlurBox> {
        return listOf(
            AsteriaOverlay.BlurBox(
                bounds.x * guiScale,
                bounds.y * guiScale,
                bounds.width * guiScale,
                bounds.height * guiScale,
                8.0f * guiScale,
                tintStrength,
            ),
        )
    }
}

object HudStyle {
    const val ACCENT = 0xFF88FF82.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val MUTED = 0x80FFFFFF.toInt()
    private const val PANEL_FALLBACK = 0xE6121212.toInt()

    fun panel(graphics: GuiGraphicsExtractor, bounds: HudBounds, radius: Float = 8.0f) {
        if (!ModuleManager.postProcessing.enabled) {
            rect(graphics, bounds.x, bounds.y, bounds.width, bounds.height, radius, PANEL_FALLBACK)
        }
    }

    fun rect(
        graphics: GuiGraphicsExtractor,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
    ) {
        val boxes = mutableListOf<GuiBoxUtil.Box>()
        AsteriaGuiRenderer.rect(boxes, x, y, width, height, radius, color)
        boxes.forEach { GuiBoxUtil.draw(graphics, it) }
    }

    fun pill(
        graphics: GuiGraphicsExtractor,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
    ) {
        val boxes = mutableListOf<GuiBoxUtil.Box>()
        AsteriaGuiRenderer.pill(boxes, x, y, width, height, color)
        boxes.forEach { GuiBoxUtil.draw(graphics, it) }
    }

    fun roundedBar(
        graphics: GuiGraphicsExtractor,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
    ) {
        rect(graphics, x, y, width, height, radius, color)
    }

    fun icon(
        graphics: GuiGraphicsExtractor,
        glyph: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int = ACCENT,
    ) {
        FontRenderer.draw(graphics, FontRenderer.Face.HudIcon, glyph, x, y, size, color)
    }
}
