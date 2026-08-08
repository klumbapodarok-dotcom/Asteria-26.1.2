package asteria.top.client.render

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import kotlin.math.max
import kotlin.math.min

object FontRenderer {
    enum class Face(val textureId: Identifier, val dataId: Identifier) {
        SfRegular(
            Identifier.fromNamespaceAndPath("asteria", "fonts/sf_pro/sf_regular.png"),
            Identifier.fromNamespaceAndPath("asteria", "fonts/sf_pro/sf_regular.json"),
        ),
        SfMedium(
            Identifier.fromNamespaceAndPath("asteria", "fonts/sf_pro/sf_medium.png"),
            Identifier.fromNamespaceAndPath("asteria", "fonts/sf_pro/sf_medium.json"),
        ),
        SfSemibold(
            Identifier.fromNamespaceAndPath("asteria", "fonts/sf_pro/sf_semibold.png"),
            Identifier.fromNamespaceAndPath("asteria", "fonts/sf_pro/sf_semibold.json"),
        ),
        ClickGuiIcon(
            Identifier.fromNamespaceAndPath("asteria", "fonts/other/clickgui.png"),
            Identifier.fromNamespaceAndPath("asteria", "fonts/other/clickgui.json"),
        ),
        SettingsIcon(
            Identifier.fromNamespaceAndPath("asteria", "fonts/other/settings_icon.png"),
            Identifier.fromNamespaceAndPath("asteria", "fonts/other/settings_icon.json"),
        ),
        HudIcon(
            Identifier.fromNamespaceAndPath("asteria", "fonts/other/hud_icon.png"),
            Identifier.fromNamespaceAndPath("asteria", "fonts/other/hud_icon.json"),
        ),
    }

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/font_text"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/font_text"))
            .build()
    )

    private val cache = mutableMapOf<Face, FontData>()

    fun draw(
        graphics: GuiGraphicsExtractor,
        face: Face,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
    ) {
        if (((color ushr 24) and 0xFF) <= 0 || text.isEmpty()) return

        val data = data(face) ?: return
        val texture = Minecraft.getInstance().textureManager.getTexture(face.textureId)
        val textureSetup = TextureSetup.singleTexture(texture.textureView, texture.sampler)
        val pose = Matrix3x2f(graphics.pose())
        val baseline = y + data.baseline * size
        var cursor = x + face.offsetX(size)
        val renderY = y + face.offsetY(size)
        var previous = -1

        for (char in text) {
            val code = char.code
            val glyph = data.glyphs[code] ?: data.glyphs['?'.code] ?: continue
            data.kernings[previous]?.get(code)?.let { cursor += it * size }

            if (glyph.hasBounds) {
                val x0 = cursor + glyph.planeLeft * size
                val y0 = renderY + data.baseline * size - glyph.planeTop * size
                val x1 = cursor + glyph.planeRight * size
                val y1 = renderY + data.baseline * size - glyph.planeBottom * size

                graphics.guiRenderState.addGlyphToCurrentLayer(
                    GlyphState(
                        pipeline,
                        textureSetup,
                        pose,
                        x0,
                        y0,
                        x1,
                        y1,
                        glyph.minU,
                        glyph.minV,
                        glyph.maxU,
                        glyph.maxV,
                        color,
                        graphics.scissorStack.peek(),
                    )
                )
            }

            cursor += glyph.advance * size
            previous = code
        }
    }

    fun drawCentered(
        graphics: GuiGraphicsExtractor,
        face: Face,
        text: String,
        centerX: Float,
        y: Float,
        size: Float,
        color: Int,
    ) {
        draw(graphics, face, text, centerX - width(face, text, size) * 0.5f, y, size, color)
    }

    fun drawFormatted(
        graphics: GuiGraphicsExtractor,
        face: Face,
        formattedText: net.minecraft.util.FormattedCharSequence,
        x: Float,
        y: Float,
        size: Float,
        defaultColor: Int,
        bloom: Boolean = false,
        isBloom: Boolean = false,
    ) {
        val data = data(face) ?: return
        val texture = Minecraft.getInstance().textureManager.getTexture(face.textureId)
        val textureSetup = TextureSetup.singleTexture(texture.textureView, texture.sampler)
        val pose = Matrix3x2f(graphics.pose())
        val renderY = y + face.offsetY(size)
        var cursor = x + face.offsetX(size)
        var previous = -1

        if (bloom) {
            val offsets = floatArrayOf(-1.5f, -1.0f, -0.5f, 0.5f, 1.0f, 1.5f)
            for (dx in offsets) {
                for (dy in offsets) {
                    drawFormatted(graphics, face, formattedText, x + dx, y + dy, size, defaultColor, false, true)
                }
            }
        }

        formattedText.accept { index, style, codePoint ->
            val textColor = style.color
            val origColor = textColor?.value ?: defaultColor
            val color = if (isBloom) {
                (origColor and 0x00FFFFFF) or (0x15 shl 24)
            } else {
                origColor
            }

            val glyph = data.glyphs[codePoint] ?: data.glyphs['?'.code] ?: return@accept true
            data.kernings[previous]?.get(codePoint)?.let { cursor += it * size }

            if (glyph.hasBounds) {
                val x0 = cursor + glyph.planeLeft * size
                val y0 = renderY + data.baseline * size - glyph.planeTop * size
                val x1 = cursor + glyph.planeRight * size
                val y1 = renderY + data.baseline * size - glyph.planeBottom * size

                graphics.guiRenderState.addGlyphToCurrentLayer(
                    GlyphState(
                        pipeline,
                        textureSetup,
                        pose,
                        x0,
                        y0,
                        x1,
                        y1,
                        glyph.minU,
                        glyph.minV,
                        glyph.maxU,
                        glyph.maxV,
                        color,
                        graphics.scissorStack.peek(),
                    )
                )
            }

            cursor += glyph.advance * size
            previous = codePoint
            true
        }
    }

    fun width(face: Face, text: String, size: Float): Float {
        val data = data(face) ?: return 0.0f
        var previous = -1
        var width = 0.0f
        for (char in text) {
            val code = char.code
            val glyph = data.glyphs[code] ?: data.glyphs['?'.code] ?: continue
            data.kernings[previous]?.get(code)?.let { width += it * size }
            width += glyph.advance * size
            previous = code
        }
        return width
    }

    private fun Face.offsetX(size: Float): Float {
        return if (this == Face.SfRegular) 0.0f else 0.0f
    }

    private fun Face.offsetY(size: Float): Float {
        return if (this == Face.SfRegular || this == Face.SfMedium || this == Face.SfSemibold) -size * 0.08f else 0.0f
    }

    private fun data(face: Face): FontData? {
        return cache[face] ?: loadFontData(face)?.also { cache[face] = it }
    }

    private fun loadFontData(face: Face): FontData? {
        return runCatching {
            val resource = Minecraft.getInstance().resourceManager.getResource(face.dataId).orElse(null) ?: return null
            resource.open().bufferedReader().use { reader ->
                parse(JsonParser.parseReader(reader).asJsonObject)
            }
        }.getOrNull()
    }

    private fun parse(root: JsonObject): FontData {
        val atlas = root.getAsJsonObject("atlas")
        val metrics = root.getAsJsonObject("metrics")
        val atlasWidth = atlas.get("width").asFloat
        val atlasHeight = atlas.get("height").asFloat
        val baseline = metrics.get("lineHeight").asFloat + metrics.get("descender").asFloat

        val glyphs = mutableMapOf<Int, Glyph>()
        root.getAsJsonArray("glyphs")?.forEach { element ->
            val objectValue = element.asJsonObject
            val code = objectValue.get("unicode").asInt
            val advance = objectValue.get("advance").asFloat
            val plane = objectValue.getAsJsonObject("planeBounds")
            val bounds = objectValue.getAsJsonObject("atlasBounds")

            glyphs[code] = if (plane != null && bounds != null) {
                Glyph(
                    advance,
                    plane.get("left").asFloat,
                    plane.get("top").asFloat,
                    plane.get("right").asFloat,
                    plane.get("bottom").asFloat,
                    bounds.get("left").asFloat / atlasWidth,
                    1.0f - bounds.get("top").asFloat / atlasHeight,
                    bounds.get("right").asFloat / atlasWidth,
                    1.0f - bounds.get("bottom").asFloat / atlasHeight,
                    true,
                )
            } else {
                Glyph(advance, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, false)
            }
        }

        val kernings = mutableMapOf<Int, MutableMap<Int, Float>>()
        root.getAsJsonArray("kerning")?.forEach { element ->
            val objectValue = element.asJsonObject
            kernings.getOrPut(objectValue.get("unicode1").asInt) { mutableMapOf() }[objectValue.get("unicode2").asInt] =
                objectValue.get("advance").asFloat
        }

        return FontData(baseline, glyphs, kernings)
    }

    private data class FontData(
        val baseline: Float,
        val glyphs: Map<Int, Glyph>,
        val kernings: Map<Int, Map<Int, Float>>,
    )

    private data class Glyph(
        val advance: Float,
        val planeLeft: Float,
        val planeTop: Float,
        val planeRight: Float,
        val planeBottom: Float,
        val minU: Float,
        val minV: Float,
        val maxU: Float,
        val maxV: Float,
        val hasBounds: Boolean,
    )

    private data class GlyphState(
        private val pipeline: RenderPipeline,
        private val textureSetup: TextureSetup,
        private val pose: Matrix3x2f,
        private val x0: Float,
        private val y0: Float,
        private val x1: Float,
        private val y1: Float,
        private val u0: Float,
        private val v0: Float,
        private val u1: Float,
        private val v1: Float,
        private val color: Int,
        private val scissorArea: ScreenRectangle?,
    ) : GuiElementRenderState {
        override fun buildVertices(consumer: VertexConsumer) {
            consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color)
            consumer.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color)
            consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color)
            consumer.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color)
        }

        override fun pipeline(): RenderPipeline = pipeline

        override fun textureSetup(): TextureSetup = textureSetup

        override fun scissorArea(): ScreenRectangle? = scissorArea

        override fun bounds(): ScreenRectangle {
            val left = min(x0, x1).toInt()
            val top = min(y0, y1).toInt()
            val right = max(x0, x1).toInt() + 1
            val bottom = max(y0, y1).toInt() + 1
            return ScreenRectangle(left, top, max(1, right - left), max(1, bottom - top))
        }
    }
}
