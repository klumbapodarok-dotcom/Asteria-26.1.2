package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LiquidGlassShaderContractTest {
    public static void main(String[] args) throws Exception {
        Path shader = Path.of("src/main/resources/assets/asteria/shaders/core/liquid_glass.fsh");
        if (!Files.isRegularFile(shader)) {
            throw new AssertionError("Vulkan liquid-glass fragment shader is missing");
        }

        String source = Files.readString(shader);
        require(source, "#version 330", "Minecraft pipeline GLSL version");
        require(source, "uniform sampler2D OriginalSampler", "captured framebuffer sampler");
        require(source, "layout(std140) uniform LiquidGlassInfo", "Vulkan-compatible uniform block");
        require(source, "Boxes[MAX_GLASS_BOXES]", "per-box rectangles");
        require(source, "selectedBox = i", "topmost overlap ownership");
        require(source, "coverageMask = max(coverageMask, candidateMask)", "gap-free overlap coverage");
        require(source, "clamp(samplePixel", "box-local sample clamp");
        require(source, "pow(abs(normalizedLocal.x), 8.0)", "exponent-8 liquid lens shape");
        require(source, "vec2 lensLocal", "box-local lens mapping");
        require(source, "vec2 chromaticOffset", "box-local chromatic aberration");
        require(source, "texture(BlurSampler, redUv).r", "red chromatic sample");
        require(source, "texture(BlurSampler, blueUv).b", "blue chromatic sample");
        require(source, "for (int x = -4; x <= 4; x++)", "nine-tap horizontal blur span");
        require(source, "for (int y = -4; y <= 4; y++)", "nine-tap vertical blur span");
        require(source, "float shadowGradient", "liquid lens shadow gradient");
        reject(source, "border *", "liquid glass outline lighting must be disabled");
        require(source, "float roundedRectMask", "per-box rounded shape mask");
        require(source, "BoxData[selectedBox].x", "selected box corner radius");
        require(source, "float applyRounding", "blur-compatible radius rounding");
        require(source, "int roundingRule = int(BoxInfo.y + 0.5)", "blur-compatible rounding rule selection");
        require(source, "uniform sampler2D BlurSampler", "selected blur input");
        reject(source, "mix(lighting, Fresnel.rgb, tintStrength)", "liquid glass shader tint must be disabled");
        require(source, "dot(blurred.rgb, vec3(0.2126, 0.7152, 0.0722))", "luminance-aware saturation control");

        String module = Files.readString(Path.of(
            "src/client/kotlin/asteria/top/client/module/modules/visual/PostProcessingModule.kt"
        ));
        require(module, "BooleanSetting(\"Liquid Glass\", false)", "independent liquid-glass toggle");
        require(module, "EnumSetting(\"Blur\"", "blur algorithm selector");
        reject(module, "LIQUID_GLASS(\"Liquid Glass\")", "liquid glass must not replace the blur algorithm");

        String renderer = Files.readString(Path.of(
            "src/client/kotlin/asteria/top/client/render/DoubleKawaseBlurRenderer.kt"
        ));
        require(renderer, "renderLiquidGlass(originalTarget, blurTarget, mainTarget", "blurred liquid-glass composition");
        require(renderer, "RoundingUtil.ROUNDING_RULE.glslId.toFloat()", "shared blur corner rounding rule");

        String boxes = Files.readString(Path.of(
            "src/client/kotlin/asteria/top/client/util/GuiBoxUtil.kt"
        ));
        require(boxes, "postProcessTint: Boolean", "post-process tint classification");

        String overlay = Files.readString(Path.of(
            "src/client/kotlin/asteria/top/client/gui/AsteriaOverlay.kt"
        ));
        reject(overlay, "hidePostProcessTint", "liquid-glass tint suppression");
        reject(overlay, "!it.postProcessTint", "classified tint filtering");
        reject(overlay, "postProcessing.liquidGlass.value", "toggle-driven tint suppression");
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
