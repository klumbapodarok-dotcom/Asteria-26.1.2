package asteria.top.client.gui.hud

import asteria.top.client.config.ClientConfig
import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.mixin.CooldownInstanceAccessor
import asteria.top.client.mixin.ItemCooldownsAccessor
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.MsdfIconRenderer
import asteria.top.client.render.RoundedTextureRenderer
import asteria.top.client.render.TextureRenderer
import asteria.top.client.util.AnimationUtil
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

private const val POTION_TITLE = "Зелья"
private const val POTION_EMPTY_TEXT = "Нету активных зелей"
private val POTION_ICON = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/firstaidkitline.png")

private const val COOLDOWN_TITLE = "Задержки"
private const val COOLDOWN_EMPTY_TEXT = "Нету активных кулдаунов"
private val COOLDOWN_ICON = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/clockoutline.png")
private const val COOLDOWN_ITEM_NATIVE_SIZE = 16.0f

private const val WATERMARK_ROW_HEIGHT = 20.0f
private const val WATERMARK_TEXT_SIZE = 8.5f
private const val WATERMARK_PADDING = (WATERMARK_ROW_HEIGHT - WATERMARK_TEXT_SIZE) * 0.5f
private const val WATERMARK_ROW_GAP = 3.0f
private const val WATERMARK_ICON_SIZE = 10.0f
private const val WATERMARK_AVATAR_SIZE = 16.0f
private const val WATERMARK_AVATAR_INSET = (WATERMARK_ROW_HEIGHT - WATERMARK_AVATAR_SIZE) * 0.5f
private const val WATERMARK_ICON_GAP = 2.75f
private const val WATERMARK_GROUP_GAP = 4.5f
private const val WATERMARK_AVATAR_GAP = 3.5f
private val WATERMARK_FACE = FontRenderer.Face.SfMedium
private const val WATERMARK_BACKGROUND = 0xCC000000.toInt()
private const val WATERMARK_ACCENT = HudStyle.THEME
private const val WATERMARK_AVATAR_FRAMES = 102
private const val WATERMARK_AVATAR_FRAME_MS = 100L

private const val WATERMARK_ROW_PITCH = WATERMARK_ROW_HEIGHT + WATERMARK_ROW_GAP
private const val WATERMARK_ROW_FOLLOW_SMOOTH = 70.0f
private const val WATERMARK_ROW_SETTLE_SMOOTH = 170.0f
private const val WATERMARK_ROW_RUBBER_LIMIT = 7.5f
private const val WATERMARK_ROW_SWAP_SLACK = 1.5f

private const val WATERMARK_ROW_IDENTITY = 0
private const val WATERMARK_ROW_STATS = 1

private const val WATERMARK_MERGE_TRIGGER = 16.0f
private const val WATERMARK_SPLIT_TRIGGER = 12.0f
private const val WATERMARK_MERGE_TAU = 90.0f
private const val WATERMARK_DRAG_STRAY = 10.0f
private const val WATERMARK_GHOST_COLOR = 0x24FFFFFF
private const val WATERMARK_GHOST_TAU = 90.0f
private const val WATERMARK_GHOST_GLASS = 0.45f
private const val WATERMARK_GHOST_GAP = 3.0f
private const val WATERMARK_MERGE_BLEND_DISTANCE = 30.0f

private const val WATERMARK_PANEL_WIDTH = 108.0f
private const val WATERMARK_PANEL_HEADER_HEIGHT = 19.0f
private const val WATERMARK_PANEL_ROW_HEIGHT = 15.0f
private const val WATERMARK_PANEL_PADDING = 6.0f
private const val WATERMARK_PANEL_GAP = 4.0f
private const val WATERMARK_PANEL_RADIUS = 8.0f
private const val WATERMARK_PANEL_TEXT_SIZE = 8.5f
private const val WATERMARK_PANEL_TITLE_SIZE = 9.0f
private const val WATERMARK_PANEL_SHOW_DURATION = 170L
private const val WATERMARK_PANEL_HIDE_DURATION = 140L
private const val WATERMARK_PANEL_TOGGLE_WIDTH = 14.0f
private const val WATERMARK_PANEL_TOGGLE_HEIGHT = 9.0f
private const val WATERMARK_PANEL_TOGGLE_KNOB = 7.0f
private const val WATERMARK_PANEL_TOGGLE_OFF = 0x33FFFFFF

private const val WATERMARK_PANEL_CLICK_SLOP = 3.0f
private const val WATERMARK_PANEL_CLICK_TIME_MS = 350L

private enum class WatermarkMetric(val key: String, val label: String, val row: Int) {
    SERVER("server", "Server IP", WATERMARK_ROW_IDENTITY),
    PING("ping", "Ping", WATERMARK_ROW_IDENTITY),
    FPS("fps", "FPS", WATERMARK_ROW_STATS),
    TPS("tps", "TPS", WATERMARK_ROW_STATS),
    BPS("bps", "BPS", WATERMARK_ROW_STATS),
    COORDINATES("coordinates", "Coordinates", WATERMARK_ROW_STATS),
}

class WatermarkHudWidget : HudWidget(
    id = "watermark",
    title = "Watermark",
    iconGlyph = "R",
    x = 7.0f,
    y = 7.0f,
    width = 300.0f,
    height = WATERMARK_ROW_HEIGHT * 2.0f + WATERMARK_ROW_GAP,
) {
    private val avatar = Identifier.fromNamespaceAndPath("asteria", "textures/gui/zywo_avatar_frames.png")
    private val serverIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/server2line.png")
    private val pingIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/piechartoutline.png")
    private val fpsIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/scopeoutline.png")
    private val tpsIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/chipline.png")
    private val bpsIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/dashboard3line.png")
    private val coordinatesIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/earth2line.png")

    private var smoothedBps = 0.0

    private var identityRowWidth = 0.0f
    private var statsRowWidth = 0.0f

    private var rowsSwapped = false
    private val rowOffsetX = floatArrayOf(Float.NaN, Float.NaN)
    private val rowOffsetY = floatArrayOf(Float.NaN, Float.NaN)
    private val rowVelocityX = floatArrayOf(0.0f, 0.0f)
    private val rowVelocityY = floatArrayOf(0.0f, 0.0f)
    private var merged = false
    private var mergeFirst = WATERMARK_ROW_IDENTITY
    private var mergeAmount = 0.0f
    private var draggedRow = -1
    private var floatingRow = -1
    private var dragGrabX = 0.0f
    private var dragGrabY = 0.0f
    private var ghostAmount = 0.0f
    private var lastRowUpdateNanos = 0L

    private val hiddenMetrics = mutableSetOf<WatermarkMetric>()
    private val panelFade = AnimationUtil.TimedAnimation(0.0f)
    private var panelOpen = false
    private var pressX = 0.0f
    private var pressY = 0.0f
    private var pressMillis = 0L
    private var dragMoved = false

    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        return (enabled && ModuleManager.interfaceModule.watermark.value) || preview
    }

    override fun update(mc: Minecraft, preview: Boolean) {
        identityRowWidth = WATERMARK_AVATAR_INSET +
            WATERMARK_AVATAR_SIZE + WATERMARK_AVATAR_GAP + textWidth(playerName(mc)) +
            shownMetrics(WATERMARK_ROW_IDENTITY).sumOf { (WATERMARK_GROUP_GAP + groupWidth(metricValue(it, mc))).toDouble() }.toFloat() +
            WATERMARK_PADDING
        val stats = shownMetrics(WATERMARK_ROW_STATS)
        statsRowWidth = if (stats.isEmpty()) 0.0f else {
            WATERMARK_PADDING * 2.0f +
                stats.sumOf { groupWidth(metricValue(it, mc)).toDouble() }.toFloat() +
                WATERMARK_GROUP_GAP * (stats.size - 1)
        }
        val rows = visibleRows().size
        val stackedWidth = maxOf(identityRowWidth, statsRowWidth)
        val stackedHeight = rows * WATERMARK_ROW_HEIGHT + (rows - 1) * WATERMARK_ROW_GAP
        bounds.width = stackedWidth + (mergedWidth() - stackedWidth) * mergeAmount
        bounds.height = stackedHeight + (WATERMARK_ROW_HEIGHT - stackedHeight) * mergeAmount
    }

    private fun mergeSecond(): Int =
        if (mergeFirst == WATERMARK_ROW_IDENTITY) WATERMARK_ROW_STATS else WATERMARK_ROW_IDENTITY

    /** Where a row's own content begins, measured from its capsule's left edge. */
    private fun leadingInset(row: Int): Float =
        if (row == WATERMARK_ROW_IDENTITY) WATERMARK_AVATAR_INSET else WATERMARK_PADDING

    /**
     * Inside the joined plate the trailing row starts right where the leading
     * row's content ended, one group gap later, so the two read as one line.
     */
    private fun mergedOriginX(row: Int): Float {
        if (row == mergeFirst) return 0.0f
        return rowWidth(mergeFirst) - WATERMARK_PADDING + WATERMARK_GROUP_GAP - leadingInset(row)
    }

    private fun mergedWidth(): Float = mergedOriginX(mergeSecond()) + rowWidth(mergeSecond())

    private fun canMerge(): Boolean = visibleRows().size > 1

    private var identityMetrics = WatermarkMetric.entries.filter { it.row == WATERMARK_ROW_IDENTITY }
    private var statsMetrics = WatermarkMetric.entries.filter { it.row == WATERMARK_ROW_STATS }
    private var rowSlots = listOf(WATERMARK_ROW_IDENTITY, WATERMARK_ROW_STATS)

    private fun shownMetrics(row: Int): List<WatermarkMetric> =
        if (row == WATERMARK_ROW_IDENTITY) identityMetrics else statsMetrics

    /** Rows in slot order, which is what the swap state actually reorders. */
    private fun visibleRows(): List<Int> = rowSlots

    private fun refreshMetricCaches() {
        identityMetrics = WatermarkMetric.entries.filter { it.row == WATERMARK_ROW_IDENTITY && it !in hiddenMetrics }
        statsMetrics = WatermarkMetric.entries.filter { it.row == WATERMARK_ROW_STATS && it !in hiddenMetrics }
        val order = if (rowsSwapped) {
            listOf(WATERMARK_ROW_STATS, WATERMARK_ROW_IDENTITY)
        } else {
            listOf(WATERMARK_ROW_IDENTITY, WATERMARK_ROW_STATS)
        }
        rowSlots = order.filter { it == WATERMARK_ROW_IDENTITY || statsMetrics.isNotEmpty() }
        // Nothing left to join to once a row is gone.
        if (rowSlots.size < 2) merged = false
    }

    override fun advance(mc: Minecraft, preview: Boolean) = updateRows(mc, preview)

    override fun blurBoxes(guiScale: Float, tintStrength: Float): List<AsteriaOverlay.BlurBox> {
        val radius = WATERMARK_ROW_HEIGHT * 0.5f
        val boxes = visibleRows().map { row ->
            val plate = plateRect(row)
            AsteriaOverlay.BlurBox(
                plate[0] * guiScale,
                plate[1] * guiScale,
                plate[2] * guiScale,
                WATERMARK_ROW_HEIGHT * guiScale,
                radius * guiScale,
                0.65f,
            )
        }.toMutableList()
        val ghost = ghostRow()
        if (ghost >= 0) {
            val slot = ghostSlot(ghost)
            boxes += AsteriaOverlay.BlurBox(
                (bounds.x + slot[0]) * guiScale,
                (bounds.y + targetY(ghost)) * guiScale,
                slot[1] * guiScale,
                WATERMARK_ROW_HEIGHT * guiScale,
                radius * guiScale,
                0.65f,
                opacity = ghostAmount * WATERMARK_GHOST_GLASS,
            )
        }
        val fade = panelFade.value
        if (fade > 0.004f) {
            val panel = panelRect()
            boxes += AsteriaOverlay.BlurBox(
                panel.x * guiScale,
                panel.y * guiScale,
                panel.width * guiScale,
                panel.height * guiScale,
                WATERMARK_PANEL_RADIUS * guiScale,
                0.65f,
                // The glass fades with the panel instead of snapping in behind it.
                opacity = fade,
            )
        }
        return boxes
    }

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val rows = visibleRows()
        val order = if (floatingRow >= 0) rows.sortedBy { it == floatingRow } else rows
        drawDropSlot(graphics)
        order.forEach { drawRowBackground(graphics, it) }
        order.forEach { row ->
            if (row == WATERMARK_ROW_IDENTITY) {
                drawIdentityRow(graphics, mc, rowX(row), rowY(row))
            } else {
                drawStatsRow(graphics, mc, rowX(row), rowY(row))
            }
        }
        drawPanel(graphics)
    }

    private fun plateRect(row: Int): FloatArray {
        val x = rowX(row)
        val y = rowY(row)
        val width = rowWidth(row)
        if (mergeAmount <= 0.004f) return floatArrayOf(x, y, width)
        val m = mergeAmount
        return floatArrayOf(
            x + (bounds.x - x) * m,
            y + (bounds.y - y) * m,
            width + (mergedWidth() - width) * m,
        )
    }

    private fun ghostRow(): Int {
        if (ghostAmount <= 0.004f) return -1
        return if (draggedRow >= 0) draggedRow else floatingRow
    }

    private fun ghostSlot(row: Int): FloatArray {
        var x = targetX(row)
        var width = rowWidth(row)
        if (!merged) return floatArrayOf(x, width)
        val other = otherRow(row)
        val otherX = mergedOriginX(other)
        if (row == mergeFirst) {
            width = (otherX - WATERMARK_GHOST_GAP - x).coerceAtLeast(WATERMARK_ROW_HEIGHT)
        } else {
            val left = otherX + rowWidth(other) + WATERMARK_GHOST_GAP
            width = (x + width - left).coerceAtLeast(WATERMARK_ROW_HEIGHT)
            x = left
        }
        return floatArrayOf(x, width)
    }

    private fun drawDropSlot(graphics: GuiGraphicsExtractor) {
        if (ModuleManager.postProcessing.enabled) return
        val row = ghostRow()
        if (row < 0) return
        val slot = ghostSlot(row)
        HudStyle.rect(
            graphics,
            bounds.x + slot[0],
            bounds.y + targetY(row),
            slot[1],
            WATERMARK_ROW_HEIGHT,
            WATERMARK_ROW_HEIGHT * 0.5f,
            faded(WATERMARK_GHOST_COLOR, ghostAmount),
        )
    }

    private fun drawRowBackground(graphics: GuiGraphicsExtractor, row: Int) {
        val radius = WATERMARK_ROW_HEIGHT * 0.5f
        val plate = plateRect(row)
        val x = plate[0]
        val y = plate[1]
        val width = plate[2]
        if (!ModuleManager.postProcessing.enabled) {
            HudStyle.rect(graphics, x, y, width, WATERMARK_ROW_HEIGHT, radius, WATERMARK_BACKGROUND)
        }
    }

    private fun drawIdentityRow(graphics: GuiGraphicsExtractor, mc: Minecraft, x: Float, y: Float) {
        var cursor = x + WATERMARK_AVATAR_INSET
        val avatarY = y + WATERMARK_AVATAR_INSET
        val frame = ((System.currentTimeMillis() / WATERMARK_AVATAR_FRAME_MS) % WATERMARK_AVATAR_FRAMES).toInt()
        RoundedTextureRenderer.frame(
            graphics,
            avatar,
            cursor,
            avatarY,
            WATERMARK_AVATAR_SIZE,
            WATERMARK_AVATAR_SIZE,
            1.0f,
            frame,
        )
        cursor += WATERMARK_AVATAR_SIZE + WATERMARK_AVATAR_GAP

        val name = playerName(mc)
        FontRenderer.draw(graphics, WATERMARK_FACE, name, cursor, textY(y), WATERMARK_TEXT_SIZE, HudStyle.TEXT)
        cursor += textWidth(name)

        shownMetrics(WATERMARK_ROW_IDENTITY).forEach { metric ->
            cursor = drawMetric(graphics, metricIcon(metric), metricValue(metric, mc), cursor + WATERMARK_GROUP_GAP, y)
        }
    }

    private fun drawStatsRow(graphics: GuiGraphicsExtractor, mc: Minecraft, x: Float, y: Float) {
        var cursor = x + WATERMARK_PADDING
        shownMetrics(WATERMARK_ROW_STATS).forEachIndexed { index, metric ->
            if (index > 0) cursor += WATERMARK_GROUP_GAP
            cursor = drawMetric(graphics, metricIcon(metric), metricValue(metric, mc), cursor, y)
        }
    }

    private fun metricIcon(metric: WatermarkMetric): Identifier = when (metric) {
        WatermarkMetric.SERVER -> serverIcon
        WatermarkMetric.PING -> pingIcon
        WatermarkMetric.FPS -> fpsIcon
        WatermarkMetric.TPS -> tpsIcon
        WatermarkMetric.BPS -> bpsIcon
        WatermarkMetric.COORDINATES -> coordinatesIcon
    }

    private fun metricValue(metric: WatermarkMetric, mc: Minecraft): String = when (metric) {
        WatermarkMetric.SERVER -> serverAddress(mc)
        WatermarkMetric.PING -> ping(mc)
        WatermarkMetric.FPS -> fps(mc)
        WatermarkMetric.TPS -> tps()
        WatermarkMetric.BPS -> bps(mc)
        WatermarkMetric.COORDINATES -> coordinates(mc)
    }

    /** Sits under the watermark, or above it when there is no room below. */
    private fun panelRect(): HudBounds {
        val height = WATERMARK_PANEL_HEADER_HEIGHT +
            WatermarkMetric.entries.size * WATERMARK_PANEL_ROW_HEIGHT +
            WATERMARK_PANEL_PADDING
        val below = bounds.y + bounds.height + WATERMARK_PANEL_GAP
        val screenHeight = Minecraft.getInstance().window.guiScaledHeight.toFloat()
        val y = if (below + height <= screenHeight - 2.0f) {
            below
        } else {
            (bounds.y - height - WATERMARK_PANEL_GAP).coerceAtLeast(2.0f)
        }
        return HudBounds(bounds.x, y, WATERMARK_PANEL_WIDTH, height)
    }

    private fun drawPanel(graphics: GuiGraphicsExtractor) {
        val fade = panelFade.value
        if (fade <= 0.004f) return
        val panel = panelRect()
        if (!ModuleManager.postProcessing.enabled) {
            HudStyle.rect(
                graphics,
                panel.x,
                panel.y,
                panel.width,
                panel.height,
                WATERMARK_PANEL_RADIUS,
                faded(WATERMARK_BACKGROUND, fade),
            )
        }
        FontRenderer.draw(
            graphics,
            FontRenderer.Face.SfSemibold,
            "Watermark",
            panel.x + WATERMARK_PANEL_PADDING + 2.0f,
            panel.y + WATERMARK_PANEL_PADDING,
            WATERMARK_PANEL_TITLE_SIZE,
            faded(HudStyle.TEXT, fade),
        )
        WatermarkMetric.entries.forEachIndexed { index, metric ->
            val rowY = panel.y + WATERMARK_PANEL_HEADER_HEIGHT + index * WATERMARK_PANEL_ROW_HEIGHT
            val on = metric !in hiddenMetrics
            FontRenderer.draw(
                graphics,
                WATERMARK_FACE,
                metric.label,
                panel.x + WATERMARK_PANEL_PADDING + 2.0f,
                rowY + (WATERMARK_PANEL_ROW_HEIGHT - WATERMARK_PANEL_TEXT_SIZE) * 0.5f,
                WATERMARK_PANEL_TEXT_SIZE,
                faded(if (on) HudStyle.TEXT else HudStyle.MUTED, fade),
            )
            val toggleX = panel.x + panel.width - WATERMARK_PANEL_PADDING - WATERMARK_PANEL_TOGGLE_WIDTH - 2.0f
            val toggleY = rowY + (WATERMARK_PANEL_ROW_HEIGHT - WATERMARK_PANEL_TOGGLE_HEIGHT) * 0.5f
            HudStyle.rect(
                graphics,
                toggleX,
                toggleY,
                WATERMARK_PANEL_TOGGLE_WIDTH,
                WATERMARK_PANEL_TOGGLE_HEIGHT,
                WATERMARK_PANEL_TOGGLE_HEIGHT * 0.5f,
                faded(if (on) WATERMARK_ACCENT else WATERMARK_PANEL_TOGGLE_OFF, fade),
            )
            val knobInset = (WATERMARK_PANEL_TOGGLE_HEIGHT - WATERMARK_PANEL_TOGGLE_KNOB) * 0.5f
            HudStyle.rect(
                graphics,
                if (on) toggleX + WATERMARK_PANEL_TOGGLE_WIDTH - WATERMARK_PANEL_TOGGLE_KNOB - knobInset else toggleX + knobInset,
                toggleY + knobInset,
                WATERMARK_PANEL_TOGGLE_KNOB,
                WATERMARK_PANEL_TOGGLE_KNOB,
                WATERMARK_PANEL_TOGGLE_KNOB * 0.5f,
                faded(0xFF000000.toInt() or 0xFFFFFF, fade),
            )
        }
    }

    /** Scales a colour's own alpha by the panel fade. */
    private fun faded(color: Int, fade: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * fade.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    override fun containsMouse(mouseX: Float, mouseY: Float): Boolean {
        if (super.containsMouse(mouseX, mouseY)) return true
        if (!panelOpen) return false
        val panel = panelRect()
        return mouseX >= panel.x && mouseX <= panel.x + panel.width &&
            mouseY >= panel.y && mouseY <= panel.y + panel.height
    }

    override fun mousePressed(button: Int, mouseX: Float, mouseY: Float): Boolean {
        if (panelOpen && toggleMetricAt(mouseX, mouseY)) return true
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false
        val row = visibleRows().firstOrNull { candidate ->
            val x = rowX(candidate)
            val y = rowY(candidate)
            mouseX >= x && mouseX <= x + rowWidth(candidate) &&
                mouseY >= y && mouseY <= y + WATERMARK_ROW_HEIGHT
        } ?: return false
        draggedRow = row
        floatingRow = row
        dragGrabX = mouseX - rowX(row)
        dragGrabY = mouseY - rowY(row)
        pressX = mouseX
        pressY = mouseY
        pressMillis = System.currentTimeMillis()
        dragMoved = false
        return true
    }

    private fun toggleMetricAt(mouseX: Float, mouseY: Float): Boolean {
        val panel = panelRect()
        val inside = mouseX >= panel.x && mouseX <= panel.x + panel.width &&
            mouseY >= panel.y && mouseY <= panel.y + panel.height
        if (!inside) return false
        val rowsTop = panel.y + WATERMARK_PANEL_HEADER_HEIGHT
        val index = ((mouseY - rowsTop) / WATERMARK_PANEL_ROW_HEIGHT).toInt()
        val metric = if (mouseY >= rowsTop) WatermarkMetric.entries.getOrNull(index) else null
        if (metric != null) {
            if (!hiddenMetrics.remove(metric)) hiddenMetrics += metric
            refreshMetricCaches()
            ClientConfig.save()
        }
        // Everything landing on the panel is consumed, title and padding included,
        // so a stray click there never reaches the HUD behind it.
        return true
    }

    override fun mouseReleased(button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || draggedRow < 0) return false
        // A press that never really moved was a click, not a reorder, and opens
        // or closes the metric picker instead.
        val mc = Minecraft.getInstance()
        val movedX = abs(toLocalX(mc.mouseHandler.getScaledXPos(mc.window).toFloat()) - pressX)
        val movedY = abs(toLocalY(mc.mouseHandler.getScaledYPos(mc.window).toFloat()) - pressY)
        val quick = System.currentTimeMillis() - pressMillis <= WATERMARK_PANEL_CLICK_TIME_MS
        if (quick && movedX <= WATERMARK_PANEL_CLICK_SLOP && movedY <= WATERMARK_PANEL_CLICK_SLOP) {
            panelOpen = !panelOpen
        }
        draggedRow = -1
        return true
    }

    override fun mouseMissed() {
        panelOpen = false
    }

    override fun saveState(state: JsonObject) {
        state.addProperty("rowsSwapped", rowsSwapped)
        state.addProperty("merged", merged)
        state.addProperty("mergeFirst", mergeFirst)
        WatermarkMetric.entries.forEach { state.addProperty(it.key, it !in hiddenMetrics) }
    }

    override fun loadState(state: JsonObject) {
        state.get("rowsSwapped")?.takeIf { it.isJsonPrimitive }?.let { rowsSwapped = it.asBoolean }
        state.get("merged")?.takeIf { it.isJsonPrimitive }?.let { merged = it.asBoolean }
        state.get("mergeFirst")?.takeIf { it.isJsonPrimitive }?.let {
            mergeFirst = if (it.asInt == WATERMARK_ROW_STATS) WATERMARK_ROW_STATS else WATERMARK_ROW_IDENTITY
        }
        WatermarkMetric.entries.forEach { metric ->
            val shown = state.get(metric.key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: return@forEach
            if (shown) hiddenMetrics -= metric else hiddenMetrics += metric
        }
        refreshMetricCaches()
        mergeAmount = if (merged) 1.0f else 0.0f
        rowOffsetX[0] = Float.NaN
        rowOffsetX[1] = Float.NaN
        rowOffsetY[0] = Float.NaN
        rowOffsetY[1] = Float.NaN
    }

    /** Slot 0 is the upper capsule, slot 1 the lower one. */
    private fun slotOf(row: Int): Int = visibleRows().indexOf(row).coerceAtLeast(0)

    private fun lastSlot(): Int = (visibleRows().size - 1).coerceAtLeast(0)

    private fun slotOffset(slot: Int): Float = slot * WATERMARK_ROW_PITCH

    private fun rowWidth(row: Int): Float =
        if (row == WATERMARK_ROW_IDENTITY) identityRowWidth else statsRowWidth

    /**
     * Row positions are kept relative to the widget origin, so moving the whole
     * watermark with the left button carries the rows along without any lag.
     */
    private fun rowOffsetX(row: Int): Float =
        if (rowOffsetX[row].isFinite()) rowOffsetX[row] else targetX(row)

    private fun rowOffsetY(row: Int): Float =
        if (rowOffsetY[row].isFinite()) rowOffsetY[row] else targetY(row)

    private fun targetX(row: Int): Float = if (merged) mergedOriginX(row) else 0.0f

    private fun targetY(row: Int): Float = if (merged) 0.0f else slotOffset(slotOf(row))

    private fun rowX(row: Int): Float = bounds.x + rowOffsetX(row)

    private fun rowY(row: Int): Float = bounds.y + rowOffsetY(row)

    /**
     * Advances the row positions once per frame, so the blur pass and the render
     * pass agree on where the capsules are. The smoothing is driven by elapsed
     * time rather than by a fixed per-call factor to stay frame-rate independent.
     */
    private fun updateRows(mc: Minecraft, preview: Boolean) {
        val now = System.nanoTime()
        val deltaMs = if (lastRowUpdateNanos == 0L) 0.0f else ((now - lastRowUpdateNanos) / 1_000_000.0).toFloat()
        lastRowUpdateNanos = now
        val dt = deltaMs.coerceIn(0.0f, 100.0f)

        // Reordering is only reachable while the HUD is editable; leaving that
        // state has to drop the drag rather than let it follow a grabbed cursor.
        val editing = preview || mc.screen is ChatScreen
        if (!editing) {
            draggedRow = -1
            panelOpen = false
        }
        panelFade.run(
            if (panelOpen) 1.0f else 0.0f,
            if (panelOpen) WATERMARK_PANEL_SHOW_DURATION else WATERMARK_PANEL_HIDE_DURATION,
            { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) },
            true,
        )
        panelFade.update()

        if (draggedRow >= 0) {
            val mouseX = toLocalX(mc.mouseHandler.getScaledXPos(mc.window).toFloat())
            val mouseY = toLocalY(mc.mouseHandler.getScaledYPos(mc.window).toFloat())
            if (abs(mouseX - pressX) > WATERMARK_PANEL_CLICK_SLOP || abs(mouseY - pressY) > WATERMARK_PANEL_CLICK_SLOP) {
                dragMoved = true
                // The picker belongs to a resting watermark, not to one being
                // rearranged under the cursor.
                panelOpen = false
            }
            val wantedX = mouseX - dragGrabX - bounds.x
            val wantedY = mouseY - dragGrabY - bounds.y
            if (merged) dragMerged(wantedX, wantedY, dt) else dragStacked(wantedX, wantedY, dt)
        }

        listOf(WATERMARK_ROW_IDENTITY, WATERMARK_ROW_STATS).forEach { row ->
            if (row == draggedRow) return@forEach
            rowOffsetX[row] = spring(rowOffsetX(row), targetX(row), rowVelocityX, row, WATERMARK_ROW_SETTLE_SMOOTH, dt)
            rowOffsetY[row] = spring(rowOffsetY(row), targetY(row), rowVelocityY, row, WATERMARK_ROW_SETTLE_SMOOTH, dt)
        }
        mergeAmount = approach(mergeAmount, mergeProximity(), WATERMARK_MERGE_TAU, dt)
        val showGhost = draggedRow >= 0 && dragMoved
        ghostAmount = approach(ghostAmount, if (showGhost) 1.0f else 0.0f, WATERMARK_GHOST_TAU, dt)

        if (draggedRow < 0 && floatingRow >= 0 &&
            rowOffsetX(floatingRow) == targetX(floatingRow) &&
            rowOffsetY(floatingRow) == targetY(floatingRow) && ghostAmount <= 0.0f
        ) {
            floatingRow = -1
        }
    }

    /**
     * How joined the two capsules currently look. The plate only counts as whole
     * while both halves actually sit in it, so pulling one out lets the shared
     * background come apart with it rather than leaving the text to slide out of
     * a plate that stays behind.
     */
    private fun mergeProximity(): Float {
        if (!merged) return 0.0f
        // Nothing fuses under the cursor: once a half is actually being moved the
        // two capsules stay separate, and they only flow together once it has been
        // dropped. A press that never moved is a click on the panel, not a drag,
        // and must not make the plate flicker apart.
        if (draggedRow >= 0 && dragMoved) return 0.0f
        var strayed = 0.0f
        listOf(WATERMARK_ROW_IDENTITY, WATERMARK_ROW_STATS).forEach { row ->
            val dx = rowOffsetX(row) - mergedOriginX(row)
            val dy = rowOffsetY(row)
            strayed = maxOf(strayed, sqrt(dx * dx + dy * dy))
        }
        return (1.0f - strayed / WATERMARK_MERGE_BLEND_DISTANCE).coerceIn(0.0f, 1.0f)
    }

    /**
     * Two stacked capsules. Vertical travel trades the slots as before; enough
     * horizontal travel instead joins this capsule onto the other one, on the
     * side it was dragged towards.
     */
    private fun dragStacked(wantedX: Float, wantedY: Float, dt: Float) {
        val upper = slotOffset(0)
        val lower = slotOffset(lastSlot())
        follow(rowVelocityY, rubberClamp(wantedY, upper, lower), dt, vertical = true)
        // Sideways there is only ever one lane, and the row leaves it by joining
        // the other capsule rather than by being parked somewhere off to the side.
        // With a join available the lane runs exactly to the join threshold, so
        // the pull that triggers it never fights the rubber on the way.
        val sideways = if (canMerge()) WATERMARK_MERGE_TRIGGER else 0.0f
        follow(rowVelocityX, rubberClamp(wantedX, -sideways, sideways), dt, vertical = false)

        if (canMerge() && abs(wantedX) > WATERMARK_MERGE_TRIGGER) {
            // Dragged right, it lands after the other row; dragged left, before it.
            merged = true
            mergeFirst = if (wantedX > 0.0f) otherRow(draggedRow) else draggedRow
            reanchor(wantedX, wantedY, mergedOriginX(draggedRow), 0.0f)
            return
        }

        // Once the dragged capsule is more than half a pitch from its own slot
        // it has taken the other slot over, and the rows trade places.
        val slot = slotOf(draggedRow)
        val travelled = rowOffsetY(draggedRow) - slotOffset(slot)
        val towardsOtherSlot = if (slot == 0) travelled > 0.0f else travelled < 0.0f
        if (towardsOtherSlot && abs(travelled) > WATERMARK_ROW_PITCH * 0.5f + WATERMARK_ROW_SWAP_SLACK) {
            rowsSwapped = !rowsSwapped
            refreshMetricCaches()
        }
    }

    /**
     * One joined plate. Sideways travel reorders the two halves inside it, and
     * pulling up or down splits them back into stacked rows on that side.
     */
    private fun dragMerged(wantedX: Float, wantedY: Float, dt: Float) {
        if (abs(wantedY) > WATERMARK_SPLIT_TRIGGER) {
            merged = false
            // The row lands in the slot it was pulled towards.
            rowsSwapped = if (wantedY < 0.0f) {
                draggedRow == WATERMARK_ROW_STATS
            } else {
                draggedRow == WATERMARK_ROW_IDENTITY
            }
            refreshMetricCaches()
            reanchor(wantedX, wantedY, 0.0f, slotOffset(slotOf(draggedRow)))
            return
        }
        val other = otherRow(draggedRow)
        // The grabbed half follows the cursor as its own capsule, but only along
        // the plate it belongs to: it can slide from one end to the other and a
        // little past, not away across the screen.
        val maxX = (mergedWidth() - rowWidth(draggedRow)).coerceAtLeast(0.0f)
        val minX = -WATERMARK_DRAG_STRAY
        val limitX = maxX + WATERMARK_DRAG_STRAY
        follow(rowVelocityX, rubberClamp(wantedX, minX, limitX), dt, vertical = false)
        follow(rowVelocityY, rubberClamp(wantedY, 0.0f, 0.0f), dt, vertical = true)

        val draggedCentre = rowOffsetX(draggedRow) + rowWidth(draggedRow) * 0.5f
        val otherCentre = mergedOriginX(other) + rowWidth(other) * 0.5f
        val passedOther = if (draggedRow == mergeFirst) draggedCentre > otherCentre else draggedCentre < otherCentre
        if (passedOther) mergeFirst = if (draggedRow == mergeFirst) other else draggedRow
    }

    private fun otherRow(row: Int): Int =
        if (row == WATERMARK_ROW_IDENTITY) WATERMARK_ROW_STATS else WATERMARK_ROW_IDENTITY

    /** Moves the dragged row one frame closer to where the cursor asks it to be. */
    private fun follow(velocity: FloatArray, target: Float, dt: Float, vertical: Boolean) {
        val offsets = if (vertical) rowOffsetY else rowOffsetX
        val current = if (vertical) rowOffsetY(draggedRow) else rowOffsetX(draggedRow)
        offsets[draggedRow] = spring(current, target, velocity, draggedRow, WATERMARK_ROW_FOLLOW_SMOOTH, dt)
    }

    /**
     * Keeps a dragged offset inside its lane. Past the ends the row still moves
     * with the cursor, but asymptotically, so it can never be dragged far from
     * the watermark however hard the cursor pulls.
     */
    private fun rubberClamp(value: Float, min: Float, max: Float): Float {
        val overshoot = overshoot(value, min, max)
        if (overshoot <= 0.0f) return value
        val resisted = WATERMARK_ROW_RUBBER_LIMIT * (1.0f - exp(-overshoot / WATERMARK_ROW_RUBBER_LIMIT))
        return if (value < min) min - resisted else max + resisted
    }

    private fun overshoot(value: Float, min: Float, max: Float): Float = when {
        value < min -> min - value
        value > max -> value - max
        else -> 0.0f
    }


    /**
     * Re-reads the grab point after a join or a split so the cursor keeps holding
     * the same spot of the capsule. Without it the layout change reads as a jump
     * and immediately trips the opposite threshold.
     */
    private fun reanchor(wantedX: Float, wantedY: Float, newX: Float, newY: Float) {
        dragGrabX += wantedX - newX
        dragGrabY += wantedY - newY
    }

    private fun approach(current: Float, target: Float, tauMs: Float, dtMs: Float): Float {
        if (dtMs <= 0.0f) return current
        val factor = 1.0f - exp(-dtMs / tauMs)
        val next = current + (target - current) * factor
        return if (abs(target - next) < 0.01f) target else next
    }

    /**
     * Critically damped spring: the value eases away and eases back in without
     * overshooting, which is what separates a capsule gliding to its new place
     * from one snapping there. Velocity is carried between frames, so grabbing a
     * moving row picks it up mid-flight instead of restarting the motion.
     */
    private fun spring(
        current: Float,
        target: Float,
        velocity: FloatArray,
        index: Int,
        smoothTimeMs: Float,
        dtMs: Float,
    ): Float {
        if (dtMs <= 0.0f) return current
        val omega = 2.0f / smoothTimeMs.coerceAtLeast(1.0f)
        val x = omega * dtMs
        val decay = 1.0f / (1.0f + x + 0.48f * x * x + 0.235f * x * x * x)
        val change = current - target
        val temp = (velocity[index] + omega * change) * dtMs
        velocity[index] = (velocity[index] - omega * temp) * decay
        val next = target + (change + temp) * decay
        // Land exactly, so the "has settled" checks elsewhere can compare values.
        if (abs(target - next) < 0.01f && abs(velocity[index]) < 0.01f) {
            velocity[index] = 0.0f
            return target
        }
        return next
    }

    /** Draws an accent icon plus its white label, returning the cursor past the text. */
    private fun drawMetric(
        graphics: GuiGraphicsExtractor,
        icon: Identifier,
        value: String,
        x: Float,
        rowY: Float,
    ): Float {
        val iconY = rowY + (WATERMARK_ROW_HEIGHT - WATERMARK_ICON_SIZE) * 0.5f
        MsdfIconRenderer.draw(
            graphics,
            icon,
            x,
            iconY,
            WATERMARK_ICON_SIZE,
            WATERMARK_ICON_SIZE,
            WATERMARK_ACCENT,
            edge = MsdfIconRenderer.Edge.CrispRange6,
        )
        val textX = x + WATERMARK_ICON_SIZE + WATERMARK_ICON_GAP
        FontRenderer.draw(graphics, WATERMARK_FACE, value, textX, textY(rowY), WATERMARK_TEXT_SIZE, HudStyle.TEXT)
        return textX + textWidth(value)
    }

    private fun textY(rowY: Float): Float = rowY + (WATERMARK_ROW_HEIGHT - WATERMARK_TEXT_SIZE) * 0.5f

    private fun textWidth(value: String): Float =
        FontRenderer.width(WATERMARK_FACE, value, WATERMARK_TEXT_SIZE)

    private fun groupWidth(value: String): Float =
        WATERMARK_ICON_SIZE + WATERMARK_ICON_GAP + textWidth(value)

    private fun playerName(mc: Minecraft): String = mc.player?.gameProfile?.name ?: "Zywo"

    private fun serverAddress(mc: Minecraft): String = mc.currentServer?.ip ?: "singleplayer"

    private fun fps(mc: Minecraft): String = "${mc.fps} FPS"

    private fun tps(): String = "20 TPS"

    private fun ping(mc: Minecraft): String {
        val player = mc.player ?: return "0 PING"
        val latency = mc.connection?.getPlayerInfo(player.uuid)?.latency?.coerceAtLeast(0) ?: 0
        return "$latency PING"
    }

    /**
     * Horizontal speed from the last tick's movement delta, smoothed so the
     * readout does not flicker between frames within a tick.
     */
    private fun bps(mc: Minecraft): String {
        val player = mc.player
        if (player == null) {
            smoothedBps = 0.0
            return "0.0 BPS"
        }
        val dx = player.x - player.xOld
        val dz = player.z - player.zOld
        val current = sqrt(dx * dx + dz * dz) * 20.0
        smoothedBps += (current - smoothedBps) * 0.2
        return String.format(Locale.ROOT, "%.1f BPS", smoothedBps)
    }

    private fun coordinates(mc: Minecraft): String {
        val position = mc.player?.blockPosition() ?: return "0 0 0"
        return "${position.x} ${position.y} ${position.z}"
    }
}

class CoordinatesHudWidget : HudWidget(
    id = "coordinates",
    title = "Coordinates",
    iconGlyph = "H",
    x = 7.0f,
    y = 96.0f,
    width = 128.0f,
    height = 25.0f,
    enabledByDefault = false,
) {
    override fun update(mc: Minecraft, preview: Boolean) {
        val text = coordinates(mc)
        bounds.width = maxOf(128.0f, FontRenderer.width(FontRenderer.Face.SfRegular, text, 8.5f) + 38.0f)
    }

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        HudStyle.panel(graphics, bounds)
        HudStyle.icon(graphics, iconGlyph, bounds.x + 9.0f, bounds.y + 7.0f, 10.0f)
        FontRenderer.draw(
            graphics,
            FontRenderer.Face.SfRegular,
            coordinates(mc),
            bounds.x + 27.0f,
            bounds.y + 8.0f,
            8.5f,
            HudStyle.TEXT,
        )
    }

    private fun coordinates(mc: Minecraft): String {
        val position = mc.player?.blockPosition() ?: return "X 0  Y 0  Z 0"
        return "X ${position.x}  Y ${position.y}  Z ${position.z}"
    }
}

/**
 * The active effects, on the shared list template: the effect's own icon, its
 * name with the level beside it, and the time it has left.
 */
class PotionsHudWidget : IconListHudWidget<Identifier>(
    id = "potions",
    title = "Potions",
    iconGlyph = "B",
    x = 142.0f,
    y = 39.0f,
    panelTitle = POTION_TITLE,
    panelIcon = POTION_ICON,
    emptyText = POTION_EMPTY_TEXT,
) {
    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        val hasRows = mc.player?.activeEffects?.isNotEmpty() == true
        val chatPreview = mc.screen is ChatScreen
        return (enabled && ModuleManager.interfaceModule.potions.value && (hasRows || chatPreview)) || preview
    }

    override fun rows(mc: Minecraft, preview: Boolean): List<IconListRow<Identifier>> {
        if (!enabled && !preview) return emptyList()
        return mc.player?.activeEffects
            ?.map { effect ->
                val level = if (effect.amplifier > 0) " ${effect.amplifier + 1}" else ""
                IconListRow(
                    effect.descriptionId,
                    Component.translatable(effect.descriptionId).string + level,
                    formatDuration(effect.duration),
                    potionIcon(effect.descriptionId)
                )
            }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    override fun drawRowIcon(
        graphics: GuiGraphicsExtractor,
        icon: Identifier,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
    ) {
        TextureRenderer.draw(graphics, icon, x, y, size, size, fadeColor(0xFFFFFFFF.toInt(), alpha))
    }

    private fun formatDuration(ticks: Int): String {
        if (ticks < 0) return "∞"
        val seconds = ceil(ticks / 20.0).toInt()
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    private fun potionIcon(descriptionId: String): Identifier {
        val path = descriptionId.substringAfterLast('.')
        return Identifier.fromNamespaceAndPath("minecraft", "textures/mob_effect/$path.png")
    }
}

/**
 * The same list template as PotionsHudWidget, listing what is on cooldown: the
 * item itself on the left, its name beside it, and the wait still on it.
 *
 * Live cooldown data isn't exposed by any public Minecraft API — ItemCooldowns
 * only exposes isOnCooldown()/getCooldownPercent() for a single stack, not a
 * list of what is currently cooling down or how much time is actually left — so
 * ItemCooldownsAccessor/CooldownInstanceAccessor (mixin @Accessor interfaces)
 * reach into its private cooldowns map and tickCount field directly.
 */
class CooldownsHudWidget : IconListHudWidget<ItemStack>(
    id = "cooldowns",
    title = "Cooldowns",
    iconGlyph = "C",
    x = 142.0f,
    y = 104.0f,
    panelTitle = COOLDOWN_TITLE,
    panelIcon = COOLDOWN_ICON,
    emptyText = COOLDOWN_EMPTY_TEXT,
) {
    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        val accessor = mc.player?.cooldowns as? ItemCooldownsAccessor
        val hasRows = accessor?.getCooldownsMap()?.isNotEmpty() == true
        val chatPreview = mc.screen is ChatScreen
        return (enabled && ModuleManager.interfaceModule.cooldowns.value && (hasRows || chatPreview)) || preview
    }

    override fun rows(mc: Minecraft, preview: Boolean): List<IconListRow<ItemStack>> {
        if (!enabled && !preview) return emptyList()
        val player = mc.player ?: return emptyList()
        val accessor = player.cooldowns as? ItemCooldownsAccessor ?: return emptyList()
        val tickCount = accessor.getTickCount()
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        return accessor.getCooldownsMap().entries
            .mapNotNull { (group, instanceObj) ->
                val instance = instanceObj as? CooldownInstanceAccessor ?: return@mapNotNull null
                val remainingTicks = instance.getEndTime() - tickCount - partialTick
                if (remainingTicks <= 0.0f) return@mapNotNull null
                val item = BuiltInRegistries.ITEM.getOptional(group).orElse(null) ?: return@mapNotNull null
                val stack = item.defaultInstance
                IconListRow(group.toString(), item.getName(stack).string, formatDuration(remainingTicks), stack)
            }
            .sortedBy { it.name.lowercase() }
    }

    override fun drawRowIcon(
        graphics: GuiGraphicsExtractor,
        icon: ItemStack,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
    ) {
        if (icon.isEmpty || size <= 0.05f) return
        // graphics.item() only ever draws at its native 16x16, so the row's box is
        // reached by scaling around the icon's top-left corner.
        val scale = size / COOLDOWN_ITEM_NATIVE_SIZE
        graphics.pose().pushMatrix()
        graphics.pose().translate(x, y)
        graphics.pose().scale(scale, scale)
        graphics.item(icon, 0, 0)
        graphics.pose().popMatrix()
    }

    private fun formatDuration(remainingTicks: Float): String {
        val seconds = ceil(remainingTicks / 20.0).toInt().coerceAtLeast(0)
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
}

class InventoryHudWidget : HudWidget(
    id = "inventory",
    title = "Inventory",
    iconGlyph = "L",
    x = 142.0f,
    y = 96.0f,
    width = 164.0f,
    height = 56.0f,
    enabledByDefault = false,
) {
    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val player = mc.player ?: return
        HudStyle.panel(graphics, bounds)
        val slotSize = 18.0f
        for (index in 0 until 27) {
            val column = index % 9
            val row = index / 9
            val slotX = bounds.x + 1.0f + column * slotSize
            val slotY = bounds.y + 1.0f + row * slotSize
            HudStyle.rect(graphics, slotX, slotY, 17.0f, 17.0f, 2.0f, 0x12000000)
            val stack = player.inventory.getItem(index + 9)
            if (!stack.isEmpty) {
                graphics.item(stack, slotX.toInt(), slotY.toInt())
                graphics.itemDecorations(mc.font, stack, slotX.toInt(), slotY.toInt())
            }
        }
    }
}

class HotbarHudWidget : HudWidget(
    id = "hotbar",
    title = "Hotbar",
    iconGlyph = "H",
    x = Float.NaN,
    y = Float.NaN,
    width = 184.0f,
    height = 24.0f,
    enabledByDefault = false,
    movable = false,
) {
    private var animatedSelectedSlot = -1.0f

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val player = mc.player ?: return
        HudStyle.panel(graphics, bounds)
        val selected = player.inventory.selectedSlot
        if (animatedSelectedSlot < 0.0f) {
            animatedSelectedSlot = selected.toFloat()
        } else {
            animatedSelectedSlot += (selected - animatedSelectedSlot) * 0.28f
        }
        val selectedSlotX = bounds.x + 2.0f + animatedSelectedSlot * 20.0f
        HudStyle.rect(graphics, selectedSlotX, bounds.y + 2.0f, 20.0f, 20.0f, 5.0f, 0x5988FF82)
        for (slot in 0 until 9) {
            val slotX = bounds.x + 2.0f + slot * 20.0f
            val stack = player.inventory.getItem(slot)
            if (!stack.isEmpty) {
                graphics.item(stack, (slotX + 2.0f).toInt(), (bounds.y + 4.0f).toInt())
                graphics.itemDecorations(mc.font, stack, (slotX + 2.0f).toInt(), (bounds.y + 4.0f).toInt())
            }
        }
    }
}
