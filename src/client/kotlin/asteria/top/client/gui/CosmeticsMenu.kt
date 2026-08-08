package asteria.top.client.gui

import asteria.top.client.render.FontRenderer
import asteria.top.client.render.MsdfIconRenderer
import asteria.top.client.module.ModuleManager
import asteria.top.client.util.GuiBoxUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import org.figuramc.figura.gui.screens.ConfigScreen
import org.figuramc.figura.model.rendering.EntityRenderMode
import org.figuramc.figura.utils.ui.UIHelper
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min

object CosmeticsMenu {
    private const val DESIGN_WIDTH = 297.0f
    private const val DESIGN_HEIGHT = 299.0f
    private const val OPEN_ANIMATION_MS = 190L
    private const val CATEGORY_ANIMATION_MS = 420L
    private const val PANEL_COLOR = 0xD9000000.toInt()
    private const val CHIP_COLOR = 0x42000000
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val MUTED = 0xFF8A8A8A.toInt()
    private const val CHIP_MUTED = 0xFFB2B2B2.toInt()
    private const val ACCENT = 0xFF91B7FF.toInt()

    private val CLOSE_ICON = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/xoutline.png")
    private val BACKPACK_ICON = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/boxoutline.png")
    private val HEAD_ICON = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/monitoroutline.png")
    private val WINGS_ICON = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/sparksoutline.png")

    private enum class Category(val title: String, val icon: Identifier) {
        BACKPACKS("Рюкзаки", BACKPACK_ICON),
        HEADWEAR("Головной убор", HEAD_ICON),
        WINGS("Крылья", WINGS_ICON),
    }

    private data class Rect(val x: Float, val y: Float, val width: Float, val height: Float)
    private data class Layout(
        val panel: Rect,
        val close: Rect,
        val previous: Rect,
        val next: Rect,
        val settings: Rect,
        val scale: Float,
    )

    @JvmStatic
    var visible = false
        private set

    private var renderVisible = false
    private var opening = false
    private var animationStartedAt = 0L
    private var selectedIndex = 1
    private var transitionFromIndex = 1
    private var slideDirection = 0
    private var categoryAnimationStartedAt = 0L
    private var rotatingPreview = false
    private var rotationAnchorX = 0.0f
    private var rotationAnchorYaw = 0.0f
    private var previewYaw = 0.0f
    private var lastRotationUpdateAt = 0L

    @JvmStatic
    fun openFromClickGui() {
        AsteriaClickGui.hideForCosmetics()
        visible = true
        renderVisible = true
        opening = true
        animationStartedAt = now()
        Minecraft.getInstance().mouseHandler.releaseMouse()
    }

    @JvmStatic
    fun closeToClickGui() {
        rotatingPreview = false
        visible = false
        renderVisible = false
        opening = false
        AsteriaClickGui.showFromCosmetics()
    }

    @JvmStatic
    fun shouldRender(): Boolean {
        if (!visible && renderVisible && now() - animationStartedAt >= OPEN_ANIMATION_MS) renderVisible = false
        return renderVisible
    }

    @JvmStatic
    fun animationProgress(): Float {
        val progress = ((now() - animationStartedAt).toFloat() / OPEN_ANIMATION_MS).coerceIn(0.0f, 1.0f)
        return if (opening) easeOut(progress) else 1.0f - easeOut(progress)
    }

    @JvmStatic
    fun blurBoxes(guiScale: Float): List<AsteriaOverlay.BlurBox> {
        if (!shouldRender()) return emptyList()
        val panel = layout().panel
        return listOf(
            AsteriaOverlay.BlurBox(
                panel.x * guiScale,
                panel.y * guiScale,
                panel.width * guiScale,
                panel.height * guiScale,
                22.5f * layout().scale * guiScale,
                tintStrength = shaderFillForCss(PANEL_COLOR),
                opacity = animationProgress(),
            )
        )
    }

    @JvmStatic
    fun keyPressed(key: Int, action: Int): Boolean {
        if (!visible) return false
        if (action == GLFW.GLFW_PRESS && (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            closeToClickGui()
        }
        return true
    }

    @JvmStatic
    fun mouseClicked(button: Int, action: Int): Boolean {
        if (!visible || action != GLFW.GLFW_PRESS) return false
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true
        val mc = Minecraft.getInstance()
        val x = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
        val y = mc.mouseHandler.getScaledYPos(mc.window).toFloat()
        val layout = layout()
        when {
            contains(x, y, layout.close) -> closeToClickGui()
            contains(x, y, layout.previous) -> selectRelative(-1)
            contains(x, y, layout.next) -> selectRelative(1)
            contains(x, y, layout.settings) -> openSettings()
            contains(x, y, previewInteractionRect(layout)) -> {
                rotatingPreview = true
                rotationAnchorX = x
                rotationAnchorYaw = previewYaw
                lastRotationUpdateAt = now()
            }
        }
        return true
    }

    @JvmStatic
    fun mouseReleased(button: Int, action: Int): Boolean {
        if (!visible) return false
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_RELEASE) {
            rotatingPreview = false
            lastRotationUpdateAt = now()
        }
        return true
    }

    @JvmStatic
    fun mouseScrolled(@Suppress("UNUSED_PARAMETER") vertical: Double): Boolean = visible

    @JvmStatic
    fun extract(graphics: GuiGraphicsExtractor) {
        if (!shouldRender()) return
        val layout = layout()
        val panel = layout.panel
        val alpha = animationProgress()
        // The ClickGUI panels get their translucent tint from the blur compositor itself.
        // Only draw a regular fallback panel when PostProcessing is disabled; drawing both
        // layers would cover the blurred world with an almost opaque black rectangle.
        if (!ModuleManager.postProcessing.enabled) {
            val panelColor = withAlpha(PANEL_COLOR, alpha)
            GuiBoxUtil.draw(graphics, GuiBoxUtil.Box(panel.x, panel.y, panel.width, panel.height, 22.5f * layout.scale, panelColor, false))
        }

        graphics.nextStratum()
        renderPlayers(graphics, layout)
        graphics.nextStratum()
        renderHeader(graphics, layout, alpha)
        renderCategorySelector(graphics, layout, alpha)
        GuiBoxUtil.draw(graphics, GuiBoxUtil.Box(layout.settings.x, layout.settings.y, layout.settings.width, layout.settings.height, 7.0f * layout.scale, withAlpha(CHIP_COLOR, alpha), false))
        FontRenderer.drawCentered(graphics, FontRenderer.Face.SfRegular, "Открыть настройки", layout.settings.x + layout.settings.width * 0.5f, layout.settings.y + 5.2f * layout.scale, 8.0f * layout.scale, withAlpha(WHITE, alpha))
    }

    private fun renderHeader(graphics: GuiGraphicsExtractor, layout: Layout, alpha: Float) {
        val p = layout.panel
        val s = layout.scale
        val title = "Выбор вашей косметики"
        val titleSize = 12.0f * s
        val titleWidth = FontRenderer.width(FontRenderer.Face.SfRegular, title, titleSize)
        val titleX = p.x + (p.width - titleWidth) * 0.5f
        FontRenderer.draw(graphics, FontRenderer.Face.SfRegular, title, titleX, p.y + 12.5f * s, titleSize, withAlpha(WHITE, alpha))
        MsdfIconRenderer.draw(graphics, CLOSE_ICON, layout.close.x, layout.close.y, layout.close.width, layout.close.height, withAlpha(MUTED, alpha))
    }

    private fun renderCategorySelector(graphics: GuiGraphicsExtractor, layout: Layout, alpha: Float) {
        val p = layout.panel
        val s = layout.scale
        val progress = categoryProgress()
        val stripWidth = 138.45f * s
        val strip = Rect(p.x + (p.width - stripWidth) * 0.5f, p.y + 35.5f * s, stripWidth, 22.0f * s)
        val itemSpacing = 54.0f * s

        // A single segmented container keeps every label on the same baseline and removes
        // the uneven three-pill silhouette. Only the selected center segment is highlighted.
        GuiBoxUtil.draw(graphics, GuiBoxUtil.Box(strip.x, strip.y, strip.width, strip.height, 8.0f * s, withAlpha(CHIP_COLOR, alpha), false))
        renderCategoryCarousel(graphics, strip, itemSpacing, progress, alpha)

        FontRenderer.drawCentered(graphics, FontRenderer.Face.SfRegular, "<", layout.previous.x + layout.previous.width * 0.5f, layout.previous.y + 1.0f * s, 12.0f * s, withAlpha(WHITE, alpha))
        FontRenderer.drawCentered(graphics, FontRenderer.Face.SfRegular, ">", layout.next.x + layout.next.width * 0.5f, layout.next.y + 1.0f * s, 12.0f * s, withAlpha(WHITE, alpha))
    }

    private fun renderPlayers(graphics: GuiGraphicsExtractor, layout: Layout) {
        val p = layout.panel
        val s = layout.scale
        updatePreviewRotation()

        val direction = slideDirection
        val baseIndex = if (direction == 0) selectedIndex else transitionFromIndex
        val progress = categoryProgress()
        val movement = if (direction == 0) 0.0f else direction * progress
        val spacing = 84.5f

        // Render the same conveyor as the category strip: old side previews leave through
        // the panel edge while the next category and its own avatar enter together.
        val slots = (-2..2)
            .map { relative -> relative to (relative.toFloat() - movement) }
            .filter { (_, position) -> abs(position) <= 1.65f }
            .sortedByDescending { (_, position) -> abs(position) }

        val categoryOccurrences = mutableMapOf<Int, Int>()
        for ((relative, position) in slots) {
            val categoryIndex = (baseIndex + relative + Category.entries.size) % Category.entries.size
            val variant = categoryOccurrences.getOrDefault(categoryIndex, 0)
            categoryOccurrences[categoryIndex] = variant + 1
            val player = CosmeticsAvatarPreview.entityFor(categoryIndex, variant) ?: continue
            val activeBlend = (1.0f - abs(position)).coerceIn(0.0f, 1.0f)
            val centerX = 148.5f + position * spacing
            val centerY = 169.0f - 2.0f * activeBlend
            val size = 54.0f + 29.0f * activeBlend
            val modelAlpha = 0.38f + 0.62f * activeBlend
            val yaw = previewYaw * activeBlend

            UIHelper.drawEntity(
                p.x + centerX * s, p.y + centerY * s, size * s,
                -5.0f,
                yaw,
                player,
                graphics,
                Vector3f(),
                EntityRenderMode.FIGURA_GUI,
                (p.x + 8.0f * s).toInt(),
                (p.y + 58.0f * s).toInt(),
                (p.x + 289.0f * s).toInt(),
                (p.y + 266.0f * s).toInt(),
                modelAlpha,
            )
        }
    }

    private fun updatePreviewRotation() {
        val time = now()
        val elapsed = if (lastRotationUpdateAt == 0L) 0.0f else (time - lastRotationUpdateAt).coerceAtMost(50L).toFloat()
        lastRotationUpdateAt = time
        if (rotatingPreview) {
            val mc = Minecraft.getInstance()
            val mouseX = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
            previewYaw = rotationAnchorYaw + (mouseX - rotationAnchorX) * 1.8f
        } else if (abs(previewYaw) > 0.01f) {
            previewYaw = wrapDegrees(previewYaw)
            previewYaw *= exp(-elapsed / 150.0f)
            if (abs(previewYaw) < 0.05f) previewYaw = 0.0f
        }
    }

    private fun previewInteractionRect(layout: Layout): Rect {
        val p = layout.panel
        val s = layout.scale
        return Rect(p.x + 12.0f * s, p.y + 62.0f * s, p.width - 24.0f * s, 204.0f * s)
    }

    private fun openSettings() {
        rotatingPreview = false
        visible = false
        renderVisible = false
        opening = false
        Minecraft.getInstance().setScreen(ConfigScreen(null))
    }

    private fun wrapDegrees(value: Float): Float {
        var wrapped = value % 360.0f
        if (wrapped > 180.0f) wrapped -= 360.0f
        if (wrapped < -180.0f) wrapped += 360.0f
        return wrapped
    }

    private fun selectRelative(direction: Int) {
        if (slideDirection != 0) return
        transitionFromIndex = selectedIndex
        selectedIndex = (selectedIndex + direction + Category.entries.size) % Category.entries.size
        slideDirection = direction
        categoryAnimationStartedAt = now()
    }

    private fun categoryAt(relative: Int, centerIndex: Int = selectedIndex): Category {
        val index = (centerIndex + relative + Category.entries.size) % Category.entries.size
        return Category.entries[index]
    }

    private fun renderCategoryCarousel(
        graphics: GuiGraphicsExtractor,
        strip: Rect,
        itemSpacing: Float,
        progress: Float,
        alpha: Float,
    ) {
        val s = strip.height / 22.0f
        val baseIndex = if (slideDirection == 0) selectedIndex else transitionFromIndex
        val movement = if (slideDirection == 0) 0.0f else slideDirection * progress
        val stripCenter = strip.x + strip.width * 0.5f
        val innerLeft = strip.x + 7.0f * s
        val innerRight = strip.x + strip.width - 7.0f * s
        val clipTop = strip.y.toInt().coerceAtLeast(0)
        val clipBottom = (strip.y + strip.height).toInt().coerceAtLeast(clipTop + 1)
        val scrollPhase = ((now() - animationStartedAt).coerceAtLeast(0L) % 3600L).toFloat() / 3600.0f
        val smoothScroll = 0.5f - 0.5f * cos(scrollPhase * 6.2831855f)

        for (relativeIndex in -2..2) {
            val position = relativeIndex.toFloat() - movement
            val centerX = stripCenter + position * itemSpacing
            if (centerX < strip.x - itemSpacing || centerX > strip.x + strip.width + itemSpacing) continue

            val category = categoryAt(relativeIndex, baseIndex)
            val activeBlend = (1.0f - abs(position)).coerceIn(0.0f, 1.0f)
            val viewportWidth = (30.0f + 38.0f * activeBlend) * s
            val viewportLeft = maxOf(innerLeft, centerX - viewportWidth * 0.5f)
            val viewportRight = minOf(innerRight, centerX + viewportWidth * 0.5f)
            if (viewportRight <= viewportLeft) continue

            val clipLeft = viewportLeft.toInt().coerceAtLeast(0)
            val clipRight = viewportRight.toInt().coerceAtLeast(clipLeft + 1)
            val iconSize = (6.8f + 0.7f * activeBlend) * s
            val fontSize = (7.0f + 1.0f * activeBlend) * s
            val itemAlpha = alpha * (0.68f + 0.32f * activeBlend)
            val textColor = withAlpha(blendColor(CHIP_MUTED, WHITE, activeBlend), itemAlpha)
            val iconColor = withAlpha(blendColor(CHIP_MUTED, ACCENT, activeBlend), itemAlpha)
            val textWidth = FontRenderer.width(FontRenderer.Face.SfRegular, category.title, fontSize)
            val groupWidth = iconSize + 3.0f * s + textWidth
            val availableWidth = viewportRight - viewportLeft
            val overflow = (groupWidth - availableWidth).coerceAtLeast(0.0f)
            val scrollOffset = if (slideDirection == 0) overflow * smoothScroll else 0.0f
            val iconX = if (overflow > 0.0f) viewportLeft - scrollOffset else viewportLeft + (availableWidth - groupWidth) * 0.5f

            graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom)
            MsdfIconRenderer.draw(graphics, category.icon, iconX, strip.y + (strip.height - iconSize) * 0.5f, iconSize, iconSize, iconColor)
            FontRenderer.draw(graphics, FontRenderer.Face.SfRegular, category.title, iconX + iconSize + 3.0f * s, strip.y + (strip.height - fontSize) * 0.5f, fontSize, textColor)
            graphics.disableScissor()
        }
    }

    private fun categoryProgress(): Float {
        if (slideDirection == 0) return 1.0f
        val linear = ((now() - categoryAnimationStartedAt).toFloat() / CATEGORY_ANIMATION_MS).coerceIn(0.0f, 1.0f)
        if (linear >= 1.0f) {
            slideDirection = 0
            transitionFromIndex = selectedIndex
        }
        // Fast initial travel with a long, soft landing at the destination.
        return easeOut(linear)
    }

    private fun layout(): Layout {
        val mc = Minecraft.getInstance()
        val availableW = (mc.window.guiScaledWidth - 12.0f).coerceAtLeast(1.0f)
        val availableH = (mc.window.guiScaledHeight - 12.0f).coerceAtLeast(1.0f)
        val scale = min(1.0f, min(availableW / DESIGN_WIDTH, availableH / DESIGN_HEIGHT))
        val width = DESIGN_WIDTH * scale
        val height = DESIGN_HEIGHT * scale
        val panel = Rect((mc.window.guiScaledWidth - width) * 0.5f, (mc.window.guiScaledHeight - height) * 0.5f, width, height)
        return Layout(
            panel,
            Rect(panel.x + 271.5f * scale, panel.y + 14.5f * scale, 12.5f * scale, 12.5f * scale),
            Rect(panel.x + 60.0f * scale, panel.y + 37.0f * scale, 16.0f * scale, 19.0f * scale),
            Rect(panel.x + 221.0f * scale, panel.y + 37.0f * scale, 16.0f * scale, 19.0f * scale),
            Rect(panel.x + 92.5f * scale, panel.y + 263.0f * scale, 112.0f * scale, 24.0f * scale),
            scale,
        )
    }

    private fun contains(x: Float, y: Float, rect: Rect): Boolean =
        x >= rect.x && x <= rect.x + rect.width && y >= rect.y && y <= rect.y + rect.height

    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * factor.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun shaderFillForCss(color: Int): Float {
        val cssAlpha = ((color ushr 24) and 0xFF) / 255.0f
        val globalTint = (ModuleManager.postProcessing.tintStrength.value / 100.0f).coerceIn(0.0f, 0.95f)
        val remainingAfterTint = 1.0f - globalTint
        val targetRemaining = 1.0f - cssAlpha
        return (1.0f - targetRemaining / remainingAfterTint).coerceIn(0.0f, 1.0f)
    }

    private fun blendColor(from: Int, to: Int, progress: Float): Int {
        val t = progress.coerceIn(0.0f, 1.0f)
        fun channel(shift: Int): Int {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun easeOut(value: Float): Float = 1.0f - (1.0f - value) * (1.0f - value) * (1.0f - value)
    private fun now(): Long = System.nanoTime() / 1_000_000L
}
