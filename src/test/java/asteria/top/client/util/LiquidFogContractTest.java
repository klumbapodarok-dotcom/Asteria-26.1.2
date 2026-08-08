package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LiquidFogContractTest {
    public static void main(String[] args) throws Exception {
        String module = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/visual/LiquidFogModule.kt"));
        String manager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/ModuleManager.kt"));
        String client = Files.readString(Path.of("src/client/kotlin/asteria/top/client/AsteriaClient.kt"));
        String renderer = Files.readString(Path.of("src/client/kotlin/asteria/top/client/render/LiquidFogRenderer.kt"));
        String shader = Files.readString(Path.of("src/main/resources/assets/asteria/shaders/core/liquid_fog.fsh"));

        require(module, "class LiquidFogModule : Module(", "Liquid Fog module should exist");
        require(module, "name = \"Liquid Fog\"", "Liquid Fog should keep the old module name");
        require(module, "category = ModuleCategory.VISUALS", "Liquid Fog should be a visual module");
        require(module, "FloatSetting(\"Fallout\", 0.03f, 0.0f, 1.0f, 0.01f)", "Fallout setting should be ported");
        require(module, "FloatSetting(\"Density\", 0.75f, 0.0f, 1.0f, 0.01f)", "Density setting should be ported");
        require(module, "BooleanSetting(\"HSV Mode\", false)", "HSV mode setting should be ported");

        require(manager, "val liquidFog = LiquidFogModule()", "ModuleManager should expose Liquid Fog");
        require(manager, "liquidFog,", "ModuleManager should register Liquid Fog");
        require(client, "LevelRenderEvents.END_MAIN.register { context -> ModuleManager.liquidFog.render(context) }", "Liquid Fog should render at the end of the main world pass");

        require(renderer, "object LiquidFogRenderer", "Liquid Fog renderer should exist");
        require(renderer, "withFragmentShader(Identifier.fromNamespaceAndPath(\"asteria\", \"core/liquid_fog\"))", "renderer should use the liquid fog shader asset");
        require(renderer, "withSampler(\"DepthSampler\")", "renderer should bind copied depth");
        require(renderer, "ColorTargetState(BlendFunction.TRANSLUCENT)", "renderer should alpha-blend the fog overlay");
        require(renderer, "copyTextureToTexture(", "renderer should copy the main depth texture before rendering");
        require(renderer, "destination.depthTexture ?: return false", "renderer should read main depth through the new render target API");
        require(renderer, "LiquidFogInfo", "renderer should use a UBO for fog uniforms");
        require(renderer, "context.gameRenderer().mainCamera", "renderer should read camera state from the level render context");
        require(renderer, "camera.yRot()", "renderer should feed camera yaw");
        require(renderer, "context.gameRenderer().mainCamera.xRot()", "renderer should feed camera pitch");

        require(shader, "uniform sampler2D DepthSampler;", "shader should sample copied depth");
        require(shader, "layout(std140) uniform LiquidFogInfo", "shader should use the renderer UBO");
        require(shader, "snoise(vec3", "shader should keep the fluid noise field");
        require(shader, "hsvMode == 1", "shader should keep HSV mode");
        require(shader, "minimumVisibility", "shader should preserve minimum fog visibility");
    }

    private static void require(String source, String token, String label) {
        if (!source.contains(token)) {
            throw new AssertionError(label + " is missing: " + token);
        }
    }
}
