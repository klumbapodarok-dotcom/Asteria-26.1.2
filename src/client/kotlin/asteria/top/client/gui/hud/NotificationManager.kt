package asteria.top.client.gui.hud

import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.config.ClientConfig
import asteria.top.client.module.Module
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.MsdfIconRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object NotificationManager {
    private const val HEIGHT = 20.0f
    private const val RADIUS = 7.75f
    private const val PADDING = 5.75f
    private const val ICON_SIZE = 10.0f
    private const val ICON_TEXT_GAP = 3.0f
    private const val FONT_SIZE = 8.5f
    private const val STACK_GAP = 4.0f
    private const val ENTER_MS = 240L
    private const val HOLD_MS = 2100L
    private const val EXIT_MS = 360L
    private const val SHIFT_MS = 260L
    private const val PREVIEW_INTERVAL_MS = 3000L
    private const val PREVIEW_TRANSITION_MS = 320L

    private val checkIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/checkoutline.png")
    private val xIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/xoutline.png")
    private val notifications = mutableListOf<Notification>()
    private val positionStates = mutableMapOf<Long, PositionState>()
    private var nextId = 0L
    private var anchorY = Float.NaN
    private var dragging = false
    private var dragOffsetY = 0.0f

    fun moduleStateChanged(module: Module, enabled: Boolean) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || !ModuleManager.interfaceModule.notifications.value) return
        notifications += Notification(++nextId, module.name, enabled, System.currentTimeMillis())
    }

    fun extract(graphics: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        updateDragging(mc)
        val layouts = layouts(mc, removeExpired = true)
        layouts.forEach { layout ->
            val rowScale = layout.scale
            val rowHeight = HEIGHT * rowScale
            if (!ModuleManager.postProcessing.enabled) {
                HudStyle.rect(graphics, layout.x, layout.y, layout.width, rowHeight, RADIUS * rowScale, withAlpha(0xCC000000.toInt(), layout.progress))
            }
            MsdfIconRenderer.draw(
                graphics,
                if (layout.notification.enabled) checkIcon else xIcon,
                layout.x + PADDING * rowScale,
                layout.y + (rowHeight - ICON_SIZE * rowScale) * 0.5f,
                ICON_SIZE * rowScale,
                ICON_SIZE * rowScale,
                withAlpha(if (layout.notification.enabled) 0xFF50FF2D.toInt() else 0xFFFF5B5B.toInt(), layout.progress),
            )
            FontRenderer.draw(
                graphics,
                FontRenderer.Face.SfRegular,
                text(layout.notification),
                layout.x + (PADDING + ICON_SIZE + ICON_TEXT_GAP) * rowScale,
                layout.y + 5.75f * rowScale,
                FONT_SIZE * rowScale,
                withAlpha(0xFFFFFFFF.toInt(), layout.progress),
            )
        }
    }

    fun blurBoxes(guiScale: Float): List<AsteriaOverlay.BlurBox> {
        val mc = Minecraft.getInstance()
        if (mc.player == null || !ModuleManager.postProcessing.enabled) return emptyList()
        return layouts(mc, removeExpired = false).map { layout ->
            AsteriaOverlay.BlurBox(
                layout.x * guiScale,
                layout.y * guiScale,
                layout.width * guiScale,
                HEIGHT * layout.scale * guiScale,
                RADIUS * layout.scale * guiScale,
                0.65f,
                layout.progress,
            )
        }
    }

    fun hasVisibleNotifications(): Boolean {
        val preview = Minecraft.getInstance().screen is ChatScreen && ModuleManager.interfaceModule.notifications.value
        return preview || notifications.any { progress(it, System.currentTimeMillis()) > 0.001f }
    }

    fun mouseClicked(button: Int, action: Int): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.screen !is ChatScreen || !ModuleManager.interfaceModule.notifications.value) return false
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS) return false
        val mouseX = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
        val mouseY = mc.mouseHandler.getScaledYPos(mc.window).toFloat()
        val hit = layouts(mc, removeExpired = false).any {
            mouseX >= it.x && mouseX <= it.x + it.width && mouseY >= it.y && mouseY <= it.y + HEIGHT * it.scale
        }
        if (!hit) return false
        ensureAnchor(mc, previewCount())
        dragging = true
        dragOffsetY = mouseY - anchorY
        return true
    }

    fun mouseReleased(action: Int): Boolean {
        if (!dragging) return false
        if (action == GLFW.GLFW_RELEASE) {
            dragging = false
            ClientConfig.save()
        }
        return true
    }

    fun saveLayout(): com.google.gson.JsonObject {
        return com.google.gson.JsonObject().also { root ->
            if (anchorY.isFinite()) root.addProperty("y", anchorY)
        }
    }

    fun loadLayout(root: com.google.gson.JsonObject) {
        root.get("y")?.takeIf { it.isJsonPrimitive }?.asFloat?.takeIf { it.isFinite() }?.let { anchorY = it }
    }

    private fun layouts(mc: Minecraft, removeExpired: Boolean): List<Layout> {
        val now = System.currentTimeMillis()
        if (removeExpired) notifications.removeAll { now - it.createdAt >= ENTER_MS + HOLD_MS + EXIT_MS }
        val chatPreview = mc.screen is ChatScreen && ModuleManager.interfaceModule.notifications.value
        val activeNotifications = notifications.asReversed().filter { progress(it, now) > 0.001f }
        // In chat, use the rotating sample only while the real notification queue is empty.
        // Active notifications must remain visible so the preview never masks them.
        val preview = chatPreview && activeNotifications.isEmpty()
        val entries = if (preview) {
            listOf(Notification(-1L, "Aura", (now / PREVIEW_INTERVAL_MS) % 2L == 0L, now))
        } else {
            // New notifications occupy the first slot; existing cards smoothly move down.
            activeNotifications
        }
        ensureAnchor(mc, entries.size)
        val rowScale = ModuleManager.interfaceModule.hudScaleMultiplier()
        val activeIds = entries.map { it.id }.toSet()
        positionStates.keys.removeIf { it !in activeIds }
        return entries.mapIndexedNotNull { index, notification ->
            val progress = if (preview) previewProgress(now) else progress(notification, now)
            if (progress <= 0.001f) return@mapIndexedNotNull null
            val slot = if (preview) index.toFloat() else animatedSlot(notification.id, index, now)
            val text = text(notification)
            val baseWidth = PADDING + ICON_SIZE + ICON_TEXT_GAP + FontRenderer.width(FontRenderer.Face.SfRegular, text, FONT_SIZE) + PADDING
            val width = baseWidth * rowScale
            val x = (mc.window.guiScaledWidth - width) * 0.5f
            val enterOffset = if (preview) 0.0f else -8.0f * (1.0f - easeOutCubic(progress))
            val y = anchorY + slot * (HEIGHT + STACK_GAP) * rowScale + enterOffset * rowScale
            Layout(notification, x, y, width, rowScale, progress)
        }
    }

    private fun animatedSlot(id: Long, target: Int, now: Long): Float {
        val state = positionStates[id]
        if (state == null) {
            positionStates[id] = PositionState(target.toFloat(), target, now)
            return target.toFloat()
        }
        if (state.target != target) {
            state.from = state.current(now)
            state.target = target
            state.startedAt = now
        }
        return state.current(now)
    }

    private fun updateDragging(mc: Minecraft) {
        if (!dragging || mc.screen !is ChatScreen) return
        val mouseY = mc.mouseHandler.getScaledYPos(mc.window).toFloat()
        anchorY = mouseY - dragOffsetY
        clampAnchor(mc, previewCount())
    }

    private fun ensureAnchor(mc: Minecraft, count: Int) {
        if (!anchorY.isFinite()) anchorY = mc.window.guiScaledHeight * 0.62f
        clampAnchor(mc, count)
    }

    private fun clampAnchor(mc: Minecraft, count: Int) {
        val rowScale = ModuleManager.interfaceModule.hudScaleMultiplier()
        val stackHeight = (HEIGHT + (count.coerceAtLeast(1) - 1) * (HEIGHT + STACK_GAP)) * rowScale
        anchorY = anchorY.coerceIn(7.0f, (mc.window.guiScaledHeight - stackHeight - 7.0f).coerceAtLeast(7.0f))
    }

    private fun previewCount(): Int {
        val now = System.currentTimeMillis()
        val activeCount = notifications.count { progress(it, now) > 0.001f }
        return activeCount.coerceAtLeast(1)
    }

    private fun previewProgress(now: Long): Float {
        val phase = (now % PREVIEW_INTERVAL_MS).toFloat()
        return when {
            phase < PREVIEW_TRANSITION_MS -> (phase / PREVIEW_TRANSITION_MS).coerceIn(0.0f, 1.0f)
            phase > PREVIEW_INTERVAL_MS - PREVIEW_TRANSITION_MS ->
                ((PREVIEW_INTERVAL_MS - phase) / PREVIEW_TRANSITION_MS).coerceIn(0.0f, 1.0f)
            else -> 1.0f
        }
    }

    private fun progress(notification: Notification, now: Long): Float {
        val elapsed = now - notification.createdAt
        return when {
            elapsed < 0L -> 0.0f
            elapsed < ENTER_MS -> (elapsed.toFloat() / ENTER_MS).coerceIn(0.0f, 1.0f)
            elapsed < ENTER_MS + HOLD_MS -> 1.0f
            elapsed < ENTER_MS + HOLD_MS + EXIT_MS ->
                1.0f - ((elapsed - ENTER_MS - HOLD_MS).toFloat() / EXIT_MS).coerceIn(0.0f, 1.0f)
            else -> 0.0f
        }
    }

    private fun easeOutCubic(value: Float): Float {
        val inverse = 1.0f - value.coerceIn(0.0f, 1.0f)
        return 1.0f - inverse * inverse * inverse
    }

    private fun text(notification: Notification): String {
        return "${notification.moduleName} ${if (notification.enabled) "включен" else "выключен"}"
    }

    private fun withAlpha(color: Int, multiplier: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * multiplier.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private data class Notification(val id: Long, val moduleName: String, val enabled: Boolean, val createdAt: Long)
    private data class PositionState(var from: Float, var target: Int, var startedAt: Long) {
        fun current(now: Long): Float {
            val progress = ((now - startedAt).toFloat() / SHIFT_MS).coerceIn(0.0f, 1.0f)
            val eased = 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress)
            return from + (target - from) * eased
        }
    }
    private data class Layout(val notification: Notification, val x: Float, val y: Float, val width: Float, val scale: Float, val progress: Float)
}
