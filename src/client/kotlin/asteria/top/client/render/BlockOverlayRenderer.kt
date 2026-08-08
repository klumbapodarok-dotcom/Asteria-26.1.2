package asteria.top.client.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import asteria.top.client.gui.hud.HudStyle
import asteria.top.client.module.modules.visual.BlockOverlayModule
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object BlockOverlayRenderer {
    private const val INFO_UBO_SIZE = 64
    private val dynamicColor = Vector4f(1.0f, 1.0f, 1.0f, 1.0f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()
    private val infoData = ByteBuffer.allocateDirect(INFO_UBO_SIZE).order(ByteOrder.nativeOrder())
    private var infoUbo: GpuBuffer? = null

    private val defaultPipeline = shaderPipeline("default", "block_overlay")
    private val plasmaPipeline = shaderPipeline("plasma", "plasma_block_overlay")

    private val solidPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/block_overlay_solid"))
            .withVertexShader(Identifier.withDefaultNamespace("core/position_color"))
            .withFragmentShader(Identifier.withDefaultNamespace("core/position_color"))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build()
    )

    fun render(
        context: LevelRenderContext,
        worldBox: AABB,
        seed: Long,
        overlayAlpha: Float,
        module: BlockOverlayModule,
    ) {
        val cameraState = context.levelState().cameraRenderState
        val camera = cameraState.pos
        val box = worldBox.move(-camera.x, -camera.y, -camera.z)
        val modelView = cameraState.viewRotationMatrix

        renderShader(modelView, box, overlayAlpha, module)
    }

    private fun shaderPipeline(name: String, shaderFile: String): RenderPipeline {
        val shader = Identifier.fromNamespaceAndPath("asteria", "core/blockoverlay/$shaderFile")
        return RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/block_overlay_$name"))
                .withVertexShader(Identifier.fromNamespaceAndPath("asteria", "core/blockoverlay/block_overlay"))
                .withFragmentShader(shader)
                .withUniform("BlockOverlayInfo", UniformType.UNIFORM_BUFFER)
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .build()
        )
    }

    private fun renderShader(modelView: Matrix4f, box: AABB, overlayAlpha: Float, module: BlockOverlayModule) {
        val pipeline = when (module.shaderPreset.value) {
            BlockOverlayModule.ShaderPreset.DEFAULT -> defaultPipeline
            BlockOverlayModule.ShaderPreset.PLASMA -> plasmaPipeline
        }

        val theme = HudStyle.ACCENT
        val red = ((theme shr 16) and 0xFF) / 255.0f
        val green = ((theme shr 8) and 0xFF) / 255.0f
        val blue = (theme and 0xFF) / 255.0f
        val alpha = module.alphaSetting.value * overlayAlpha
        val backgroundColor = withAlpha(darkenRgb(theme, 0.22f), alpha)
        val info = writeInfo(
            red,
            green,
            blue,
            min(red * 1.8f, 1.0f),
            min(green * 1.8f, 1.0f),
            min(blue * 1.8f, 1.0f),
            (System.currentTimeMillis() % 100000L) / 1000.0f,
            module.waveSpeed.value,
            module.waveScale.value,
            0.0f,
            module.glow.value,
            module.fill.value,
            alpha,
        )

        drawMesh(solidPipeline, buildFilledBox(box, backgroundColor), modelView)
        drawMesh(pipeline, buildTexturedBox(box), modelView, info)
    }

    private fun writeInfo(
        red: Float,
        green: Float,
        blue: Float,
        red2: Float,
        green2: Float,
        blue2: Float,
        time: Float,
        speed: Float,
        scale: Float,
        outline: Float,
        glow: Float,
        fill: Float,
        alpha: Float,
    ): GpuBuffer {
        val buffer = infoUbo ?: RenderSystem.getDevice().createBuffer(
            { "Asteria block overlay info" },
            GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
            INFO_UBO_SIZE.toLong(),
        ).also { infoUbo = it }

        infoData.clear()
        val encoded = Std140Builder.intoBuffer(infoData)
            .putVec4(red, green, blue, 1.0f)
            .putVec4(red2, green2, blue2, 1.0f)
            .putVec4(time, speed, scale, outline)
            .putVec4(glow, fill, alpha, 0.0f)
            .get()
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), encoded)
        return buffer
    }

    private fun drawMesh(pipeline: RenderPipeline, mesh: MeshData, modelView: Matrix4f, info: GpuBuffer? = null) {
        try {
            val format = pipeline.vertexFormat
            val vertexBuffer = format.uploadImmediateVertexBuffer(mesh.vertexBuffer())
            val indexData = mesh.indexBuffer()
            val indexBuffer: GpuBuffer
            val indexType: VertexFormat.IndexType
            if (indexData != null) {
                indexBuffer = format.uploadImmediateIndexBuffer(indexData)
                indexType = mesh.drawState().indexType()
            } else {
                val sequential = RenderSystem.getSequentialBuffer(mesh.drawState().mode())
                indexBuffer = sequential.getBuffer(mesh.drawState().indexCount())
                indexType = sequential.type()
            }

            val target = Minecraft.getInstance().mainRenderTarget
            val colorView = RenderSystem.outputColorTextureOverride ?: target.colorTextureView ?: return
            val depthView = RenderSystem.outputDepthTextureOverride ?: target.depthTextureView
            val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                modelView,
                dynamicColor,
                modelOffset,
                textureMatrix,
            )
            val encoder = RenderSystem.getDevice().createCommandEncoder()
            val pass: RenderPass = if (depthView != null) {
                encoder.createRenderPass(
                    { "Asteria block overlay" },
                    colorView,
                    OptionalInt.empty(),
                    depthView,
                    OptionalDouble.empty(),
                )
            } else {
                encoder.createRenderPass({ "Asteria block overlay" }, colorView, OptionalInt.empty())
            }

            pass.use {
                it.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(it)
                it.setUniform("DynamicTransforms", dynamicTransforms)
                if (info != null) it.setUniform("BlockOverlayInfo", info)
                it.setVertexBuffer(0, vertexBuffer)
                it.setIndexBuffer(indexBuffer, indexType)
                it.drawIndexed(0, 0, mesh.drawState().indexCount(), 1)
            }
        } finally {
            mesh.close()
        }
    }

    private fun buildTexturedBox(box: AABB): MeshData {
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val x1 = box.minX.toFloat(); val x2 = box.maxX.toFloat()
        val y1 = box.minY.toFloat(); val y2 = box.maxY.toFloat()
        val z1 = box.minZ.toFloat(); val z2 = box.maxZ.toFloat()
        addTexturedQuad(builder, x1, y1, z1, 0f, 0f, x1, y1, z2, 0f, 1f, x2, y1, z2, 1f, 1f, x2, y1, z1, 1f, 0f)
        addTexturedQuad(builder, x1, y2, z1, 0f, 0f, x2, y2, z1, 1f, 0f, x2, y2, z2, 1f, 1f, x1, y2, z2, 0f, 1f)
        addTexturedQuad(builder, x1, y1, z1, 0f, 0f, x2, y1, z1, 1f, 0f, x2, y2, z1, 1f, 1f, x1, y2, z1, 0f, 1f)
        addTexturedQuad(builder, x1, y1, z2, 0f, 0f, x1, y2, z2, 0f, 1f, x2, y2, z2, 1f, 1f, x2, y1, z2, 1f, 0f)
        addTexturedQuad(builder, x1, y1, z1, 0f, 0f, x1, y2, z1, 0f, 1f, x1, y2, z2, 1f, 1f, x1, y1, z2, 1f, 0f)
        addTexturedQuad(builder, x2, y1, z1, 0f, 0f, x2, y1, z2, 1f, 0f, x2, y2, z2, 1f, 1f, x2, y2, z1, 0f, 1f)
        return builder.buildOrThrow()
    }

    private fun addTexturedQuad(builder: BufferBuilder, vararg values: Float) {
        for (index in 0 until 4) {
            val offset = index * 5
            builder.addVertex(values[offset], values[offset + 1], values[offset + 2])
                .setUv(values[offset + 3], values[offset + 4])
                .setColor(0xFFFFFFFF.toInt())
        }
    }

    private fun buildFilledBox(box: AABB, color: Int): MeshData {
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        addFilledBox(builder, box, color)
        return builder.buildOrThrow()
    }

    private fun addFilledBox(builder: BufferBuilder, box: AABB, color: Int) {
        val x1 = box.minX.toFloat(); val x2 = box.maxX.toFloat()
        val y1 = box.minY.toFloat(); val y2 = box.maxY.toFloat()
        val z1 = box.minZ.toFloat(); val z2 = box.maxZ.toFloat()
        addQuad(builder, color, x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1)
        addQuad(builder, color, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2)
        addQuad(builder, color, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1)
        addQuad(builder, color, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2)
        addQuad(builder, color, x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2)
        addQuad(builder, color, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1)
    }

    private fun addQuad(builder: BufferBuilder, color: Int, vararg coordinates: Float) {
        for (index in 0 until 4) {
            val offset = index * 3
            builder.addVertex(coordinates[offset], coordinates[offset + 1], coordinates[offset + 2]).setColor(color)
        }
    }

    private fun addBezierRibbon(
        builder: BufferBuilder,
        p0: Vec3,
        p1: Vec3,
        p2: Vec3,
        p3: Vec3,
        faceNormal: Vec3,
        samples: Int,
        color: Int,
        halfWidth: Double,
    ) {
        val points = Array(samples + 1) { sample -> cubicBezier(p0, p1, p2, p3, sample.toDouble() / samples) }
        for (index in 0 until samples) {
            val start = points[index]
            val end = points[index + 1]
            val direction = end.subtract(start)
            if (direction.lengthSqr() < 1.0E-6) continue
            val perpendicular = faceNormal.cross(direction).normalize().scale(halfWidth)
            val startLeft = start.add(perpendicular)
            val startRight = start.subtract(perpendicular)
            val endLeft = end.add(perpendicular)
            val endRight = end.subtract(perpendicular)
            addQuad(
                builder,
                color,
                startLeft.x.toFloat(), startLeft.y.toFloat(), startLeft.z.toFloat(),
                startRight.x.toFloat(), startRight.y.toFloat(), startRight.z.toFloat(),
                endRight.x.toFloat(), endRight.y.toFloat(), endRight.z.toFloat(),
                endLeft.x.toFloat(), endLeft.y.toFloat(), endLeft.z.toFloat(),
            )
        }
    }

    private fun cubicBezier(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Double): Vec3 {
        val inverse = 1.0 - t
        val inverseSquared = inverse * inverse
        val squared = t * t
        return p0.scale(inverseSquared * inverse)
            .add(p1.scale(3.0 * inverseSquared * t))
            .add(p2.scale(3.0 * inverse * squared))
            .add(p3.scale(squared * t))
    }

    private fun faceNeighbors(face: Int): IntArray = when (face) {
        0, 1 -> intArrayOf(2, 3, 4, 5)
        2, 3 -> intArrayOf(0, 1, 4, 5)
        else -> intArrayOf(0, 1, 2, 3)
    }

    private fun faceBasis(face: Int): Array<Vec3> = when (face) {
        0, 1 -> arrayOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0))
        2, 3 -> arrayOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0))
        else -> arrayOf(Vec3(0.0, 0.0, 1.0), Vec3(0.0, 1.0, 0.0))
    }

    private fun faceNormal(face: Int): Vec3 = when (face) {
        0 -> Vec3(0.0, 1.0, 0.0)
        1 -> Vec3(0.0, -1.0, 0.0)
        2 -> Vec3(0.0, 0.0, -1.0)
        3 -> Vec3(0.0, 0.0, 1.0)
        4 -> Vec3(-1.0, 0.0, 0.0)
        else -> Vec3(1.0, 0.0, 0.0)
    }

    private fun edgePoint(box: AABB, faceA: Int, faceB: Int, t: Double, inset: Double): Vec3 {
        var x = Double.NaN; var y = Double.NaN; var z = Double.NaN
        val fixedA = faceFixedCoords(box, faceA, inset)
        if (!fixedA[0].isNaN()) x = fixedA[0]
        if (!fixedA[1].isNaN()) y = fixedA[1]
        if (!fixedA[2].isNaN()) z = fixedA[2]
        val fixedB = faceFixedCoords(box, faceB, inset)
        if (!fixedB[0].isNaN()) x = fixedB[0]
        if (!fixedB[1].isNaN()) y = fixedB[1]
        if (!fixedB[2].isNaN()) z = fixedB[2]
        val clamped = clamp01(t)
        if (x.isNaN()) x = lerp(box.minX, box.maxX, clamped)
        if (y.isNaN()) y = lerp(box.minY, box.maxY, clamped)
        if (z.isNaN()) z = lerp(box.minZ, box.maxZ, clamped)
        return Vec3(x, y, z)
    }

    private fun faceFixedCoords(box: AABB, face: Int, inset: Double): DoubleArray = when (face) {
        0 -> doubleArrayOf(Double.NaN, box.maxY - inset, Double.NaN)
        1 -> doubleArrayOf(Double.NaN, box.minY + inset, Double.NaN)
        2 -> doubleArrayOf(Double.NaN, Double.NaN, box.minZ + inset)
        3 -> doubleArrayOf(Double.NaN, Double.NaN, box.maxZ - inset)
        4 -> doubleArrayOf(box.minX + inset, Double.NaN, Double.NaN)
        else -> doubleArrayOf(box.maxX - inset, Double.NaN, Double.NaN)
    }

    private fun facePoint(box: AABB, face: Int, u: Double, v: Double, inset: Double): Vec3 {
        val uu = clamp01(u); val vv = clamp01(v)
        return when (face) {
            0 -> Vec3(lerp(box.minX, box.maxX, uu), box.maxY - inset, lerp(box.minZ, box.maxZ, vv))
            1 -> Vec3(lerp(box.minX, box.maxX, uu), box.minY + inset, lerp(box.minZ, box.maxZ, vv))
            2 -> Vec3(lerp(box.minX, box.maxX, uu), lerp(box.minY, box.maxY, vv), box.minZ + inset)
            3 -> Vec3(lerp(box.minX, box.maxX, uu), lerp(box.minY, box.maxY, vv), box.maxZ - inset)
            4 -> Vec3(box.minX + inset, lerp(box.minY, box.maxY, vv), lerp(box.minZ, box.maxZ, uu))
            else -> Vec3(box.maxX - inset, lerp(box.minY, box.maxY, vv), lerp(box.minZ, box.maxZ, uu))
        }
    }

    private fun rand01(seed: Long, salt: Int): Double {
        var value = seed + -7046029254386353131L * (salt + 1L)
        value = value xor (value ushr 30); value *= -4659424759600103767L
        value = value xor (value ushr 27); value *= -7722482329241934357L
        value = value xor (value ushr 31)
        return (value and 0xFFFFFFL).toDouble() / 0x1000000.toDouble()
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val value = (alpha.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
        return (value shl 24) or (color and 0x00FFFFFF)
    }

    private fun darkenRgb(color: Int, factor: Float): Int {
        val amount = factor.coerceIn(0.0f, 1.0f)
        val red = (((color shr 16) and 0xFF) * amount).toInt()
        val green = (((color shr 8) and 0xFF) * amount).toInt()
        val blue = ((color and 0xFF) * amount).toInt()
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
    private fun clamp01(value: Double): Double = max(0.0, min(1.0, value))
}
