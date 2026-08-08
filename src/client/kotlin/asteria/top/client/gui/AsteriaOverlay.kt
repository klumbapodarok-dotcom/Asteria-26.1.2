package asteria.top.client.gui

import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.module.setting.IntSetting
import asteria.top.client.module.setting.ModuleSetting
import asteria.top.client.module.setting.MultiBooleanSetting
import asteria.top.client.render.AsteriaGuiRenderer
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.FontRenderer.Face
import asteria.top.client.render.PlayerHeadRenderer
import asteria.top.client.render.TextureRenderer
import asteria.top.client.util.AnimationUtil
import asteria.top.client.util.BoxAnimationUtil
import asteria.top.client.util.ControlGeometry
import asteria.top.client.util.GuiBoxUtil
import asteria.top.client.util.ScissorUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object AsteriaOverlay {
    private const val WIDTH = 706.0f
    private const val HEIGHT = 444.0f
    private const val RADIUS = 34.0f
    const val PANEL_CORNER_RADIUS = RADIUS

    private const val SIDEBAR_W = 140.0f
    private const val SETTINGS_CLIP_X = 139.0f
    private const val SETTINGS_CLIP_W = 535.0f
    private const val MODULE_CLIP_Y = 70.0f
    private const val MODULE_CARD_X = 139.0f
    private const val MODULE_CARD_Y = 79.0f
    private const val MODULE_CARD_W = 260.0f
    private const val MODULE_CARD_COLLAPSED_H = 51.0f
    private const val MODULE_CARD_COL_GAP = 14.0f
    private const val MODULE_CARD_ROW_GAP = 11.0f

    private const val ACCENT = 0xFF88FF82.toInt()
    private const val WINDOW = 0xE6000000.toInt()
    private const val WINDOW_SOFT = 0x99262626.toInt()
    private const val CONTENT = 0xFF121212.toInt()
    private const val MODULE_CARD_OUTLINE_TINT = 0x1AFFFFFF
    private const val MODULE_CARD_OUTLINE_INSET = 1.0f
    private const val MODULE_CARD_OUTLINE_RADIUS = 14.0f
    private const val MODULE_CARD_RADIUS = MODULE_CARD_OUTLINE_RADIUS
    private const val DROPDOWN_OUTLINE_TINT = 0x1AFFFFFF
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val TEXT_75 = 0xBFFFFFFF.toInt()
    private const val TEXT_50 = 0x80FFFFFF.toInt()
    private const val TEXT_25 = 0x40FFFFFF
    private const val TEXT_10 = 0x1AFFFFFF
    private const val OPEN_ANIMATION_MS = 190L
    private const val CATEGORY_ANIMATION_MS = 170L
    private const val MODULE_OPEN_MS = 560L
    private const val MODULE_CLOSE_MS = 240L
    private const val DROPDOWN_OPEN_MS = 360L
    private const val DROPDOWN_CLOSE_MS = 180L
    private const val CONTROL_ANIMATION_SPEED = 0.24f
    private const val SCROLL_STEP = 38.0f
    private const val COLLAPSED_HEIGHT = 76.0f
    private const val COLLAPSED_WIDTH = 190.0f
    private val GUI_ANIMATION_MODE = AnimationUtil.Mode.BOUNCE
    private val LOGO_TEXTURE = Identifier.fromNamespaceAndPath("asteria", "textures/gui/asteria.png")
    private val LOGO_GLOW_TEXTURE = Identifier.fromNamespaceAndPath("asteria", "textures/glow.png")

    data class BlurBox(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
        val tintStrength: Float = 0.0f,
        val opacity: Float = 1.0f,
    )
    private data class Frame(val x: Float, val y: Float, val width: Float, val height: Float, val scaleX: Float, val scaleY: Float)
    private data class Rect(val x: Float, val y: Float, val width: Float, val height: Float)
    private data class ChipItem(val index: Int, val label: String, val rect: Rect, val switchRect: Rect? = null)
    private data class SettingItem(
        val setting: ModuleSetting<*>,
        val y: Float,
        val height: Float,
        val chips: List<ChipItem> = emptyList(),
        val slider: Rect? = null,
        val switchRect: Rect? = null,
        val expansion: Float = 0.0f,
    )
    private data class ExpandedLayout(val height: Float, val items: List<SettingItem>)
    private data class ModuleExpansion(var from: Float, var target: Float, var startedAt: Long)
    private data class DropdownExpansion(var from: Float, var target: Float, var startedAt: Long)

    @JvmStatic
    var visible = false
        private set

    private var selectedCategory = ModuleCategory.COMBAT
    private var previousCategory = selectedCategory
    private var renderVisible = false
    private var opening = false
    private var openAnimationStart = 0L
    private var categoryAnimationStart = 0L
    private val expandedModuleNames = mutableSetOf<String>()
    private var activeFloatSetting: FloatSetting? = null
    private var activeIntSetting: IntSetting? = null
    private var activeSliderKey: String? = null
    private var activeSliderX = 0.0f
    private var activeSliderW = 1.0f
    private var targetModuleScroll = 0.0f
    private var moduleScroll = 0.0f
    private var bindingModule: asteria.top.client.module.Module? = null
    private val animatedControls = mutableMapOf<String, Float>()
    private val sliderVisualPercents = mutableMapOf<String, Float>()
    private val expandedSettingGroups = mutableSetOf<String>()
    private val moduleExpansions = mutableMapOf<String, ModuleExpansion>()
    private val dropdownExpansions = mutableMapOf<String, DropdownExpansion>()

    private val categoryOrder = listOf(
        ModuleCategory.COMBAT,
        ModuleCategory.MOVEMENT,
        ModuleCategory.VISUALS,
        ModuleCategory.PLAYER,
        ModuleCategory.MISC,
    )
    private val categoryIcons = listOf("M", "L", "G", "H", "I")

    @JvmStatic
    fun toggle() {
        val nextVisible = !visible
        if (nextVisible == visible && renderVisible) return
        visible = nextVisible
        opening = nextVisible
        openAnimationStart = nowMillis()
        if (nextVisible) renderVisible = true
        else {
            collapseExpandedModules()
            asteria.top.client.config.ClientConfig.save()
        }
        val mc = Minecraft.getInstance()
        if (visible) {
            mc.mouseHandler.releaseMouse()
        } else collapseExpandedModules()
        if (!visible && mc.level != null && mc.screen == null) {
            mc.mouseHandler.grabMouse()
        }
    }

    @JvmStatic
    fun shouldRender(): Boolean {
        updateRenderVisibility()
        return renderVisible
    }

    @JvmStatic
    fun animationProgress(): Float = if (renderVisible) 1.0f else 0.0f

    @JvmStatic
    fun panelRect(): FloatArray {
        val frame = animatedFrame()
        return floatArrayOf(frame.x, frame.y, frame.width, frame.height)
    }

    @JvmStatic
    fun blurBoxes(guiScale: Float): List<BlurBox> {
        if (!shouldRender()) return emptyList()
        val frame = animatedFrame()
        updateModuleScroll(frame)
        val clipX = frame.x * guiScale
        val clipY = frame.y * guiScale
        val clipRight = (frame.x + frame.width) * guiScale
        val clipBottom = (frame.y + frame.height) * guiScale
        val moduleClipX = xRel(frame.x, frame.scaleX, SETTINGS_CLIP_X) * guiScale
        val moduleClipTop = yRel(frame.y, frame.scaleY, MODULE_CLIP_Y) * guiScale
        return GuiBoxUtil.toBlurRegions(layoutBoxes(), guiScale).mapNotNull { box ->
            val x = maxOf(box.x, clipX)
            val y = maxOf(box.y, if (box.x >= moduleClipX - 1.0f) moduleClipTop else clipY)
            val right = minOf(box.x + box.width, clipRight)
            val bottom = minOf(box.y + box.height, clipBottom)
            val width = right - x
            val height = bottom - y
            if (width <= 0.0f || height <= 0.0f) null else BlurBox(x, y, width, height, box.radius)
        }
    }

    @JvmStatic
    fun keyPressed(key: Int, action: Int): Boolean {
        if (!renderVisible || action != GLFW.GLFW_PRESS) return false
        bindingModule?.let { module ->
            module.bind = if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) -1 else key
            bindingModule = null
            asteria.top.client.config.ClientConfig.save()
            return true
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            toggle()
            return true
        }
        return true
    }

    @JvmStatic
    fun mouseReleased(button: Int, action: Int) {
        if (action == GLFW.GLFW_RELEASE) {
            activeFloatSetting = null
            activeIntSetting = null
            activeSliderKey = null
        }
    }

    @JvmStatic
    fun mouseClicked(button: Int, action: Int): Boolean {
        if (!visible || action != GLFW.GLFW_PRESS) return false
        val mc = Minecraft.getInstance()
        val window = mc.window
        return clickAt(
            mc.mouseHandler.getScaledXPos(window).toFloat(),
            mc.mouseHandler.getScaledYPos(window).toFloat(),
            button,
        )
    }

    @JvmStatic
    fun mouseScrolled(vertical: Double): Boolean {
        if (!visible) return false
        val frame = animatedFrame()
        val maxScroll = maxModuleScroll(frame)
        if (maxScroll <= 0.0f) return true
        targetModuleScroll = (targetModuleScroll - vertical.toFloat() * SCROLL_STEP).coerceIn(0.0f, maxScroll)
        return true
    }

    @JvmStatic
    fun extract(graphics: GuiGraphicsExtractor) {
        if (!shouldRender()) return
        updateModuleScroll(animatedFrame())
        dragActiveSlider(Minecraft.getInstance().mouseHandler.getScaledXPos(Minecraft.getInstance().window).toFloat())
        val frame = animatedFrame()
        val ox = frame.x
        val oy = frame.y
        val boxes = layoutBoxes()
        boxes.asSequence()
            .filter { !it.blur && !it.isSettingsBox(ox, oy) }
            .forEach { GuiBoxUtil.draw(graphics, it) }
        ScissorUtil.withScissor(
            graphics,
            xRel(ox, frame.scaleX, SETTINGS_CLIP_X),
            yRel(oy, frame.scaleY, MODULE_CLIP_Y),
            SETTINGS_CLIP_W * frame.scaleX,
            (frame.height - MODULE_CLIP_Y * frame.scaleY - 8.0f * frame.scaleY).coerceAtLeast(0.0f),
        ) {
            boxes.asSequence()
                .filter { !it.blur && it.isSettingsBox(ox, oy) }
                .forEach { box ->
                    val clip = box.clip
                    if (clip != null) GuiBoxUtil.draw(graphics, clipBox(box, clip))
                    else GuiBoxUtil.draw(graphics, box)
                }
        }
        graphics.nextStratum()
        drawText(graphics)
    }

    private fun clickAt(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val frame = animatedFrame()
        if (!contains(mouseX, mouseY, frame.x, frame.y, frame.width, frame.height)) return false
        val ox = frame.x
        val oy = frame.y
        val scaleX = frame.scaleX
        val scaleY = frame.scaleY

        categoryOrder.forEachIndexed { index, category ->
            val y = yRel(oy, scaleY, 88.0f + index * 45.0f)
            if (contains(mouseX, mouseY, xRel(ox, scaleX, 6.0f), y, 130.0f * scaleX, 40.0f * scaleY)) {
                if (category != selectedCategory) {
                    previousCategory = selectedCategory
                    selectedCategory = category
                    collapseExpandedModules()
                    targetModuleScroll = 0.0f
                    moduleScroll = 0.0f
                    categoryAnimationStart = nowMillis()
                }
                return true
            }
        }

        if (contains(mouseX, mouseY, xRel(ox, scaleX, SETTINGS_CLIP_X), yRel(oy, scaleY, MODULE_CLIP_Y), SETTINGS_CLIP_W * scaleX, (frame.height - MODULE_CLIP_Y * scaleY - 8.0f * scaleY).coerceAtLeast(0.0f))) {
            ModuleManager.modulesIn(selectedCategory).forEachIndexed { index, module ->
                val card = moduleCardRect(index, ox, oy, scaleX, scaleY, module)
                if (contains(mouseX, mouseY, card.x, card.y, card.width, card.height)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        val openingModule = !isModuleExpanded(module)
                        if (openingModule) expandedModuleNames.add(module.name) else expandedModuleNames.remove(module.name)
                        startModuleExpansion(module.name, openingModule)
                        targetModuleScroll = targetModuleScroll.coerceIn(0.0f, maxModuleScroll(frame))
                        moduleScroll = moduleScroll.coerceIn(0.0f, targetModuleScroll)
                        return true
                    }
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        if (isModuleExpanded(module) && moduleExpandProgress(module) < 0.96f) return true
                        return clickModuleSetting(module, mouseX, mouseY, ox, oy, scaleX, scaleY)
                    }
                }
            }
        }

        return true
    }

    private fun drawText(graphics: GuiGraphicsExtractor) {
        val frame = animatedFrame()
        val ox = frame.x
        val oy = frame.y
        val scaleX = frame.scaleX
        val scaleY = frame.scaleY
        val textScale = ((scaleX + scaleY) * 0.5f).coerceAtLeast(0.75f)
        val alpha = 1.0f
        val categoryProgress = categoryProgress()
        val categorySlide = (1.0f - categoryProgress) * 8.0f * scaleX
        val logoX = xRel(ox, scaleX, 46.0f)
        val logoY = yRel(oy, scaleY, 14.0f)
        val logoW = 64.0f * textScale
        val logoH = 54.0f * textScale
        val glowSize = 116.0f * textScale
        val logoCenterX = logoX + logoW * 0.5f
        val logoCenterY = logoY + logoH * 0.5f
        TextureRenderer.draw(graphics, LOGO_GLOW_TEXTURE, logoCenterX - glowSize * 0.5f, logoCenterY - glowSize * 0.5f, glowSize, glowSize, withAlpha(ACCENT, 0.38f * alpha))
        TextureRenderer.draw(graphics, LOGO_TEXTURE, logoX, logoY, logoW, logoH, withAlpha(ACCENT, alpha))
        text(graphics, Face.SfRegular, "Asteria", xRel(ox, scaleX, 176.0f), yRel(oy, scaleY, 25.0f), 12.0f * textScale, withAlpha(TEXT_25, alpha))
        text(graphics, Face.SfRegular, ">", xRel(ox, scaleX, 234.0f), yRel(oy, scaleY, 25.0f), 12.0f * textScale, withAlpha(TEXT_10, alpha))
        text(graphics, Face.SfRegular, selectedCategory.displayName, xRel(ox, scaleX, 249.0f) + categorySlide, yRel(oy, scaleY, 25.0f), 12.0f * textScale, withAlpha(ACCENT, alpha))
        PlayerHeadRenderer.draw(graphics, xRel(ox, scaleX, 11.0f), yRel(oy, scaleY, 400.0f), 30.0f * textScale)
        text(graphics, Face.SfSemibold, playerName(), xRel(ox, scaleX, 46.0f), yRel(oy, scaleY, 403.0f), 10.0f * textScale, withAlpha(WHITE, alpha))
        text(graphics, Face.SfRegular, "Release:", xRel(ox, scaleX, 46.0f), yRel(oy, scaleY, 417.0f), 8.0f * textScale, withAlpha(TEXT_50, alpha))
        text(graphics, Face.SfRegular, " 1.0", xRel(ox, scaleX, 78.0f), yRel(oy, scaleY, 417.0f), 8.0f * textScale, withAlpha(ACCENT, alpha))

        categoryOrder.forEachIndexed { index, category ->
            val rowY = yRel(oy, scaleY, 88.0f + index * 45.0f)
            val y = rowY + 14.0f * scaleY
            val activeBlend = categoryTextBlend(category)
            drawCategoryIcon(graphics, categoryIcons.getOrElse(index) { "J" }, xRel(ox, scaleX, 17.33f), rowY + 14.0f * scaleY, activeBlend, alpha, textScale)
            text(graphics, Face.SfRegular, category.displayName, xRel(ox, scaleX, 45.0f), y, 13.2f * textScale, blendColor(TEXT_75, ACCENT, activeBlend, alpha))
        }

        ScissorUtil.withScissor(
            graphics,
            xRel(ox, scaleX, SETTINGS_CLIP_X),
            yRel(oy, scaleY, MODULE_CLIP_Y),
            SETTINGS_CLIP_W * scaleX,
            (frame.height - MODULE_CLIP_Y * scaleY - 8.0f * scaleY).coerceAtLeast(0.0f),
        ) {
            drawModuleCardText(graphics, ox, oy, scaleX, scaleY, textScale, alpha)
        }
    }

    private fun layoutBoxes(): List<GuiBoxUtil.Box> {
        val frame = animatedFrame()
        val ox = frame.x
        val oy = frame.y
        val scaleX = frame.scaleX
        val scaleY = frame.scaleY
        val radiusScale = ((scaleX + scaleY) * 0.5f).coerceAtLeast(0.75f)
        val alpha = 1.0f
        val boxes = mutableListOf<GuiBoxUtil.Box>()
        AsteriaGuiRenderer.panel(boxes, ox, oy, frame.width, frame.height, withAlpha(WINDOW_SOFT, alpha))
        AsteriaGuiRenderer.panel(boxes, ox, oy, frame.width, frame.height, withAlpha(WINDOW, alpha))
        ModuleManager.modulesIn(selectedCategory).forEachIndexed { index, module ->
            val rect = moduleSlotRect(index, ox, oy, scaleX, scaleY)
            val expandProgress = moduleExpandProgress(module)
            val cardHeight = moduleCardHeight(module) * scaleY
            AsteriaGuiRenderer.blurRect(
                boxes,
                rect.x,
                rect.y,
                rect.width,
                cardHeight,
                MODULE_CARD_RADIUS * radiusScale,
                withAlpha(0xFF0B0B0B.toInt(), alpha),
            )
            AsteriaGuiRenderer.outlineRect(
                boxes,
                rect.x + MODULE_CARD_OUTLINE_INSET * scaleX,
                rect.y + MODULE_CARD_OUTLINE_INSET * scaleY,
                rect.width - MODULE_CARD_OUTLINE_INSET * 2.0f * scaleX,
                cardHeight - MODULE_CARD_OUTLINE_INSET * 2.0f * scaleY,
                MODULE_CARD_OUTLINE_RADIUS * radiusScale,
                withAlpha(MODULE_CARD_OUTLINE_TINT, alpha),
            )
            drawModuleIndicatorBoxes(
                boxes,
                module,
                rect.x + 216.0f * scaleX,
                rect.y + 13.0f * scaleY,
                ControlGeometry.MODULE_SWITCH_WIDTH * scaleX,
                ControlGeometry.MODULE_SWITCH_HEIGHT * scaleY,
                radiusScale,
                alpha,
            )
            if (expandProgress > 0.01f) {
                layoutSettingBoxes(boxes, module, ox, oy, scaleX, scaleY, radiusScale, alpha)
            }
        }
        AsteriaGuiRenderer.rect(boxes, xRel(ox, scaleX, 11.0f), yRel(oy, scaleY, 400.0f), 30.0f * scaleX, 30.0f * scaleY, 8.0f * radiusScale, withAlpha(CONTENT, alpha))

        val highlightY = categoryHighlightY(oy, scaleY)
        if (highlightY.isFinite()) {
            AsteriaGuiRenderer.rect(boxes, xRel(ox, scaleX, 6.0f), highlightY, 130.0f * scaleX, 40.0f * scaleY, 9.0f * radiusScale, withAlpha(0x0D88FF82, alpha))
        }
        return boxes
    }

    private fun GuiBoxUtil.Box.isSettingsBox(ox: Float, _oy: Float): Boolean {
        return x >= ox + SETTINGS_CLIP_X - 1.0f
    }

    private fun clipBox(box: GuiBoxUtil.Box, clip: GuiBoxUtil.Clip): GuiBoxUtil.Box {
        val x = maxOf(box.x, clip.x)
        val y = maxOf(box.y, clip.y)
        val right = minOf(box.x + box.width, clip.right)
        val bottom = minOf(box.y + box.height, clip.bottom)
        return box.copy(
            x = x,
            y = y,
            width = (right - x).coerceAtLeast(0.0f),
            height = (bottom - y).coerceAtLeast(0.0f),
            clip = null,
        )
    }

    private fun moduleSlotRect(index: Int, ox: Float, oy: Float, scaleX: Float, scaleY: Float): Rect {
        val col = index % 2
        val row = index / 2
        val columnOffset = expandedOffsetBeforeIndex(index)
        val x = xRel(ox, scaleX, MODULE_CARD_X + col * (MODULE_CARD_W + MODULE_CARD_COL_GAP))
        val y = yRel(oy, scaleY, MODULE_CARD_Y + row * (MODULE_CARD_COLLAPSED_H + MODULE_CARD_ROW_GAP) + columnOffset - moduleScroll)
        return Rect(x, y, MODULE_CARD_W * scaleX, MODULE_CARD_COLLAPSED_H * scaleY)
    }

    private fun moduleCardRect(index: Int, ox: Float, oy: Float, scaleX: Float, scaleY: Float, module: asteria.top.client.module.Module): Rect {
        val slot = moduleSlotRect(index, ox, oy, scaleX, scaleY)
        return slot.copy(height = moduleCardHeight(module) * scaleY)
    }

    private fun expandedOffsetBeforeIndex(index: Int): Float {
        if (index < 2) return 0.0f
        val modules = ModuleManager.modulesIn(selectedCategory)
        var offset = 0.0f
        var previousIndex = index - 2
        while (previousIndex >= 0) {
            offset += moduleCardHeight(modules.getOrNull(previousIndex)) - MODULE_CARD_COLLAPSED_H
            previousIndex -= 2
        }
        return offset
    }

    private fun updateModuleScroll(frame: Frame) {
        val maxScroll = maxModuleScroll(frame)
        targetModuleScroll = targetModuleScroll.coerceIn(0.0f, maxScroll)
        moduleScroll = (moduleScroll + (targetModuleScroll - moduleScroll) * 0.24f).coerceIn(0.0f, maxScroll)
        if (kotlin.math.abs(moduleScroll - targetModuleScroll) < 0.05f) moduleScroll = targetModuleScroll
    }

    private fun maxModuleScroll(frame: Frame): Float {
        val visibleHeight = (frame.height / frame.scaleY.coerceAtLeast(0.001f) - MODULE_CARD_Y - 8.0f).coerceAtLeast(0.0f)
        return (moduleContentHeight() - visibleHeight).coerceAtLeast(0.0f)
    }

    private fun moduleContentHeight(): Float {
        val modules = ModuleManager.modulesIn(selectedCategory)
        if (modules.isEmpty()) return 0.0f
        var bottom = 0.0f
        modules.forEachIndexed { index, module ->
            val row = index / 2
            val top = row * (MODULE_CARD_COLLAPSED_H + MODULE_CARD_ROW_GAP) + expandedOffsetBeforeIndex(index)
            bottom = maxOf(bottom, top + moduleCardHeight(module))
        }
        return bottom
    }

    private fun drawCategoryIcon(graphics: GuiGraphicsExtractor, icon: String, x: Float, y: Float, activeBlend: Float, alpha: Float, scale: Float) {
        text(graphics, Face.ClickGuiIcon, icon, x, y, 13.6f * scale, blendColor(TEXT_75, ACCENT, activeBlend, alpha))
    }

    private fun drawModuleCardText(graphics: GuiGraphicsExtractor, ox: Float, oy: Float, scaleX: Float, scaleY: Float, textScale: Float, alpha: Float) {
        ModuleManager.modulesIn(selectedCategory).forEachIndexed { index, module ->
            val rect = moduleSlotRect(index, ox, oy, scaleX, scaleY)
            text(graphics, Face.SfMedium, module.name, rect.x + 9.0f * scaleX, rect.y + 9.0f * scaleY, 15.0f * textScale, withAlpha(WHITE, alpha))
            val cardHeight = moduleCardHeight(module) * scaleY
            if (moduleExpandProgress(module) > 0.01f) {
                val parentX = ox + SETTINGS_CLIP_X * scaleX
                val parentY = oy + MODULE_CLIP_Y * scaleY
                val parentRight = parentX + SETTINGS_CLIP_W * scaleX
                val parentBottom = oy + HEIGHT * scaleY - 8.0f * scaleY
                val clipX = maxOf(rect.x, parentX)
                val clipY = parentY
                val clipRight = minOf(rect.x + rect.width, parentRight)
                val clipBottom = minOf(rect.y + cardHeight, parentBottom)
                drawSettingText(graphics, module, rect.x, rect.y, scaleX, scaleY, textScale, alpha, clipBottom)
            }
        }
    }

    private fun moduleCardHeight(module: asteria.top.client.module.Module?): Float {
        if (module == null) return MODULE_CARD_COLLAPSED_H
        return lerp(MODULE_CARD_COLLAPSED_H, expandedLayout(module).height, moduleExpandProgress(module))
    }

    private fun isModuleExpanded(module: asteria.top.client.module.Module): Boolean {
        return module.name in expandedModuleNames
    }

    private fun collapseExpandedModules() {
        expandedModuleNames.forEach { moduleName ->
            startModuleExpansion(moduleName, false)
        }
        expandedModuleNames.clear()
        expandedSettingGroups.clear()
    }

    private fun startModuleExpansion(moduleName: String, opening: Boolean) {
        val current = moduleExpansions[moduleName]?.let { moduleExpandProgress(moduleName) }
            ?: if (opening) 0.0f else 1.0f
        moduleExpansions[moduleName] = ModuleExpansion(current, if (opening) 1.0f else 0.0f, nowMillis())
    }

    private fun moduleExpandProgress(module: asteria.top.client.module.Module): Float {
        return moduleExpandProgress(module.name)
    }

    private fun moduleExpandProgress(moduleName: String): Float {
        val state = moduleExpansions[moduleName] ?: return if (moduleName in expandedModuleNames) 1.0f else 0.0f
        val duration = if (state.target > state.from) MODULE_OPEN_MS else MODULE_CLOSE_MS
        val linear = ((nowMillis() - state.startedAt).toFloat() / duration).coerceIn(0.0f, 1.0f)
        val eased = if (state.target > state.from) {
            AnimationUtil.easeOutSoftBack(linear)
        } else {
            AnimationUtil.easeInSine(linear)
        }
        return (state.from + (state.target - state.from) * eased).coerceIn(0.0f, 1.0f)
    }

    private fun expansionIcon(expanded: Boolean): String = if (expanded) "D" else "C"

    private fun drawModuleIndicatorBoxes(boxes: MutableList<GuiBoxUtil.Box>, module: asteria.top.client.module.Module, x: Float, y: Float, width: Float, height: Float, radiusScale: Float, alpha: Float) {
        val progress = animatedValue("module:${module.name}:enabled", if (module.enabled) 1.0f else 0.0f)
        drawAnimatedSwitchBoxes(boxes, Rect(x, y, width, height), progress, radiusScale, alpha)
    }

    private fun layoutSettingBoxes(boxes: MutableList<GuiBoxUtil.Box>, module: asteria.top.client.module.Module, ox: Float, oy: Float, scaleX: Float, scaleY: Float, radiusScale: Float, alpha: Float) {
        val firstSettingBox = boxes.size
        val moduleIndex = ModuleManager.modulesIn(selectedCategory).indexOf(module).coerceAtLeast(0)
        val slot = moduleSlotRect(moduleIndex, ox, oy, scaleX, scaleY)
        val x = slot.x
        val y = slot.y
        expandedLayout(module).items.forEach { item ->
            when (val setting = item.setting) {
                is FloatSetting, is IntSetting -> item.slider?.let { slider ->
                    val sliderKey = controlKey(module, setting, "slider")
                    drawSliderBoxes(
                        boxes,
                        absoluteRect(slider, x, y, scaleX, scaleY),
                        animatedValue(sliderKey, sliderVisualPercent(sliderKey, setting)),
                        animatedValue(controlKey(module, setting, "slider-held"), if (setting === activeFloatSetting || setting === activeIntSetting) 1.0f else 0.0f),
                        radiusScale,
                        alpha,
                    )
                }
                is BooleanSetting -> item.switchRect?.let { switchRect ->
                    val rect = absoluteRect(switchRect, x, y, scaleX, scaleY)
                    drawAnimatedSwitchBoxes(boxes, rect, animatedValue(controlKey(module, setting, "switch"), if (setting.value) 1.0f else 0.0f), radiusScale, alpha)
                }
                is MultiBooleanSetting -> item.chips.firstOrNull()?.let { headerChip ->
                    val header = absoluteRect(headerChip.rect, x, y, scaleX, scaleY)
                    val dropdownHeight = header.height + setting.value.size * 27.0f * item.expansion * scaleY
                    AsteriaGuiRenderer.blurRect(boxes, header.x, header.y, header.width, dropdownHeight, 8.0f * radiusScale, withAlpha(0xFF0B0B0B.toInt(), alpha))
                    AsteriaGuiRenderer.outlineRect(boxes, header.x, header.y, header.width, dropdownHeight, 8.0f * radiusScale, withAlpha(DROPDOWN_OUTLINE_TINT, alpha))
                    item.chips.drop(1).forEach { chip ->
                        val option = setting.value.getOrNull(chip.index) ?: return@forEach
                        chip.switchRect?.let { switchRect ->
                            val rect = absoluteRect(switchRect, x, y, scaleX, scaleY)
                            drawAnimatedSwitchBoxes(boxes, rect, animatedValue(controlKey(module, option, "switch"), if (option.value) 1.0f else 0.0f), radiusScale, alpha * item.expansion)
                        }
                    }
                }
                is EnumSetting<*> -> item.chips.firstOrNull()?.let { chip ->
                    val header = absoluteRect(chip.rect, x, y, scaleX, scaleY)
                    val dropdownHeight = header.height + setting.optionCount() * 27.0f * item.expansion * scaleY
                    AsteriaGuiRenderer.blurRect(boxes, header.x, header.y, header.width, dropdownHeight, 8.0f * radiusScale, withAlpha(0xFF0B0B0B.toInt(), alpha))
                    AsteriaGuiRenderer.outlineRect(boxes, header.x, header.y, header.width, dropdownHeight, 8.0f * radiusScale, withAlpha(DROPDOWN_OUTLINE_TINT, alpha))
                }
            }
        }
        val bindRect = Rect(x + 9.0f * scaleX, y + 41.0f * scaleY, 242.0f * scaleX, 28.0f * scaleY)
        AsteriaGuiRenderer.blurRect(boxes, bindRect.x, bindRect.y, bindRect.width, bindRect.height, 8.0f * radiusScale, withAlpha(0xFF0B0B0B.toInt(), alpha))
        AsteriaGuiRenderer.outlineRect(boxes, bindRect.x, bindRect.y, bindRect.width, bindRect.height, 8.0f * radiusScale, withAlpha(DROPDOWN_OUTLINE_TINT, alpha))
        val clip = GuiBoxUtil.Clip(
            x,
            yRel(oy, scaleY, MODULE_CLIP_Y),
            x + slot.width,
            y + moduleCardHeight(module) * scaleY,
        )
        for (index in firstSettingBox until boxes.size) {
            boxes[index] = boxes[index].copy(clip = clip)
        }
    }

    private fun drawSettingText(graphics: GuiGraphicsExtractor, module: asteria.top.client.module.Module, x: Float, y: Float, scaleX: Float, scaleY: Float, textScale: Float, alpha: Float, clipBottom: Float) {
        if (y + 48.0f * scaleY < clipBottom) {
            text(graphics, Face.SfRegular, "Bind: ${bindName(module.bind)}", x + 18.0f * scaleX, y + 48.0f * scaleY, 11.6f * textScale, withAlpha(if (bindingModule === module) ACCENT else WHITE, alpha))
        }
        expandedLayout(module).items.forEach { item ->
            val labelY = y + item.y * scaleY
            if (labelY >= clipBottom) return@forEach
            when (val setting = item.setting) {
                is FloatSetting, is IntSetting -> {
                    text(graphics, Face.SfRegular, setting.name, x + 9.0f * scaleX, labelY, 12.0f * textScale, withAlpha(WHITE, alpha))
                    textRight(graphics, setting.displayValue(), x + 242.0f * scaleX, labelY, 12.0f * textScale, withAlpha(ACCENT, alpha))
                }
                is BooleanSetting -> {
                    text(graphics, Face.SfRegular, setting.name, x + 9.0f * scaleX, labelY, 11.6f * textScale, withAlpha(WHITE, alpha))
                    text(graphics, Face.SfRegular, booleanDescription(setting.name), x + 9.0f * scaleX, labelY + 15.0f * scaleY, 9.4f * textScale, withAlpha(TEXT_50, alpha))
                }
                is MultiBooleanSetting -> {
                    text(graphics, Face.SfRegular, setting.name, x + 9.0f * scaleX, labelY, 11.6f * textScale, withAlpha(WHITE, alpha))
                    item.chips.firstOrNull()?.let { chip ->
                        val rect = absoluteRect(chip.rect, x, y, scaleX, scaleY)
                        val selected = setting.enabledNames().joinToString(", ").ifEmpty { "None" }
                        text(graphics, Face.SfRegular, selected, rect.x + 9.0f * scaleX, rect.y + 7.0f * scaleY, 10.8f * textScale, withAlpha(WHITE, alpha))
                        text(graphics, Face.ClickGuiIcon, expansionIcon(isSettingGroupExpanded(module, setting)), rect.x + rect.width - 17.0f * scaleX, rect.y + 7.0f * scaleY, 9.5f * textScale, withAlpha(TEXT_50, alpha))
                    }
                    if (item.expansion >= 0.96f) {
                        item.chips.drop(1).forEach { chip ->
                            val option = setting.value.getOrNull(chip.index) ?: return@forEach
                            val rect = absoluteRect(chip.rect, x, y, scaleX, scaleY)
                            text(graphics, Face.SfRegular, option.name, rect.x + 9.0f * scaleX, rect.y + 5.0f * scaleY, 11.2f * textScale, withAlpha(WHITE, alpha * item.expansion))
                        }
                    }
                }
                is EnumSetting<*> -> {
                    text(graphics, Face.SfRegular, setting.name, x + 9.0f * scaleX, labelY, 11.6f * textScale, withAlpha(WHITE, alpha))
                    item.chips.firstOrNull()?.let { chip ->
                        val rect = absoluteRect(chip.rect, x, y, scaleX, scaleY)
                        text(graphics, Face.SfRegular, setting.displayValue(), rect.x + 9.0f * scaleX, rect.y + 7.0f * scaleY, 10.8f * textScale, withAlpha(WHITE, alpha))
                        text(graphics, Face.ClickGuiIcon, expansionIcon(isSettingGroupExpanded(module, setting)), rect.x + rect.width - 17.0f * scaleX, rect.y + 7.0f * scaleY, 9.5f * textScale, withAlpha(TEXT_50, alpha))
                    }
                    if (item.expansion > 0.01f) {
                        item.chips.drop(1).forEach { chip ->
                            val rect = absoluteRect(chip.rect, x, y, scaleX, scaleY)
                            text(graphics, Face.SfRegular, chip.label, rect.x + 9.0f * scaleX, rect.y + 5.0f * scaleY, 10.8f * textScale, withAlpha(if (chip.label == setting.displayValue()) ACCENT else WHITE, alpha * item.expansion))
                        }
                    }
                }
            }
        }
    }

    private fun clickModuleSetting(module: asteria.top.client.module.Module, mouseX: Float, mouseY: Float, ox: Float, oy: Float, scaleX: Float, scaleY: Float): Boolean {
        val moduleIndex = ModuleManager.modulesIn(selectedCategory).indexOf(module).coerceAtLeast(0)
        val slot = moduleSlotRect(moduleIndex, ox, oy, scaleX, scaleY)
        val x = slot.x
        val y = slot.y
        if (isModuleExpanded(module)) {
            val switchX = x + 216.0f * scaleX
            val switchY = y + 13.0f * scaleY
            val switchW = ControlGeometry.MODULE_SWITCH_WIDTH * scaleX
            val switchH = ControlGeometry.MODULE_SWITCH_HEIGHT * scaleY
            if (contains(mouseX, mouseY, switchX, switchY, switchW, switchH)) {
                module.toggle()
                return true
            }
        } else {
            module.toggle()
            return true
        }
        if (contains(mouseX, mouseY, x + 9.0f * scaleX, y + 41.0f * scaleY, 242.0f * scaleX, 28.0f * scaleY)) {
            bindingModule = module
            return true
        }
        expandedLayout(module).items.forEach { item ->
            when (val setting = item.setting) {
                is FloatSetting, is IntSetting -> item.slider?.let { slider ->
                    val rect = absoluteRect(slider, x, y, scaleX, scaleY)
                    if (contains(mouseX, mouseY, rect.x, rect.y - 7.0f * scaleY, rect.width, 18.0f * scaleY)) {
                        val sliderKey = controlKey(module, setting, "slider")
                        val percent = ((mouseX - rect.x) / rect.width).coerceIn(0.0f, 1.0f)
                        sliderVisualPercents[sliderKey] = percent
                        setNumericPercent(setting, percent)
                        activeFloatSetting = setting as? FloatSetting
                        activeIntSetting = setting as? IntSetting
                        activeSliderKey = sliderKey
                        activeSliderX = rect.x
                        activeSliderW = rect.width
                        return true
                    }
                }
                is BooleanSetting -> item.switchRect?.let { switchRect ->
                    val rect = absoluteRect(switchRect, x, y, scaleX, scaleY)
                    if (contains(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height)) {
                        setting.adjust(1)
                        return true
                    }
                }
                is MultiBooleanSetting -> {
                    val header = item.chips.firstOrNull()?.let { absoluteRect(it.rect, x, y, scaleX, scaleY) } ?: return@forEach
                    if (contains(mouseX, mouseY, header.x, header.y, header.width, header.height)) {
                        toggleSettingGroup(module, setting)
                        return true
                    }
                    if (item.expansion > 0.01f) {
                        item.chips.drop(1).forEach { chip ->
                            val rect = absoluteRect(chip.rect, x, y, scaleX, scaleY)
                            val switchRect = chip.switchRect?.let { absoluteRect(it, x, y, scaleX, scaleY) }
                            if (contains(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height) ||
                                (switchRect != null && contains(mouseX, mouseY, switchRect.x, switchRect.y, switchRect.width, switchRect.height))
                            ) {
                                setting.value.getOrNull(chip.index)?.adjust(1)
                                return true
                            }
                        }
                    }
                }
                is EnumSetting<*> -> item.chips.forEachIndexed { chipIndex, chip ->
                    if (chipIndex > 0 && item.expansion < 0.96f) return@forEachIndexed
                    val rect = absoluteRect(chip.rect, x, y, scaleX, scaleY)
                    if (contains(mouseX, mouseY, rect.x, rect.y, rect.width, rect.height)) {
                        if (chipIndex == 0) {
                            toggleSettingGroup(module, setting)
                        } else {
                            setting.selectIndex(chip.index)
                            toggleSettingGroup(module, setting)
                        }
                        return true
                    }
                }
            }
        }
        return true
    }

    private fun expandedLayout(module: asteria.top.client.module.Module): ExpandedLayout {
        val items = mutableListOf<SettingItem>()
        var cursor = 79.0f
        module.settings.filter { it.isVisible() }.forEach { setting ->
            when (setting) {
                is FloatSetting, is IntSetting -> {
                    items += SettingItem(setting, cursor, 34.0f, slider = Rect(9.0f, cursor + 19.0f, 233.0f, 5.5f))
                    cursor += 34.0f
                }
                is BooleanSetting -> {
                    items += SettingItem(
                        setting,
                        cursor + 1.0f,
                        39.0f,
                        switchRect = Rect(214.0f, cursor + 7.0f, ControlGeometry.SETTING_SWITCH_WIDTH, ControlGeometry.SETTING_SWITCH_HEIGHT),
                    )
                    cursor += 39.0f
                }
                is MultiBooleanSetting -> {
                    val expansion = dropdownExpandProgress(module, setting)
                    val headerY = cursor + 18.0f
                    val chips = mutableListOf(ChipItem(-1, setting.name, Rect(9.0f, headerY, 242.0f, 28.0f)))
                    setting.value.forEachIndexed { index, option ->
                        val rowY = headerY + 28.0f + index * 27.0f * expansion
                        chips += ChipItem(index, option.name, Rect(9.0f, rowY, 242.0f, 27.0f * expansion), Rect(217.0f, rowY + 6.0f * expansion, ControlGeometry.SETTING_SWITCH_WIDTH, ControlGeometry.SETTING_SWITCH_HEIGHT))
                    }
                    val height = 48.0f + setting.value.size * 27.0f * expansion
                    items += SettingItem(setting, cursor, height, chips = chips, expansion = expansion)
                    cursor += height + 2.0f
                }
                is EnumSetting<*> -> {
                    val expansion = dropdownExpandProgress(module, setting)
                    val headerY = cursor + 18.0f
                    val chips = mutableListOf(ChipItem(-1, setting.name, Rect(9.0f, headerY, 242.0f, 28.0f)))
                    repeat(setting.optionCount()) { index ->
                        val rowY = headerY + 28.0f + index * 27.0f * expansion
                        chips += ChipItem(index, setting.optionDisplay(index), Rect(9.0f, rowY, 242.0f, 27.0f * expansion))
                    }
                    val height = 48.0f + setting.optionCount() * 27.0f * expansion
                    items += SettingItem(setting, cursor, height, chips = chips, expansion = expansion)
                    cursor += height + 2.0f
                }
            }
        }
        return ExpandedLayout(cursor + 8.0f, items)
    }

    private fun settingGroupKey(module: asteria.top.client.module.Module, setting: ModuleSetting<*>): String {
        return "${module.category.name}:${module.name}:${setting.name}:dropdown"
    }

    private fun isSettingGroupExpanded(module: asteria.top.client.module.Module, setting: ModuleSetting<*>): Boolean {
        return settingGroupKey(module, setting) in expandedSettingGroups
    }

    private fun toggleSettingGroup(module: asteria.top.client.module.Module, setting: ModuleSetting<*>) {
        val key = settingGroupKey(module, setting)
        val opening = key !in expandedSettingGroups
        if (opening) expandedSettingGroups.add(key) else expandedSettingGroups.remove(key)
        val current = dropdownExpansions[key]?.let { dropdownExpandProgress(module, setting) }
            ?: if (opening) 0.0f else 1.0f
        dropdownExpansions[key] = DropdownExpansion(current, if (opening) 1.0f else 0.0f, nowMillis())
    }

    private fun dropdownExpandProgress(module: asteria.top.client.module.Module, setting: ModuleSetting<*>): Float {
        val key = settingGroupKey(module, setting)
        val state = dropdownExpansions[key] ?: return if (key in expandedSettingGroups) 1.0f else 0.0f
        val duration = if (state.target > state.from) DROPDOWN_OPEN_MS else DROPDOWN_CLOSE_MS
        val linear = ((nowMillis() - state.startedAt).toFloat() / duration).coerceIn(0.0f, 1.0f)
        val eased = if (state.target > state.from) AnimationUtil.easeOutSoftBack(linear) else AnimationUtil.easeInSine(linear)
        return (state.from + (state.target - state.from) * eased).coerceIn(0.0f, 1.0f)
    }

    private fun booleanDescription(name: String): String {
        return when (name.lowercase()) {
            "players" -> "Attack other players"
            "mobs" -> "Attack hostile mobs"
            "animals" -> "Attack passive animals"
            "only crits" -> "Only attack on critical hits"
            "smart crits" -> "Avoid unsafe critical timing"
            "raytrace" -> "Require visible attack trace"
            "shield break" -> "Swap to break shields"
            "always shield" -> "Prefer shield handling"
            "ignore walls" -> "Allow targets behind blocks"
            "no attack if eat" -> "Pause attacks while eating"
            "targeting" -> "Enable combat targeting"
            "silent swap" -> "Doesn't queue swap packets"
            "auto jump" -> "Jumps after using wind charge"
            else -> "Toggle ${name.lowercase()}"
        }
    }

    private fun absoluteRect(rect: Rect, x: Float, y: Float, scaleX: Float, scaleY: Float): Rect {
        return Rect(x + rect.x * scaleX, y + rect.y * scaleY, rect.width * scaleX, rect.height * scaleY)
    }

    private fun drawSliderBoxes(boxes: MutableList<GuiBoxUtil.Box>, rect: Rect, percent: Float, held: Float, radiusScale: Float, alpha: Float) {
        val pct = percent.coerceIn(0.0f, 1.0f)
        val hold = AnimationUtil.clamp01(held)
        val thumb = (9.5f + 1.5f * hold) * radiusScale
        val thumbRadius = thumb * 0.5f
        val thumbCenterX = (rect.x + rect.width * pct).coerceIn(rect.x + thumbRadius, rect.x + rect.width - thumbRadius)
        val thumbCenterY = rect.y + rect.height * 0.5f
        val fillW = (rect.width * pct).coerceAtLeast(rect.height)
        AsteriaGuiRenderer.pill(boxes, rect.x, rect.y, rect.width, rect.height, withAlpha(0xFF2D4D2B.toInt(), alpha))
        AsteriaGuiRenderer.pill(boxes, rect.x, rect.y, fillW, rect.height, withAlpha(ACCENT, alpha))
        AsteriaGuiRenderer.circle(boxes, thumbCenterX, thumbCenterY, thumbRadius, withAlpha(WHITE, alpha))
    }

    private fun drawAnimatedSwitchBoxes(boxes: MutableList<GuiBoxUtil.Box>, rect: Rect, progress: Float, radiusScale: Float, alpha: Float) {
        val t = AnimationUtil.clamp01(progress)
        val inset = 2.0f * radiusScale
        val knob = (rect.height - inset * 2.0f).coerceAtLeast(7.0f * radiusScale)
        val knobRadius = knob * 0.5f
        val knobCenterX = rect.x + inset + knobRadius + (rect.width - knob - inset * 2.0f) * t
        val knobCenterY = rect.y + rect.height * 0.5f
        AsteriaGuiRenderer.pill(boxes, rect.x, rect.y, rect.width, rect.height, blendColor(0xFF1D1D1D.toInt(), ACCENT, t, alpha))
        AsteriaGuiRenderer.circle(boxes, knobCenterX + 0.45f * radiusScale, knobCenterY + 0.85f * radiusScale, knobRadius, withAlpha(0x33000000, alpha))
        AsteriaGuiRenderer.circle(boxes, knobCenterX, knobCenterY, knobRadius, withAlpha(WHITE, alpha))
    }

    private fun controlKey(module: asteria.top.client.module.Module, setting: ModuleSetting<*>, suffix: String): String {
        return "${module.category.name}:${module.name}:${setting.name}:$suffix"
    }

    private fun animatedValue(key: String, target: Float): Float {
        val current = animatedControls[key]
        if (current == null) {
            val initial = AnimationUtil.clamp01(target)
            animatedControls[key] = initial
            return initial
        }
        val next = current + (AnimationUtil.clamp01(target) - current) * CONTROL_ANIMATION_SPEED
        animatedControls[key] = next
        return next
    }

    private fun numericPercent(setting: ModuleSetting<*>): Float {
        return when (setting) {
            is FloatSetting -> (setting.value - setting.min) / (setting.max - setting.min)
            is IntSetting -> (setting.value - setting.min).toFloat() / (setting.max - setting.min).toFloat()
            else -> 0.0f
        }
    }

    private fun sliderVisualPercent(key: String, setting: ModuleSetting<*>): Float {
        return if (key == activeSliderKey) {
            sliderVisualPercents[key] ?: numericPercent(setting)
        } else {
            numericPercent(setting)
        }
    }

    private fun setNumericPercent(setting: ModuleSetting<*>, percent: Float) {
        when (setting) {
            is FloatSetting -> setting.setByPercent(percent)
            is IntSetting -> setting.setByPercent(percent)
        }
    }

    private fun dragActiveSlider(mouseX: Float) {
        val percent = ((mouseX - activeSliderX) / activeSliderW).coerceIn(0.0f, 1.0f)
        activeSliderKey?.let { sliderVisualPercents[it] = percent }
        activeFloatSetting?.setByPercent(percent)
        activeIntSetting?.setByPercent(percent)
    }

    private fun playerName(): String {
        val mc = Minecraft.getInstance()
        return mc.player?.name?.string ?: "Player"
    }

    private fun bindName(bind: Int): String {
        if (bind < 0) return "None"
        return GLFW.glfwGetKeyName(bind, 0)?.uppercase() ?: "Key $bind"
    }

    private fun origin(): Pair<Float, Float> {
        val window = Minecraft.getInstance().window
        return ((window.guiScaledWidth - WIDTH) * 0.5f) to ((window.guiScaledHeight - HEIGHT) * 0.5f)
    }

    private fun animatedFrame(): Frame {
        val (x, y) = origin()
        val progress = openProgress()
        val width = BoxAnimationUtil.size(COLLAPSED_WIDTH, WIDTH, progress, GUI_ANIMATION_MODE)
        val height = BoxAnimationUtil.size(COLLAPSED_HEIGHT, HEIGHT, progress, GUI_ANIMATION_MODE)
        val left = x + (WIDTH - width) * 0.5f
        return Frame(left, y, width, height, width / WIDTH, height / HEIGHT)
    }

    private fun openProgress(): Float {
        val linear = BoxAnimationUtil.progress(openAnimationStart.toDouble(), OPEN_ANIMATION_MS.toFloat())
        return if (opening) linear else 1.0f - linear
    }

    private fun updateRenderVisibility() {
        if (visible) {
            renderVisible = true
            return
        }
        if (renderVisible && nowMillis() - openAnimationStart >= OPEN_ANIMATION_MS) {
            renderVisible = false
        }
    }

    private fun categoryProgress(): Float {
        return AnimationUtil.apply(GUI_ANIMATION_MODE, BoxAnimationUtil.progress(categoryAnimationStart.toDouble(), CATEGORY_ANIMATION_MS.toFloat()))
    }

    private fun categoryTextBlend(category: ModuleCategory): Float {
        val progress = categoryProgress()
        return when (category) {
            selectedCategory -> progress
            previousCategory -> 1.0f - progress
            else -> 0.0f
        }
    }

    private fun categoryHighlightY(oy: Float, scale: Float): Float {
        val from = categoryOrder.indexOf(previousCategory)
        val to = categoryOrder.indexOf(selectedCategory)
        if (to < 0) return Float.NaN
        val progress = categoryProgress()
        val fromY = yRel(oy, scale, 90.0f + (if (from >= 0) from else to) * 45.0f)
        val toY = yRel(oy, scale, 90.0f + to * 45.0f)
        return lerp(fromY, toY, progress)
    }

    private fun withAlpha(color: Int, alphaMultiplier: Float): Int {
        val baseAlpha = (color ushr 24) and 0xFF
        val alpha = (baseAlpha * AnimationUtil.clamp01(alphaMultiplier)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun blendColor(from: Int, to: Int, progress: Float, alphaMultiplier: Float): Int {
        val t = AnimationUtil.clamp01(progress)
        val a = lerp(((from ushr 24) and 0xFF).toFloat(), ((to ushr 24) and 0xFF).toFloat(), t)
        val r = lerp(((from ushr 16) and 0xFF).toFloat(), ((to ushr 16) and 0xFF).toFloat(), t)
        val g = lerp(((from ushr 8) and 0xFF).toFloat(), ((to ushr 8) and 0xFF).toFloat(), t)
        val b = lerp((from and 0xFF).toFloat(), (to and 0xFF).toFloat(), t)
        val alpha = (a * AnimationUtil.clamp01(alphaMultiplier)).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
    }

    private fun lerp(from: Float, to: Float, progress: Float): Float {
        return from + (to - from) * AnimationUtil.clamp01(progress)
    }

    private fun nowMillis(): Long = System.nanoTime() / 1_000_000L

    private fun xRel(ox: Float, scale: Float, x: Float): Float = ox + x * scale

    private fun yRel(oy: Float, scale: Float, y: Float): Float = oy + y * scale

    private fun text(graphics: GuiGraphicsExtractor, face: Face, text: String, x: Float, y: Float, size: Float, color: Int) {
        FontRenderer.draw(graphics, face, text, x, y, size, color)
    }

    private fun textRight(graphics: GuiGraphicsExtractor, text: String, right: Float, y: Float, size: Float, color: Int) {
        FontRenderer.draw(graphics, Face.SfRegular, text, right - FontRenderer.width(Face.SfRegular, text, size), y, size, color)
    }

    private fun contains(mx: Float, my: Float, x: Float, y: Float, w: Float, h: Float): Boolean {
        return mx >= x && mx <= x + w && my >= y && my <= y + h
    }

}
