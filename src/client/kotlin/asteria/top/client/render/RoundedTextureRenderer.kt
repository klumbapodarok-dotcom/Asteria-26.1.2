package asteria.top.client.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import asteria.top.client.util.RoundedRectangleGeometry
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

object RoundedTextureRenderer {
    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/rounded_texture"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/rounded_texture"))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    )

    private val fullTexturePipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/rounded_full_texture"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/rounded_full_texture"))
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
        radius: Float,
        color: Int = 0xFFFFFFFF.toInt(),
    ) {
        if (((color ushr 24) and 0xFF) <= 0 || width <= 0.05f || height <= 0.05f) return
        val texture = Minecraft.getInstance().textureManager.getTexture(textureId)
        graphics.guiRenderState.addGuiElement(
            FullTextureState(
                TextureSetup.singleTexture(texture.textureView, texture.sampler),
                Matrix3x2f(graphics.pose()),
                x,
                y,
                x + width,
                y + height,
                RoundedRectangleGeometry.stableRadius(width, height, radius),
                color,
                RoundedRectangleGeometry.antiAliasWidth(width, height),
                graphics.scissorStack.peek(),
            )
        )
    }

    fun skinRegion(
        graphics: GuiGraphicsExtractor,
        textureId: Identifier,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        uPixels: Float,
        vPixels: Float,
        regionPixels: Float = 8.0f,
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
                RoundedRectangleGeometry.stableRadius(width, height, radius),
                uPixels,
                vPixels,
                regionPixels,
                color,
                RoundedRectangleGeometry.antiAliasWidth(width, height),
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
        private val radius: Float,
        private val uPixels: Float,
        private val vPixels: Float,
        private val regionPixels: Float,
        private val color: Int,
        private val antiAliasWidth: Float,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            val encodedRadius = floor(radius.coerceIn(0.0f, 16.0f) / 16.0f * 255.0f + 0.5f).toInt().coerceIn(0, 255)
            val encodedColor = (color and 0xFF000000.toInt()) or (encodedRadius shl 16)
            consumer.addVertexWith2DPose(pose, left, top).setUv(uPixels, vPixels).setColor(encodedColor)
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(uPixels, vPixels + 1.0f).setColor(encodedColor)
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(uPixels + 1.0f, vPixels + 1.0f).setColor(encodedColor)
            consumer.addVertexWith2DPose(pose, right, top).setUv(uPixels + 1.0f, vPixels).setColor(encodedColor)
        }

        override fun pipeline(): RenderPipeline = pipeline

        override fun textureSetup(): TextureSetup = textureSetup

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

    private data class FullTextureState(
        private val textureSetup: TextureSetup,
        private val pose: Matrix3x2f,
        private val left: Float,
        private val top: Float,
        private val right: Float,
        private val bottom: Float,
        private val radius: Float,
        private val color: Int,
        private val antiAliasWidth: Float,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            val encodedRadius = floor(radius.coerceIn(0.0f, 16.0f) / 16.0f * 255.0f + 0.5f).toInt().coerceIn(0, 255)
            val encodedColor = (color and 0xFF000000.toInt()) or (encodedRadius shl 16)
            consumer.addVertexWith2DPose(pose, left, top).setUv(0.0f, 0.0f).setColor(encodedColor)
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(0.0f, 1.0f).setColor(encodedColor)
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(1.0f, 1.0f).setColor(encodedColor)
            consumer.addVertexWith2DPose(pose, right, top).setUv(1.0f, 0.0f).setColor(encodedColor)
        }

        override fun pipeline(): RenderPipeline = fullTexturePipeline

        override fun textureSetup(): TextureSetup = textureSetup

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
