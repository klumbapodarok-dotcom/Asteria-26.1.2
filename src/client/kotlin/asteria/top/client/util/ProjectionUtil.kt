package asteria.top.client.util

import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object ProjectionUtil {
    data class Snapshot(
        val cameraPos: Vec3,
        val xRot: Float,
        val yRot: Float,
        val fov: Float,
        val viewportWidth: Float,
        val viewportHeight: Float,
        val bobView: Boolean,
        val isPlayer: Boolean,
        val walkDistance: Float,
        val bob: Float,
    )

    @Volatile
    private var snapshot: Snapshot? = null

    // worldFov must be Camera.getFov() — the FOV the level pass actually projects with.
    // CameraRenderState.hudFov is a fixed 70 used only for the hand/HUD projection, so projecting
    // world points with it pushed tags outward from screen centre whenever the player's FOV differs.
    fun capture(cameraRenderState: CameraRenderState, worldFov: Float, bobViewEnabled: Boolean) {
        val pos = cameraRenderState.pos
        val window = Minecraft.getInstance().window
        val width = window.guiScaledWidth.toFloat()
        val height = window.guiScaledHeight.toFloat()
        if (width <= 0.0f || height <= 0.0f) return

        val entityState = cameraRenderState.entityRenderState

        snapshot = Snapshot(
            pos,
            cameraRenderState.xRot,
            cameraRenderState.yRot,
            worldFov,
            width,
            height,
            bobViewEnabled,
            entityState?.isPlayer ?: false,
            entityState?.backwardsInterpolatedWalkDistance ?: 0.0f,
            entityState?.bob ?: 0.0f,
        )
    }

    fun project(vec: Vec3): Vector2f? {
        val state = snapshot ?: return null
        val cameraRotation = Axis.YP.rotationDegrees(-state.yRot)
            .mul(Axis.XP.rotationDegrees(state.xRot), Quaternionf())
        cameraRotation.conjugate()

        val viewPos = Vector3f(
            (state.cameraPos.x - vec.x).toFloat(),
            (state.cameraPos.y - vec.y).toFloat(),
            (state.cameraPos.z - vec.z).toFloat(),
        )
        viewPos.rotate(cameraRotation)
        applyViewBob(state, viewPos)

        if (!viewPos.x.isFinite() || !viewPos.y.isFinite() || !viewPos.z.isFinite() || viewPos.z >= -1.0e-4f) {
            return null
        }

        val fov = state.fov.toDouble()
        if (!fov.isFinite() || fov <= 0.0) return null

        val halfWidth = state.viewportWidth * 0.5f
        val halfHeight = state.viewportHeight * 0.5f
        val tanHalfFov = tan(Math.toRadians(fov * 0.5))
        if (!tanHalfFov.isFinite() || abs(tanHalfFov) < 1.0e-4) return null

        val scale = halfHeight / (viewPos.z * tanHalfFov)

        return Vector2f(
            -viewPos.x * scale.toFloat() + halfWidth,
            halfHeight - viewPos.y * scale.toFloat(),
        )
    }

    // The world pass applies walk bobbing to the view matrix (GameRenderer.bobView) rather than to
    // the camera position, so anything projected purely from the camera position drifts against the
    // world while walking. Vanilla builds it as translate(t) * rotZ * rotX in view space; our viewPos
    // is the negated view-space vector, so the matching transform here is rotZ(rotX(v)) - t.
    private fun applyViewBob(state: Snapshot, viewPos: Vector3f) {
        if (!state.bobView || !state.isPlayer || state.bob == 0.0f) return

        val bob = state.bob
        val walk = state.walkDistance * Math.PI
        val sinWalk = sin(walk).toFloat()
        val cosWalk = cos(walk).toFloat()

        val angleX = Math.toRadians(abs(cos(walk - 0.2) * bob) * 5.0).toFloat()
        val angleZ = Math.toRadians((sinWalk * bob * 3.0f).toDouble()).toFloat()

        viewPos.rotateX(angleX)
        viewPos.rotateZ(angleZ)
        viewPos.x -= sinWalk * bob * 0.5f
        viewPos.y += abs(cosWalk * bob)
    }
}
