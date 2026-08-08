package asteria.top.client.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import asteria.top.client.module.modules.visual.LiquidFogModule
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalInt

object LiquidFogRenderer {
    private const val UBO_SIZE = 80
    private const val START_TIME = 0L
    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/liquid_fog"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/liquid_fog"))
            .withSampler("DepthSampler")
            .withUniform("LiquidFogInfo", UniformType.UNIFORM_BUFFER)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    )

    private var depthCopy: TextureTarget? = null
    private var ubo: GpuBuffer? = null
    private var targetWidth = 0
    private var targetHeight = 0
    private var startedAt = START_TIME
    private val infoBuffer = ByteBuffer.allocateDirect(UBO_SIZE).order(ByteOrder.nativeOrder())

    fun render(context: LevelRenderContext, module: LiquidFogModule) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null || minecraft.player == null) return
        val destination = minecraft.mainRenderTarget
        if (destination.width <= 0 || destination.height <= 0) return
        if (!ensureResources(destination)) return

        val depthTarget = depthCopy ?: return
        val fogUbo = ubo ?: return
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        if (!copyDepth(destination, depthTarget)) return
        encoder.writeToBuffer(fogUbo.slice(), writeInfoBuffer(context, module, destination.width.toFloat(), destination.height.toFloat()))
        encoder.createRenderPass({ "Asteria liquid fog" }, destination.colorTextureView ?: return, OptionalInt.empty()).use { pass ->
            bindDefaults(pass)
            pass.setPipeline(pipeline)
            pass.bindTexture("DepthSampler", depthTarget.depthTextureView ?: return, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
            pass.setUniform("LiquidFogInfo", fogUbo)
            pass.draw(0, 3)
        }
    }

    private fun ensureResources(destination: RenderTarget): Boolean {
        if (destination.width == targetWidth && destination.height == targetHeight && depthCopy != null && ubo != null) return true
        release()
        targetWidth = destination.width
        targetHeight = destination.height
        depthCopy = TextureTarget("asteria-liquid-fog-depth", targetWidth, targetHeight, true)
        ubo = RenderSystem.getDevice().createBuffer({ "Asteria Liquid Fog UBO" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, UBO_SIZE.toLong())
        if (startedAt == START_TIME) startedAt = System.nanoTime()
        return true
    }

    private fun copyDepth(destination: RenderTarget, depthTarget: RenderTarget): Boolean {
        val source = destination.depthTexture ?: return false
        val target = depthTarget.depthTexture ?: return false
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            source,
            target,
            0,
            0,
            0,
            0,
            0,
            minOf(destination.width, depthTarget.width),
            minOf(destination.height, depthTarget.height),
        )
        return true
    }

    private fun writeInfoBuffer(context: LevelRenderContext, module: LiquidFogModule, width: Float, height: Float): ByteBuffer {
        val time = ((System.nanoTime() - startedAt).toDouble() / 1.0E9).toFloat()
        val camera = context.gameRenderer().mainCamera
        val farPlane = (Minecraft.getInstance().options.renderDistance().get() * 16.0f).coerceAtLeast(96.0f)
        infoBuffer.clear()
        return Std140Builder.intoBuffer(infoBuffer)
            .putVec4(width, height, 1.0f / width, 1.0f / height)
            .putVec4(time, camera.yRot(), context.gameRenderer().mainCamera.xRot(), farPlane)
            .putVec4(module.fallout.value, module.density.value, 0.25f, if (module.hsvMode.value) 1.0f else 0.0f)
            .putVec4(0.41f, 1.0f, 0.51f, 1.0f)
            .putVec4(0.13f, 0.56f, 1.0f, 0.25f)
            .get()
    }

    private fun bindDefaults(pass: RenderPass) {
        RenderSystem.bindDefaultUniforms(pass)
    }

    fun release() {
        depthCopy?.destroyBuffers()
        depthCopy = null
        ubo?.close()
        ubo = null
        targetWidth = 0
        targetHeight = 0
    }
}
