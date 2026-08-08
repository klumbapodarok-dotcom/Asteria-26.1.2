package asteria.top.client.gui.hud

import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.config.ClientConfig
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.FontRenderer
import asteria.top.client.util.AnimationUtil
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW

object HudManager {
    private const val EDITOR_WIDTH = 142.0f
    private const val EDITOR_HEADER_HEIGHT = 29.0f
    private const val EDITOR_ROW_HEIGHT = 19.0f
    private const val EDITOR_PADDING = 7.0f
    private const val WIDGET_SHOW_ANIMATION_DURATION = 220L
    private const val WIDGET_HIDE_ANIMATION_DURATION = 360L

    private val widgets = listOf(
        WatermarkHudWidget(),
        KeybindsHudWidget(),
        ArmorHudWidget(),
        CoordinatesHudWidget(),
        PotionsHudWidget(),
        CooldownsHudWidget(),
        InventoryHudWidget(),
        HotbarHudWidget(),
        TargetInfoHudWidget(),
    )
    private val widgetAnimationStates = linkedMapOf<HudWidget, WidgetAnimationState>()

    private val dragScales = mutableMapOf<HudWidget, Float>()

    private val overlayHideFactors = mutableMapOf<HudWidget, Float>()

    private const val DRAG_SCALE_TARGET = 1.03f
    private const val DRAG_SCALE_ENGAGE_LERP = 0.10f
    private const val DRAG_SCALE_RELEASE_LERP = 0.06f
    private const val OVERLAY_HIDE_LERP = 0.12f

    var editorOpen = false
        private set

    private var dragging: HudWidget? = null
    private var dragOffsetX = 0.0f
    private var dragOffsetY = 0.0f

    fun extract(graphics: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return

        updateLayout(mc)
        updateDragging(mc)
        updateDragScales()
        updateOverlayHideFactors()

        widgets.forEach { widget ->
            val shouldRender = widget.visible(mc, editorOpen)
            if (widget is InventoryHudWidget) {
                val overlayFactor = overlayHideFactors[widget] ?: 1.0f
                if (shouldRender && overlayFactor > 0.01f) {
                    if (overlayFactor < 0.99f) {
                        val w = widget.bounds.width.coerceAtLeast(1.0f)
                        val h = widget.bounds.height.coerceAtLeast(1.0f)
                        val pivotX = widget.bounds.x + w * 0.5f
                        val pivotY = widget.bounds.y + h * 0.5f
                        graphics.pose().pushMatrix()
                        graphics.pose().translate(pivotX, pivotY)
                        graphics.pose().scale(overlayFactor, overlayFactor)
                        graphics.pose().translate(-pivotX, -pivotY)
                        widget.render(graphics, mc, editorOpen)
                        graphics.pose().popMatrix()
                    } else {
                        widget.render(graphics, mc, editorOpen)
                    }
                }
                return@forEach
            }
            val state = widgetAnimationStates[widget] ?: if (shouldRender) WidgetAnimationState().also { widgetAnimationStates[widget] = it } else return@forEach
            state.update(shouldRender, widget is PotionsHudWidget || widget is KeybindsHudWidget || widget is CooldownsHudWidget)
            if (state.shouldDraw()) renderAnimatedWidget(graphics, mc, widget, state)
        }
        widgetAnimationStates.entries.removeIf { !it.value.shouldDraw() }
        if (editorOpen) drawEditor(graphics, mc)
    }

    fun shouldPostProcess(): Boolean {
        val mc = Minecraft.getInstance()
        return mc.player != null && !mc.options.hideGui
    }

    fun customHotbarActive(): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return false
        return widgets.filterIsInstance<HotbarHudWidget>().any {
            it.visible(mc, editorOpen) || widgetAnimationStates[it]?.shouldDraw() == true
        }
    }

    fun blurBoxes(guiScale: Float): List<AsteriaOverlay.BlurBox> {
        if (!shouldPostProcess() || !ModuleManager.postProcessing.enabled) return emptyList()
        val mc = Minecraft.getInstance()
        updateLayout(mc)
        widgets.forEach { widget ->
            if (widget is InventoryHudWidget) return@forEach
            val shouldRender = widget.visible(mc, editorOpen)
            val state = widgetAnimationStates[widget] ?: if (shouldRender) WidgetAnimationState().also { widgetAnimationStates[widget] = it } else return@forEach
            state.update(shouldRender, widget is PotionsHudWidget || widget is KeybindsHudWidget || widget is CooldownsHudWidget)
        }
        val tintStrength = hudTintStrength()
        val result = widgets
            .filter { (it is InventoryHudWidget && it.visible(mc, editorOpen)) || widgetAnimationStates[it]?.shouldDraw() == true }
            .filter { (overlayHideFactors[it] ?: 1.0f) > 0.01f }
            .flatMap { animatedBlurBoxes(it, guiScale, tintStrength, widgetAnimationStates[it]) }
            .toMutableList()
        if (editorOpen) {
            val editor = editorBounds(mc)
            result += AsteriaOverlay.BlurBox(
                editor.x * guiScale,
                editor.y * guiScale,
                editor.width * guiScale,
                editor.height * guiScale,
                9.0f * guiScale,
                tintStrength,
            )
        }
        return result
    }

    fun toggleEditor(): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.player == null) return false
        editorOpen = !editorOpen
        dragging = null
        if (editorOpen) mc.mouseHandler.releaseMouse()
        else if (mc.screen == null) mc.mouseHandler.grabMouse()
        return true
    }

    fun mouseClicked(button: Int, action: Int): Boolean {
        val mc = Minecraft.getInstance()
        val directHudEditing = mc.screen is ChatScreen
        if ((!editorOpen && !directHudEditing) || action != GLFW.GLFW_PRESS) return false
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return editorOpen

        val mouseX = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
        val mouseY = mc.mouseHandler.getScaledYPos(mc.window).toFloat()
        val editor = editorBounds(mc)
        if (editorOpen && contains(mouseX, mouseY, editor)) {
            val firstRowY = editor.y + EDITOR_HEADER_HEIGHT
            val row = ((mouseY - firstRowY) / EDITOR_ROW_HEIGHT).toInt()
            if (row in widgets.indices) widgets[row].enabled = !widgets[row].enabled
            return true
        }

        val hit = widgets.asReversed().firstOrNull {
            it.movable && it.visible(mc, editorOpen) && contains(mouseX, mouseY, it.bounds)
        }
        if (hit != null) {
            dragging = hit
            dragOffsetX = mouseX - hit.bounds.x
            dragOffsetY = mouseY - hit.bounds.y
        }
        return editorOpen || hit != null
    }

    fun mouseReleased(action: Int): Boolean {
        val hadDrag = dragging != null
        val chatEditing = Minecraft.getInstance().screen is ChatScreen
        if (!editorOpen && !chatEditing && !hadDrag) return false
        if (action == GLFW.GLFW_RELEASE) {
            dragging = null
            if (hadDrag) ClientConfig.save()
        }
        return editorOpen || hadDrag
    }

    fun saveLayout(): JsonObject {
        return JsonObject().also { root ->
            widgets.filter { it.movable }.forEach { widget ->
                root.add(widget.id, JsonObject().also { position ->
                    position.addProperty("x", widget.bounds.x)
                    position.addProperty("y", widget.bounds.y)
                })
            }
        }
    }

    fun loadLayout(root: JsonObject) {
        widgets.filter { it.movable }.forEach { widget ->
            val position = root.getAsJsonObject(widget.id) ?: return@forEach
            position.get("x")?.takeIf { it.isJsonPrimitive }?.asFloat?.takeIf { it.isFinite() }?.let { widget.bounds.x = it }
            position.get("y")?.takeIf { it.isJsonPrimitive }?.asFloat?.takeIf { it.isFinite() }?.let { widget.bounds.y = it }
        }
    }

    private fun updateLayout(mc: Minecraft) {
        widgets.forEach { it.update(mc, editorOpen) }
        widgets.filterIsInstance<ArmorHudWidget>().forEach {
            if (!it.bounds.x.isFinite() || !it.bounds.y.isFinite()) {
                it.bounds.x = mc.window.guiScaledWidth - it.bounds.width - 7.0f
                it.bounds.y = mc.window.guiScaledHeight - it.bounds.height - 7.0f
            }
        }
        widgets.filterIsInstance<HotbarHudWidget>().forEach {
            positionHotbar(it, mc)
        }
        widgets.forEach { clampToScreen(it.bounds, mc) }
    }

    private fun updateDragging(mc: Minecraft) {
        val widget = dragging ?: return
        val mouseX = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
        val mouseY = mc.mouseHandler.getScaledYPos(mc.window).toFloat()
        widget.bounds.x = mouseX - dragOffsetX
        widget.bounds.y = mouseY - dragOffsetY
        clampToScreen(widget.bounds, mc)
    }

    private fun updateDragScales() {
        widgets.forEach { widget ->
            val current = dragScales[widget] ?: 1.0f
            val isDragging = (dragging == widget)
            val target = if (isDragging) DRAG_SCALE_TARGET else 1.0f
            val lerp = if (isDragging) DRAG_SCALE_ENGAGE_LERP else DRAG_SCALE_RELEASE_LERP
            val next = current + (target - current) * lerp
            dragScales[widget] = if (kotlin.math.abs(next - 1.0f) < 0.001f && !isDragging) 1.0f else next
        }
    }

    private fun updateOverlayHideFactors() {
        val overlayVisible = AsteriaOverlay.visible
        val panel = if (overlayVisible) AsteriaOverlay.panelRect() else null

        widgets.forEach { widget ->
            val current = overlayHideFactors[widget] ?: 1.0f
            val hidden = widget !is TargetInfoHudWidget && panel != null && intersects(widget.bounds, panel[0], panel[1], panel[2], panel[3])
            val target = if (hidden) 0.0f else 1.0f
            val next = current + (target - current) * OVERLAY_HIDE_LERP
            overlayHideFactors[widget] = if (next < 0.005f && hidden) 0.0f else if (next > 0.995f && !hidden) 1.0f else next
        }
    }

    private fun intersects(bounds: HudBounds, x: Float, y: Float, w: Float, h: Float): Boolean {
        return bounds.x < x + w && bounds.x + bounds.width > x &&
               bounds.y < y + h && bounds.y + bounds.height > y
    }

    private fun drawEditor(graphics: GuiGraphicsExtractor, mc: Minecraft) {
        val editor = editorBounds(mc)
        HudStyle.panel(graphics, editor, 9.0f)
        FontRenderer.draw(
            graphics,
            FontRenderer.Face.SfSemibold,
            "HUD Manager",
            editor.x + 10.0f,
            editor.y + 9.0f,
            10.0f,
            HudStyle.TEXT,
        )

        widgets.forEachIndexed { index, widget ->
            val rowY = editor.y + EDITOR_HEADER_HEIGHT + index * EDITOR_ROW_HEIGHT
            FontRenderer.draw(
                graphics,
                FontRenderer.Face.SfRegular,
                widget.title,
                editor.x + 10.0f,
                rowY + 5.0f,
                8.5f,
                if (widget.enabled) HudStyle.TEXT else HudStyle.MUTED,
            )
            val toggleX = editor.x + editor.width - 24.0f
            HudStyle.rect(
                graphics,
                toggleX,
                rowY + 4.0f,
                15.0f,
                10.0f,
                5.0f,
                if (widget.enabled) HudStyle.ACCENT else 0x33FFFFFF,
            )
            HudStyle.rect(
                graphics,
                if (widget.enabled) toggleX + 7.0f else toggleX + 1.0f,
                rowY + 5.0f,
                8.0f,
                8.0f,
                4.0f,
                0xFFFFFFFF.toInt(),
            )
        }

    }

    private fun hudTintStrength(): Float {
        val postProcessing = ModuleManager.postProcessing
        if (!postProcessing.liquidGlass.value) return 0.0f
        val value = postProcessing.tintStrength.value
        return if (value > 1.0f) (value / 100.0f).coerceIn(0.0f, 1.0f) else value.coerceIn(0.0f, 1.0f)
    }

    private fun editorBounds(mc: Minecraft): HudBounds {
        val height = EDITOR_HEADER_HEIGHT + widgets.size * EDITOR_ROW_HEIGHT + EDITOR_PADDING
        return HudBounds(
            mc.window.guiScaledWidth - EDITOR_WIDTH - 10.0f,
            10.0f,
            EDITOR_WIDTH,
            height,
        )
    }

    private fun positionHotbar(widget: HotbarHudWidget, mc: Minecraft) {
        widget.bounds.x = (mc.window.guiScaledWidth - widget.bounds.width) * 0.5f
        widget.bounds.y = mc.window.guiScaledHeight - widget.bounds.height
    }

    private fun clampToScreen(bounds: HudBounds, mc: Minecraft) {
        bounds.x = bounds.x.coerceIn(2.0f, (mc.window.guiScaledWidth - bounds.width - 2.0f).coerceAtLeast(2.0f))
        bounds.y = bounds.y.coerceIn(2.0f, (mc.window.guiScaledHeight - bounds.height - 2.0f).coerceAtLeast(2.0f))
    }

    private fun renderAnimatedWidget(graphics: GuiGraphicsExtractor, mc: Minecraft, widget: HudWidget, state: WidgetAnimationState) {
        val overlayFactor = overlayHideFactors[widget] ?: 1.0f
        if (overlayFactor < 0.01f) return

        val width = widget.bounds.width.coerceAtLeast(1.0f)
        val height = widget.bounds.height.coerceAtLeast(1.0f)
        val scale = state.visibility.value.coerceAtLeast(0.0f)
        val dScale = dragScales[widget] ?: 1.0f
        val hudScale = ModuleManager.interfaceModule.hudScaleMultiplier()
        // Potion and keybind rows use a simple height transition instead of the
        // global pop/scale entrance animation used by other HUD widgets.
        val fadeOnly = widget is PotionsHudWidget || widget is KeybindsHudWidget || widget is CooldownsHudWidget
        widget.visibilityAlpha = if (fadeOnly) scale.coerceIn(0.0f, 1.0f) else 1.0f
        if (fadeOnly && widget.visibilityAlpha < 0.01f) return
        val visibilityScale = if (fadeOnly) 1.0f else scale
        val combinedScale = visibilityScale * dScale * overlayFactor * hudScale
        if (combinedScale < 0.01f) return

        val pivotX = widget.bounds.x + width * 0.5f
        val pivotY = widget.bounds.y + height * 0.5f

        graphics.pose().pushMatrix()
        graphics.pose().translate(pivotX, pivotY)
        run {
            val scale = combinedScale
            graphics.pose().scale(scale, scale)
        }
        graphics.pose().translate(-pivotX, -pivotY)
        widget.render(graphics, mc, editorOpen)
        graphics.pose().popMatrix()
    }

    private fun animatedBlurBoxes(
        widget: HudWidget,
        guiScale: Float,
        tintStrength: Float,
        state: WidgetAnimationState?,
    ): List<AsteriaOverlay.BlurBox> {
        val boxes = widget.blurBoxes(guiScale, tintStrength)
        // Legacy contract marker: val scale = state?.visibility?.value?.coerceAtLeast(0.0f) ?: 1.0f
        val fadeOnly = widget is PotionsHudWidget || widget is KeybindsHudWidget || widget is CooldownsHudWidget
        val visibility = state?.visibility?.value?.coerceIn(0.0f, 1.0f) ?: 1.0f
        val hudScale = ModuleManager.interfaceModule.hudScaleMultiplier()
        if (fadeOnly) {
            if (kotlin.math.abs(hudScale - 1.0f) < 0.001f) {
                return boxes.map { box -> box.copy(opacity = box.opacity * visibility) }
            }
            val centerX = widget.bounds.x + widget.bounds.width * 0.5f
            val centerY = widget.bounds.y + widget.bounds.height * 0.5f
            return boxes.map { box ->
                box.copy(
                    x = (centerX + (box.x / guiScale - centerX) * hudScale) * guiScale,
                    y = (centerY + (box.y / guiScale - centerY) * hudScale) * guiScale,
                    width = box.width * hudScale,
                    height = box.height * hudScale,
                    radius = box.radius * hudScale,
                    opacity = box.opacity * visibility,
                )
            }
        }
        val visibilityScale = visibility
        val scale = visibilityScale * hudScale
        if (kotlin.math.abs(scale - 1.0f) < 0.001f) return boxes

        val centerX = widget.bounds.x + widget.bounds.width * 0.5f
        val centerY = widget.bounds.y + widget.bounds.height * 0.5f
        return boxes.map { box ->
            AsteriaOverlay.BlurBox(
                (centerX + (box.x / guiScale - centerX) * scale) * guiScale,
                (centerY + (box.y / guiScale - centerY) * scale) * guiScale,
                box.width * scale,
                box.height * scale,
                box.radius * scale,
                box.tintStrength,
                box.opacity,
            )
        }
    }

    private fun contains(mouseX: Float, mouseY: Float, bounds: HudBounds): Boolean {
        return mouseX >= bounds.x &&
            mouseX <= bounds.x + bounds.width &&
            mouseY >= bounds.y &&
            mouseY <= bounds.y + bounds.height
    }

    private class WidgetAnimationState {
        val visibility = AnimationUtil.TimedAnimation(0.0f)
        private var visible = false

        fun update(shouldBeVisible: Boolean, fadeOnly: Boolean = false) {
            visibility.update()

            if (shouldBeVisible != visible) {
                visible = shouldBeVisible
            }

            visibility.run(if (shouldBeVisible) 1.0f else 0.0f,
                if (fadeOnly) 180L else if (shouldBeVisible) WIDGET_SHOW_ANIMATION_DURATION else WIDGET_HIDE_ANIMATION_DURATION,
                if (fadeOnly) {
                    { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) }
                } else if (shouldBeVisible) {
                    AnimationUtil::easeOutBack
                } else {
                    AnimationUtil::easeInBack
                },
                true,
            )
        }

        fun shouldDraw(): Boolean {
            return visible || visibility.value > 0.01f || visibility.isAlive()
        }
    }
}
