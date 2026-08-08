package asteria.top.client.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
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

object TextureRenderer {
    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/tinted_texture"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/tinted_texture"))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    )

    fun draw(
        graphics: GuiGraphicsExtractor,
        textureId: Identifier,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int = 0xFFFFFFFF.toInt(),
    ) {
        if (((color ushr 24) and 0xFF) <= 0 || width <= 0.05f || height <= 0.05f) return
        val texture = Minecraft.getInstance().textureManager.getTexture(textureId)
        graphics.guiRenderState.addGuiElement(
            State(
                TextureSetup.singleTexture(texture.textureView, texture.sampler),
                Matrix3x2f(graphics.pose()),
                x,
                y,
                x + width,
                y + height,
                color,
                graphics.scissorStack.peek(),
            )
        )
    }

    private data class State(
        private val textureSetup: TextureSetup,
        private val pose: Matrix3x2f,
        private val left: Float,
        private val top: Float,
        private val right: Float,
        private val bottom: Float,
        private val color: Int,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            consumer.addVertexWith2DPose(pose, left, top).setUv(0.0f, 0.0f).setColor(color)
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(0.0f, 1.0f).setColor(color)
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(1.0f, 1.0f).setColor(color)
            consumer.addVertexWith2DPose(pose, right, top).setUv(1.0f, 0.0f).setColor(color)
        }

        override fun pipeline(): RenderPipeline = pipeline

        override fun textureSetup(): TextureSetup = textureSetup

        override fun scissorArea(): ScreenRectangle? = scissorArea

        override fun bounds(): ScreenRectangle {
            val x = floor(min(left, right)).toInt()
            val y = floor(min(top, bottom)).toInt()
            val width = max(1, ceil(kotlin.math.abs(right - left)).toInt())
            val height = max(1, ceil(kotlin.math.abs(bottom - top)).toInt())
            return ScreenRectangle(x, y, width, height)
        }
    }
}
