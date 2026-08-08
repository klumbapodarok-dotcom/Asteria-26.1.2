package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TargetEspContractTest {
    public static void main(String[] args) throws Exception {
        String module = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/visual/TargetEspModule.kt"));
        String setting = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/setting/ModuleSetting.kt"));
        String overlay = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/AsteriaOverlay.kt"));
        String manager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/ModuleManager.kt"));
        String client = Files.readString(Path.of("src/client/kotlin/asteria/top/client/AsteriaClient.kt"));
        String shader = Files.readString(Path.of("src/main/resources/assets/asteria/shaders/core/target_ghost.fsh"));

        require(module, "class TargetEspModule : Module(", "Target ESP module should exist");
        require(module, "name = \"Target ESP\"", "Target ESP should keep the expected module name");
        require(module, "category = ModuleCategory.VISUALS", "Target ESP should be a visual module");
        require(module, "EnumSetting(\"Mode\", Mode.entries.toTypedArray(), Mode.GHOSTS)", "Target ESP should default to Ghosts");
        require(module, "enum class Mode(val label: String)", "Target ESP modes should be named like old modes");
        require(module, "GHOSTS(\"Ghosts\")", "Ghosts mode should be present");
        require(module, "CRYSTALS(\"Crystals\")", "Crystals mode should be available");
        require(module, "BooleanSetting(\"Ghost Trail\", false).visibleWhen { mode.value == Mode.GHOSTS }", "Ghost Trail setting should keep old name/default and only show in Ghosts");
        require(module, "FloatSetting(\"Trail Length\", 3.0f, 1.0f, 12.0f, 0.25f).visibleWhen { mode.value == Mode.GHOSTS }", "Trail Length setting should use a restrained range and only show in Ghosts");
        require(module, "FloatSetting(\"Rotation Speed\", 8.0f, 1.0f, 18.0f, 0.25f).visibleWhen { mode.value == Mode.GHOSTS }", "Rotation Speed setting should use a restrained range and only show in Ghosts");
        require(module, "FloatSetting(\"Radius\", 0.82f, 0.35f, 1.45f, 0.025f).visibleWhen { mode.value == Mode.GHOSTS }", "Radius setting should use a restrained range and only show in Ghosts");
        require(module, "FloatSetting(\"Head Size\", 0.095f, 0.035f, 0.22f, 0.005f).visibleWhen { mode.value == Mode.GHOSTS }", "Head Size setting should use a restrained range and only show in Ghosts");
        require(module, "FloatSetting(\"Trail Size\", 0.055f, 0.02f, 0.14f, 0.005f).visibleWhen { mode.value == Mode.GHOSTS }", "Trail Size setting should use a restrained range and only show in Ghosts");
        reject(module, "100.0f", "Target ESP settings should not allow extreme 100-value ranges");
        reject(module, "\"Rotate speed\"", "Crystal rotate setting should be removed");
        reject(module, "\"Spiky\"", "Crystal spiky setting should be removed");
        reject(module, "\"Spiky only on hit\"", "Crystal spiky-on-hit setting should be removed");
        require(module, "ModuleManager.killaura.target ?: ModuleManager.backtrack.target", "Target ESP should render the current combat target");
        require(module, "targetCenter(target).subtract(cameraPos)", "Target ESP geometry should be camera-relative like the old renderer");
        reject(module, "val pose = poseStack.last()", "Camera-relative crystal vertices should not be multiplied by the level render pose stack");
        reject(module, "pose.mulPose(poseStack.last().pose())", "Camera-relative ghost billboards should not be multiplied by the level render pose stack");
        require(module, "Mode.CRYSTALS -> renderCrystalOrbit(target, poseStack, cameraPos)", "Crystals mode should render the imported orbit visual");
        require(module, "private fun renderCrystalOrbit(", "Crystal orbit renderer should be present");
        require(module, "Mode.GHOSTS -> renderGhostPaths(target, System.currentTimeMillis(), poseStack, cameraPos)", "Ghosts should render through the multi-path old renderer port");
        require(module, "enum class GhostPathMode(val label: String)", "Ghosts should expose the old path mode selector");
        require(module, "DEFAULT(\"Default\")", "Default ghost path mode should be present");
        require(module, "TRIANGLE(\"Triangle\")", "Triangle ghost path mode should be present");
        require(module, "COLUMN(\"Column\")", "Column ghost path mode should be present");
        require(module, "SPIRAL(\"Spiral\")", "Spiral ghost path mode should be present");
        require(module, "DOUBLE_HELIX(\"Double Helix\")", "Double Helix ghost path mode should be present");
        require(module, "TRIPLE_HELIX(\"Triple Helix\")", "Triple Helix ghost path mode should be present");
        require(module, "when (ghostPathMode.value)", "Ghost rendering should dispatch by selected path mode");
        require(module, "else -> 3", "Triangle/default/column/triple-helix ghosts should keep three path lanes");
        require(module, "ghostIndex == 0", "Triangle path branch 0 should be ported");
        require(module, "ghostIndex == 1", "Triangle path branch 1 should be ported");
        require(module, "else ->", "Triangle path branch 2 should be ported");
        reject(module, "addCrystalMesh(", "Crystal mesh builder should be removed");
        reject(module, "updateSpikyHitAnimation(", "Crystal spiky animation should be removed");
        require(module, "withFragmentShader(Identifier.fromNamespaceAndPath(\"asteria\", \"core/target_ghost\"))", "Ghosts should use the custom ghost shader");
        require(module, "private val ghostPipeline: RenderPipeline = RenderPipelines.register", "Ghosts should have their own procedural pipeline");
        require(module, ".withColorTargetState(ColorTargetState(BlendFunction.ADDITIVE))", "Ghosts should use old additive blending");
        reject(module, "GLOW_TEXTURE", "Crystal bloom texture should be removed");
        reject(module, "Identifier.fromNamespaceAndPath(\"asteria\", \"images/target/ghosts.png\")", "Crystal bloom asset should be unused");
        reject(module, "Identifier.fromNamespaceAndPath(\"asteria\", \"textures/target_ghosts.png\")", "Normal procedural ghosts should not use the old texture asset");
        require(module, "BlendFunction.ADDITIVE", "Ghosts should use additive blending");
        require(module, "renderTexturedBillboards(", "Ghosts should render billboard quads");
        reject(module, "renderColoredTriangles(", "Crystals should render no triangle meshes");
        reject(module, "Gizmos.line(", "Target ESP should not be a gizmo-line approximation");

        require(manager, "val targetEsp = TargetEspModule()", "ModuleManager should expose Target ESP");
        require(manager, "targetEsp,", "ModuleManager should register Target ESP");
        require(client, "LevelRenderEvents.END_MAIN.register { context -> ModuleManager.targetEsp.renderGizmos(context) }", "Target ESP should render after the main world pass for direct framebuffer drawing");
        require(setting, "fun visibleWhen(predicate: () -> Boolean)", "Module settings should support mode-specific visibility");
        require(setting, "fun isVisible(): Boolean", "Module settings should expose visibility for the overlay");
        require(overlay, "module.settings.filter { it.isVisible() }.forEach { setting ->", "Overlay should hide settings whose mode predicate is false");

        reject(shader, "uniform sampler2D Sampler0;", "Normal ghost shader should be procedural like the old renderer");
        require(shader, "if (length(p) > 1.0) {\n        discard;\n    }", "Ghost shader should discard quad corners so ghosts are circular");
        require(shader, "float radius = 0.00005", "Ghost shader should preserve the old tiny core radius");
        require(shader, "float glowSize = 1.05", "Ghost shader should keep a compact glow extent");
        require(shader, "fragColor = vec4(col, alpha * vertexColor.a)", "Ghost shader should output procedural additive color");
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
