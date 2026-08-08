package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ViewmodelAuraContractTest {
    public static void main(String[] args) throws Exception {
        String mixins = Files.readString(Path.of("src/client/resources/asteria.client.mixins.json"));
        String itemInHandRendererMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/ItemInHandRendererMixin.kt"));
        String localPlayerMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/LocalPlayerMixin.kt"));
        String module = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/visual/ViewModelModule.kt"));
        String manager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/ModuleManager.kt"));

        require(mixins, "ItemInHandRendererMixin", "client mixin set should include first-person item transform");
        require(module, "name = \"View Model\"", "View Model module should exist");
        require(module, "category = ModuleCategory.VISUALS", "View Model should be a visual module");
        require(module, "FloatSetting(\"X\", 0.0f, -1.0f, 1.0f, 0.025f)", "View Model should expose X");
        require(module, "FloatSetting(\"Y\", 0.0f, -1.0f, 1.0f, 0.025f)", "View Model should expose Y");
        require(module, "FloatSetting(\"Z\", 0.0f, -1.0f, 1.0f, 0.025f)", "View Model should expose Z");
        require(module, "FloatSetting(\"Scale\", 1.0f, 0.5f, 1.5f, 0.025f)", "View Model should expose Scale");
        reject(module, "Aura Rotation", "View Model should not expose Aura Rotation");
        reject(module, "FloatSetting(\"Pitch\"", "View Model should not expose Pitch");
        reject(module, "FloatSetting(\"Yaw\"", "View Model should not expose Yaw");
        reject(module, "FloatSetting(\"Roll\"", "View Model should not expose Roll");
        require(module, "fun applyTransform(poseStack: PoseStack, hand: InteractionHand)", "View Model should expose a first-person transform hook");
        require(manager, "val viewModel = ViewModelModule()", "ModuleManager should expose View Model");
        require(manager, "viewModel,", "ModuleManager should register View Model");
        require(itemInHandRendererMixin, "ModuleManager.viewModel.applyTransform(poseStack, hand)", "View Model transform should be applied to first-person items");
        reject(itemInHandRendererMixin, "getViewXRot(F)F", "View Model should not intercept first-person pitch");
        reject(itemInHandRendererMixin, "getViewYRot(F)F", "View Model should not intercept first-person yaw");
        reject(itemInHandRendererMixin, "aura.packetPitch(original)", "View Model should not reuse aura packet pitch");
        reject(itemInHandRendererMixin, "aura.packetYaw(original)", "View Model should not reuse aura packet yaw");
        reject(itemInHandRendererMixin, "applyAuraRotation", "View Model should not expose Aura Rotation hooks");
        reject(localPlayerMixin, "getViewXRot", "packet-only player mixin should not drive the viewmodel directly");
        reject(localPlayerMixin, "getViewYRot", "packet-only player mixin should not drive the viewmodel directly");
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
