package asteria.top.client.gui.hud

import asteria.top.client.module.ModuleManager
import asteria.top.client.render.FontRenderer
import asteria.top.client.util.AnimationUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import kotlin.math.sin

private const val KEYBINDS_WIDTH = 64.0f
private const val KEYBIND_EMPTY_TEXT = "Нету активных биндов"
private const val KEYBIND_EMPTY_HORIZONTAL_PADDING = 14.0f
private const val KEYBIND_INDICATOR_RIGHT_INSET = 7.0f
private const val KEYBIND_ROW_HEIGHT = 14.0f
private const val KEYBIND_MODULE_TEXT_SIZE = 8.5f
private const val KEYBIND_BIND_TEXT_SIZE = 10.0f
private const val KEYBIND_PANEL_RADIUS = 7.75f
private const val KEYBIND_EMPTY_HEIGHT = 20.0f
private const val KEYBIND_ROW_FADE_IN_DURATION = 180L
private const val KEYBIND_ROW_FADE_OUT_DURATION = 180L
private const val SWITCH_ANIMATION_DURATION = 240L

class KeybindsHudWidget : HudWidget(
    id = "keybinds",
    title = "Keybinds",
    iconGlyph = "A",
    x = 7.5f,
    y = 28.0f,
    width = KEYBINDS_WIDTH,
    height = 38.0f,
) {
    private var renderRows = emptyList<RowState>()
    private val rowAnimations = linkedMapOf<String, RowState>()
    private val switchAnimations = mutableMapOf<String, SwitchAnimation>()

    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        val hasRows = ModuleManager.modules.any { it.bind >= 0 }
        val chatPreview = mc.screen is ChatScreen
        return (enabled && ModuleManager.interfaceModule.keybinds.value && (hasRows || chatPreview)) || preview
    }

    override fun update(mc: Minecraft, preview: Boolean) {
        renderRows = collectRows(activeRows(preview))
        val rows = renderRows
        bounds.height = if (rows.isEmpty()) KEYBIND_EMPTY_HEIGHT else 6.0f + rows.size.toFloat() * KEYBIND_ROW_HEIGHT
        val contentWidth = rows.maxOfOrNull {
            FontRenderer.width(FontRenderer.Face.SfRegular, it.name, KEYBIND_MODULE_TEXT_SIZE) +
                FontRenderer.width(FontRenderer.Face.SfRegular, it.bind, KEYBIND_MODULE_TEXT_SIZE) + 40.0f
        } ?: 0.0f
        bounds.width = if (rows.isEmpty()) {
            maxOf(
                KEYBINDS_WIDTH,
                FontRenderer.width(FontRenderer.Face.SfRegular, KEYBIND_EMPTY_TEXT, KEYBIND_MODULE_TEXT_SIZE) +
                    KEYBIND_EMPTY_HORIZONTAL_PADDING,
            )
        } else {
            maxOf(KEYBINDS_WIDTH, contentWidth)
        }
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
        val rows = renderRows
        val showEmptyState = preview || mc.screen is ChatScreen
        if (rows.isEmpty() && !showEmptyState) return

        if (!ModuleManager.postProcessing.enabled) {
            HudStyle.rect(
                graphics,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                KEYBIND_PANEL_RADIUS,
                fadeColor(0xCC000000.toInt(), visibilityAlpha),
            )
        }
        if (rows.isEmpty()) {
            FontRenderer.drawCentered(
                graphics,
                FontRenderer.Face.SfRegular,
                KEYBIND_EMPTY_TEXT,
                bounds.x + bounds.width * 0.5f,
                bounds.y + 5.75f,
                KEYBIND_MODULE_TEXT_SIZE,
                fadeColor(0xFF9A9A9A.toInt(), visibilityAlpha),
            )
            return
        }

        var rowOffset = 0.0f
        rows.forEach { row ->
            val rowAlpha = row.animation.value.coerceIn(0.0f, 1.0f) * visibilityAlpha
            if (rowAlpha <= 0.001f) return@forEach
            val rowY = bounds.y + 5.75f + rowOffset
            val switchVisual = switchVisual(row.name, if (row.enabled) 1.0f else 0.0f)
            val color = fadeColor(blendColor(0xFF9A9A9A.toInt(), HudStyle.TEXT, switchVisual.position), rowAlpha)
            val bindColor = color

            drawSwitch(graphics, bounds.x + 7.0f, rowY + 0.75f, switchVisual, rowAlpha)
            FontRenderer.draw(graphics, FontRenderer.Face.SfRegular, row.name, bounds.x + 25.0f, rowY, KEYBIND_MODULE_TEXT_SIZE, color)
            val bindWidth = FontRenderer.width(FontRenderer.Face.SfRegular, row.bind, KEYBIND_MODULE_TEXT_SIZE)
            FontRenderer.draw(
                graphics,
                FontRenderer.Face.SfRegular,
                row.bind,
                bounds.x + bounds.width - bindWidth - KEYBIND_INDICATOR_RIGHT_INSET,
                rowY,
                KEYBIND_MODULE_TEXT_SIZE,
                bindColor,
            )
            rowOffset += KEYBIND_ROW_HEIGHT
        }
    }

    private fun activeRows(preview: Boolean): List<BindRow> {
        if (!enabled && !preview) return emptyList()
        val active = ModuleManager.modules
            .filter { it.bind >= 0 }
            .sortedBy { it.name.lowercase() }
            .map { BindRow(it.name, keyName(it.bind), it.enabled) }
        if (active.isNotEmpty() || !preview) return active
        return emptyList()
    }

    private fun collectRows(activeRows: List<BindRow>): List<RowState> {
        val activeRowsByName = activeRows.associateBy { it.name }
        activeRows.forEach { row ->
            val state = rowAnimations.computeIfAbsent(row.name) { RowState(row.name, row.bind) }
            state.bind = row.bind
            state.enabled = row.enabled
        }
        val rows = mutableListOf<RowState>()
        val iterator = rowAnimations.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val row = entry.value
            val active = activeRowsByName.containsKey(entry.key)
            row.animation.update()
            row.animation.run(
                if (active) 1.0f else 0.0f,
                if (active) KEYBIND_ROW_FADE_IN_DURATION else KEYBIND_ROW_FADE_OUT_DURATION,
                { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) },
                true,
            )
            if (!active && row.animation.value <= 0.001f) iterator.remove() else rows += row
        }
        return rows.sortedBy { it.name.lowercase() }
        /*
        val activeRowsByName = activeRows.associateBy { it.name }
        activeRows.forEach { row ->
            val state = rowAnimations.computeIfAbsent(row.name) { RowState(row.name, row.bind) }
            state.bind = row.bind
            state.enabled = row.enabled
        }

        val rows = mutableListOf<RowState>()
        val iterator = rowAnimations.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val row = entry.value
            val active = activeRowsByName.containsKey(row.name)
            row.animation.update()
            row.animation.run(if (active) 1.0f else 0.0f,
                if (active) ROW_ENTER_ANIMATION_DURATION else ROW_EXIT_ANIMATION_DURATION,
                if (active) AnimationUtil::easeOutSoftBack else AnimationUtil::easeInBack,
                true,
            )
            if (!active && row.animation.value <= 0.01f) iterator.remove()
            else rows += row
        }

        return rows.sortedBy { it.name.lowercase() }
        */
    }

    private fun keyName(key: Int): String {
        return GLFW.glfwGetKeyName(key, 0)?.uppercase() ?: when (key) {
            GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT"
            GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT"
            else -> key.toString()
        }
    }

    private fun fadeColor(color: Int, alphaMultiplier: Float): Int {
        val alpha = ((color ushr 24) * alphaMultiplier.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun drawSwitch(graphics: GuiGraphicsExtractor, x: Float, y: Float, visual: SwitchVisual, alpha: Float) {
        val width = 12.0f
        val height = 7.0f
        val knobDiameter = height * 0.72f
        val sideInset = height * 0.5f
        val leftCenter = x + sideInset
        val rightCenter = x + width - sideInset
        val centerX = lerp(leftCenter, rightCenter, visual.position)
        val centerY = y + height * 0.5f
        val knobWidth = knobDiameter + (width - height) * 0.58f * visual.stretch
        HudStyle.pill(graphics, x, y, width, height, fadeColor(blendColor(0xFF575757.toInt(), 0xFF91B7FF.toInt(), visual.position), alpha))
        HudStyle.rect(
            graphics,
            centerX - knobWidth * 0.5f,
            centerY - knobDiameter * 0.5f,
            knobWidth,
            knobDiameter,
            knobDiameter * 0.5f,
            fadeColor(blendColor(0xFFC5C5C5.toInt(), 0xFFFFFFFF.toInt(), visual.position), alpha),
        )
    }

    private fun switchVisual(key: String, target: Float): SwitchVisual {
        val now = System.currentTimeMillis()
        val state = switchAnimations[key]
        if (state == null) {
            switchAnimations[key] = SwitchAnimation(target, target, now)
            return SwitchVisual(target, 0.0f)
        }
        val current = switchPosition(state, now)
        if (state.target != target) {
            state.from = current
            state.target = target
            state.startedAt = now
        }
        val linear = ((now - state.startedAt).toFloat() / SWITCH_ANIMATION_DURATION).coerceIn(0.0f, 1.0f)
        val eased = linear * linear * (3.0f - 2.0f * linear)
        return SwitchVisual(
            lerp(state.from, state.target, eased),
            sin(Math.PI.toFloat() * linear).coerceAtLeast(0.0f) * kotlin.math.abs(state.target - state.from),
        )
    }

    private fun switchPosition(state: SwitchAnimation, now: Long): Float {
        val linear = ((now - state.startedAt).toFloat() / SWITCH_ANIMATION_DURATION).coerceIn(0.0f, 1.0f)
        val eased = linear * linear * (3.0f - 2.0f * linear)
        return lerp(state.from, state.target, eased)
    }

    private fun blendColor(from: Int, to: Int, progress: Float): Int {
        val t = progress.coerceIn(0.0f, 1.0f)
        val a = lerp(((from ushr 24) and 0xFF).toFloat(), ((to ushr 24) and 0xFF).toFloat(), t).toInt()
        val r = lerp(((from ushr 16) and 0xFF).toFloat(), ((to ushr 16) and 0xFF).toFloat(), t).toInt()
        val g = lerp(((from ushr 8) and 0xFF).toFloat(), ((to ushr 8) and 0xFF).toFloat(), t).toInt()
        val b = lerp((from and 0xFF).toFloat(), (to and 0xFF).toFloat(), t).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress.coerceIn(0.0f, 1.0f)

    private data class BindRow(val name: String, val bind: String, val enabled: Boolean)

    private data class SwitchVisual(val position: Float, val stretch: Float)
    private data class SwitchAnimation(var from: Float, var target: Float, var startedAt: Long)

    private class RowState(
        val name: String,
        var bind: String,
        var enabled: Boolean = false,
    ) {
        val animation = AnimationUtil.TimedAnimation(0.0f)
    }
}
