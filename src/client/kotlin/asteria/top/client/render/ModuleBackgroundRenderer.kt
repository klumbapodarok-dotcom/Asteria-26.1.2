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

object ModuleBackgroundRenderer {
    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/module_background"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/module_background"))
            .build()
    )

    fun draw(graphics: GuiGraphicsExtractor, x: Float, y: Float, width: Float, height: Float, radius: Float) {
        if (width <= 0.05f || height <= 0.05f) return
        graphics.guiRenderState.addGuiElement(
            State(
                Matrix3x2f(graphics.pose()),
                x,
                y,
                x + width,
                y + height,
                RoundedRectangleGeometry.stableRadius(width, height, radius),
                RoundedRectangleGeometry.antiAliasWidth(width, height),
                graphics.scissorStack.peek(),
            )
        )
    }

    private data class State(
        private val pose: Matrix3x2f,
        private val left: Float,
        private val top: Float,
        private val right: Float,
        private val bottom: Float,
        private val radius: Float,
        private val antiAliasWidth: Float,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            val encodedRadius = floor(radius.coerceAtLeast(0.0f) * 256.0f + 0.5f)
            consumer.addVertexWith2DPose(pose, left, top).setUv(0.0f, encodedRadius).setColor(0xFFFFFFFF.toInt())
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(0.0f, encodedRadius + 1.0f).setColor(0xFFFFFFFF.toInt())
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(1.0f, encodedRadius + 1.0f).setColor(0xFFFFFFFF.toInt())
            consumer.addVertexWith2DPose(pose, right, top).setUv(1.0f, encodedRadius).setColor(0xFFFFFFFF.toInt())
        }

        override fun pipeline(): RenderPipeline = pipeline

        override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

        override fun scissorArea(): ScreenRectangle? = scissorArea

        override fun bounds(): ScreenRectangle {
            val aa = antiAliasWidth + 1.0f
            val x = floor(left - aa).toInt()
            val y = floor(top - aa).toInt()
            val width = max(1, ceil((right - left) + aa * 2.0f).toInt())
            val height = max(1, ceil((bottom - top) + aa * 2.0f).toInt())
            return ScreenRectangle(x, y, width, height)
        }
    }
}
