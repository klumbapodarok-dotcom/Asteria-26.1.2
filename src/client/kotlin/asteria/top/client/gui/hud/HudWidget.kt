package asteria.top.client.gui.hud

import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.AsteriaGuiRenderer
import asteria.top.client.util.GuiBoxUtil
import com.google.gson.JsonObject
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

    /**
     * Steps widget-owned animations. Unlike [update] this runs exactly once per
     * frame, so the blur pass and the render pass of one frame see identical
     * positions instead of drifting a fraction of a frame apart.
     */
    open fun advance(mc: Minecraft, preview: Boolean) = Unit

    abstract fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean)

    /**
     * Widget-local mouse interaction, offered before the manager starts its own
     * whole-widget drag. Returning true consumes the click.
     */
    open fun mousePressed(button: Int, mouseX: Float, mouseY: Float): Boolean = false

    /**
     * Maps a screen coordinate into the widget's own space. Widgets are rendered
     * scaled by the HUD size setting around the centre of their bounds, so hit
     * testing has to undo that transform or every control drifts further from its
     * pixels the further it sits from that centre.
     */
    fun toLocalX(screenX: Float): Float = unscale(screenX, bounds.x + bounds.width * 0.5f)

    fun toLocalY(screenY: Float): Float = unscale(screenY, bounds.y + bounds.height * 0.5f)

    private fun unscale(value: Float, pivot: Float): Float {
        val scale = ModuleManager.interfaceModule.hudScaleMultiplier()
        if (scale <= 0.0f || kotlin.math.abs(scale - 1.0f) < 0.001f) return value
        return pivot + (value - pivot) / scale
    }

    /**
     * The area the widget wants clicks from. Widgets that open popups outside
     * their own bounds widen this so the popup stays clickable.
     */
    open fun containsMouse(mouseX: Float, mouseY: Float): Boolean =
        mouseX >= bounds.x && mouseX <= bounds.x + bounds.width &&
            mouseY >= bounds.y && mouseY <= bounds.y + bounds.height

    /** Called when a press landed somewhere else, so popups can close. */
    open fun mouseMissed() = Unit

    /** Returns true when the widget was mid-interaction and finished it here. */
    open fun mouseReleased(button: Int): Boolean = false

    /** Extra per-widget layout state stored next to the saved position. */
    open fun saveState(state: JsonObject) = Unit

    open fun loadState(state: JsonObject) = Unit

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
    /**
     * Interface theme colour. Every HUD widget accent (watermark, keybinds,
     * icon lists, target info) is this value, so world effects that should
     * match the interface read it from here instead of repeating a literal.
     */
    const val THEME = 0xFF91B7FF.toInt()
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
