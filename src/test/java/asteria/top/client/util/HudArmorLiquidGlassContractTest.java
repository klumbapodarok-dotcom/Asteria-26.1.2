package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class HudArmorLiquidGlassContractTest {
    public static void main(String[] args) throws Exception {
        String widgets = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/StandardHudWidgets.kt"));
        String keybinds = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/KeybindsHudWidget.kt"));
        String armorWidget = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/ArmorHudWidget.kt"));
        String targetInfo = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/TargetInfoHudWidget.kt"));

        reject(widgets, "class KeybindsHudWidget", "keybinds widget should live in its own source file");
        reject(widgets, "class ArmorHudWidget", "armor widget should live in its own source file");
        reject(widgets, "class TargetInfoHudWidget", "target info widget should live in its own source file");

        require(keybinds, "private const val KEYBINDS_WIDTH = 126.0f", "keybinds should match potions panel width");
        require(keybinds, "x = 7.5f", "keybinds default x should match Asteria");
        require(keybinds, "y = 28.0f", "keybinds default y should match Asteria");
        require(keybinds, "bounds.height = 31.0f + animatedRows * KEYBIND_ROW_HEIGHT", "keybinds should animate potions-style row spacing");
        require(keybinds, "HudStyle.panel(graphics, bounds)", "keybinds should use the same single-panel layout as potions");
        require(keybinds, "FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, \"Binds\", bounds.x + 9.0f, bounds.y + 8.0f, 10.0f, HudStyle.TEXT)", "keybinds should use potions-style header text placement");
        require(keybinds, "HudStyle.icon(graphics, iconGlyph, bounds.x + bounds.width - 20.0f, bounds.y + 7.0f, 10.0f)", "keybinds should use potions-style header icon placement");
        require(keybinds, "val rowY = bounds.y + 29.0f + index * 17.0f", "keybind rows should align with potions rows");
        require(keybinds, "FontRenderer.width(FontRenderer.Face.SfSemibold, row.bind, KEYBIND_BIND_TEXT_SIZE)", "keybinds should measure right-aligned bind text");
        reject(keybinds, "TextureRenderer.draw(graphics, dotTexture", "keybinds should not render dots in potions layout");
        reject(keybinds, "HudStyle.rect(graphics, badgeX", "keybinds should not draw bind indicator boxes in potions layout");

        require(armorWidget, "height = 56.0f", "armor widget should leave room for durability percent labels");
        require(armorWidget, "graphics.item(stack", "armor widget should draw the actual armor item texture");
        require(armorWidget, "graphics.itemDecorations", "armor widget should keep vanilla item overlays");
        require(armorWidget, "durabilityPercent(stack)", "armor widget should render durability percent labels");
        require(armorWidget, "FontRenderer.drawCentered", "armor widget durability labels should be centered under icons");
        reject(armorWidget, "HudStyle.icon", "armor widget must not draw a header icon");
        reject(armorWidget, "FontRenderer.draw(graphics", "armor widget must not draw title/header text");
        reject(armorWidget, "HudStyle.rect", "armor widget must not add item slot boxes");

        reject(targetInfo, "targetEquipment", "target info must not add item slots");
        reject(targetInfo, "TARGET_SLOT_SIZE", "target info height must not reserve equipment slots");
        require(targetInfo, "val panelHeight = 55.0f", "target info should match Asteria head geometry height");
        require(targetInfo, "HudStyle.pill", "target info HP bar should have rounded ends");
        require(targetInfo, "private val healthBarAnimation = TargetBarAnimation()", "target info should animate health bar ratio");
        require(targetInfo, "private val absorptionBarAnimation = TargetBarAnimation()", "target info should animate absorption bar ratio");
        require(targetInfo, "healthBarAnimation.run(ratio, if (ratio < healthBarAnimation.value) 260L else 320L)", "health bar should use Asteria decrease/increase timing");
        require(targetInfo, "AnimationUtil.easeOutSoftBack", "target info bar animation should use back-out easing");
        require(targetInfo, "val panelScale = 1.0f", "target info should leave whole-widget scaling to HudManager");
        reject(targetInfo, "private var pulseStartMillis = 0L", "target info must not run a private target-change pulse");
        reject(targetInfo, "it.hurtTime / 10.0f", "target head must not pulse separately from the widget");
        require(targetInfo, "drawTargetHead", "target info should render target head abstraction");
        require(targetInfo, "Identifier.fromNamespaceAndPath(\"asteria\", \"textures/glow.png\")", "target info dot should use the glow texture");
        require(targetInfo, "TextureRenderer.draw(graphics, dotTexture", "target info dot should render as a textured glow sprite");
        reject(targetInfo, "FontRenderer.drawCentered(graphics, FontRenderer.Face.SfRegular, \".\"", "target info dot must not use a font period");
        require(targetInfo, "left - 2.5f", "target info dot should sit one pixel farther left");
        require(targetInfo, "centerY - dotSize * 0.5f", "target info dot should be vertically centered to the player name");
        require(targetInfo, "private const val TARGET_HEAD_WIDTH = 55.0f", "target info head panel should match Asteria width");
        require(targetInfo, "renderX + 7.0f * panelScale, renderY + 7.0f * panelScale, 41.0f * panelScale", "target info head should use Asteria face padding and size");
        require(targetInfo, "PlayerHeadRenderer.drawStableHead(graphics, target, headDrawX, headDrawY, headDrawSize, radius)", "target info should use stable base-face rendering to avoid upper skin layer bleed");
        require(targetInfo, "drawHeadStroke(graphics, headDrawX, headDrawY, headDrawSize, radius)", "target info should draw Asteria-style subtle rounded head stroke");
        reject(targetInfo, "livingTexture(target)", "target info should match Asteria by not rendering mob texture portraits");
        require(targetInfo, "override fun blurBoxes(guiScale: Float, tintStrength: Float): List<AsteriaOverlay.BlurBox>", "target info should register HP-track liquid-glass blur regions");
        require(targetInfo, "(bounds.x + TARGET_HEAD_WIDTH + 8.0f) * guiScale", "target info HP track should be sent to the liquid-glass shader");
        require(targetInfo, "HudStyle.panel(graphics, HudBounds(barX, barY, barW, 6.0f), 3.0f)", "target info empty HP track should use the shader-backed HUD panel path");
        require(targetInfo, "HudStyle.rect(graphics, badgeX, renderY + 9.0f * panelScale, badgeWidth, 13.0f, 6.5f, 0x4088FF82)", "target info HP value box should be rounded like a pill");
        require(targetInfo, "HudStyle.panel(graphics, HudBounds(x, y, headWidth, panelHeight), 10.0f)", "target info should keep the split liquid-glass panel path");

        String playerHeadRenderer = Files.readString(Path.of("src/client/kotlin/asteria/top/client/render/PlayerHeadRenderer.kt"));
        require(playerHeadRenderer, "fun drawStableHead(", "player head renderer should expose a stable base-face head path");
        require(playerHeadRenderer, "fun drawHead(", "player head renderer should expose a Asteria-style head path");
        require(playerHeadRenderer, "val superGap = gap * 2.0f", "Asteria head should model the same inset gap as Asteria");
        require(playerHeadRenderer, "RoundedTextureRenderer.skinRegion(graphics, skin, x + gap, y + gap, size - superGap, size - superGap, radius, 8.0f, 8.0f)", "Asteria head should draw inset base face region");
        require(playerHeadRenderer, "RoundedTextureRenderer.skinRegion(graphics, skin, x, y, size, size, radius, 40.0f, 8.0f)", "Asteria head should draw hat overlay region");

        String roundedTextureRenderer = Files.readString(Path.of("src/client/kotlin/asteria/top/client/render/RoundedTextureRenderer.kt"));
        require(roundedTextureRenderer, "ColorTargetState(BlendFunction.TRANSLUCENT)", "rounded texture renderer should alpha-blend skin overlays like Asteria");
        require(roundedTextureRenderer, "val encodedColor = (color and 0xFF000000.toInt()) or (encodedRadius shl 16)", "rounded texture renderer should pass radius separately from skin UVs");
        String roundedTextureShader = Files.readString(Path.of("src/main/resources/assets/asteria/shaders/core/rounded_texture.fsh"));
        require(roundedTextureShader, "float radius = max(vertexColor.r * 16.0, 0.0)", "rounded skin texture shader should use a mask radius separate from UVs");
        require(roundedTextureShader, "uPixels + 0.5 + localX * max(regionPixels - 1.0, 0.0)", "rounded skin texture shader should sample inside texel centers horizontally");
        require(roundedTextureShader, "vPixels + 0.5 + localY * max(regionPixels - 1.0, 0.0)", "rounded skin texture shader should sample inside texel centers vertically");

        String hudStyle = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/HudWidget.kt"));
        reject(hudStyle, "AsteriaGuiRenderer.outlineRect", "HUD panel style must not add outlines");

        require(widgets, "Identifier.fromNamespaceAndPath(\"asteria\", \"textures/gui/asteria.png\")", "watermark should use asteria texture");
        require(widgets, "private val logoBoxWidth = 31.0f", "watermark logo should live in its own square box");
        require(widgets, "val logoX = bounds.x - logoBoxWidth - boxGap", "watermark logo box should be separate and to the left of the main bounds");
        require(widgets, "HudStyle.panel(graphics, HudBounds(logoX, bounds.y, logoBoxWidth, bounds.height))", "watermark should render a separate left logo box");
        require(widgets, "HudStyle.panel(graphics, HudBounds(infoX, bounds.y, infoWidth, bounds.height))", "watermark should render the info content in a separate right box");
        require(widgets, "val logoWidth = 22.0f", "watermark logo should render at the texture aspect for better quality");
        require(widgets, "val logoHeight = 18.75f", "watermark logo should fit inside the square box without stretching");
        require(widgets, "override fun blurBoxes(guiScale: Float, tintStrength: Float): List<AsteriaOverlay.BlurBox>", "watermark should register logo-specific liquid-glass blur regions");
        require(widgets, "AsteriaOverlay.BlurBox(\n                logoX * guiScale", "watermark logo box should be sent to the liquid-glass shader");
        require(widgets, "AsteriaOverlay.BlurBox(\n                (logoX + iconInset) * guiScale", "watermark icon tile should be sent to the liquid-glass shader");
        require(widgets, "HudStyle.panel(graphics, HudBounds(logoX + 3.0f, bounds.y + 3.0f, logoBoxWidth - 6.0f, bounds.height - 6.0f), 5.0f)", "watermark asteria tile should use the shader-backed HUD panel path");
        reject(widgets, "HudStyle.rect(graphics, logoX + 3.0f", "watermark asteria tile should not use a normal filled rect");
        require(widgets, "HudStyle.ACCENT", "watermark logo should be tinted to match the theme color");
        reject(widgets, "Identifier.fromNamespaceAndPath(\"asteria\", \"icon.png\")", "watermark should not use the old icon texture");

        String hudManager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/hud/HudManager.kt"));
        require(hudManager, "hudTintStrength()", "HUD blur boxes should provide liquid-glass tint strength");
        require(hudManager, ".flatMap { it.blurBoxes(guiScale, tintStrength) }", "HUD blur collection should support widget-specific liquid-glass regions");
        reject(hudManager, "it.bounds.x - 1.0f", "HUD editor must not outline widget bounds");

        String overlay = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/AsteriaOverlay.kt"));
        require(overlay, "data class BlurBox(val x: Float, val y: Float, val width: Float, val height: Float, val radius: Float, val tintStrength: Float = 0.0f)", "blur boxes should carry optional tint strength");

        String renderer = Files.readString(Path.of("src/client/kotlin/asteria/top/client/render/DoubleKawaseBlurRenderer.kt"));
        require(renderer, "box.tintStrength", "liquid-glass UBO should serialize per-box tint strength");

        String shader = Files.readString(Path.of("src/main/resources/assets/asteria/shaders/core/liquid_glass.fsh"));
        reject(shader, "float tintStrength = clamp(BoxData[selectedBox].y", "liquid glass shader should read per-box tint strength");
        reject(shader, "mix(lighting, Fresnel.rgb, tintStrength)", "liquid glass shader should apply tint support");
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
