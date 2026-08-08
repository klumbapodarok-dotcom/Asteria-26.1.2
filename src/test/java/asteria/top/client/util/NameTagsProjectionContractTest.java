package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NameTagsProjectionContractTest {
    public static void main(String[] args) throws Exception {
        String nameTags = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/visual/NameTagsModule.kt"));
        String projection = Files.readString(Path.of("src/client/kotlin/asteria/top/client/util/ProjectionUtil.kt"));
        String levelRendererMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/LevelRendererMixin.kt"));
        String clientMixins = Files.readString(Path.of("src/client/resources/asteria.client.mixins.json"));

        require(projection, "data class Snapshot", "projection utility should keep an immutable render-camera snapshot");
        require(projection, "fun capture(", "projection utility should capture camera state from level rendering");
        require(projection, "fun project(vec: Vec3): Vector2f?", "projection should project from the captured render snapshot");
        require(projection, "val xRot: Float", "projection snapshot should retain render camera pitch");
        require(projection, "val yRot: Float", "projection snapshot should retain render camera yaw");
        require(projection, "val fov: Float", "projection snapshot should retain render camera FOV");
        require(projection, "Axis.YP.rotationDegrees(-state.yRot)", "projection should use captured render yaw");
        require(projection, "Axis.XP.rotationDegrees(state.xRot)", "projection should use captured render pitch");
        require(projection, "(state.cameraPos.x - vec.x).toFloat()", "projection should preserve the known front-facing sign convention");
        reject(projection, "clip.mul(state.viewRotationMatrix)", "nametag projection should not rely on the rejected matrix path");
        reject(projection, "clip.w <= 1.0e-6f", "nametag projection should not cull every point through clip-space w");
        reject(projection, "fun project(vec: Vec3, tickDelta: Float)", "projection should not expose an ignored tickDelta parameter");
        reject(projection, "fun project(x: Double, y: Double, z: Double, tickDelta: Float)", "projection should not expose ignored tickDelta coordinates");

        require(nameTags, "ProjectionUtil.project(worldPos) ?: continue", "nametags should project using the captured render snapshot");
        reject(nameTags, "ProjectionUtil.project(worldPos, partialTick)", "nametags should not pass tick delta to projection");

        require(levelRendererMixin, "ProjectionUtil.capture(cameraRenderState, projectionMatrix)", "level renderer mixin should capture render camera state");
        require(clientMixins, "\"LevelRendererMixin\"", "level renderer mixin should be registered on the client");
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
