package asteria.top.client.gui.hud

import asteria.top.client.module.ModuleManager
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.MsdfIconRenderer
import asteria.top.client.util.AnimationUtil
import asteria.top.client.util.GuiShapeUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.exp

// Like the watermark, the mock is a 12px-font design whose absolute pixel sizes
// read far too large against the rest of the HUD, so only its layout is taken
// from Figma while the metrics come from the shared HUD template. The mock's
// 10px inset is that same 0.75 away from the padding used here, and the bottom
// padding is derived from it rather than typed in, which is what keeps the space
// under the last row equal to the space at the sides however the rows are
// retuned. Sizes sit a notch above the HUD's 8.5 on a medium face: at 8.5 the
// MSDF stems land under a screen pixel and read as blurry.
private const val KEYBIND_TITLE = "Клавиши"
private const val KEYBIND_EMPTY_TEXT = "Нету активных биндов"
private const val KEYBIND_TITLE_SIZE = 8.5f
private const val KEYBIND_TEXT_SIZE = 8.5f
private const val KEYBIND_HEADER_HEIGHT = 21.0f
private const val KEYBIND_ROW_HEIGHT = 14.0f
private const val KEYBIND_PADDING = 7.0f
private const val KEYBIND_ROW_TEXT_INSET = (KEYBIND_ROW_HEIGHT - KEYBIND_TEXT_SIZE) * 0.5f
private const val KEYBIND_BOTTOM_PADDING = KEYBIND_PADDING - KEYBIND_ROW_TEXT_INSET
private const val KEYBIND_ICON_WIDTH = 10.0f
private const val KEYBIND_ICON_GAP = 2.75f
// Smallest gap kept between the longest name and the key column. The keys hang
// off the panel's right padding, so this is what widens the panel rather than
// letting a long name run into its key.
private const val KEYBIND_COLUMN_GAP = 5.5f
private const val KEYBIND_PANEL_RADIUS = 10.0f
private const val KEYBIND_BACKGROUND = 0xCC000000.toInt()
private const val KEYBIND_ACCENT = HudStyle.THEME
private const val KEYBIND_EMPTY_COLOR = 0xFF9A9A9A.toInt()
// box-shadow: 0 0 10px rgba(0, 0, 0, 0.83) from the mock. It is only drawn with
// the glass off, because with it on the panel itself lives underneath the HUD
// layer and a shadow drawn here would darken the glass instead of ringing it.
private const val KEYBIND_SHADOW_SIZE = 6.0f
private const val KEYBIND_SHADOW_OPACITY = 0.5f

private const val KEYBIND_ROW_FADE_IN_DURATION = 200L
private const val KEYBIND_ROW_FADE_OUT_DURATION = 170L
// A row slides this far as it fades, so it reads as arriving rather than blinking.
private const val KEYBIND_ROW_SLIDE = 4.0f
// Time constant of the width easing. Names change length in jumps; the panel
// must not.
private const val KEYBIND_WIDTH_TAU = 90.0f

// The glyph is baked with a margin inside its 64px atlas, so the quad it is
// drawn on is 64/56 of the glyph's own width and centred on the same point.
private val KEYBIND_ICON = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/keysoutline.png")
private const val KEYBIND_ICON_QUAD = 64.0f / 56.0f

// A medium face rather than the mock's 400: at HUD sizes the regular cut has no
// solid core left between its antialiased edges and reads as smeared. One face
// for the title, the names and the keys, so nothing in the panel reads a weight
// apart from anything else.
private val KEYBIND_FACE = FontRenderer.Face.SfMedium

/**
 * The keybind list from the mock: a titled panel that lists the modules which
 * are both bound and currently on, one row each, name in white and key in the
 * accent blue.
 */
class KeybindsHudWidget : HudWidget(
    id = "keybinds",
    title = "Keybinds",
    iconGlyph = "A",
    x = 7.5f,
    y = 28.0f,
    width = 90.0f,
    height = KEYBIND_HEADER_HEIGHT + KEYBIND_ROW_HEIGHT * 2.0f + KEYBIND_BOTTOM_PADDING,
) {
    private val rowStates = linkedMapOf<String, RowState>()
    private var renderRows = emptyList<RowState>()
    private val emptyAppear = AnimationUtil.TimedAnimation(0.0f)
    /** Width of the name-plus-key block, which is what the key column hangs off. */
    private var rowBlockWidth = 0.0f
    private var lastAdvanceNanos = 0L

    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        val hasRows = ModuleManager.modules.any { it.bind >= 0 && it.enabled }
        val chatPreview = mc.screen is ChatScreen
        return (enabled && ModuleManager.interfaceModule.keybinds.value && (hasRows || chatPreview)) || preview
    }

    /**
     * Layout is stepped here rather than in [update] so the blur pass and the
     * render pass of one frame see the same panel: both run after this, and this
     * runs exactly once.
     */
    override fun advance(mc: Minecraft, preview: Boolean) {
        val now = System.nanoTime()
        val deltaMs = if (lastAdvanceNanos == 0L) 0.0f else ((now - lastAdvanceNanos) / 1_000_000.0).toFloat()
        lastAdvanceNanos = now
        val dt = deltaMs.coerceIn(0.0f, 100.0f)

        // A widget on its way out has to keep the shape it had. Re-targeting here
        // would fold its rows shut and shrink the panel underneath the fade, and
        // closing the chat on an empty list would take the panel apart in front
        // of the player instead of fading it away whole.
        if (!visible(mc, preview) && visibilityAlpha > 0.01f) return

        val active = activeRows(preview)
        syncRows(active)

        // With nothing bound and on there is nothing to list, so the panel only
        // says so where it is being looked at deliberately: the HUD editor and
        // the chat screen, which is where widgets are moved from.
        val showEmpty = active.isEmpty() && (preview || mc.screen is ChatScreen)
        emptyAppear.update()
        emptyAppear.run(
            if (showEmpty) 1.0f else 0.0f,
            if (showEmpty) KEYBIND_ROW_FADE_IN_DURATION else KEYBIND_ROW_FADE_OUT_DURATION,
            { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) },
            true,
        )

        // Measured from the rows that are staying, not from the ones on their way
        // out, so a long name that has just been switched off lets the panel
        // narrow while it fades instead of holding it open until it is gone.
        rowBlockWidth = approach(rowBlockWidth, targetRowBlockWidth(active), KEYBIND_WIDTH_TAU, dt)

        // Each row occupies its own share of the body, so a row that is fading
        // is also folding: the panel grows and shrinks with its contents and the
        // rows under it slide up without a second animation to keep in step.
        bounds.height = KEYBIND_HEADER_HEIGHT + bodyHeight() + KEYBIND_BOTTOM_PADDING
        bounds.width = KEYBIND_PADDING * 2.0f + maxOf(
            headerWidth(),
            rowBlockWidth,
            if (showEmpty) textWidth(KEYBIND_EMPTY_TEXT) else 0.0f,
        )
    }

    override fun blurBoxes(guiScale: Float, tintStrength: Float): List<asteria.top.client.gui.AsteriaOverlay.BlurBox> {
        return listOf(
            asteria.top.client.gui.AsteriaOverlay.BlurBox(
                bounds.x * guiScale,
                bounds.y * guiScale,
                bounds.width * guiScale,
                bounds.height * guiScale,
                KEYBIND_PANEL_RADIUS * guiScale,
                0.65f,
            )
        )
    }

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val alpha = visibilityAlpha.coerceIn(0.0f, 1.0f)
        if (alpha <= 0.004f) return

        if (!ModuleManager.postProcessing.enabled) {
            GuiShapeUtil.softShadow(
                graphics,
                bounds.x,
                bounds.y,
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                KEYBIND_PANEL_RADIUS,
                KEYBIND_SHADOW_SIZE,
                KEYBIND_SHADOW_OPACITY * alpha,
                1.0f,
            )
            HudStyle.rect(
                graphics,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                KEYBIND_PANEL_RADIUS,
                fadeColor(KEYBIND_BACKGROUND, alpha),
            )
        }

        drawHeader(graphics, alpha)

        var offset = 0.0f
        renderRows.forEach { row ->
            val appear = row.appear.value.coerceIn(0.0f, 1.0f)
            val band = KEYBIND_ROW_HEIGHT * appear
            drawRow(graphics, row, bounds.y + KEYBIND_HEADER_HEIGHT + offset, band, appear, alpha)
            offset += band
        }
        val empty = emptyAppear.value.coerceIn(0.0f, 1.0f)
        if (empty > 0.004f) {
            val textY = bounds.y + KEYBIND_HEADER_HEIGHT + offset +
                (KEYBIND_ROW_HEIGHT * empty - KEYBIND_TEXT_SIZE) * 0.5f
            FontRenderer.drawCentered(
                graphics,
                KEYBIND_FACE,
                KEYBIND_EMPTY_TEXT,
                bounds.x + bounds.width * 0.5f,
                textY,
                KEYBIND_TEXT_SIZE,
                fadeColor(KEYBIND_EMPTY_COLOR, alpha * empty),
            )
        }
    }

    private fun drawHeader(graphics: GuiGraphicsExtractor, alpha: Float) {
        val quad = KEYBIND_ICON_WIDTH * KEYBIND_ICON_QUAD
        MsdfIconRenderer.draw(
            graphics,
            KEYBIND_ICON,
            bounds.x + KEYBIND_PADDING - (quad - KEYBIND_ICON_WIDTH) * 0.5f,
            bounds.y + KEYBIND_HEADER_HEIGHT * 0.5f - quad * 0.5f,
            quad,
            quad,
            fadeColor(KEYBIND_ACCENT, alpha),
            edge = MsdfIconRenderer.Edge.CrispRange6,
        )
        FontRenderer.draw(
            graphics,
            KEYBIND_FACE,
            KEYBIND_TITLE,
            bounds.x + KEYBIND_PADDING + KEYBIND_ICON_WIDTH + KEYBIND_ICON_GAP,
            bounds.y + (KEYBIND_HEADER_HEIGHT - KEYBIND_TITLE_SIZE) * 0.5f,
            KEYBIND_TITLE_SIZE,
            fadeColor(HudStyle.TEXT, alpha),
        )
    }

    /**
     * One row, drawn centred in the share of the body it currently occupies. A
     * half-folded row therefore sits in a half-tall band and carries its text
     * with it, which is what makes the list fold rather than jump.
     */
    private fun drawRow(
        graphics: GuiGraphicsExtractor,
        row: RowState,
        top: Float,
        band: Float,
        appear: Float,
        alpha: Float,
    ) {
        val rowAlpha = alpha * appear
        if (rowAlpha <= 0.004f) return
        val textY = top + (band - KEYBIND_TEXT_SIZE) * 0.5f
        val slide = (1.0f - appear) * KEYBIND_ROW_SLIDE
        FontRenderer.draw(
            graphics,
            KEYBIND_FACE,
            row.name,
            bounds.x + KEYBIND_PADDING + slide,
            textY,
            KEYBIND_TEXT_SIZE,
            fadeColor(HudStyle.TEXT, rowAlpha),
        )
        // Hung off the panel's right padding, so the keys line up with the edge
        // the title and names are inset from however wide the panel has grown.
        FontRenderer.draw(
            graphics,
            KEYBIND_FACE,
            row.bind,
            bounds.x + bounds.width - KEYBIND_PADDING - textWidth(row.bind) + slide,
            textY,
            KEYBIND_TEXT_SIZE,
            fadeColor(KEYBIND_ACCENT, rowAlpha),
        )
    }

    /** Bound modules that are switched on, which is the whole list the mock shows. */
    private fun activeRows(preview: Boolean): List<BindRow> {
        if (!enabled && !preview) return emptyList()
        return ModuleManager.modules
            .filter { it.bind >= 0 && it.enabled }
            .map { BindRow(it.name, keyName(it.bind)) }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Folds the current list into the animated one: rows that are new start
     * folded open, rows that left fold shut and are dropped once they have.
     */
    private fun syncRows(active: List<BindRow>) {
        val activeByName = active.associateBy { it.name }
        active.forEach { row ->
            rowStates.getOrPut(row.name) { RowState(row.name) }.bind = row.bind
        }
        val rows = mutableListOf<RowState>()
        val iterator = rowStates.iterator()
        while (iterator.hasNext()) {
            val row = iterator.next().value
            val stillBound = activeByName.containsKey(row.name)
            row.appear.update()
            row.appear.run(
                if (stillBound) 1.0f else 0.0f,
                if (stillBound) KEYBIND_ROW_FADE_IN_DURATION else KEYBIND_ROW_FADE_OUT_DURATION,
                { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) },
                true,
            )
            if (!stillBound && row.appear.value <= 0.001f) iterator.remove() else rows += row
        }
        renderRows = rows.sortedBy { it.name.lowercase() }
    }

    private fun bodyHeight(): Float {
        var height = KEYBIND_ROW_HEIGHT * emptyAppear.value.coerceIn(0.0f, 1.0f)
        renderRows.forEach { height += KEYBIND_ROW_HEIGHT * it.appear.value.coerceIn(0.0f, 1.0f) }
        return height
    }

    private fun headerWidth(): Float =
        KEYBIND_ICON_WIDTH + KEYBIND_ICON_GAP + FontRenderer.width(KEYBIND_FACE, KEYBIND_TITLE, KEYBIND_TITLE_SIZE)

    private fun targetRowBlockWidth(active: List<BindRow>): Float {
        var width = 0.0f
        active.forEach { row ->
            width = maxOf(width, textWidth(row.name) + KEYBIND_COLUMN_GAP + textWidth(row.bind))
        }
        return width
    }

    private fun textWidth(text: String): Float = FontRenderer.width(KEYBIND_FACE, text, KEYBIND_TEXT_SIZE)

    private fun approach(current: Float, target: Float, tauMs: Float, dtMs: Float): Float {
        if (dtMs <= 0.0f) return current
        val next = current + (target - current) * (1.0f - exp(-dtMs / tauMs))
        return if (abs(target - next) < 0.05f) target else next
    }

    /**
     * Labels are always Latin. GLFW names a key by what the active layout prints
     * on it, so on a Cyrillic layout the same bind would come back as "К", while
     * the key codes for letters and digits are their ASCII values and say what is
     * engraved on the keyboard. The layout name is only a fallback for
     * punctuation, and is dropped if it is not printable ASCII.
     */
    private fun keyName(key: Int): String {
        return when (key) {
            in GLFW.GLFW_KEY_A..GLFW.GLFW_KEY_Z -> ('A' + (key - GLFW.GLFW_KEY_A)).toString()
            in GLFW.GLFW_KEY_0..GLFW.GLFW_KEY_9 -> ('0' + (key - GLFW.GLFW_KEY_0)).toString()
            GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS"
            GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT"
            GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT"
            GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL"
            GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL"
            GLFW.GLFW_KEY_LEFT_ALT -> "LALT"
            GLFW.GLFW_KEY_RIGHT_ALT -> "RALT"
            GLFW.GLFW_KEY_SPACE -> "SPACE"
            GLFW.GLFW_KEY_TAB -> "TAB"
            GLFW.GLFW_KEY_ENTER -> "ENTER"
            GLFW.GLFW_KEY_ESCAPE -> "ESC"
            GLFW.GLFW_KEY_BACKSPACE -> "BACK"
            GLFW.GLFW_KEY_DELETE -> "DEL"
            GLFW.GLFW_KEY_INSERT -> "INS"
            GLFW.GLFW_KEY_HOME -> "HOME"
            GLFW.GLFW_KEY_END -> "END"
            GLFW.GLFW_KEY_PAGE_UP -> "PGUP"
            GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN"
            GLFW.GLFW_KEY_UP -> "UP"
            GLFW.GLFW_KEY_DOWN -> "DOWN"
            GLFW.GLFW_KEY_LEFT -> "LEFT"
            GLFW.GLFW_KEY_RIGHT -> "RIGHT"
            in GLFW.GLFW_KEY_F1..GLFW.GLFW_KEY_F25 -> "F${key - GLFW.GLFW_KEY_F1 + 1}"
            in GLFW.GLFW_KEY_KP_0..GLFW.GLFW_KEY_KP_9 -> "NUM${key - GLFW.GLFW_KEY_KP_0}"
            else -> latinKeyName(key) ?: key.toString()
        }
    }

    private fun latinKeyName(key: Int): String? =
        GLFW.glfwGetKeyName(key, 0)
            ?.uppercase()
            ?.takeIf { name -> name.isNotEmpty() && name.all { it.code in 33..126 } }

    private fun fadeColor(color: Int, alphaMultiplier: Float): Int {
        val alpha = ((color ushr 24) * alphaMultiplier.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private data class BindRow(val name: String, val bind: String)

    private class RowState(val name: String) {
        var bind: String = ""
        val appear = AnimationUtil.TimedAnimation(0.0f)
    }
}
