package asteria.top.client.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object MsdfIconRenderer {
    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/msdf_icon"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/msdf_icon"))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    )

    private val sharpPipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/msdf_icon_sharp"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/msdf_icon_sharp"))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    )

    private val crispPipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/msdf_icon_crisp"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/msdf_icon_crisp"))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    )

    /**
     * How an icon's edge is resolved. The distance range has to match how the
     * png was baked — the older atlases hold 12 texels, the ones gen-icons.ps1
     * produces hold 6 — and getting it wrong does not fail loudly, it just
     * softens or hardens the edge.
     *
     * [SoftRange12] is the original smoothstep resolve, which spreads the edge
     * over a ramp and reads blurry at HUD sizes; it stays the default only
     * because the menus were laid out against it.
     */
    enum class Edge { SoftRange12, CrispRange12, CrispRange6 }

    fun draw(
        graphics: GuiGraphicsExtractor,
        textureId: Identifier,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        rotationDegrees: Float = 0.0f,
        edge: Edge = Edge.SoftRange12,
    ) {
        if (((color ushr 24) and 0xFF) == 0 || width <= 0.05f || height <= 0.05f) return
        val texture = Minecraft.getInstance().textureManager.getTexture(textureId)
        graphics.guiRenderState.addGuiElement(
            State(
                TextureSetup.singleTexture(
                    texture.textureView,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                ),
                Matrix3x2f(graphics.pose()),
                x,
                y,
                x + width,
                y + height,
                color,
                Math.toRadians(rotationDegrees.toDouble()).toFloat(),
                graphics.scissorStack.peek(),
                edge,
            )
        )
    }

    private data class State(
        private val setup: TextureSetup,
        private val pose: Matrix3x2f,
        private val left: Float,
        private val top: Float,
        private val right: Float,
        private val bottom: Float,
        private val color: Int,
        private val rotation: Float,
        private val scissor: ScreenRectangle?,
        private val edge: Edge,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            vertex(consumer, left, top, 0.0f, 0.0f)
            vertex(consumer, left, bottom, 0.0f, 1.0f)
            vertex(consumer, right, bottom, 1.0f, 1.0f)
            vertex(consumer, right, top, 1.0f, 0.0f)
        }

        private fun vertex(consumer: VertexConsumer, x: Float, y: Float, u: Float, v: Float) {
            val centerX = (left + right) * 0.5f
            val centerY = (top + bottom) * 0.5f
            val cosine = cos(rotation)
            val sine = sin(rotation)
            val localX = x - centerX
            val localY = y - centerY
            val rotatedX = centerX + localX * cosine - localY * sine
            val rotatedY = centerY + localX * sine + localY * cosine
            consumer.addVertexWith2DPose(pose, rotatedX, rotatedY).setUv(u, v).setColor(color)
        }

        override fun pipeline(): RenderPipeline = when (edge) {
            Edge.SoftRange12 -> pipeline
            Edge.CrispRange12 -> crispPipeline
            Edge.CrispRange6 -> sharpPipeline
        }
        override fun textureSetup(): TextureSetup = setup
        override fun scissorArea(): ScreenRectangle? = scissor

        override fun bounds(): ScreenRectangle {
            val width = kotlin.math.abs(right - left)
            val height = kotlin.math.abs(bottom - top)
            val halfExtent = if (rotation == 0.0f) max(width, height) * 0.5f else sqrt(width * width + height * height) * 0.5f
            val centerX = (left + right) * 0.5f
            val centerY = (top + bottom) * 0.5f
            val x = floor(centerX - halfExtent).toInt()
            val y = floor(centerY - halfExtent).toInt()
            val extent = max(1, ceil(halfExtent * 2.0f).toInt())
            return ScreenRectangle(
                x,
                y,
                extent,
                extent,
            )
        }
    }
}
