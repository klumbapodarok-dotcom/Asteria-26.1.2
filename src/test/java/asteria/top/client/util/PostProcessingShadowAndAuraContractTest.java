package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PostProcessingShadowAndAuraContractTest {
    public static void main(String[] args) throws Exception {
        String postProcessing = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/visual/PostProcessingModule.kt"));
        require(postProcessing, "val shadow = setting(BooleanSetting(\"Shadow\", true))", "post-processing shadow toggle");
        require(postProcessing, "val shadowRadius = setting(IntSetting(\"Shadow Radius\", 28, 0, 80, 1))", "post-processing shadow radius control");

        String renderer = Files.readString(Path.of("src/client/kotlin/asteria/top/client/render/DoubleKawaseBlurRenderer.kt"));
        require(renderer, "postProcessing.shadow.value", "renderer must read shadow toggle");
        require(renderer, "postProcessing.shadowRadius.value.toFloat()", "renderer must read shadow radius");
        require(renderer, "shadowEnabled: Boolean", "composite buffer must accept shadow toggle");
        require(renderer, "shadowRadius: Float", "composite buffer must accept shadow radius");
        require(renderer, "if (shadowEnabled) ShadowUtil.OPACITY * opacity else 0.0f", "shadow toggle should drive opacity");

        String aura = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/combat/KillauraModule.kt"));
        reject(aura, "BooleanSetting(\"Client look\"", "aura should not expose stale client-look boolean");
        reject(aura, "BooleanSetting(\"Elytra override\"", "aura should not expose stale elytra override boolean");
        reject(aura, "Elytra distance", "aura should not expose unused elytra distance setting");
        reject(aura, "Elytra pre distance", "aura should not expose unused elytra pre-distance setting");
        reject(aura, "clientLook", "aura should not keep clientLook state");
        reject(aura, "elytraOverride", "aura should not keep elytraOverride state");
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
