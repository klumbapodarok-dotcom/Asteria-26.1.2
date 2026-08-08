package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class HudHotbarReplacementContractTest {
    public static void main(String[] args) throws Exception {
        String widgets = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/StandardHudWidgets.kt"));
        String hudWidget = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/HudWidget.kt"));
        String targetInfo = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/TargetInfoHudWidget.kt"));
        String hudManager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/HudManager.kt"));
        String hudOverlay = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/HudOverlay.kt"));
        String guiMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/GuiMixin.kt"));

        require(targetInfo, "private const val TARGET_DOT_Y_OFFSET = 1.0f", "target info dot should have an explicit downward offset");
        require(targetInfo, "centerY - dotSize * 0.5f + TARGET_DOT_Y_OFFSET", "target info dot should render slightly below the name centerline");

        require(hudManager, "fun customHotbarActive(): Boolean", "HUD manager should expose custom hotbar visibility");
        require(hudManager, "it.visible(mc, editorOpen) || widgetAnimationStates[it]?.shouldDraw() == true", "custom hotbar active state should include hide animation frames");
        require(hudOverlay, "fun customHotbarActive(): Boolean = HudManager.customHotbarActive()", "HUD overlay should expose custom hotbar state to mixins");
        require(hudWidget, "val movable: Boolean = true", "HUD widgets should expose whether the editor can drag them");
        require(widgets, "movable = false", "custom hotbar widget should opt out of dragging");
        require(hudManager, "it.movable && it.visible(mc, true)", "HUD editor drag picking should ignore non-movable widgets");
        require(hudManager, "widgets.filterIsInstance<HotbarHudWidget>().forEach", "custom hotbar should be visited every layout pass");
        require(hudManager, "positionHotbar(it, mc)", "custom hotbar should be positioned every layout pass");
        require(hudManager, "private fun positionHotbar(widget: HotbarHudWidget, mc: Minecraft)", "custom hotbar should use a dedicated vanilla-position helper");
        require(hudManager, "widget.bounds.y = mc.window.guiScaledHeight - widget.bounds.height", "custom hotbar should take the vanilla bottom hotbar position");
        reject(hudManager, "if (!it.bounds.x.isFinite() || !it.bounds.y.isFinite()) {\n                it.bounds.x = (mc.window.guiScaledWidth - it.bounds.width) * 0.5f", "custom hotbar position must not only initialize once");

        require(guiMixin, "method = [\"extractItemHotbar\"]", "GUI mixin should hook vanilla item hotbar extraction");
        require(guiMixin, "if (HudOverlay.customHotbarActive()) ci.cancel()", "custom hotbar should cancel vanilla hotbar extraction");
        reject(guiMixin, "if (HudOverlay.customHotbarActive()) return", "hotbar hook must cancel rather than no-op");
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
