package asteria.top.client.gui

import asteria.top.client.config.ClientConfig
import asteria.top.client.module.Module
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
import asteria.top.client.render.MsdfIconRenderer
import asteria.top.client.render.PlayerHeadRenderer
import asteria.top.client.util.GuiBoxUtil
import asteria.top.client.util.GuiShapeUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object AsteriaClickGui {
    const val PANEL_CORNER_RADIUS = 17.0f

    private const val DESIGN_WIDTH = 794.5f
    private const val DESIGN_HEIGHT = 410.0f
    private const val UI_SCALE = 0.90f
    private const val CONTENT_INSET_X = 3.5f
    private const val PANEL_WIDTH = 147.5f
    private const val PANEL_HEIGHT = 322.0f
    private const val PANEL_GAP = 12.5f
    private const val PANEL_PADDING = 8.5f
    private const val HEADER_HEIGHT = 35.0f
    private const val ROW_HEIGHT = 25.5f
    private const val ROW_GAP = 4.5f
    private const val ROW_STEP = ROW_HEIGHT + ROW_GAP
    private const val MODULE_ROW_RADIUS = 8.5f
    private const val PROFILE_WIDTH = 97.5f
    private const val PROFILE_HEIGHT = 41.5f
    private const val PROFILE_RADIUS = 14.5f
    // Figma/CSS proportions: 211 px popup against a 295 px category panel.
    // Design coordinates are half of the exported CSS dimensions.
    private const val POPUP_WIDTH = 105.5f
    private const val POPUP_HEIGHT = 155.0f
    private const val POPUP_CONTENT_PADDING_Y = 6.0f
    private const val POPUP_ROW_HEIGHT = 18.5f
    private const val POPUP_ENUM_ROW_HEIGHT = 22.0f
    private const val POPUP_NUMERIC_ROW_HEIGHT = 25.0f
    private const val POPUP_OPTION_ROW_HEIGHT = 16.5f
    private const val POPUP_ROW_GAP = 4.0f
    private const val POPUP_HORIZONTAL_PADDING = 9.5f
    private const val POPUP_ENUM_HORIZONTAL_PADDING = 5.5f
    private const val POPUP_ENUM_RADIUS = 7.0f
    private const val POPUP_SCALE = 1.15f
    private const val POPUP_SCROLL_AMOUNT = 22.0f
    private const val OPEN_TIME_MS = 170L
    private const val SWITCH_ANIMATION_MS = 240L
    private const val ROW_HOVER_ANIMATION_MS = 240.0f
    private const val ICON_HOVER_ANIMATION_MS = 140.0f
    private const val ENUM_EXPANSION_ANIMATION_MS = 220.0f
    private const val SCROLL_RESPONSE_MS = 90.0f
    private const val ICON_CLICK_ANIMATION_MS = 520L

    private const val PANEL_COLOR = 0xD9000000.toInt()
    private const val PROFILE_COLOR = 0xD9000000.toInt()
    private const val ROW_COLOR = 0x66000000
    private const val ROW_HOVER_COLOR = 0x24FFFFFF
    private const val POPUP_COLOR = 0xC4000000.toInt()
    private const val POPUP_FOREGROUND_COLOR = 0x70000000
    private const val POPUP_ROW_COLOR = 0xB5000000.toInt()
    private const val POPUP_OPTION_SELECTED_COLOR = 0x16FFFFFF
    private const val ACCENT = 0xFF91B7FF.toInt()
    private const val SWITCH_OFF = 0xFF424242.toInt()
    private const val SWITCH_KNOB_OFF = 0xFF878787.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val TEXT_MUTED = 0xFF949494.toInt()
    private const val ICON_HOVER_BACKGROUND = 0x35FFFFFF
    private val SETTINGS_ICON = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/settingsoutline.png")
    private val FILTER_ICON = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/filteroutline.png")

    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float)
    private data class Frame(val x: Float, val y: Float, val scale: Float, val width: Float, val height: Float)
    private data class ModuleRow(val module: Module, val rect: Rect)
    private data class CategoryPanel(val category: ModuleCategory, val rect: Rect, val rows: List<ModuleRow>)
    private data class SettingTarget(
        val label: String,
        val setting: ModuleSetting<*>? = null,
        val option: BooleanSetting? = null,
        val enumOptionIndex: Int? = null,
        val visibility: Float = 1.0f,
        val bind: Boolean = false,
    )
    private data class SettingRow(val target: SettingTarget, val rect: Rect)
    private data class Popup(
        val module: Module,
        val rect: Rect,
        val contentRect: Rect,
        val rows: List<SettingRow>,
        val maxScroll: Float,
        val scale: Float,
    )
    private data class Layout(val frame: Frame, val profile: Rect, val panels: List<CategoryPanel>, val popup: Popup?)
    private data class SwitchAnimation(var from: Float, var target: Float, var startedAt: Long)
    private data class SwitchVisual(val position: Float, val stretch: Float)
    private data class HoverAnimation(var value: Float, var updatedAt: Long)
    private data class ScrollAnimation(var position: Float, var target: Float, var updatedAt: Long)
    private data class IconClickAnimation(var startedAt: Long)

    @JvmStatic
    var visible = false
        private set

    private var renderVisible = false
    private var opening = false
    private var animationStartedAt = 0L
    private val categoryScrolls = mutableMapOf<ModuleCategory, ScrollAnimation>()
    private var popupModule: Module? = null
    private var popupAnchorX = 0.0f
    private var popupAnchorY = 0.0f
    private val popupScroll = ScrollAnimation(0.0f, 0.0f, 0L)
    private var bindingModule: Module? = null
    private var expandedEnum: EnumSetting<*>? = null
    private var activeNumeric: ModuleSetting<*>? = null
    private var activeNumericRect: Rect? = null
    private val switchAnimations = mutableMapOf<String, SwitchAnimation>()
    private val hoverAnimations = mutableMapOf<String, HoverAnimation>()
    private val iconClickAnimations = mutableMapOf<String, IconClickAnimation>()

    private val categories = listOf(
        ModuleCategory.COMBAT,
        ModuleCategory.MOVEMENT,
        ModuleCategory.VISUALS,
        ModuleCategory.PLAYER,
        ModuleCategory.MISC,
    )

    @JvmStatic
    fun toggle() {
        visible = !visible
        opening = visible
        animationStartedAt = now()
        if (visible) {
            renderVisible = true
            Minecraft.getInstance().mouseHandler.releaseMouse()
        } else {
            popupModule = null
            bindingModule = null
            activeNumeric = null
            activeNumericRect = null
            ClientConfig.save()
            val mc = Minecraft.getInstance()
            if (mc.level != null && mc.screen == null) mc.mouseHandler.grabMouse()
        }
    }

    @JvmStatic
    fun shouldRender(): Boolean {
        if (!visible && renderVisible && now() - animationStartedAt >= OPEN_TIME_MS) renderVisible = false
        return renderVisible
    }

    @JvmStatic
    fun animationProgress(): Float {
        val linear = ((now() - animationStartedAt).toFloat() / OPEN_TIME_MS).coerceIn(0.0f, 1.0f)
        return if (opening) linear else 1.0f - linear
    }

    @JvmStatic
    fun blurBoxes(guiScale: Float): List<AsteriaOverlay.BlurBox> {
        if (!shouldRender()) return emptyList()
        val layout = layout()
        val boxes = mutableListOf<AsteriaOverlay.BlurBox>()
        if (ModuleManager.test1.enabled) {
            boxes += blurBox(layout.profile, PROFILE_RADIUS * layout.frame.scale, PROFILE_COLOR, guiScale)
        }
        layout.panels.forEach { panel ->
            boxes += blurBox(panel.rect, PANEL_CORNER_RADIUS * layout.frame.scale, PANEL_COLOR, guiScale)
        }
        layout.popup?.let { popup ->
            boxes += blurBox(popup.rect, 11.5f * popup.scale, POPUP_COLOR, guiScale)
        }
        return boxes
    }

    @JvmStatic
    fun keyPressed(key: Int, action: Int): Boolean {
        if (!renderVisible || action != GLFW.GLFW_PRESS) return false
        bindingModule?.let { module ->
            module.bind = if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) -1 else key
            bindingModule = null
            ClientConfig.save()
            return true
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) toggle()
        return true
    }

    @JvmStatic
    fun mouseClicked(button: Int, action: Int): Boolean {
        if (!visible || action != GLFW.GLFW_PRESS) return false
        val mc = Minecraft.getInstance()
        val mouseX = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
        val mouseY = mc.mouseHandler.getScaledYPos(mc.window).toFloat()
        val layout = layout()

        layout.popup?.let { popup ->
            if (contains(mouseX, mouseY, popup.rect)) {
                popup.rows.firstOrNull { contains(mouseX, mouseY, it.rect) }?.let { row ->
                    activateSetting(popup.module, row, mouseX, button, popup.scale)
                }
                return true
            }
        }

        layout.panels.asSequence().filter { panel ->
            contains(mouseX, mouseY, panelContentRect(panel, layout.frame.scale))
        }.flatMap { it.rows.asSequence() }.firstOrNull {
            contains(mouseX, mouseY, it.rect)
        }?.let { row ->
            val settingsClick = (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) &&
                settingsIconRect(row, layout.frame.scale)?.let { contains(mouseX, mouseY, it) } == true
            if (settingsClick) {
                iconClickAnimations[row.module.name] = IconClickAnimation(now())
                if (popupModule === row.module) {
                    popupModule = null
                    expandedEnum = null
                } else {
                    popupModule = row.module
                    expandedEnum = null
                    popupAnchorX = row.rect.x + row.rect.width + 5.0f * layout.frame.scale
                    popupAnchorY = row.rect.y
                    popupScroll.position = 0.0f
                    popupScroll.target = 0.0f
                    popupScroll.updatedAt = now()
                }
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                row.module.toggle()
                ClientConfig.save()
            }
            return true
        }

        if (layout.panels.none { contains(mouseX, mouseY, it.rect) }) {
            popupModule = null
            expandedEnum = null
        }
        return true
    }

    @JvmStatic
    fun mouseReleased(@Suppress("UNUSED_PARAMETER") button: Int, action: Int) {
        if (action == GLFW.GLFW_RELEASE) {
            activeNumeric = null
            activeNumericRect = null
        }
    }

    @JvmStatic
    fun mouseScrolled(vertical: Double): Boolean {
        if (!visible) return false
        val mc = Minecraft.getInstance()
        val mouseX = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
        val mouseY = mc.mouseHandler.getScaledYPos(mc.window).toFloat()
        val layout = layout()
        layout.popup?.takeIf { contains(mouseX, mouseY, it.rect) }?.let { popup ->
            popupScroll.target = (
                popupScroll.target + if (vertical < 0.0) POPUP_SCROLL_AMOUNT else -POPUP_SCROLL_AMOUNT
            ).coerceIn(0.0f, popup.maxScroll)
            return true
        }
        layout.panels.firstOrNull { contains(mouseX, mouseY, it.rect) }?.let { panel ->
            val visibleRows = visibleModuleRows()
            val maxOffset = max(0, ModuleManager.modulesIn(panel.category).size - visibleRows)
            val state = categoryScrolls.getOrPut(panel.category) { ScrollAnimation(0.0f, 0.0f, now()) }
            state.target = (state.target + if (vertical < 0.0) 1.0f else -1.0f).coerceIn(0.0f, maxOffset.toFloat())
            return true
        }
        return true
    }

    @JvmStatic
    fun extract(graphics: GuiGraphicsExtractor) {
        if (!shouldRender()) return
        dragNumeric()
        val layout = layout()
        layoutBoxes(layout).filterNot { it.blur }.forEach { GuiBoxUtil.draw(graphics, it) }
        layout.panels.forEach { panel ->
            val content = panelContentRect(panel, layout.frame.scale)
            val rowBoxes = moduleRowBoxes(panel, layout.frame.scale, animationProgress())
            drawOutsideRect(graphics, content, layout.popup?.rect) {
                rowBoxes.forEach { GuiBoxUtil.draw(graphics, it) }
            }
        }
        graphics.nextStratum()
        drawBaseText(graphics, layout)
        layout.popup?.let { popup ->
            graphics.nextStratum()
            drawPopupShadow(graphics, popup)
            popupBoxes(layout).forEach { GuiBoxUtil.draw(graphics, it) }
            graphics.nextStratum()
            drawPopupText(graphics, popup, animationProgress())
        }
    }

    private fun layout(): Layout {
        val mc = Minecraft.getInstance()
        val availableW = (mc.window.guiScaledWidth - 12.0f).coerceAtLeast(1.0f)
        val availableH = (mc.window.guiScaledHeight - 12.0f).coerceAtLeast(1.0f)
        val scale = min(1.0f, min(availableW / DESIGN_WIDTH, availableH / DESIGN_HEIGHT)) * UI_SCALE
        val width = DESIGN_WIDTH * scale
        val height = DESIGN_HEIGHT * scale
        val frame = Frame(
            (mc.window.guiScaledWidth - width) * 0.5f,
            (mc.window.guiScaledHeight - height) * 0.5f,
            scale,
            width,
            height,
        )
        val panelY = (mc.window.guiScaledHeight - PANEL_HEIGHT * scale) * 0.5f
        val profile = Rect(
            frame.x + (frame.width - PROFILE_WIDTH * scale) * 0.5f,
            (panelY - (PROFILE_HEIGHT + 23.0f) * scale).coerceAtLeast(6.0f),
            PROFILE_WIDTH * scale,
            PROFILE_HEIGHT * scale,
        )
        val panels = categories.mapIndexed { index, category ->
            val panel = Rect(
                frame.x + (CONTENT_INSET_X + index * (PANEL_WIDTH + PANEL_GAP)) * scale,
                panelY,
                PANEL_WIDTH * scale,
                PANEL_HEIGHT * scale,
            )
            val allModules = ModuleManager.modulesIn(category)
            val maxOffset = max(0, allModules.size - visibleModuleRows())
            val scroll = scrollPosition(category, maxOffset)
            val contentTop = panel.y + HEADER_HEIGHT * scale
            val contentBottom = panel.y + (PANEL_HEIGHT - PANEL_PADDING) * scale
            val rows = allModules.mapIndexed { rowIndex, module ->
                ModuleRow(
                    module,
                    Rect(
                        panel.x + PANEL_PADDING * scale,
                        contentTop + (rowIndex - scroll) * ROW_STEP * scale,
                        (PANEL_WIDTH - PANEL_PADDING * 2.0f) * scale,
                        ROW_HEIGHT * scale,
                    ),
                )
            }.filter { row -> row.rect.y + row.rect.height > contentTop && row.rect.y < contentBottom }
            CategoryPanel(category, panel, rows)
        }
        val popup = popupModule?.let { popupLayout(it, frame) }
        return Layout(frame, profile, panels, popup)
    }

    private fun popupLayout(module: Module, frame: Frame): Popup {
        val scale = frame.scale * POPUP_SCALE
        val targets = settingTargets(module)
        val width = POPUP_WIDTH * scale
        val height = POPUP_HEIGHT * scale
        val rowSteps = targets.map(::popupRowStep)
        val joinedOptionGap = targets.indices.sumOf { index ->
            val target = targets[index]
            if (target.enumOptionIndex == null && target.setting is EnumSetting<*> && targets.getOrNull(index + 1)?.enumOptionIndex != null) {
                POPUP_ROW_GAP.toDouble()
            } else {
                0.0
            }
        }.toFloat()
        val viewportHeight = POPUP_HEIGHT - POPUP_CONTENT_PADDING_Y * 2.0f
        val contentHeight = (rowSteps.sum() - joinedOptionGap).coerceAtLeast(0.0f)
        val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0.0f)
        val scroll = popupScrollPosition(maxScroll)
        val mc = Minecraft.getInstance()
        var x = popupAnchorX
        if (x + width > mc.window.guiScaledWidth - 6.0f) {
            x = popupAnchorX - width - (PANEL_WIDTH + 10.0f) * frame.scale
        }
        x = x.coerceIn(6.0f, (mc.window.guiScaledWidth - width - 6.0f).coerceAtLeast(6.0f))
        val y = popupAnchorY.coerceIn(6.0f, (mc.window.guiScaledHeight - height - 6.0f).coerceAtLeast(6.0f))
        val rect = Rect(x, y, width, height)
        val contentRect = Rect(
            x + POPUP_HORIZONTAL_PADDING * scale,
            y + POPUP_CONTENT_PADDING_Y * scale,
            width - POPUP_HORIZONTAL_PADDING * 2.0f * scale,
            viewportHeight * scale,
        )
        var rowY = contentRect.y - scroll * scale
        val rows = targets.mapIndexed { index, target ->
            val step = rowSteps[index]
            SettingRow(
                target,
                Rect(
                    x + POPUP_HORIZONTAL_PADDING * scale,
                    rowY,
                    width - POPUP_HORIZONTAL_PADDING * 2.0f * scale,
                    if (target.enumOptionIndex != null) {
                        (POPUP_OPTION_ROW_HEIGHT - POPUP_ROW_GAP) * target.visibility * scale
                    } else {
                        (step - POPUP_ROW_GAP) * scale
                    },
                ),
            ).also {
                val joinsExpandedOptions = target.enumOptionIndex == null &&
                    target.setting is EnumSetting<*> &&
                    targets.getOrNull(index + 1)?.enumOptionIndex != null
                rowY += (if (joinsExpandedOptions) step - POPUP_ROW_GAP else step) * scale
            }
        }
        return Popup(module, rect, contentRect, rows, maxScroll, scale)
    }

    private fun popupRowStep(target: SettingTarget): Float {
        if (target.enumOptionIndex != null) return POPUP_OPTION_ROW_HEIGHT * target.visibility
        return when (target.setting) {
            is FloatSetting, is IntSetting -> POPUP_NUMERIC_ROW_HEIGHT
            is EnumSetting<*> -> POPUP_ENUM_ROW_HEIGHT
            else -> POPUP_ROW_HEIGHT
        }
    }

    private fun settingTargets(module: Module): List<SettingTarget> {
        val targets = mutableListOf<SettingTarget>()
        targets += SettingTarget("Bind", bind = true)
        module.settings.filter { it.isVisible() }.forEach { setting ->
            if (setting is MultiBooleanSetting) {
                setting.value.forEach { option ->
                    targets += SettingTarget("${setting.name}: ${option.name}", setting, option)
                }
            } else {
                targets += SettingTarget(setting.name, setting)
                if (setting is EnumSetting<*>) {
                    val expansion = enumExpansionProgress(module, setting)
                    if (expansion <= 0.001f) return@forEach
                    repeat(setting.optionCount()) { index ->
                        targets += SettingTarget(
                            setting.optionDisplay(index),
                            setting,
                            enumOptionIndex = index,
                            visibility = expansion,
                        )
                    }
                }
            }
        }
        return targets
    }

    private fun layoutBoxes(layout: Layout): List<GuiBoxUtil.Box> {
        val boxes = mutableListOf<GuiBoxUtil.Box>()
        val scale = layout.frame.scale
        val alpha = animationProgress()

        if (ModuleManager.test1.enabled && !ModuleManager.postProcessing.enabled) {
            AsteriaGuiRenderer.rect(boxes, layout.profile.x, layout.profile.y, layout.profile.width, layout.profile.height, PROFILE_RADIUS * scale, withAlpha(PROFILE_COLOR, alpha))
        }

        layout.panels.forEach { panel ->
            if (!ModuleManager.postProcessing.enabled) {
                AsteriaGuiRenderer.rect(boxes, panel.rect.x, panel.rect.y, panel.rect.width, panel.rect.height, PANEL_CORNER_RADIUS * scale, withAlpha(PANEL_COLOR, alpha))
            }
        }

        return boxes
    }

    private fun popupBoxes(layout: Layout): List<GuiBoxUtil.Box> {
        val boxes = mutableListOf<GuiBoxUtil.Box>()
        val alpha = animationProgress()
        layout.popup?.let { popup ->
            val scale = popup.scale
            if (!ModuleManager.postProcessing.enabled) {
                AsteriaGuiRenderer.rect(boxes, popup.rect.x, popup.rect.y, popup.rect.width, popup.rect.height, 11.5f * scale, withAlpha(POPUP_COLOR, alpha))
            } else {
                AsteriaGuiRenderer.rect(boxes, popup.rect.x, popup.rect.y, popup.rect.width, popup.rect.height, 11.5f * scale, withAlpha(POPUP_FOREGROUND_COLOR, alpha))
            }
            val contentBoxStart = boxes.size
            popup.rows.forEachIndexed { index, row ->
                val rowAlpha = alpha * row.target.visibility
                val enumSetting = row.target.setting as? EnumSetting<*>
                val isEnumOption = row.target.enumOptionIndex != null
                if (enumSetting != null && !isEnumOption) {
                    val lastOption = popup.rows
                        .drop(index + 1)
                        .takeWhile { candidate ->
                            candidate.target.enumOptionIndex != null && candidate.target.setting === enumSetting
                        }
                        .lastOrNull()
                    val groupBottom = lastOption?.let {
                        it.rect.y + it.rect.height + POPUP_ROW_GAP * it.target.visibility * scale
                    } ?: (row.rect.y + row.rect.height)
                    AsteriaGuiRenderer.rect(
                        boxes,
                        row.rect.x,
                        row.rect.y,
                        row.rect.width,
                        groupBottom - row.rect.y,
                        POPUP_ENUM_RADIUS * scale,
                        withAlpha(POPUP_ROW_COLOR, alpha),
                    )
                } else if (!isEnumOption) {
                    AsteriaGuiRenderer.rect(
                        boxes,
                        row.rect.x,
                        row.rect.y,
                        row.rect.width,
                        row.rect.height,
                        5.5f * scale,
                        withAlpha(POPUP_ROW_COLOR, rowAlpha),
                    )
                }
                if (isSelectedEnumOption(row.target)) {
                    val isLastOption = isLastEnumOption(popup, index)
                    val selectedHeight = row.rect.height + POPUP_ROW_GAP * row.target.visibility * scale
                    val selectedColor = withAlpha(POPUP_OPTION_SELECTED_COLOR, rowAlpha)
                    if (isLastOption) {
                        val radius = POPUP_ENUM_RADIUS * scale
                        boxes += GuiBoxUtil.Box(
                            row.rect.x,
                            row.rect.y,
                            row.rect.width,
                            selectedHeight,
                            radius,
                            selectedColor,
                            blur = false,
                            shape = GuiBoxUtil.Shape.BOTTOM_ROUNDED_RECT,
                        )
                    } else {
                        AsteriaGuiRenderer.rect(boxes, row.rect.x, row.rect.y, row.rect.width, selectedHeight, 0.0f, selectedColor)
                    }
                }
                val setting = row.target.setting
                val checked = row.target.option?.value ?: (setting as? BooleanSetting)?.value
                if (checked != null) {
                    val switchW = 19.5f * scale
                    val switchH = 11.5f * scale
                    drawCircularSwitch(
                        boxes,
                        "setting:${popup.module.name}:${row.target.label}",
                        row.rect.x + row.rect.width - switchW - 6.0f * scale,
                        row.rect.y + (row.rect.height - switchH) * 0.5f,
                        switchW,
                        switchH,
                        checked,
                        alpha,
                    )
                }
                if (setting is FloatSetting || setting is IntSetting) {
                    val percent = numericPercent(setting)
                    val trackX = row.rect.x + 7.0f * scale
                    val trackW = row.rect.width - 14.0f * scale
                    AsteriaGuiRenderer.range(
                        boxes,
                        trackX,
                        row.rect.y + row.rect.height - 3.5f * scale,
                        trackW,
                        1.5f * scale,
                        5.0f * scale,
                        percent,
                        withAlpha(ACCENT, alpha),
                        withAlpha(0xFF303030.toInt(), alpha),
                    )
                }
            }
            val clip = GuiBoxUtil.Clip(
                popup.contentRect.x,
                popup.contentRect.y,
                popup.contentRect.x + popup.contentRect.width,
                popup.contentRect.y + popup.contentRect.height,
            )
            for (index in contentBoxStart until boxes.size) {
                boxes[index] = boxes[index].copy(clip = clip)
            }
        }
        return boxes
    }

    private fun moduleRowBoxes(panel: CategoryPanel, scale: Float, alpha: Float): List<GuiBoxUtil.Box> {
        val boxes = mutableListOf<GuiBoxUtil.Box>()
        val cursorX = mouseX()
        val cursorY = mouseY()
        panel.rows.forEach { row ->
            val hovered = contains(cursorX, cursorY, row.rect)
            val hover = hoverProgress("module:${row.module.name}", hovered, ROW_HOVER_ANIMATION_MS)
            AsteriaGuiRenderer.rect(
                boxes,
                row.rect.x,
                row.rect.y,
                row.rect.width,
                row.rect.height,
                MODULE_ROW_RADIUS * scale,
                withAlpha(ROW_COLOR, alpha),
            )
            if (hover > 0.001f) {
                AsteriaGuiRenderer.rect(
                    boxes,
                    row.rect.x,
                    row.rect.y,
                    row.rect.width,
                    row.rect.height,
                    MODULE_ROW_RADIUS * scale,
                    withAlpha(ROW_HOVER_COLOR, alpha * hover),
                )
            }
            settingsIconRect(row, scale)?.let { iconRect ->
                val iconHover = hoverProgress("settings:${row.module.name}", contains(cursorX, cursorY, iconRect), ICON_HOVER_ANIMATION_MS)
                if (iconHover > 0.001f) {
                    AsteriaGuiRenderer.rect(
                        boxes,
                        iconRect.x - 1.5f * scale,
                        iconRect.y - 1.5f * scale,
                        iconRect.width + 3.0f * scale,
                        iconRect.height + 3.0f * scale,
                        3.5f * scale,
                        withAlpha(ICON_HOVER_BACKGROUND, alpha * iconHover),
                    )
                }
            }
            val switchW = 19.5f * scale
            val switchH = 11.5f * scale
            drawCircularSwitch(
                boxes,
                "module:${row.module.name}",
                row.rect.x + row.rect.width - switchW - 6.5f * scale,
                row.rect.y + (row.rect.height - switchH) * 0.5f,
                switchW,
                switchH,
                row.module.enabled,
                alpha,
            )
        }
        return boxes
    }

    private fun drawBaseText(graphics: GuiGraphicsExtractor, layout: Layout) {
        val scale = layout.frame.scale
        val alpha = animationProgress()
        if (ModuleManager.test1.enabled) {
            val profile = layout.profile
            val headSize = 20.5f * scale
            PlayerHeadRenderer.draw(graphics, profile.x + 7.5f * scale, profile.y + 10.5f * scale, headSize)
            text(graphics, Face.SfMedium, playerName(), profile.x + 34.0f * scale, profile.y + 9.0f * scale, 8.0f * scale, withAlpha(WHITE, alpha))
            text(graphics, Face.SfRegular, serverAddress(), profile.x + 34.0f * scale, profile.y + 22.5f * scale, 6.5f * scale, withAlpha(TEXT_MUTED, alpha))
        }

        layout.panels.forEach { panel ->
            val header = Rect(panel.rect.x, panel.rect.y, panel.rect.width, HEADER_HEIGHT * scale)
            drawOutsideRect(graphics, header, layout.popup?.rect) {
                FontRenderer.drawCentered(
                    graphics,
                    Face.SfRegular,
                    categoryName(panel.category),
                    panel.rect.x + panel.rect.width * 0.5f,
                    panel.rect.y + 12.0f * scale,
                    12.0f * scale,
                    withAlpha(WHITE, alpha),
                )
            }
            val content = panelContentRect(panel, scale)
            drawOutsideRect(graphics, content, layout.popup?.rect) {
                panel.rows.forEach { row ->
                    val labelX = row.rect.x + 8.0f * scale
                    val labelY = row.rect.y + 7.5f * scale
                    val labelSize = 9.0f * scale
                    text(graphics, Face.SfRegular, row.module.name, labelX, labelY, labelSize, withAlpha(WHITE, alpha))
                    settingsIconRect(row, scale)?.let { iconRect ->
                        val iconHover = hoverProgress("settings:${row.module.name}", contains(mouseX(), mouseY(), iconRect), ICON_HOVER_ANIMATION_MS)
                        MsdfIconRenderer.draw(
                            graphics,
                            SETTINGS_ICON,
                            iconRect.x,
                            iconRect.y,
                            iconRect.width,
                            iconRect.height,
                            withAlpha(blendColor(TEXT_MUTED, WHITE, iconHover), alpha),
                            iconRotation(row.module.name),
                        )
                    }
                }
            }
        }

    }

    private fun drawPopupText(graphics: GuiGraphicsExtractor, popup: Popup, alpha: Float) {
        val popupScale = popup.scale
        graphics.enableScissor(
            floor(popup.contentRect.x).toInt(),
            floor(popup.contentRect.y).toInt(),
            ceil(popup.contentRect.x + popup.contentRect.width).toInt(),
            ceil(popup.contentRect.y + popup.contentRect.height).toInt(),
        )
        popup.rows.forEachIndexed { index, row ->
                val target = row.target
                val rowAlpha = alpha * target.visibility
                val color = when {
                    target.bind && bindingModule === popup.module -> ACCENT
                    isSelectedEnumOption(target) -> WHITE
                    target.enumOptionIndex != null -> TEXT_MUTED
                    else -> WHITE
                }
                val labelSize = 7.0f * popupScale
                val optionVisualHeight = if (target.enumOptionIndex != null) {
                    row.rect.height + POPUP_ROW_GAP * target.visibility * popupScale
                } else {
                    row.rect.height
                }
                val labelY = row.rect.y + if (target.setting is FloatSetting || target.setting is IntSetting) {
                    4.0f * popupScale
                } else {
                    (optionVisualHeight - labelSize) * 0.5f
                }
                val enumSetting = target.setting as? EnumSetting<*>
                val labelX = row.rect.x + if (enumSetting != null && target.enumOptionIndex == null) {
                    POPUP_ENUM_HORIZONTAL_PADDING * popupScale
                } else {
                    7.0f * popupScale
                }
                val enumHasVisibleOptions = enumSetting != null && popup.rows.any { candidate ->
                    candidate.target.enumOptionIndex != null && candidate.target.setting === enumSetting
                }
                val label = when {
                    enumSetting != null && target.enumOptionIndex == null && enumHasVisibleOptions -> "Variants"
                    enumSetting != null && target.enumOptionIndex == null -> enumSetting.displayValue()
                    else -> target.label
                }
                text(graphics, Face.SfRegular, label, labelX, labelY, labelSize, withAlpha(color, rowAlpha))
                val value = settingValue(popup.module, target)
                if (value != null && target.option == null && target.setting !is BooleanSetting) {
                    textRight(graphics, value, row.rect.x + row.rect.width - 7.0f * popupScale, labelY, 6.5f * popupScale, withAlpha(if (target.bind) TEXT_MUTED else ACCENT, rowAlpha))
                }
                if (enumSetting != null && target.enumOptionIndex == null) {
                    val iconSize = 8.5f * popupScale
                    MsdfIconRenderer.draw(
                        graphics,
                        FILTER_ICON,
                        row.rect.x + row.rect.width - iconSize - POPUP_ENUM_HORIZONTAL_PADDING * popupScale,
                        row.rect.y + (row.rect.height - iconSize) * 0.5f,
                        iconSize,
                        iconSize,
                        withAlpha(WHITE, rowAlpha),
                    )
                }
        }
        graphics.disableScissor()
    }

    private fun drawPopupShadow(graphics: GuiGraphicsExtractor, popup: Popup) {
        val interfaceModule = ModuleManager.interfaceModule
        val guiScale = Minecraft.getInstance().window.guiScale.toFloat().coerceAtLeast(1.0f)
        GuiShapeUtil.softShadow(
            graphics,
            popup.rect.x,
            popup.rect.y,
            popup.rect.x + popup.rect.width,
            popup.rect.y + popup.rect.height,
            11.5f * popup.scale,
            interfaceModule.shadowSize.value / guiScale,
            interfaceModule.shadowOpacity.value / 100.0f * animationProgress(),
            interfaceModule.shadowSmoothness.value / 100.0f,
        )
    }

    private fun activateSetting(module: Module, row: SettingRow, mouseX: Float, button: Int, scale: Float) {
        val target = row.target
        if (target.bind) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) bindingModule = module
            return
        }
        target.enumOptionIndex?.let { optionIndex ->
            (target.setting as? EnumSetting<*>)?.selectIndex(optionIndex)
            expandedEnum = null
            ClientConfig.save()
            return
        }
        target.option?.let {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) it.adjust(1)
            ClientConfig.save()
            return
        }
        when (val setting = target.setting) {
            is BooleanSetting -> setting.adjust(1)
            is EnumSetting<*> -> expandedEnum = if (expandedEnum === setting) null else setting
            is FloatSetting, is IntSetting -> {
                val trackRect = Rect(
                    row.rect.x + 7.0f * scale,
                    row.rect.y + row.rect.height - 3.5f * scale,
                    row.rect.width - 14.0f * scale,
                    1.5f * scale,
                )
                setNumeric(setting, (mouseX - trackRect.x) / trackRect.width)
                activeNumeric = setting
                activeNumericRect = trackRect
            }
        }
        ClientConfig.save()
    }

    private fun dragNumeric() {
        val setting = activeNumeric ?: return
        val rect = activeNumericRect ?: return
        val mc = Minecraft.getInstance()
        val mouseX = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
        setNumeric(setting, (mouseX - rect.x) / rect.width)
    }

    private fun setNumeric(setting: ModuleSetting<*>, percent: Float) {
        when (setting) {
            is FloatSetting -> setting.setByPercent(percent.coerceIn(0.0f, 1.0f))
            is IntSetting -> setting.setByPercent(percent.coerceIn(0.0f, 1.0f))
        }
    }

    private fun numericPercent(setting: ModuleSetting<*>): Float = when (setting) {
        is FloatSetting -> ((setting.value - setting.min) / (setting.max - setting.min)).coerceIn(0.0f, 1.0f)
        is IntSetting -> ((setting.value - setting.min).toFloat() / (setting.max - setting.min).toFloat()).coerceIn(0.0f, 1.0f)
        else -> 0.0f
    }

    private fun settingValue(module: Module, target: SettingTarget): String? = when {
        target.enumOptionIndex != null -> null
        target.bind -> bindName(module.bind)
        target.setting is EnumSetting<*> -> null
        target.setting is FloatSetting || target.setting is IntSetting -> target.setting.displayValue()
        else -> null
    }

    private fun isSelectedEnumOption(target: SettingTarget): Boolean {
        val index = target.enumOptionIndex ?: return false
        val setting = target.setting as? EnumSetting<*> ?: return false
        return setting.values.getOrNull(index) == setting.value
    }

    private fun isLastEnumOption(popup: Popup, rowIndex: Int): Boolean {
        val row = popup.rows.getOrNull(rowIndex) ?: return false
        if (row.target.enumOptionIndex == null) return false
        val setting = row.target.setting as? EnumSetting<*> ?: return false
        return popup.rows.drop(rowIndex + 1).none { candidate ->
            candidate.target.enumOptionIndex != null && candidate.target.setting === setting
        }
    }

    private fun enumExpansionProgress(module: Module, setting: EnumSetting<*>): Float {
        return hoverProgress(
            "enum:${module.name}:${setting.name}",
            expandedEnum === setting,
            ENUM_EXPANSION_ANIMATION_MS,
        )
    }

    private fun blurBox(rect: Rect, radius: Float, color: Int, guiScale: Float): AsteriaOverlay.BlurBox {
        return AsteriaOverlay.BlurBox(
            rect.x * guiScale,
            rect.y * guiScale,
            rect.width * guiScale,
            rect.height * guiScale,
            radius * guiScale,
            shaderFillForCss(color),
        )
    }

    private fun shaderFillForCss(color: Int): Float {
        val cssAlpha = ((color ushr 24) and 0xFF) / 255.0f
        val globalTint = (ModuleManager.postProcessing.tintStrength.value / 100.0f).coerceIn(0.0f, 0.95f)
        val remainingAfterTint = 1.0f - globalTint
        val targetRemaining = 1.0f - cssAlpha
        return (1.0f - targetRemaining / remainingAfterTint).coerceIn(0.0f, 1.0f)
    }

    private fun drawCircularSwitch(
        boxes: MutableList<GuiBoxUtil.Box>,
        key: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        checked: Boolean,
        alpha: Float,
    ) {
        val visual = switchVisual(key, if (checked) 1.0f else 0.0f)
        val knobDiameter = height * 0.72f
        val sideInset = height * 0.5f
        val leftCenter = x + sideInset
        val rightCenter = x + width - sideInset
        val knobCenterX = lerp(leftCenter, rightCenter, visual.position)
        val knobCenterY = y + height * 0.5f
        val knobWidth = knobDiameter + (width - height) * 0.58f * visual.stretch
        AsteriaGuiRenderer.rect(
            boxes,
            x,
            y,
            width,
            height,
            height * 0.5f,
            withAlpha(blendColor(SWITCH_OFF, ACCENT, visual.position), alpha),
        )
        AsteriaGuiRenderer.rect(
            boxes,
            knobCenterX - knobWidth * 0.5f,
            knobCenterY - knobDiameter * 0.5f,
            knobWidth,
            knobDiameter,
            knobDiameter * 0.5f,
            withAlpha(blendColor(SWITCH_KNOB_OFF, WHITE, visual.position), alpha),
        )
    }

    private fun switchVisual(key: String, target: Float): SwitchVisual {
        val currentTime = now()
        val state = switchAnimations[key]
        if (state == null) {
            switchAnimations[key] = SwitchAnimation(target, target, currentTime)
            return SwitchVisual(target, 0.0f)
        }

        val current = switchPosition(state, currentTime)
        if (state.target != target) {
            state.from = current
            state.target = target
            state.startedAt = currentTime
        }

        val linear = ((currentTime - state.startedAt).toFloat() / SWITCH_ANIMATION_MS).coerceIn(0.0f, 1.0f)
        val eased = linear * linear * (3.0f - 2.0f * linear)
        val position = lerp(state.from, state.target, eased)
        val distance = kotlin.math.abs(state.target - state.from)
        val stretch = sin(Math.PI.toFloat() * linear).coerceAtLeast(0.0f) * distance
        return SwitchVisual(position, stretch)
    }

    private fun switchPosition(state: SwitchAnimation, currentTime: Long): Float {
        val linear = ((currentTime - state.startedAt).toFloat() / SWITCH_ANIMATION_MS).coerceIn(0.0f, 1.0f)
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

    private fun hoverProgress(key: String, hovered: Boolean, durationMs: Float): Float {
        val currentTime = now()
        val state = hoverAnimations.getOrPut(key) { HoverAnimation(if (hovered) 1.0f else 0.0f, currentTime) }
        val elapsed = (currentTime - state.updatedAt).coerceIn(0L, 250L).toFloat()
        val response = 1.0f - exp(-elapsed / (durationMs / 3.0f))
        state.value = lerp(state.value, if (hovered) 1.0f else 0.0f, response)
        if (kotlin.math.abs(state.value - if (hovered) 1.0f else 0.0f) < 0.001f) {
            state.value = if (hovered) 1.0f else 0.0f
        }
        state.updatedAt = currentTime
        return state.value
    }

    private fun scrollPosition(category: ModuleCategory, maxOffset: Int): Float {
        val currentTime = now()
        val state = categoryScrolls.getOrPut(category) { ScrollAnimation(0.0f, 0.0f, currentTime) }
        state.target = state.target.coerceIn(0.0f, maxOffset.toFloat())
        state.position = state.position.coerceIn(0.0f, maxOffset.toFloat())
        val elapsed = (currentTime - state.updatedAt).coerceIn(0L, 250L).toFloat()
        val response = 1.0f - exp(-elapsed / (SCROLL_RESPONSE_MS / 2.0f))
        state.position = lerp(state.position, state.target, response)
        if (kotlin.math.abs(state.position - state.target) < 0.001f) state.position = state.target
        state.updatedAt = currentTime
        return state.position
    }

    private fun popupScrollPosition(maxScroll: Float): Float {
        val currentTime = now()
        popupScroll.target = popupScroll.target.coerceIn(0.0f, maxScroll)
        popupScroll.position = popupScroll.position.coerceIn(0.0f, maxScroll)
        val elapsed = (currentTime - popupScroll.updatedAt).coerceIn(0L, 250L).toFloat()
        val response = 1.0f - exp(-elapsed / (SCROLL_RESPONSE_MS / 2.0f))
        popupScroll.position = lerp(popupScroll.position, popupScroll.target, response)
        if (kotlin.math.abs(popupScroll.position - popupScroll.target) < 0.001f) {
            popupScroll.position = popupScroll.target
        }
        popupScroll.updatedAt = currentTime
        return popupScroll.position
    }

    private fun iconRotation(moduleName: String): Float {
        val animation = iconClickAnimations[moduleName] ?: return 0.0f
        val progress = ((now() - animation.startedAt).toFloat() / ICON_CLICK_ANIMATION_MS).coerceIn(0.0f, 1.0f)
        if (progress >= 1.0f) {
            iconClickAnimations.remove(moduleName)
            return 0.0f
        }
        val eased = 0.5f - 0.5f * cos(Math.PI.toFloat() * progress)
        return 360.0f * eased
    }

    private fun enablePanelContentScissor(graphics: GuiGraphicsExtractor, panel: CategoryPanel, scale: Float) {
        val content = panelContentRect(panel, scale)
        graphics.enableScissor(
            floor(content.x).toInt(),
            floor(content.y).toInt(),
            ceil(content.x + content.width).toInt(),
            ceil(content.y + content.height).toInt(),
        )
    }

    private inline fun drawOutsideRect(
        graphics: GuiGraphicsExtractor,
        bounds: Rect,
        exclusion: Rect?,
        draw: () -> Unit,
    ) {
        val boundsRight = bounds.x + bounds.width
        val boundsBottom = bounds.y + bounds.height
        if (exclusion == null ||
            exclusion.x >= boundsRight || exclusion.x + exclusion.width <= bounds.x ||
            exclusion.y >= boundsBottom || exclusion.y + exclusion.height <= bounds.y
        ) {
            drawInScissor(graphics, bounds, draw)
            return
        }

        val cutLeft = exclusion.x.coerceIn(bounds.x, boundsRight)
        val cutRight = (exclusion.x + exclusion.width).coerceIn(bounds.x, boundsRight)
        val cutTop = exclusion.y.coerceIn(bounds.y, boundsBottom)
        val cutBottom = (exclusion.y + exclusion.height).coerceIn(bounds.y, boundsBottom)
        drawInScissor(graphics, Rect(bounds.x, bounds.y, bounds.width, cutTop - bounds.y), draw)
        drawInScissor(graphics, Rect(bounds.x, cutBottom, bounds.width, boundsBottom - cutBottom), draw)
        drawInScissor(graphics, Rect(bounds.x, cutTop, cutLeft - bounds.x, cutBottom - cutTop), draw)
        drawInScissor(graphics, Rect(cutRight, cutTop, boundsRight - cutRight, cutBottom - cutTop), draw)
    }

    private inline fun drawInScissor(graphics: GuiGraphicsExtractor, rect: Rect, draw: () -> Unit) {
        if (rect.width <= 0.05f || rect.height <= 0.05f) return
        graphics.enableScissor(
            floor(rect.x).toInt(),
            floor(rect.y).toInt(),
            ceil(rect.x + rect.width).toInt(),
            ceil(rect.y + rect.height).toInt(),
        )
        draw()
        graphics.disableScissor()
    }

    private fun panelContentRect(panel: CategoryPanel, scale: Float): Rect = Rect(
        panel.rect.x + PANEL_PADDING * scale,
        panel.rect.y + HEADER_HEIGHT * scale,
        panel.rect.width - PANEL_PADDING * 2.0f * scale,
        panel.rect.height - (HEADER_HEIGHT + PANEL_PADDING) * scale,
    )

    private fun visibleModuleRows(): Int = floor((PANEL_HEIGHT - HEADER_HEIGHT - PANEL_PADDING) / ROW_STEP).toInt().coerceAtLeast(1)

    private fun categoryName(category: ModuleCategory): String = when (category) {
        ModuleCategory.VISUALS -> "Render"
        else -> category.displayName
    }

    private fun playerName(): String = Minecraft.getInstance().player?.name?.string ?: "Player"

    private fun serverAddress(): String = Minecraft.getInstance().currentServer?.ip ?: "Asteria"

    private fun bindName(bind: Int): String {
        if (bind < 0) return "None"
        return GLFW.glfwGetKeyName(bind, 0)?.uppercase() ?: "Key $bind"
    }

    private fun withAlpha(color: Int, multiplier: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * multiplier.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun contains(x: Float, y: Float, rect: Rect): Boolean =
        x >= rect.x && x <= rect.x + rect.width && y >= rect.y && y <= rect.y + rect.height

    private fun settingsIconRect(row: ModuleRow, scale: Float): Rect? {
        if (!hasConfigurableSettings(row.module)) return null
        val size = 10.5f * scale
        val switchWidth = 19.5f * scale
        val switchX = row.rect.x + row.rect.width - switchWidth - 6.5f * scale
        return Rect(
            switchX - size - 2.0f * scale - 2.0f,
            row.rect.y + (row.rect.height - size) * 0.5f,
            size,
            size,
        )
    }

    private fun hasConfigurableSettings(module: Module): Boolean = module.settings.any { it.isVisible() }

    private fun mouseX(): Float = Minecraft.getInstance().mouseHandler.getScaledXPos(Minecraft.getInstance().window).toFloat()

    private fun mouseY(): Float = Minecraft.getInstance().mouseHandler.getScaledYPos(Minecraft.getInstance().window).toFloat()

    private fun text(graphics: GuiGraphicsExtractor, face: Face, value: String, x: Float, y: Float, size: Float, color: Int) {
        FontRenderer.draw(graphics, face, value, x, y, size, color)
    }

    private fun textRight(graphics: GuiGraphicsExtractor, value: String, right: Float, y: Float, size: Float, color: Int) {
        FontRenderer.draw(graphics, Face.SfRegular, value, right - FontRenderer.width(Face.SfRegular, value, size), y, size, color)
    }

    private fun now(): Long = System.nanoTime() / 1_000_000L
}
