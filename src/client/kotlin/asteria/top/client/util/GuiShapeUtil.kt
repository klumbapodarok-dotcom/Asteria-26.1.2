package asteria.top.client.util

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import asteria.top.client.math.RoundingRule
import asteria.top.client.render.RoundingType
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

object GuiShapeUtil {
    private val roundedRectPipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/rounded_rect"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/rounded_rect"))
            .build()
    )
    private val roundedRectSquirclePipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/rounded_rect_squircle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/rounded_rect_squircle"))
            .build()
    )
    private val roundedRectSharpPipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/rounded_rect_sharp"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/rounded_rect_sharp"))
            .build()
    )
    private val circlePipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/circle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/circle"))
            .build()
    )
    private val roundedOutlinePipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/rounded_outline"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/rounded_outline"))
            .build()
    )
    private val softShadowPipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/soft_shadow"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/soft_shadow"))
            .build()
    )

    fun softShadow(
        graphics: GuiGraphicsExtractor,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        size: Float,
        opacity: Float,
        smoothness: Float,
    ) {
        if (size <= 0.05f || opacity <= 0.001f) return
        val spread = size.coerceAtLeast(0.1f)
        val x0 = min(left, right) - spread
        val y0 = min(top, bottom) - spread
        val x1 = max(left, right) + spread
        val y1 = max(top, bottom) + spread
        graphics.guiRenderState.addGuiElement(
            ShaderSoftShadowState(
                Matrix3x2f(graphics.pose()),
                x0,
                y0,
                x1,
                y1,
                radius,
                spread,
                opacity.coerceIn(0.0f, 1.0f),
                smoothness.coerceIn(0.0f, 1.0f),
                graphics.scissorStack.peek(),
            )
        )
    }

    fun roundedFill(
        graphics: GuiGraphicsExtractor,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        radius: Int,
        color: Int,
        roundingRule: RoundingRule = RoundingUtil.ROUNDING_RULE,
    ) {
        roundedFill(graphics, left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), radius.toFloat(), color, roundingRule)
    }

    fun roundedFill(
        graphics: GuiGraphicsExtractor,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        color: Int,
        roundingRule: RoundingRule = RoundingUtil.ROUNDING_RULE,
        sharp: Boolean = false,
    ) {
        if (((color ushr 24) and 0xFF) <= 0) return

        val x0 = min(left, right)
        val y0 = min(top, bottom)
        val x1 = max(left, right)
        val y1 = max(top, bottom)
        val width = x1 - x0
        val height = y1 - y0
        if (width <= 0.05f || height <= 0.05f) return

        val resolvedRadius = RoundedRectangleGeometry.stableRadius(width, height, radius)
        graphics.guiRenderState.addGuiElement(
            ShaderRoundedRectState(
                if (sharp) roundedRectSharpPipeline else roundedRectPipeline,
                Matrix3x2f(graphics.pose()),
                x0,
                y0,
                x1,
                y1,
                resolvedRadius,
                color,
                RoundedRectangleGeometry.antiAliasWidth(width, height),
                graphics.scissorStack.peek(),
            )
        )
    }

    /**
     * A rounded rect whose colour runs from [leftColor] at its left edge to
     * [rightColor] at its right one. The rounding shader already multiplies the
     * interpolated vertex colour, so the gradient is the quad's own corner
     * colours rather than a second pipeline: this is the same shape [roundedFill]
     * draws, only with the two sides coloured apart.
     */
    fun roundedHorizontalGradientFill(
        graphics: GuiGraphicsExtractor,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        leftColor: Int,
        rightColor: Int,
        sharp: Boolean = false,
    ) {
        if (((leftColor ushr 24) and 0xFF) <= 0 && ((rightColor ushr 24) and 0xFF) <= 0) return

        val x0 = min(left, right)
        val y0 = min(top, bottom)
        val x1 = max(left, right)
        val y1 = max(top, bottom)
        val width = x1 - x0
        val height = y1 - y0
        if (width <= 0.05f || height <= 0.05f) return

        graphics.guiRenderState.addGuiElement(
            ShaderRoundedGradientState(
                if (sharp) roundedRectSharpPipeline else roundedRectPipeline,
                Matrix3x2f(graphics.pose()),
                x0,
                y0,
                x1,
                y1,
                RoundedRectangleGeometry.stableRadius(width, height, radius),
                leftColor,
                rightColor,
                RoundedRectangleGeometry.antiAliasWidth(width, height),
                graphics.scissorStack.peek(),
            )
        )
    }

    fun roundedVerticalGradientFill(
        graphics: GuiGraphicsExtractor,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        topColor: Int,
        bottomColor: Int,
        sharp: Boolean = false,
    ) {
        if (((topColor ushr 24) and 0xFF) <= 0 && ((bottomColor ushr 24) and 0xFF) <= 0) return
        val x0 = min(left, right)
        val y0 = min(top, bottom)
        val x1 = max(left, right)
        val y1 = max(top, bottom)
        val width = x1 - x0
        val height = y1 - y0
        if (width <= 0.05f || height <= 0.05f) return

        graphics.guiRenderState.addGuiElement(
            ShaderRoundedVerticalGradientState(
                if (sharp) roundedRectSharpPipeline else roundedRectPipeline,
                Matrix3x2f(graphics.pose()),
                x0, y0, x1, y1,
                RoundedRectangleGeometry.stableRadius(width, height, radius),
                topColor,
                bottomColor,
                RoundedRectangleGeometry.antiAliasWidth(width, height),
                graphics.scissorStack.peek(),
            )
        )
    }

    fun roundedFourColorGradientFill(
        graphics: GuiGraphicsExtractor,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        topLeftColor: Int,
        topRightColor: Int,
        bottomRightColor: Int,
        bottomLeftColor: Int,
    ) {
        val x0 = min(left, right)
        val y0 = min(top, bottom)
        val x1 = max(left, right)
        val y1 = max(top, bottom)
        val width = x1 - x0
        val height = y1 - y0
        if (width <= 0.05f || height <= 0.05f) return
        graphics.guiRenderState.addGuiElement(
            ShaderRoundedFourColorState(
                roundedRectPipeline,
                Matrix3x2f(graphics.pose()),
                x0, y0, x1, y1,
                RoundedRectangleGeometry.stableRadius(width, height, radius),
                topLeftColor, topRightColor, bottomRightColor, bottomLeftColor,
                RoundedRectangleGeometry.antiAliasWidth(width, height),
                graphics.scissorStack.peek(),
            )
        )
    }

    fun circleFill(
        graphics: GuiGraphicsExtractor,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int,
    ) {
        if (((color ushr 24) and 0xFF) <= 0 || radius <= 0.05f) return

        val x0 = centerX - radius
        val y0 = centerY - radius
        val x1 = centerX + radius
        val y1 = centerY + radius
        graphics.guiRenderState.addGuiElement(
            ShaderCircleState(
                circlePipeline,
                Matrix3x2f(graphics.pose()),
                x0,
                y0,
                x1,
                y1,
                color,
                RoundedRectangleGeometry.antiAliasWidth(radius * 2.0f, radius * 2.0f),
                graphics.scissorStack.peek(),
            )
        )
    }

    fun roundedStroke(
        graphics: GuiGraphicsExtractor,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        color: Int,
    ) {
        if (((color ushr 24) and 0xFF) <= 0) return

        val x0 = min(left, right)
        val y0 = min(top, bottom)
        val x1 = max(left, right)
        val y1 = max(top, bottom)
        val width = x1 - x0
        val height = y1 - y0
        if (width <= 0.05f || height <= 0.05f) return

        val resolvedRadius = RoundedRectangleGeometry.stableRadius(width, height, radius)
        graphics.guiRenderState.addGuiElement(
            ShaderRoundedRectState(
                roundedOutlinePipeline,
                Matrix3x2f(graphics.pose()),
                x0,
                y0,
                x1,
                y1,
                resolvedRadius,
                color,
                RoundedRectangleGeometry.antiAliasWidth(width, height),
                graphics.scissorStack.peek(),
            )
        )
    }

    private data class ShaderRoundedRectState(
        private val pipeline: RenderPipeline,
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
            val smallestAxis = min(right - left, bottom - top).coerceAtLeast(0.001f)
            val normalizedRadius = (radius / smallestAxis).coerceIn(0.0f, 0.5f)
            val encodedRadius = floor(normalizedRadius * 4096.0f + 0.5f)
            consumer.addVertexWith2DPose(pose, left, top).setUv(0.0f, encodedRadius).setColor(color)
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(0.0f, encodedRadius + 1.0f).setColor(color)
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(1.0f, encodedRadius + 1.0f).setColor(color)
            consumer.addVertexWith2DPose(pose, right, top).setUv(1.0f, encodedRadius).setColor(color)
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

    private data class ShaderRoundedGradientState(
        private val pipeline: RenderPipeline,
        private val pose: Matrix3x2f,
        private val left: Float,
        private val top: Float,
        private val right: Float,
        private val bottom: Float,
        private val radius: Float,
        private val leftColor: Int,
        private val rightColor: Int,
        private val antiAliasWidth: Float,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            val smallestAxis = min(right - left, bottom - top).coerceAtLeast(0.001f)
            val normalizedRadius = (radius / smallestAxis).coerceIn(0.0f, 0.5f)
            val encodedRadius = floor(normalizedRadius * 4096.0f + 0.5f)
            consumer.addVertexWith2DPose(pose, left, top).setUv(0.0f, encodedRadius).setColor(leftColor)
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(0.0f, encodedRadius + 1.0f).setColor(leftColor)
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(1.0f, encodedRadius + 1.0f).setColor(rightColor)
            consumer.addVertexWith2DPose(pose, right, top).setUv(1.0f, encodedRadius).setColor(rightColor)
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

    private data class ShaderRoundedVerticalGradientState(
        private val pipeline: RenderPipeline,
        private val pose: Matrix3x2f,
        private val left: Float,
        private val top: Float,
        private val right: Float,
        private val bottom: Float,
        private val radius: Float,
        private val topColor: Int,
        private val bottomColor: Int,
        private val antiAliasWidth: Float,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            val smallestAxis = min(right - left, bottom - top).coerceAtLeast(0.001f)
            val normalizedRadius = (radius / smallestAxis).coerceIn(0.0f, 0.5f)
            val encodedRadius = floor(normalizedRadius * 4096.0f + 0.5f)
            consumer.addVertexWith2DPose(pose, left, top).setUv(0.0f, encodedRadius).setColor(topColor)
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(0.0f, encodedRadius + 1.0f).setColor(bottomColor)
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(1.0f, encodedRadius + 1.0f).setColor(bottomColor)
            consumer.addVertexWith2DPose(pose, right, top).setUv(1.0f, encodedRadius).setColor(topColor)
        }

        override fun pipeline(): RenderPipeline = pipeline
        override fun textureSetup(): TextureSetup = TextureSetup.noTexture()
        override fun scissorArea(): ScreenRectangle? = scissorArea
        override fun bounds(): ScreenRectangle {
            val aa = antiAliasWidth + 1.0f
            return ScreenRectangle(
                floor(left - aa).toInt(),
                floor(top - aa).toInt(),
                max(1, ceil((right - left) + aa * 2.0f).toInt()),
                max(1, ceil((bottom - top) + aa * 2.0f).toInt()),
            )
        }
    }

    private data class ShaderRoundedFourColorState(
        private val pipeline: RenderPipeline,
        private val pose: Matrix3x2f,
        private val left: Float,
        private val top: Float,
        private val right: Float,
        private val bottom: Float,
        private val radius: Float,
        private val topLeftColor: Int,
        private val topRightColor: Int,
        private val bottomRightColor: Int,
        private val bottomLeftColor: Int,
        private val antiAliasWidth: Float,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            val smallestAxis = min(right - left, bottom - top).coerceAtLeast(0.001f)
            val encodedRadius = floor((radius / smallestAxis).coerceIn(0.0f, 0.5f) * 4096.0f + 0.5f)
            consumer.addVertexWith2DPose(pose, left, top).setUv(0.0f, encodedRadius).setColor(topLeftColor)
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(0.0f, encodedRadius + 1.0f).setColor(bottomLeftColor)
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(1.0f, encodedRadius + 1.0f).setColor(bottomRightColor)
            consumer.addVertexWith2DPose(pose, right, top).setUv(1.0f, encodedRadius).setColor(topRightColor)
        }

        override fun pipeline(): RenderPipeline = pipeline
        override fun textureSetup(): TextureSetup = TextureSetup.noTexture()
        override fun scissorArea(): ScreenRectangle? = scissorArea
        override fun bounds(): ScreenRectangle {
            val aa = antiAliasWidth + 1.0f
            return ScreenRectangle(
                floor(left - aa).toInt(), floor(top - aa).toInt(),
                max(1, ceil((right - left) + aa * 2.0f).toInt()),
                max(1, ceil((bottom - top) + aa * 2.0f).toInt()),
            )
        }
    }

    private data class ShaderCircleState(
        private val pipeline: RenderPipeline,
        private val pose: Matrix3x2f,
        private val left: Float,
        private val top: Float,
        private val right: Float,
        private val bottom: Float,
        private val color: Int,
        private val antiAliasWidth: Float,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            consumer.addVertexWith2DPose(pose, left, top).setUv(0.0f, 0.0f).setColor(color)
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(0.0f, 1.0f).setColor(color)
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(1.0f, 1.0f).setColor(color)
            consumer.addVertexWith2DPose(pose, right, top).setUv(1.0f, 0.0f).setColor(color)
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

    private data class ShaderSoftShadowState(
        private val pose: Matrix3x2f,
        private val left: Float,
        private val top: Float,
        private val right: Float,
        private val bottom: Float,
        private val radius: Float,
        private val spread: Float,
        private val opacity: Float,
        private val smoothness: Float,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            val smallestAxis = min(right - left, bottom - top).coerceAtLeast(0.001f)
            val encodedSpread = floor((spread / smallestAxis).coerceIn(0.0f, 0.49f) * 4096.0f + 0.5f)
            val encodedRadius = floor((radius / smallestAxis).coerceIn(0.0f, 0.5f) * 4096.0f + 0.5f)
            val alpha = (opacity * 255.0f).toInt().coerceIn(0, 255)
            val smooth = (smoothness * 255.0f).toInt().coerceIn(0, 255)
            val encodedColor = (alpha shl 24) or (smooth shl 16)
            consumer.addVertexWith2DPose(pose, left, top).setUv(encodedSpread, encodedRadius).setColor(encodedColor)
            consumer.addVertexWith2DPose(pose, left, bottom).setUv(encodedSpread, encodedRadius + 1.0f).setColor(encodedColor)
            consumer.addVertexWith2DPose(pose, right, bottom).setUv(encodedSpread + 1.0f, encodedRadius + 1.0f).setColor(encodedColor)
            consumer.addVertexWith2DPose(pose, right, top).setUv(encodedSpread + 1.0f, encodedRadius).setColor(encodedColor)
        }

        override fun pipeline(): RenderPipeline = softShadowPipeline
        override fun textureSetup(): TextureSetup = TextureSetup.noTexture()
        override fun scissorArea(): ScreenRectangle? = scissorArea
        override fun bounds(): ScreenRectangle = ScreenRectangle(
            floor(left).toInt(),
            floor(top).toInt(),
            max(1, ceil(right - left).toInt()),
            max(1, ceil(bottom - top).toInt()),
        )
    }
}
