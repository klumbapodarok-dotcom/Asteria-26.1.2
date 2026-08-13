package asteria.top.client.render

import asteria.top.client.gui.hud.HudStyle
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.modules.visual.AmbienceModule
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.DefaultVertexFormat
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
    private var quadVertexBuffer: GpuBuffer? = null
    private var lowResolutionTarget: TextureTarget? = null
    private var lowResolutionWidth = 0
    private var lowResolutionHeight = 0
    private val worldTweaksPipeline = pipeline("worldtweaks_sky")
    private val plasmaPipeline = pipeline("sky_plasma")
    private val sakuraPipeline = pipeline("sky_sakura")
    private val summerPipeline = pipeline("sky_summer")
    private val blackHolePipeline = pipeline("pulse_black_hole")
    private val nebulaPipeline = pipeline("pulse_nebula")

    fun shouldRender(): Boolean {
        val minecraft = Minecraft.getInstance()
        return minecraft.level != null && minecraft.player != null && ModuleManager.ambience.shouldRenderSky()
    }

    fun render() {
        if (!shouldRender()) return
        val minecraft = Minecraft.getInstance()
        val module = ModuleManager.ambience
        val pipeline = pipelineFor(module.skyMode.value) ?: return
        val target = minecraft.mainRenderTarget
        val colorView = RenderSystem.outputColorTextureOverride ?: target.colorTextureView ?: return
        val renderScale = renderScale(module.skyQuality.value)
        val renderWidth = maxOf(1, (target.width * renderScale).toInt())
        val renderHeight = maxOf(1, (target.height * renderScale).toInt())
        val skyTarget = ensureLowResolutionTarget(renderWidth, renderHeight)
        val vertexBuffer = fullscreenQuadVertexBuffer()
        val sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS)
        val indexBuffer = sequential.getBuffer(QUAD_INDEX_COUNT)
        val cameraState = minecraft.gameRenderer.gameRenderState.levelRenderState.cameraRenderState
        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            cameraState.viewRotationMatrix,
            dynamicColor,
            modelOffset,
            textureMatrix,
        )
        val skyInfo = writeInfo(minecraft, module, renderWidth.toFloat(), renderHeight.toFloat())
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.createRenderPass(
            { "Asteria ambience sky (low resolution)" },
            skyTarget.colorTextureView ?: return,
            OptionalInt.empty(),
        ).use {
            it.setPipeline(pipeline)
            RenderSystem.bindDefaultUniforms(it)
            it.setUniform("DynamicTransforms", dynamicTransforms)
            it.setUniform("SkyInfo", skyInfo)
            it.setVertexBuffer(0, vertexBuffer)
            it.setIndexBuffer(indexBuffer, sequential.type())
            it.drawIndexed(0, 0, QUAD_INDEX_COUNT, 1)
        }

        encoder.createRenderPass(
            { "Asteria ambience sky upscale" },
            colorView,
            OptionalInt.empty(),
        ).use {
            it.setPipeline(RenderPipelines.TRACY_BLIT)
            it.bindTexture(
                "InSampler",
                skyTarget.colorTextureView ?: return,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            )
            it.draw(0, 3)
        }
    }

    private fun renderScale(quality: Int): Float = when (quality) {
        1 -> 0.25f
        3 -> 0.50f
        else -> 0.35f
    }

    private fun ensureLowResolutionTarget(width: Int, height: Int): TextureTarget {
        val current = lowResolutionTarget
        if (current != null && width == lowResolutionWidth && height == lowResolutionHeight) return current

        current?.destroyBuffers()
        return TextureTarget("asteria-ambience-sky", width, height, false).also {
            lowResolutionTarget = it
            lowResolutionWidth = width
            lowResolutionHeight = height
        }
    }

    private fun pipelineFor(mode: AmbienceModule.SkyMode): RenderPipeline? = when (mode) {
        AmbienceModule.SkyMode.AURORA,
        AmbienceModule.SkyMode.FOG_BLUR -> worldTweaksPipeline
        AmbienceModule.SkyMode.PLASMA -> plasmaPipeline
        AmbienceModule.SkyMode.SAKURA -> sakuraPipeline
        AmbienceModule.SkyMode.SUMMER -> summerPipeline
        AmbienceModule.SkyMode.BLACK_HOLE -> blackHolePipeline
        AmbienceModule.SkyMode.NEBULA -> nebulaPipeline
        AmbienceModule.SkyMode.NORMAL -> null
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

    private fun writeInfo(minecraft: Minecraft, module: AmbienceModule, width: Float, height: Float): GpuBuffer {
        val buffer = infoUbo ?: RenderSystem.getDevice().createBuffer(
            { "Asteria sky info" },
            GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
            INFO_UBO_SIZE.toLong(),
        ).also { infoUbo = it }
        val interfaceColor = HudStyle.ACCENT
        val red: Float
        val green: Float
        val blue: Float
        if (module.skyColorMode.value == AmbienceModule.SkyColorMode.CUSTOM) {
            red = module.skyColor.red / 255.0f
            green = module.skyColor.green / 255.0f
            blue = module.skyColor.blue / 255.0f
        } else {
            red = ((interfaceColor shr 16) and 0xFF) / 255.0f
            green = ((interfaceColor shr 8) and 0xFF) / 255.0f
            blue = (interfaceColor and 0xFF) / 255.0f
        }
        val time = (System.currentTimeMillis() % 1_000_000L) / 1000.0f
        val camera = minecraft.gameRenderer.mainCamera
        val yaw = Math.toRadians((-camera.yRot()).toDouble()).toFloat()
        val pitch = Math.toRadians(camera.xRot().toDouble()).toFloat()
        val fov = minecraft.options.fov().get().toFloat()
        val pulseTime = ((System.nanoTime() - startedAtNanos) / 1.0E9).toFloat()

        infoData.clear()
        val encoded = Std140Builder.intoBuffer(infoData)
            .putVec4(width, height, time, module.skyMode.value.shaderMode.toFloat())
            .putVec4(red, green, blue, module.skyQuality.value.toFloat())
            .putVec4(if (module.showStars.value) 1.0f else 0.0f, nightFactor(minecraft), 1.0f, 0.0f)
            .putVec4(yaw, pitch, fov, pulseTime)
            .get()
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), encoded)
        return buffer
    }

    private fun nightFactor(minecraft: Minecraft): Float {
        val angle = minecraft.gameRenderer.gameRenderState.levelRenderState.skyRenderState.sunAngle
        val daylight = (cos(angle * Math.PI * 2.0) * 2.0 + 0.5).toFloat().coerceIn(0.0f, 1.0f)
        return 1.0f - daylight
    }

    private fun fullscreenQuadVertexBuffer(): GpuBuffer {
        quadVertexBuffer?.let { return it }
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
        builder.addVertex(-1.0f, -1.0f, 0.0f)
        builder.addVertex(1.0f, -1.0f, 0.0f)
        builder.addVertex(1.0f, 1.0f, 0.0f)
        builder.addVertex(-1.0f, 1.0f, 0.0f)
        val mesh = builder.buildOrThrow()
        return try {
            worldTweaksPipeline.vertexFormat
                .uploadImmediateVertexBuffer(mesh.vertexBuffer())
                .also { quadVertexBuffer = it }
        } finally {
            mesh.close()
        }
    }

    private const val QUAD_INDEX_COUNT = 6
}
