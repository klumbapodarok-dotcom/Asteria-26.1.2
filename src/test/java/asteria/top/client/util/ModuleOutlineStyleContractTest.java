package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ModuleOutlineStyleContractTest {
    public static void main(String[] args) throws Exception {
        String overlay = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/AsteriaOverlay.kt"));
        require(overlay, "MODULE_CARD_OUTLINE_TINT = 0x1AFFFFFF", "neutral 10-percent tint module outline");
        reject(overlay, "MODULE_CARD_OUTLINE_TINT = 0x1A88FF82", "module outline must not be green");
        require(overlay, "MODULE_CARD_OUTLINE_INSET = 1.0f", "inset module outline to avoid expansion tearing");
        require(overlay, "MODULE_CARD_OUTLINE_RADIUS = 14.0f", "stable module outline radius during expansion");
        require(overlay, "MODULE_CARD_RADIUS = MODULE_CARD_OUTLINE_RADIUS", "liquid-glass module corners should match normal module corners");
        require(overlay, "rect.x + MODULE_CARD_OUTLINE_INSET * scaleX", "module outline should be inset horizontally");
        require(overlay, "cardHeight - MODULE_CARD_OUTLINE_INSET * 2.0f * scaleY", "module outline should be inset vertically");
        require(overlay, "MODULE_CARD_OUTLINE_RADIUS * radiusScale", "module outline must not morph with the large glass radius");
        require(overlay, "val liquidGlassActive = ModuleManager.postProcessing.liquidGlass.value", "module layout must know when liquid glass is active");
        require(overlay, "if (!liquidGlassActive) {\n                AsteriaGuiRenderer.outlineRect", "module outline must be skipped while liquid glass is active");
        require(overlay, "AsteriaGuiRenderer.outlineRect", "module cards must draw an outline");
        reject(overlay, "withAlpha(MODULE_CARD_TINT, alpha)", "module cards must not draw a fill tint");
        require(overlay, "private fun GuiBoxUtil.Box.isSettingsBox(ox: Float, _oy: Float): Boolean", "settings-box classifier should ignore vertical scroll position");
        require(overlay, "return x >= ox + SETTINGS_CLIP_X - 1.0f", "settings boxes must remain under module scissor when scrolled above the viewport");
        reject(overlay, "&& y >= oy + MODULE_CLIP_Y - 1.0f", "settings boxes must not escape module scissor when y is above the clip");
        require(overlay, "DROPDOWN_OUTLINE_TINT = 0x1AFFFFFF", "neutral dropdown option outline");
        require(overlay, "MULTI_OPTION_OUTLINE_TINT = 0x1AFFFFFF", "neutral target option outline");
        require(overlay, "ChipItem(0, setting.name, Rect(9.0f, cursor - 6.0f, 230.0f, 36.0f))", "dropdown header width should match two option boxes plus the option gap");
        reject(overlay, "ChipItem(0, setting.name, Rect(9.0f, cursor - 6.0f, 233.0f, 36.0f))", "dropdown header must not be wider than option boxes");
        require(overlay, "AsteriaGuiRenderer.outlineRect(boxes, header.x", "enum header must be outline-only");
        require(overlay, "AsteriaGuiRenderer.outlineRect(boxes, rect.x, rect.y, rect.width, rect.height", "enum options must be outline-only");
        require(overlay, "AsteriaGuiRenderer.outlineRect(\n                        boxes,\n                        x + 7.0f * scaleX", "target group must be outline-only");
        require(overlay, "val groupHeaderCenterY = y + (item.y + 10.5f) * scaleY", "target group header text must be vertically centered");
        require(overlay, "groupHeaderCenterY - 6.0f * textScale", "target group label must use centered baseline");
        require(overlay, "groupHeaderCenterY - 5.5f * textScale", "target group count must use centered baseline");
        require(overlay, "private fun expansionIcon(expanded: Boolean): String = if (expanded) \"C\" else \"D\"", "clickgui expansion glyph mapping");
        require(overlay, "text(graphics, Face.SfMedium, module.name, rect.x + 9.0f * scaleX, rect.y + 9.0f * scaleY, 15.0f * textScale", "module card text should use the original left-aligned title style");
        reject(overlay, "textCenteredInRect(graphics, module.name", "module card text must not be centered");
        reject(overlay, "expansionIcon(isModuleExpanded(module))", "module cards should not draw expansion icons");
        require(overlay, "text(graphics, Face.ClickGuiIcon, expansionIcon(isSettingGroupExpanded(module, setting))", "target group expansion icon should use clickgui font glyphs");
        require(overlay, "groupHeaderCenterY - 5.0f * textScale", "target group icon must use centered clickgui baseline");
        reject(overlay, "if (isSettingGroupExpanded(module, setting)) \"v\" else \">\"", "target group arrow must not use text fallback");
        reject(overlay, "withAlpha(0x1A000000, alpha)", "dropdown options must not use dark tint fills");
        reject(overlay, "withAlpha(0x1A88FF82, alpha)", "selected options must not use green fill tint");
        reject(overlay, "withAlpha(0x331D2528, alpha)", "target groups must not use fill tint");

        String renderer = Files.readString(Path.of("src/client/kotlin/asteria/top/client/render/AsteriaGuiRenderer.kt"));
        require(renderer, "fun outlineRect", "outline renderer helper");
        require(renderer, "GuiBoxUtil.Shape.ROUNDED_OUTLINE", "outline-only GUI box shape");

        String boxes = Files.readString(Path.of("src/client/kotlin/asteria/top/client/util/GuiBoxUtil.kt"));
        require(boxes, "ROUNDED_OUTLINE", "outline-only box shape");
        require(boxes, "roundedStroke", "true rounded stroke draw path");

        Path shader = Path.of("src/main/resources/assets/asteria/shaders/core/rounded_outline.fsh");
        if (!Files.isRegularFile(shader)) {
            throw new AssertionError("rounded outline shader is missing");
        }
        String shaderSource = Files.readString(shader);
        require(shaderSource, "abs(distance)", "outline shader must render a stroke, not a filled card");
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
