package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FakeLagRenderContractTest {
    public static void main(String[] args) throws Exception {
        String fakeLag = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/player/FakeLagModule.kt"));
        String livingEntityMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/LivingEntityRendererMixin.kt"));
        String glacierClient = Files.readString(Path.of("src/client/kotlin/asteria/top/client/AsteriaClient.kt"));

        require(fakeLag, "private val render = setting(BooleanSetting(\"Render\", true))", "fake lag should expose a render toggle");
        require(fakeLag, "private val fillAlpha = setting(FloatSetting(\"Fill alpha\"", "fake lag should expose fill alpha control");
        require(fakeLag, "private var renderFromPos = Vec3.ZERO", "fake lag should retain render transition start");
        require(fakeLag, "private var renderToPos = Vec3.ZERO", "fake lag should retain render transition target");
        require(fakeLag, "private var renderFromYaw = 0.0f", "fake lag should retain render transition yaw start");
        require(fakeLag, "private var renderToYaw = 0.0f", "fake lag should retain render transition yaw target");
        require(fakeLag, "private var renderFromPitch = 0.0f", "fake lag should retain render transition pitch start");
        require(fakeLag, "private var renderToPitch = 0.0f", "fake lag should retain render transition pitch target");
        require(fakeLag, "private var renderSwitchAt = 0L", "fake lag should retain render transition timing");
        require(fakeLag, "private var renderTransitionInitialized", "fake lag should track render transition state");
        require(fakeLag, "private fun startTransition(", "fake lag should support animated render transitions");
        require(fakeLag, "fun applyRenderState(", "fake lag should expose a render-state adapter for the living-entity mixin");
        require(fakeLag, "fun renderGizmos()", "fake lag should expose a gizmo render entrypoint");
        require(fakeLag, "private fun renderGhostBox(", "fake lag should render the ghost box");
        require(fakeLag, "private fun computeGhostBox(", "fake lag should compute the frozen player box");
        require(fakeLag, "private fun getInterpolatedPos()", "fake lag should interpolate ghost position");
        require(fakeLag, "private fun getInterpolatedYaw()", "fake lag should interpolate ghost yaw");
        require(fakeLag, "private fun getInterpolatedPitch()", "fake lag should interpolate ghost pitch");
        require(fakeLag, "private fun getTransitionProgress()", "fake lag should animate render transitions");
        require(fakeLag, "fun flushNow()", "fake lag should keep an explicit flush path for other modules");
        require(fakeLag, "private fun computeAnimatedAlphaFactor()", "fake lag should scale the render glow with the last lag duration");
        require(fakeLag, "Gizmos.cuboid(box, style, false).setAlwaysOnTop()", "fake lag should draw gradient-corner cuboids for the render box");
        require(fakeLag, "GizmoStyle.strokeAndFill(", "fake lag should draw a layered stroke-and-fill box");

        require(livingEntityMixin, "ModuleManager.fakeLag.applyRenderState(entity, state)", "living entity renderer should delegate fakelag state replacement");
        require(glacierClient, "LevelRenderEvents.BEFORE_GIZMOS", "render hook should use Fabric LevelRenderEvents.BEFORE_GIZMOS");
        require(glacierClient, "ModuleManager.fakeLag.renderGizmos()", "render hook should delegate fakelag visuals to the module");
    }

    private static void require(String source, String token, String label) {
        if (!source.contains(token)) {
            throw new AssertionError(label + " is missing: " + token);
        }
    }

}
