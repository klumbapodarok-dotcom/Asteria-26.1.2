package asteria.top.client.render

import asteria.top.client.gui.hud.HudStyle
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.modules.visual.AmbienceModule
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.cos

object SkyShaderRenderer {
    private const val INFO_UBO_SIZE = 64
    private val dynamicColor = Vector4f(1.0f, 1.0f, 1.0f, 1.0f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()
    private val infoData = ByteBuffer.allocateDirect(INFO_UBO_SIZE).order(ByteOrder.nativeOrder())
    private val startedAtNanos = System.nanoTime()
    private var infoUbo: GpuBuffer? = null
    private val pipelines = mapOf(
        AmbienceModule.SkyMode.AURORA to pipeline("worldtweaks_sky"),
        AmbienceModule.SkyMode.FOG_BLUR to pipeline("worldtweaks_sky"),
        AmbienceModule.SkyMode.PLASMA to pipeline("sky_plasma"),
        AmbienceModule.SkyMode.SAKURA to pipeline("sky_sakura"),
        AmbienceModule.SkyMode.SUMMER to pipeline("sky_summer"),
        AmbienceModule.SkyMode.BLACK_HOLE to pipeline("pulse_black_hole"),
        AmbienceModule.SkyMode.NEBULA to pipeline("pulse_nebula"),
    )

    fun shouldRender(): Boolean {
        val minecraft = Minecraft.getInstance()
        return minecraft.level != null && minecraft.player != null && ModuleManager.ambience.shouldRenderSky()
    }

    fun render() {
        if (!shouldRender()) return
        val minecraft = Minecraft.getInstance()
        val module = ModuleManager.ambience
        val pipeline = pipelines[module.skyMode.value] ?: return
        val target = minecraft.mainRenderTarget
        val colorView = RenderSystem.outputColorTextureOverride ?: target.colorTextureView ?: return
        val depthView = RenderSystem.outputDepthTextureOverride ?: target.depthTextureView
        val mesh = fullscreenQuad()

        try {
            val format = pipeline.vertexFormat
            val vertexBuffer = format.uploadImmediateVertexBuffer(mesh.vertexBuffer())
            val sequential = RenderSystem.getSequentialBuffer(mesh.drawState().mode())
            val indexBuffer = sequential.getBuffer(mesh.drawState().indexCount())
            val cameraState = minecraft.gameRenderer.gameRenderState.levelRenderState.cameraRenderState
            val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                cameraState.viewRotationMatrix,
                dynamicColor,
                modelOffset,
                textureMatrix,
            )
            val skyInfo = writeInfo(module, target.width.toFloat(), target.height.toFloat())
            val encoder = RenderSystem.getDevice().createCommandEncoder()
            val pass: RenderPass = if (depthView != null) {
                encoder.createRenderPass(
                    { "Asteria ambience sky" },
                    colorView,
                    OptionalInt.empty(),
                    depthView,
                    OptionalDouble.empty(),
                )
            } else {
                encoder.createRenderPass({ "Asteria ambience sky" }, colorView, OptionalInt.empty())
            }

            pass.use {
                it.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(it)
                it.setUniform("DynamicTransforms", dynamicTransforms)
                it.setUniform("SkyInfo", skyInfo)
                it.setVertexBuffer(0, vertexBuffer)
                it.setIndexBuffer(indexBuffer, sequential.type())
                it.drawIndexed(0, 0, mesh.drawState().indexCount(), 1)
            }
        } finally {
            mesh.close()
        }
    }

    private fun pipeline(shaderName: String): RenderPipeline {
        val shader = Identifier.fromNamespaceAndPath("asteria", "core/sky/$shaderName")
        return RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/sky_$shaderName"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withUniform("SkyInfo", UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
                .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build()
        )
    }

    private fun writeInfo(module: AmbienceModule, width: Float, height: Float): GpuBuffer {
        val buffer = infoUbo ?: RenderSystem.getDevice().createBuffer(
            { "Asteria sky info" },
            GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
            INFO_UBO_SIZE.toLong(),
        ).also { infoUbo = it }
        val color = HudStyle.ACCENT
        val red = ((color shr 16) and 0xFF) / 255.0f
        val green = ((color shr 8) and 0xFF) / 255.0f
        val blue = (color and 0xFF) / 255.0f
        val time = (System.currentTimeMillis() % 1_000_000L) / 1000.0f
        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        val yaw = Math.toRadians((-camera.yRot()).toDouble()).toFloat()
        val pitch = Math.toRadians(camera.xRot().toDouble()).toFloat()
        val fov = Minecraft.getInstance().options.fov().get().toFloat()
        val pulseTime = ((System.nanoTime() - startedAtNanos) / 1.0E9).toFloat()

        infoData.clear()
        val encoded = Std140Builder.intoBuffer(infoData)
            .putVec4(width, height, time, module.skyMode.value.shaderMode.toFloat())
            .putVec4(red, green, blue, module.skyQuality.value.toFloat())
            .putVec4(if (module.showStars.value) 1.0f else 0.0f, nightFactor(), 1.0f, 0.0f)
            .putVec4(yaw, pitch, fov, pulseTime)
            .get()
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), encoded)
        return buffer
    }

    private fun nightFactor(): Float {
        val angle = Minecraft.getInstance().gameRenderer.gameRenderState.levelRenderState.skyRenderState.sunAngle
        val daylight = (cos(angle * Math.PI * 2.0) * 2.0 + 0.5).toFloat().coerceIn(0.0f, 1.0f)
        return 1.0f - daylight
    }

    private fun fullscreenQuad(): MeshData {
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
        builder.addVertex(-1.0f, -1.0f, 0.0f)
        builder.addVertex(1.0f, -1.0f, 0.0f)
        builder.addVertex(1.0f, 1.0f, 0.0f)
        builder.addVertex(-1.0f, 1.0f, 0.0f)
        return builder.buildOrThrow()
    }
}
