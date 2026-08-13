package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class HudWidgetAnimationContractTest {
    public static void main(String[] args) throws Exception {
        String hudManager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/HudManager.kt"));
        String animationUtil = Files.readString(Path.of("src/client/kotlin/asteria/top/client/util/AnimationUtil.kt"));
        String widgets = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/StandardHudWidgets.kt"));
        String keybinds = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/KeybindsHudWidget.kt"));
        String iconList = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/IconListHudWidget.kt"));
        String targetInfo = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/TargetInfoHudWidget.kt"));

        require(animationUtil, "class TimedAnimation", "HUD lifecycle should use reusable timed animation state");
        require(animationUtil, "if (safe && next == target && !isAlive()) return", "no-op safe animations must not restart forever");
        require(animationUtil, "fun easeOutQuad", "show animation should have Asteria-style QUAD_OUT easing");
        require(animationUtil, "fun easeOutSine", "hide animation should have Asteria-style SINE_OUT easing");

        require(hudManager, "private const val WIDGET_SHOW_ANIMATION_DURATION = 220L", "widget show duration should match Asteria");
        require(hudManager, "private const val WIDGET_HIDE_ANIMATION_DURATION = 360L", "widget hide duration should match Asteria");
        require(hudManager, "private val widgetAnimationStates = linkedMapOf<HudWidget, WidgetAnimationState>()", "HUD manager should keep per-widget animation state");
        require(hudManager, "val state = widgetAnimationStates[widget] ?: if (shouldRender) WidgetAnimationState().also { widgetAnimationStates[widget] = it } else return@forEach", "hidden idle widgets should not allocate no-op animation state");
        require(hudManager, "if (widget is InventoryHudWidget)", "inventory should bypass lifecycle animations");
        require(hudManager, "widget.render(graphics, mc, editorOpen)", "inventory bypass should render directly without scaling");
        require(hudManager, "state.update(", "HUD manager should update visibility state for visible and hidden widgets");
        require(hudManager, "widgetAnimationStates.entries.removeIf { !it.value.shouldDraw() }", "finished hidden animation states should be pruned");
        require(hudManager, "state.shouldDraw()", "HUD manager should keep drawing widgets while hide animation runs");
        require(hudManager, "renderAnimatedWidget(graphics, mc, widget, state)", "HUD manager should render widgets through the lifecycle animation path");
        require(hudManager, "animatedBlurBoxes(it, guiScale, tintStrength, widgetAnimationStates[it])", "HUD manager should animate widget blur/background boxes with lifecycle scale");
        require(hudManager, "private fun animatedBlurBoxes(", "HUD manager should isolate animated blur box scaling");
        require(hudManager, "val scale = state?.visibility?.value?.coerceAtLeast(0.0f) ?: 1.0f", "animated blur boxes should use widget lifecycle scale");
        require(hudManager, "val centerX = widget.bounds.x + widget.bounds.width * 0.5f", "animated blur boxes should scale from widget center");
        require(hudManager, "(box.x / guiScale - centerX) * scale", "blur box x should follow widget scale");
        require(hudManager, "box.width * scale", "blur box width should shrink and expand with widget scale");
        require(hudManager, "graphics.pose().pushMatrix()", "HUD manager should isolate lifecycle transforms");
        require(hudManager, "graphics.pose().scale(scale, scale)", "HUD manager should scale widgets like Asteria");
        require(hudManager, "graphics.pose().popMatrix()", "HUD manager should restore HUD transform state");
        require(hudManager, "val scale = state.visibility.value.coerceAtLeast(0.0f)", "widget size animation should expand the whole widget from zero");
        require(hudManager, "visibility.run(if (shouldBeVisible) 1.0f else 0.0f", "visibility animation should target widget show state");
        require(hudManager, "AnimationUtil::easeOutBack", "widget show size should use back easing");
        require(hudManager, "AnimationUtil::easeInBack", "widget hide size should use back easing");
        reject(hudManager, "pulse", "widget lifecycle should not stack a second pulse animation on top of size animation");

        require(widgets, "private var animatedSelectedSlot = -1.0f", "hotbar should animate selected-slot movement");
        require(widgets, "animatedSelectedSlot += (selected - animatedSelectedSlot) * 0.28f", "hotbar selected slot should use Asteria smoothing");
        require(widgets, "val selectedSlotX = bounds.x + 2.0f + animatedSelectedSlot * 20.0f", "hotbar highlight should render from animated slot position");

        require(keybinds, "private const val KEYBIND_ROW_FADE_IN_DURATION = 200L", "keybind row enter timing should match the mock");
        require(keybinds, "private const val KEYBIND_ROW_FADE_OUT_DURATION = 170L", "keybind row exit timing should match the mock");
        require(keybinds, "FontRenderer.Face.SfMedium", "keybind text needs the medium cut, whose stems survive HUD sizes");
        require(widgets, "private val WATERMARK_FACE = FontRenderer.Face.SfMedium", "the watermark should be set in the same cut as the keybinds panel");
        require(keybinds, "private const val KEYBIND_BOTTOM_PADDING = KEYBIND_PADDING - KEYBIND_ROW_TEXT_INSET", "keybind bottom padding should be derived, like the watermark's");
        require(keybinds, "sharp = true", "the keys glyph is baked with a 6 texel range and needs the shader that matches");
        require(keybinds, "private val rowStates = linkedMapOf<String, RowState>()", "keybinds should keep exiting rows alive");
        require(keybinds, "private var renderRows = emptyList<RowState>()", "keybinds should cache animated rows during layout");
        require(keybinds, "override fun advance(", "keybind layout should be stepped once per frame so blur and render agree");
        require(keybinds, "it.bind >= 0 && it.enabled", "keybinds should list only modules that are bound and switched on");
        require(keybinds, "row.appear.run(", "keybind rows should animate both directions");
        require(keybinds, "if (!stillBound && row.appear.value <= 0.001f) iterator.remove()", "keybind rows should be removed only after exit animation");
        require(keybinds, "KEYBIND_ROW_HEIGHT * appear", "keybind rows should fold the panel open and shut while they fade");
        reject(keybinds, "if (renderRows.isEmpty() && preview)", "keybinds should not synthesize preview rows when no bind is active");
        reject(keybinds, "return (enabled && activeRows(false).isNotEmpty()) || preview", "keybind header should not appear only because the HUD editor is open");
        reject(keybinds, "panelPulseAnimation", "keybinds should not stack panel pulse with manager lifecycle animation");
        reject(keybinds, "graphics.pose().scale(scale, scale)", "keybinds should not add a second widget-scale transform");

        // Potions and cooldowns are the keybinds panel with an icon column, so
        // the timings, the folding and the derived padding are asserted once on
        // the shared template rather than twice on its two users.
        require(iconList, "private const val LIST_ROW_FADE_IN_DURATION = 200L", "list row enter timing should match the keybinds panel");
        require(iconList, "private const val LIST_ROW_FADE_OUT_DURATION = 170L", "list row exit timing should match the keybinds panel");
        require(iconList, "private const val LIST_BOTTOM_PADDING = LIST_PADDING - LIST_ROW_TEXT_INSET", "list bottom padding should be derived, like the keybinds panel's");
        require(iconList, "private val LIST_FACE = FontRenderer.Face.SfMedium", "list text needs the medium cut, whose stems survive HUD sizes");
        require(iconList, "sharp = true", "the header glyphs are baked with a 6 texel range and need the shader that matches");
        require(iconList, "private val rowStates = linkedMapOf<String, IconListRowState<Icon>>()", "list panels should keep exiting rows alive");
        require(iconList, "private var renderRows = emptyList<IconListRowState<Icon>>()", "list panels should cache animated rows during layout");
        require(iconList, "override fun advance(", "list layout should be stepped once per frame so blur and render agree");
        require(iconList, "row.appear.run(", "list rows should animate both directions");
        require(iconList, "if (!stillActive && row.appear.value <= 0.001f) iterator.remove()", "list rows should be removed only after exit animation");
        require(iconList, "LIST_ROW_HEIGHT * appear", "list rows should fold the panel open and shut while they fade");
        require(iconList, "val iconSize = LIST_ICON_WIDTH * appear", "a folding row's icon should fold with it instead of spilling over its neighbour");
        require(iconList, "LIST_ICON_WIDTH + LIST_ICON_GAP", "a row's name should start on the same column as the panel title");
        reject(iconList, "graphics.pose().scale(scale, scale)", "list panels should not add a second widget-scale transform");

        require(widgets, "class PotionsHudWidget : IconListHudWidget<Identifier>", "potions should be built on the shared list template");
        require(widgets, "class CooldownsHudWidget : IconListHudWidget<ItemStack>", "cooldowns should be built on the shared list template");
        require(widgets, "effect.descriptionId,", "potion row animation key should ignore the level and duration text");
        require(widgets, "private const val POTION_TITLE = \"Зелья\"", "the potions panel should be titled in the HUD's language");
        require(widgets, "private const val COOLDOWN_TITLE = \"Задержки\"", "the cooldowns panel should be titled in the HUD's language");
        reject(widgets, "POTION_PANEL_PULSE_ANIMATION_DURATION", "potions should not stack panel pulse with manager lifecycle animation");

        require(targetInfo, "private val showAnimation = AnimationUtil.TimedAnimation(0.0f)", "target info should animate appearance and hiding");
        require(targetInfo, "showAnimation.run(if (visible) 1.0f else 0.0f", "target info visibility should animate from target presence");
        require(targetInfo, "private var retainedTarget: LivingEntity? = null", "target info should retain last target while hiding");
        require(targetInfo, "if (retainedTarget == null || showAnimation.value <= 0.01f)", "target info should keep drawing until hide animation finishes");
        require(targetInfo, "val panelScale = 1.0f", "target info should let HudManager animate the whole widget instead of scaling internals");
        reject(targetInfo, "TARGET_PULSE_ANIMATION_DURATION", "target info should not own target-change pulse timing");
        reject(targetInfo, "targetPulseScale", "target info should not own panel/head pulse animation");
        reject(targetInfo, "hurtTime / 10.0f", "target info head should not pulse from hurt time");
        reject(targetInfo, "targetPulseScale() * (0.94f + (1.0f - 0.94f) * showAnimation.value", "target info should not stack internal show scale with manager lifecycle animation");
    }

    private static void require(String source, String token, String label) {
        if (!source.contains(token)) {
            throw new AssertionError(label + " is missing: " + token);
        }
    }

    private static void reject(String source, String token, String label) {
        if (source.contains(token)) {
            throw new AssertionError(label + ": " + token);
        }
    }
}
