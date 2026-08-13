package asteria.top.client.gui.hud

import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.MsdfIconRenderer
import asteria.top.client.util.AnimationUtil
import asteria.top.client.util.GuiShapeUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.resources.Identifier
import kotlin.math.abs
import kotlin.math.exp

// The keybinds panel, generalised: a titled plate whose rows fold open and shut,
// with an icon column added on the left. Every metric here is the one
// KeybindsHudWidget derives from the mock, so the panels stay one family however
// they are retuned: a 21px header, 14px rows, a 7px inset and a bottom padding
// derived from it rather than typed in, which is what keeps the space under the
// last row equal to the space at the sides.
private const val LIST_TITLE_SIZE = 8.5f
private const val LIST_TEXT_SIZE = 8.5f
private const val LIST_HEADER_HEIGHT = 21.0f
private const val LIST_ROW_HEIGHT = 14.0f
private const val LIST_PADDING = 7.0f
private const val LIST_ROW_TEXT_INSET = (LIST_ROW_HEIGHT - LIST_TEXT_SIZE) * 0.5f
private const val LIST_BOTTOM_PADDING = LIST_PADDING - LIST_ROW_TEXT_INSET
// The icon column is shared by the header and the rows, so a row's name starts
// on exactly the same pixel as the panel's title.
private const val LIST_ICON_WIDTH = 10.0f
private const val LIST_ICON_GAP = 2.75f
// Smallest gap kept between the longest name and the value column. The values
// hang off the panel's right padding, so this is what widens the panel rather
// than letting a long name run into its value.
private const val LIST_COLUMN_GAP = 5.5f
private const val LIST_PANEL_RADIUS = 10.0f
private const val LIST_BACKGROUND = 0xCC000000.toInt()
private const val LIST_ACCENT = HudStyle.THEME
private const val LIST_EMPTY_COLOR = 0xFF9A9A9A.toInt()
// Only drawn with the glass off, because with it on the panel itself lives
// underneath the HUD layer and a shadow drawn here would darken the glass
// instead of ringing it.
private const val LIST_SHADOW_SIZE = 6.0f
private const val LIST_SHADOW_OPACITY = 0.5f

private const val LIST_ROW_FADE_IN_DURATION = 200L
private const val LIST_ROW_FADE_OUT_DURATION = 170L
// A row slides this far as it fades, so it reads as arriving rather than blinking.
private const val LIST_ROW_SLIDE = 4.0f
// Time constant of the width easing. Names and countdowns change length in
// jumps; the panel must not.
private const val LIST_WIDTH_TAU = 90.0f

// The header glyphs are baked with a 4 texel margin inside their 64px atlas, so
// the quad they are drawn on is 64/56 of the glyph's own width and centred on
// the same point. This is the same correction the keybinds header makes.
private const val LIST_ICON_QUAD = 64.0f / 56.0f

// A medium face rather than the mock's 400: at HUD sizes the regular cut has no
// solid core left between its antialiased edges and reads as smeared. One face
// for the title, the names and the values, so nothing in the panel reads a
// weight apart from anything else.
private val LIST_FACE = FontRenderer.Face.SfMedium

/** One row as its owner sees it: an icon, what it is, and how long it has left. */
data class IconListRow<Icon : Any>(
    val key: String,
    val name: String,
    val value: String,
    val icon: Icon,
)

/** The same row as the panel sees it, carrying the fold it is currently in. */
class IconListRowState<Icon : Any>(
    val key: String,
    var name: String,
    var value: String,
    var icon: Icon,
) {
    val appear = AnimationUtil.TimedAnimation(0.0f)
}

/**
 * The keybinds panel with an icon column: a titled plate listing one row per
 * live entry, icon on the left, name beside it and the time remaining hung off
 * the right padding in the accent blue.
 *
 * Subclasses only supply the rows and know how to draw one row's icon; the
 * layout, the folding and the panel itself are the same for all of them.
 */
abstract class IconListHudWidget<Icon : Any>(
    id: String,
    title: String,
    iconGlyph: String,
    x: Float,
    y: Float,
    private val panelTitle: String,
    private val panelIcon: Identifier,
    private val emptyText: String,
) : HudWidget(
    id = id,
    title = title,
    iconGlyph = iconGlyph,
    x = x,
    y = y,
    width = 90.0f,
    height = LIST_HEADER_HEIGHT + LIST_ROW_HEIGHT * 2.0f + LIST_BOTTOM_PADDING,
) {
    private val rowStates = linkedMapOf<String, IconListRowState<Icon>>()
    private var renderRows = emptyList<IconListRowState<Icon>>()
    private val emptyAppear = AnimationUtil.TimedAnimation(0.0f)
    /** Width of the icon-name-value block, which is what the value column hangs off. */
    private var rowBlockWidth = 0.0f
    private var lastAdvanceNanos = 0L

    /** Everything currently worth a row, already in the order it should be listed. */
    protected abstract fun rows(mc: Minecraft, preview: Boolean): List<IconListRow<Icon>>

    /**
     * Draws one row's icon in the square the layout reserved for it. [size]
     * shrinks with the row while it folds, so the icon has to be drawn to the
     * box it is given rather than at its own natural size.
     */
    protected abstract fun drawRowIcon(
        graphics: GuiGraphicsExtractor,
        icon: Icon,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
    )

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

        val active = rows(mc, preview)
        syncRows(active)

        // With nothing live there is nothing to list, so the panel only says so
        // where it is being looked at deliberately: the HUD editor and the chat
        // screen, which is where widgets are moved from.
        val showEmpty = active.isEmpty() && (preview || mc.screen is ChatScreen)
        emptyAppear.update()
        emptyAppear.run(
            if (showEmpty) 1.0f else 0.0f,
            if (showEmpty) LIST_ROW_FADE_IN_DURATION else LIST_ROW_FADE_OUT_DURATION,
            { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) },
            true,
        )

        // Measured from the rows that are staying, not from the ones on their way
        // out, so a long name that has just run out lets the panel narrow while it
        // fades instead of holding it open until it is gone.
        rowBlockWidth = approach(rowBlockWidth, targetRowBlockWidth(active), LIST_WIDTH_TAU, dt)

        // Each row occupies its own share of the body, so a row that is fading is
        // also folding: the panel grows and shrinks with its contents and the rows
        // under it slide up without a second animation to keep in step.
        bounds.height = LIST_HEADER_HEIGHT + bodyHeight() + LIST_BOTTOM_PADDING
        bounds.width = LIST_PADDING * 2.0f + maxOf(
            headerWidth(),
            rowBlockWidth,
            if (showEmpty) textWidth(emptyText) else 0.0f,
        )
    }

    override fun blurBoxes(guiScale: Float, tintStrength: Float): List<AsteriaOverlay.BlurBox> {
        return listOf(
            AsteriaOverlay.BlurBox(
                bounds.x * guiScale,
                bounds.y * guiScale,
                bounds.width * guiScale,
                bounds.height * guiScale,
                LIST_PANEL_RADIUS * guiScale,
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
                LIST_PANEL_RADIUS,
                LIST_SHADOW_SIZE,
                LIST_SHADOW_OPACITY * alpha,
                1.0f,
            )
            HudStyle.rect(
                graphics,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                LIST_PANEL_RADIUS,
                fadeColor(LIST_BACKGROUND, alpha),
            )
        }

        drawHeader(graphics, alpha)

        var offset = 0.0f
        renderRows.forEach { row ->
            val appear = row.appear.value.coerceIn(0.0f, 1.0f)
            val band = LIST_ROW_HEIGHT * appear
            drawRow(graphics, row, bounds.y + LIST_HEADER_HEIGHT + offset, band, appear, alpha)
            offset += band
        }
        val empty = emptyAppear.value.coerceIn(0.0f, 1.0f)
        if (empty > 0.004f) {
            val textY = bounds.y + LIST_HEADER_HEIGHT + offset +
                (LIST_ROW_HEIGHT * empty - LIST_TEXT_SIZE) * 0.5f
            FontRenderer.drawCentered(
                graphics,
                LIST_FACE,
                emptyText,
                bounds.x + bounds.width * 0.5f,
                textY,
                LIST_TEXT_SIZE,
                fadeColor(LIST_EMPTY_COLOR, alpha * empty),
            )
        }
    }

    private fun drawHeader(graphics: GuiGraphicsExtractor, alpha: Float) {
        val quad = LIST_ICON_WIDTH * LIST_ICON_QUAD
        MsdfIconRenderer.draw(
            graphics,
            panelIcon,
            bounds.x + LIST_PADDING - (quad - LIST_ICON_WIDTH) * 0.5f,
            bounds.y + LIST_HEADER_HEIGHT * 0.5f - quad * 0.5f,
            quad,
            quad,
            fadeColor(LIST_ACCENT, alpha),
            edge = MsdfIconRenderer.Edge.CrispRange6,
        )
        FontRenderer.draw(
            graphics,
            LIST_FACE,
            panelTitle,
            bounds.x + LIST_PADDING + LIST_ICON_WIDTH + LIST_ICON_GAP,
            bounds.y + (LIST_HEADER_HEIGHT - LIST_TITLE_SIZE) * 0.5f,
            LIST_TITLE_SIZE,
            fadeColor(HudStyle.TEXT, alpha),
        )
    }

    /**
     * One row, drawn centred in the share of the body it currently occupies. A
     * half-folded row therefore sits in a half-tall band and carries its icon and
     * text with it, which is what makes the list fold rather than jump.
     */
    private fun drawRow(
        graphics: GuiGraphicsExtractor,
        row: IconListRowState<Icon>,
        top: Float,
        band: Float,
        appear: Float,
        alpha: Float,
    ) {
        val rowAlpha = alpha * appear
        if (rowAlpha <= 0.004f) return
        val textY = top + (band - LIST_TEXT_SIZE) * 0.5f
        val slide = (1.0f - appear) * LIST_ROW_SLIDE
        // The icon folds with its row rather than overflowing the band it is
        // drawn in, so a closing row never spills over the one below it.
        val iconSize = LIST_ICON_WIDTH * appear
        drawRowIcon(
            graphics,
            row.icon,
            bounds.x + LIST_PADDING + (LIST_ICON_WIDTH - iconSize) * 0.5f + slide,
            top + (band - iconSize) * 0.5f,
            iconSize,
            rowAlpha,
        )
        FontRenderer.draw(
            graphics,
            LIST_FACE,
            row.name,
            bounds.x + LIST_PADDING + LIST_ICON_WIDTH + LIST_ICON_GAP + slide,
            textY,
            LIST_TEXT_SIZE,
            fadeColor(HudStyle.TEXT, rowAlpha),
        )
        // Hung off the panel's right padding, so the values line up with the edge
        // the title and names are inset from however wide the panel has grown.
        FontRenderer.draw(
            graphics,
            LIST_FACE,
            row.value,
            bounds.x + bounds.width - LIST_PADDING - textWidth(row.value) + slide,
            textY,
            LIST_TEXT_SIZE,
            fadeColor(LIST_ACCENT, rowAlpha),
        )
    }

    /**
     * Folds the current list into the animated one: rows that are new start
     * folded open, rows that left fold shut and are dropped once they have.
     */
    private fun syncRows(active: List<IconListRow<Icon>>) {
        val activeByKey = active.associateBy { it.key }
        active.forEach { row ->
            val state = rowStates.getOrPut(row.key) { IconListRowState(row.key, row.name, row.value, row.icon) }
            state.name = row.name
            state.value = row.value
            state.icon = row.icon
        }
        val rows = mutableListOf<IconListRowState<Icon>>()
        val iterator = rowStates.iterator()
        while (iterator.hasNext()) {
            val row = iterator.next().value
            val stillActive = activeByKey.containsKey(row.key)
            row.appear.update()
            row.appear.run(
                if (stillActive) 1.0f else 0.0f,
                if (stillActive) LIST_ROW_FADE_IN_DURATION else LIST_ROW_FADE_OUT_DURATION,
                { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) },
                true,
            )
            if (!stillActive && row.appear.value <= 0.001f) iterator.remove() else rows += row
        }
        renderRows = rows.sortedBy { it.name.lowercase() }
    }

    private fun bodyHeight(): Float {
        var height = LIST_ROW_HEIGHT * emptyAppear.value.coerceIn(0.0f, 1.0f)
        renderRows.forEach { height += LIST_ROW_HEIGHT * it.appear.value.coerceIn(0.0f, 1.0f) }
        return height
    }

    private fun headerWidth(): Float =
        LIST_ICON_WIDTH + LIST_ICON_GAP + FontRenderer.width(LIST_FACE, panelTitle, LIST_TITLE_SIZE)

    private fun targetRowBlockWidth(active: List<IconListRow<Icon>>): Float {
        var width = 0.0f
        active.forEach { row ->
            width = maxOf(
                width,
                LIST_ICON_WIDTH + LIST_ICON_GAP + textWidth(row.name) + LIST_COLUMN_GAP + textWidth(row.value),
            )
        }
        return width
    }

    private fun textWidth(text: String): Float = FontRenderer.width(LIST_FACE, text, LIST_TEXT_SIZE)

    private fun approach(current: Float, target: Float, tauMs: Float, dtMs: Float): Float {
        if (dtMs <= 0.0f) return current
        val next = current + (target - current) * (1.0f - exp(-dtMs / tauMs))
        return if (abs(target - next) < 0.05f) target else next
    }

    protected fun fadeColor(color: Int, alphaMultiplier: Float): Int {
        val alpha = ((color ushr 24) * alphaMultiplier.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
