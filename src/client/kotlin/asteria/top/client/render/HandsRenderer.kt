package asteria.top.client.render

import asteria.top.client.gui.hud.HudStyle
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.modules.visual.HandsModule
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.CommandEncoder
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalInt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Port of the original full-framebuffer Hands effect. */
object HandsRenderer {
    private const val HANDS_DATA_SIZE = 112
    private const val GLOW_DATA_SIZE = 48
    private const val GLOW_BUFFER_SCALE = 0.5f

    private val compositePipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/hands_composite"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/hands/hands_sky_plasma"))
            .withSampler("InSampler")
            .withUniform("HandsData", UniformType.UNIFORM_BUFFER)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build(),
    )

    private val blurHorizontalPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/hands_glow_horizontal"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/hands/hands_glow_horizontal"))
            .withSampler("InputSampler")
            .withUniform("GlowData", UniformType.UNIFORM_BUFFER)
            .build(),
    )

    private val blurVerticalPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/hands_glow_vertical"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/hands/hands_glow_vertical"))
            .withSampler("InputSampler")
            .withSampler("OriginalSampler")
            .withUniform("GlowData", UniformType.UNIFORM_BUFFER)
            .build(),
    )

    private val glowCompositePipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/hands_glow_composite"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.withDefaultNamespace("core/blit_screen"))
            .withSampler("InSampler")
            .withColorTargetState(ColorTargetState(BlendFunction.ADDITIVE))
            .build(),
    )

    private var handTarget: TextureTarget? = null
    private var blurTarget: TextureTarget? = null
    private var glowTarget: TextureTarget? = null
    private var targetWidth = 0
    private var targetHeight = 0
    private var glowWidth = 0
    private var glowHeight = 0
    private var handsUbo: GpuBuffer? = null
    private var glowUbo: GpuBuffer? = null
    private val handsData = directBuffer(HANDS_DATA_SIZE)
    private val glowData = directBuffer(GLOW_DATA_SIZE)
    private var captureActive = false
    private var previousColorOverride: GpuTextureView? = null
    private var previousDepthOverride: GpuTextureView? = null

    @JvmStatic
    fun beginCapture() {
        val module = ModuleManager.hands
        if (!module.enabled || captureActive) return

        val main = Minecraft.getInstance().mainRenderTarget
        if (main.width <= 0 || main.height <= 0) return
        ensureResources(main.width, main.height)
        ensureGlowResources(module.glowEnabled.value)
        val target = handTarget ?: return
        val color = target.colorTexture ?: return
        val depth = target.depthTexture ?: return

        previousColorOverride = RenderSystem.outputColorTextureOverride
        previousDepthOverride = RenderSystem.outputDepthTextureOverride
        RenderSystem.outputColorTextureOverride = target.colorTextureView
        RenderSystem.outputDepthTextureOverride = target.depthTextureView
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(color, 0, depth, 1.0)
        captureActive = true
    }

    @JvmStatic
    fun endCapture() {
        if (!captureActive) return
        captureActive = false
        val capturedDestination = previousColorOverride
        RenderSystem.outputColorTextureOverride = previousColorOverride
        RenderSystem.outputDepthTextureOverride = previousDepthOverride
        previousColorOverride = null
        previousDepthOverride = null

        val module = ModuleManager.hands
        if (!module.enabled) return
        val minecraft = Minecraft.getInstance()
        val main = minecraft.mainRenderTarget
        val source = handTarget ?: return
        val sourceView = source.colorTextureView ?: return
        val destination = capturedDestination ?: main.colorTextureView ?: return

        // One command encoder and one sampler lookup serve every pass of the
        // frame instead of the five separate ones the effect used before.
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        val sampler = linearSampler()
        val seconds = (System.nanoTime() % 1_000_000_000_000L) / 1_000_000_000.0f

        // The composite shader now combines the vanilla capture and effect in
        // one pass. This avoids a separate full-resolution framebuffer blit.
        writeHandsUniform(encoder, module, seconds, main.width, main.height)
        drawComposite(encoder, sampler, sourceView, destination)
        if (module.glowEnabled.value && module.glowExposure.value > 0.0f) {
            drawGlow(encoder, sampler, module, seconds, sourceView, destination)
        }
    }

    @JvmStatic
    fun resetCapture() {
        if (!captureActive) return
        RenderSystem.outputColorTextureOverride = previousColorOverride
        RenderSystem.outputDepthTextureOverride = previousDepthOverride
        previousColorOverride = null
        previousDepthOverride = null
        captureActive = false
    }

    private fun drawComposite(
        encoder: CommandEncoder,
        sampler: GpuSampler,
        source: GpuTextureView,
        destination: GpuTextureView,
    ) {
        val ubo = handsUbo ?: return
        encoder.createRenderPass({ "Asteria Hands composite" }, destination, OptionalInt.empty()).use { pass ->
            bindDefaults(pass)
            pass.setPipeline(compositePipeline)
            pass.bindTexture("InSampler", source, sampler)
            pass.setUniform("HandsData", ubo)
            pass.draw(0, 3)
        }
    }

    private fun drawGlow(
        encoder: CommandEncoder,
        sampler: GpuSampler,
        module: HandsModule,
        seconds: Float,
        source: GpuTextureView,
        destination: GpuTextureView,
    ) {
        val ubo = glowUbo ?: return
        val blurView = blurTarget?.colorTextureView ?: return
        val glowView = glowTarget?.colorTextureView ?: return

        // The bloom is regenerated every frame, so it stays in sync with the
        // fill at 144+ FPS instead of being refreshed on a fixed schedule.
        writeGlowUniform(encoder, module, seconds)
        encoder.createRenderPass({ "Asteria Hands horizontal glow" }, blurView, OptionalInt.empty()).use { pass ->
            bindDefaults(pass)
            pass.setPipeline(blurHorizontalPipeline)
            pass.bindTexture("InputSampler", source, sampler)
            pass.setUniform("GlowData", ubo)
            pass.draw(0, 3)
        }
        encoder.createRenderPass({ "Asteria Hands vertical glow" }, glowView, OptionalInt.empty()).use { pass ->
            bindDefaults(pass)
            pass.setPipeline(blurVerticalPipeline)
            pass.bindTexture("InputSampler", blurView, sampler)
            pass.bindTexture("OriginalSampler", source, sampler)
            pass.setUniform("GlowData", ubo)
            pass.draw(0, 3)
        }
        encoder.createRenderPass({ "Asteria Hands glow composite" }, destination, OptionalInt.empty()).use { pass ->
            bindDefaults(pass)
            pass.setPipeline(glowCompositePipeline)
            pass.bindTexture("InSampler", glowView, sampler)
            pass.draw(0, 3)
        }
    }

    private fun writeHandsUniform(encoder: CommandEncoder, module: HandsModule, seconds: Float, width: Int, height: Int) {
        val buffer = handsUbo ?: return
        // Same accent the HUD icons and watermark use, so hands read as part
        // of the interface theme instead of the old standalone green.
        val accent = HudStyle.THEME
        val gradientEnd = mixWithWhite(accent, 0.14f)
        handsData.clear()
        val encoded = Std140Builder.intoBuffer(handsData)
            .putVec4(width.toFloat(), height.toFloat(), 0.0f, 0.0f)
            .putVec4(red(accent), green(accent), blue(accent), 1.0f)
            .putVec4(seconds, module.turbulenceSpeed.value, module.fillAlpha.value, 0.0f)
            .putVec4(red(gradientEnd), green(gradientEnd), blue(gradientEnd), 1.0f)
            .putVec4(if (module.keepShading.value) 1.0f else 0.0f, module.shadingStrength.value, module.turbulenceStrength.value, 0.0f)
            .putVec4(if (module.glowEnabled.value) 1.0f else 0.0f, module.glowRadius.value, module.glowExposure.value, 0.0f)
            .putVec4(if (module.mode.value == HandsModule.Mode.SPACE) 1.0f else 0.0f, module.spaceSpeed.value, module.starDensity.value, module.nebulaStrength.value)
            .get()
        encoder.writeToBuffer(buffer.slice(), encoded)
    }

    private fun writeGlowUniform(encoder: CommandEncoder, module: HandsModule, seconds: Float) {
        val buffer = glowUbo ?: return
        val phase = (System.currentTimeMillis() % 3000L) / 3000.0f
        val wave = 0.5f - 0.5f * cos(phase * Math.PI.toFloat() * 2.0f)
        val start = HudStyle.THEME
        val end = mixWithWhite(start, 0.14f)
        val glowColor = mixColor(start, end, wave)
        // Radius follows the reduced glow buffer so its apparent on-screen
        // size stays identical after the cached texture is upscaled.
        val radius = min(96.0f, max(1.0f, module.glowRadius.value * 18.0f * 0.75f)) * GLOW_BUFFER_SCALE
        val intensity = 0.35f + module.glowExposure.value * 0.28f

        glowData.clear()
        val encoded = Std140Builder.intoBuffer(glowData)
            .putVec4(glowWidth.toFloat(), glowHeight.toFloat(), radius, 0.0f)
            .putVec4(red(glowColor), green(glowColor), blue(glowColor), intensity)
            .putVec4(seconds, module.turbulenceStrength.value, module.turbulenceSpeed.value, 0.0f)
            .get()
        encoder.writeToBuffer(buffer.slice(), encoded)
    }

    private fun ensureResources(width: Int, height: Int) {
        if (width == targetWidth && height == targetHeight && handTarget != null) return
        closeResources()
        targetWidth = width
        targetHeight = height
        glowWidth = max(1, (width * GLOW_BUFFER_SCALE).toInt())
        glowHeight = max(1, (height * GLOW_BUFFER_SCALE).toInt())
        handTarget = TextureTarget("asteria-hands", width, height, true)
        val device = RenderSystem.getDevice()
        handsUbo = device.createBuffer({ "Asteria Hands data" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, HANDS_DATA_SIZE.toLong())
        glowUbo = device.createBuffer({ "Asteria Hands glow data" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, GLOW_DATA_SIZE.toLong())
    }

    /**
     * The two bloom buffers only exist while Glow is on, so turning it off
     * gives their video memory straight back instead of keeping it reserved.
     */
    private fun ensureGlowResources(enabled: Boolean) {
        if (!enabled) {
            if (blurTarget == null && glowTarget == null) return
            blurTarget?.destroyBuffers()
            glowTarget?.destroyBuffers()
            blurTarget = null
            glowTarget = null
            return
        }
        if (blurTarget != null && glowTarget != null) return
        // Bloom does not need full-resolution geometry. Half resolution cuts
        // its fragment workload to 25% while linear upscaling keeps it soft.
        blurTarget = TextureTarget("asteria-hands-blur", glowWidth, glowHeight, false)
        glowTarget = TextureTarget("asteria-hands-glow", glowWidth, glowHeight, false)
    }

    private fun closeResources() {
        handTarget?.destroyBuffers()
        blurTarget?.destroyBuffers()
        glowTarget?.destroyBuffers()
        handTarget = null
        blurTarget = null
        glowTarget = null
        handsUbo?.close()
        glowUbo?.close()
        handsUbo = null
        glowUbo = null
    }

    private fun bindDefaults(pass: RenderPass) = RenderSystem.bindDefaultUniforms(pass)
    private fun linearSampler() = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
    private fun directBuffer(size: Int): ByteBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
    private fun red(color: Int) = ((color shr 16) and 0xFF) / 255.0f
    private fun green(color: Int) = ((color shr 8) and 0xFF) / 255.0f
    private fun blue(color: Int) = (color and 0xFF) / 255.0f

    private fun mixWithWhite(color: Int, amount: Float): Int = mixColor(color, 0xFFFFFFFF.toInt(), amount)

    private fun mixColor(first: Int, second: Int, amount: Float): Int {
        val t = amount.coerceIn(0.0f, 1.0f)
        fun channel(shift: Int) = (((first shr shift) and 0xFF) + (((second shr shift) and 0xFF) - ((first shr shift) and 0xFF)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
