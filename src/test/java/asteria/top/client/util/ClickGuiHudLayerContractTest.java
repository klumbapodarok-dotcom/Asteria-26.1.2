package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ClickGuiHudLayerContractTest {
    public static void main(String[] args) throws Exception {
        String overlay = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/HudOverlay.kt"));
        String guiMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/GuiMixin.kt"));
        String rendererMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/GuiRendererMixin.kt"));
        String blurRenderer = Files.readString(Path.of("src/client/kotlin/asteria/top/client/render/DoubleKawaseBlurRenderer.kt"));

        reject(overlay, "if (AsteriaClickGui.shouldRender()) return", "ClickGUI must not hide the HUD");
        require(overlay, "HudManager.extract(graphics)", "HUD should still be extracted while ClickGUI is open");
        require(
            guiMixin,
            "graphics.nextStratum()\n        graphics.blurBeforeThisStratum()\n        AsteriaClickGui.extract(graphics)",
            "ClickGUI blur boundary must be placed after HUD extraction"
        );
        require(
            rendererMixin,
            "DoubleKawaseBlurRenderer.renderClickGuiPass()",
            "Minecraft's between-strata blur callback should run the ClickGUI pass"
        );
        require(
            blurRenderer,
            "Pass.CLICK_GUI -> AsteriaClickGui.blurBoxes(guiScale).take(MAX_BLUR_BOXES)",
            "ClickGUI pass must blur the framebuffer containing the already-rendered HUD"
        );
    }

    private static void require(String source, String needle, String message) {
        if (!source.contains(needle)) throw new AssertionError(message + " (missing: " + needle + ")");
    }

    private static void reject(String source, String needle, String message) {
        if (source.contains(needle)) throw new AssertionError(message + " (unexpected: " + needle + ")");
    }
}
