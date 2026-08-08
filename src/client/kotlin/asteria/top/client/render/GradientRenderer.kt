package asteria.top.client.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import asteria.top.client.util.RoundedRectangleGeometry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.resources.Identifier
import org.joml.Matrix3x2f
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object GradientRenderer {
    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/gradient_rect"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/gradient_rect"))
            .build()
    )

    fun rect(
        graphics: GuiGraphicsExtractor,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        alpha: Float = 1.0f,
        rotationDegrees: Float = 0.0f,
    ) {
        if (alpha <= 0.0f || width <= 0.05f || height <= 0.05f) return

        val resolvedRadius = RoundedRectangleGeometry.stableRadius(width, height, radius)
        graphics.guiRenderState.addGuiElement(
            GradientRectState(
                Matrix3x2f(graphics.pose()),
                x,
                y,
                x + width,
                y + height,
                resolvedRadius,
                rotationDegrees,
                ((alpha.coerceIn(0.0f, 1.0f) * 255.0f).toInt() shl 24) or 0x00FFFFFF,
                RoundedRectangleGeometry.antiAliasWidth(width, height),
                graphics.scissorStack.peek(),
            )
        )
    }

    private data class GradientRectState(
        private val pose: Matrix3x2f,
        private val left: Float,
        private val top: Float,
        private val right: Float,
        private val bottom: Float,
        private val radius: Float,
        private val rotationDegrees: Float,
        private val color: Int,
        private val antiAliasWidth: Float,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            val encodedRadius = floor(radius.coerceAtLeast(0.0f) * 256.0f + 0.5f)
            val encodedRotation = floor(((rotationDegrees % 360.0f + 360.0f) % 360.0f) * 256.0f + 0.5f)
            consumer.addVertexWith2DPose(pose, left, top).setUv(encodedRotation, encodedRadius).setColor(color)
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(encodedRotation, encodedRadius + 1.0f).setColor(color)
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(encodedRotation + 1.0f, encodedRadius + 1.0f).setColor(color)
            consumer.addVertexWith2DPose(pose, right, top).setUv(encodedRotation + 1.0f, encodedRadius).setColor(color)
        }

        override fun pipeline(): RenderPipeline = pipeline

        override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

        override fun scissorArea(): ScreenRectangle? = scissorArea

        override fun bounds(): ScreenRectangle {
            val aa = antiAliasWidth + 1.0f
            val x = floor(min(left, right) - aa).toInt()
            val y = floor(min(top, bottom) - aa).toInt()
            val width = max(1, ceil(kotlin.math.abs(right - left) + aa * 2.0f).toInt())
            val height = max(1, ceil(kotlin.math.abs(bottom - top) + aa * 2.0f).toInt())
            return ScreenRectangle(x, y, width, height)
        }
    }
}
