package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class HudWidgetSplitContractTest {
    public static void main(String[] args) throws Exception {
        String widgets = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/StandardHudWidgets.kt"));
        String keybinds = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/KeybindsHudWidget.kt"));
        String targetInfo = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/TargetInfoHudWidget.kt"));

        reject(widgets, "class KeybindsHudWidget", "keybinds should not stay in StandardHudWidgets");
        reject(widgets, "class TargetInfoHudWidget", "target info should not stay in StandardHudWidgets");

        require(keybinds, "private const val KEYBINDS_WIDTH = 126.0f", "keybinds should match potions width");
        require(keybinds, "HudStyle.panel(graphics, bounds)", "keybinds should render like the potions panel");
        require(keybinds, "val rowY = bounds.y + 29.0f + index * 17.0f", "keybind rows should use potions row layout");
        require(keybinds, "private const val KEYBIND_INDICATOR_RIGHT_INSET = 12.0f", "keybind bind text should use a moderate right-shifted inset");
        require(keybinds, "bounds.x + bounds.width - bindWidth - KEYBIND_INDICATOR_RIGHT_INSET", "keybind bind text should be right-aligned with the shifted inset");
        reject(keybinds, "drawSplitHudPanel", "keybinds should not use the old split panel layout");
        reject(keybinds, "row.second.take(1)", "keybinds should show the bind text directly instead of badge normalization");

        require(targetInfo, "private const val TARGET_HEAD_WIDTH = 55.0f", "target info should use Asteria head block width");
        require(targetInfo, "val panelHeight = 55.0f", "target info should use centered Asteria head height");
        require(targetInfo, "drawTargetPanel(graphics, renderX, renderY, renderHeadWidth, renderContentWidth, renderPanelHeight)", "target info should render split head/content panel");
        require(targetInfo, "drawTargetHead(graphics, target", "target info should draw target heads through the shared head helper");
        reject(targetInfo, "livingTexture(target)", "target info should not use mob renderer textures in the target info portrait");
        require(targetInfo, "drawStableHead", "target info player head should avoid the leaking upper skin layer");
        require(targetInfo, "drawLeftAlignedDotName", "target info should use left-aligned dot/name composition");
        require(targetInfo, "val nameX = left + dotSize + 2.0f", "target info name text should align from the left after the dot");
        reject(targetInfo, "val centerX = ((left + right) * 0.5f)", "target info name must not center itself between the dot and HP badge");
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
