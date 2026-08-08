package asteria.top.client.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.gui.AsteriaClickGui
import asteria.top.client.gui.CosmeticsMenu
import asteria.top.client.gui.AsteriaClickGui.PANEL_CORNER_RADIUS
import asteria.top.client.gui.HudOverlay
import asteria.top.client.gui.hud.NotificationManager
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.modules.visual.PostProcessingModule
import asteria.top.client.util.AnimationUtil
import asteria.top.client.util.BlurUtil
import asteria.top.client.util.RoundingUtil
import asteria.top.client.util.ShadowUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalInt

object DoubleKawaseBlurRenderer {
    private enum class Pass { HUD, CLICK_GUI }

    private const val KAWASE_UBO_SIZE = 32
    private const val GAUSSIAN_UBO_SIZE = 32
    private const val MAX_BLUR_BOXES = 32
    private const val COMPOSITE_UBO_SIZE = 96 + MAX_BLUR_BOXES * 32
    private const val LIQUID_GLASS_UBO_SIZE = 80 + MAX_BLUR_BOXES * 32

    private val kawasePipeline = RenderPipelines.register(
        com.mojang.blaze3d.pipeline.RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/double_kawase"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/double_kawase"))
            .withSampler("InputSampler")
            .withUniform("KawaseInfo", UniformType.UNIFORM_BUFFER)
            .build()
    )

    private val gaussianPipeline = RenderPipelines.register(
        com.mojang.blaze3d.pipeline.RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/gaussian_blur"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/gaussian_blur"))
            .withSampler("InputSampler")
            .withUniform("GaussianInfo", UniformType.UNIFORM_BUFFER)
            .build()
    )

    private val compositePipeline = RenderPipelines.register(
        com.mojang.blaze3d.pipeline.RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/double_kawase_composite"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/double_kawase_composite"))
            .withSampler("OriginalSampler")
            .withSampler("BlurSampler")
            .withUniform("CompositeInfo", UniformType.UNIFORM_BUFFER)
            .build()
    )

    private val compositeSquirclePipeline = RenderPipelines.register(
        com.mojang.blaze3d.pipeline.RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/double_kawase_composite_squircle"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/double_kawase_composite_squircle"))
            .withSampler("OriginalSampler")
            .withSampler("BlurSampler")
            .withUniform("CompositeInfo", UniformType.UNIFORM_BUFFER)
            .build()
    )

    private val liquidGlassPipeline = RenderPipelines.register(
        com.mojang.blaze3d.pipeline.RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/liquid_glass"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/liquid_glass"))
            .withSampler("OriginalSampler")
            .withSampler("BlurSampler")
            .withUniform("LiquidGlassInfo", UniformType.UNIFORM_BUFFER)
            .build()
    )

    private var original: TextureTarget? = null
    private var halfA: TextureTarget? = null
    private var quarterA: TextureTarget? = null
    private var eighth: TextureTarget? = null
    private var quarterB: TextureTarget? = null
    private var halfB: TextureTarget? = null
    private var fullBlur: TextureTarget? = null
    private var gaussianTemp: TextureTarget? = null
    private var targetWidth = 0
    private var targetHeight = 0
    private var kawaseUbos: Array<GpuBuffer?> = arrayOfNulls(BlurUtil.MAX_KAWASE_PASSES)
    private var gaussianUbos: Array<GpuBuffer?> = arrayOfNulls(2)
    private var compositeUbo: GpuBuffer? = null
    private var liquidGlassUbo: GpuBuffer? = null
    private val kawaseInfoBuffer = reusableBuffer(KAWASE_UBO_SIZE)
    private val gaussianInfoBuffer = reusableBuffer(GAUSSIAN_UBO_SIZE)
    private val compositeInfoBuffer = reusableBuffer(COMPOSITE_UBO_SIZE)
    private val liquidGlassInfoBuffer = reusableBuffer(LIQUID_GLASS_UBO_SIZE)

    @JvmStatic
    fun renderHudPass() {
        renderIfVisible(Pass.HUD)
    }

    @JvmStatic
    fun renderClickGuiPass(): Boolean {
        // Do not consume a blur boundary created by a vanilla Screen. That
        // boundary must run through GameRenderer.processBlurEffect().
        if (Minecraft.getInstance().screen != null) return false
        if (!AsteriaClickGui.shouldRender() && !CosmeticsMenu.shouldRender()) return false
        // Consume Minecraft's blur boundary even when the module is disabled;
        // in that case ClickGUI uses its normal non-blurred panel fills.
        if (!ModuleManager.postProcessing.enabled) return true
        renderIfVisible(Pass.CLICK_GUI)
        return true
    }

    private fun renderIfVisible(pass: Pass) {
        val postProcessing = ModuleManager.postProcessing
        if (!postProcessing.enabled) return
        if (pass == Pass.CLICK_GUI && !AsteriaClickGui.shouldRender() && !CosmeticsMenu.shouldRender()) return

        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null || minecraft.player == null) return

        // F1 hides the regular HUD, but world-space GUI overlays are still extracted and
        // rendered. Keep their post-processing pass alive as long as one of them has a
        // visible blur region; otherwise only their text/icons would survive hideGui.
        if (pass == Pass.HUD &&
            !HudOverlay.shouldPostProcess() &&
            !hasWorldOverlayPostProcessing(minecraft.window.guiScale.toFloat())
        ) return

        val mainTarget = minecraft.mainRenderTarget
        val width = mainTarget.width
        val height = mainTarget.height
        if (width <= 0 || height <= 0) return

        ensureTargets(width, height)

        val originalTarget = original ?: return
        val halfATarget = halfA ?: return
        val quarterATarget = quarterA ?: return
        val eighthTarget = eighth ?: return
        val quarterBTarget = quarterB ?: return
        val halfBTarget = halfB ?: return
        val fullBlurTarget = fullBlur ?: return
        val gaussianTempTarget = gaussianTemp ?: return

        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.copyTextureToTexture(
            mainTarget.colorTexture ?: return,
            originalTarget.colorTexture ?: return,
            0, 0, 0, 0, 0, width, height
        )

        val blurTarget = when (postProcessing.blurMode.value) {
            PostProcessingModule.BlurMode.DOUBLE_KAWASE -> {
                renderDoubleKawase(
                    originalTarget,
                    arrayOf(halfATarget, quarterATarget, eighthTarget, quarterBTarget, halfBTarget, fullBlurTarget),
                    postProcessing.kawasePasses.value,
                    postProcessing.kawaseOffset.value
                )
            }

            PostProcessingModule.BlurMode.GAUSSIAN -> {
                renderGaussian(
                    originalTarget,
                    gaussianTempTarget,
                    fullBlurTarget,
                    postProcessing.gaussianRadius.value,
                    postProcessing.gaussianSigma()
                )
            }
        }

        if (postProcessing.liquidGlass.value) {
            renderLiquidGlass(originalTarget, blurTarget, mainTarget, postProcessing, pass)
        } else {
            renderComposite(originalTarget, blurTarget, mainTarget, normalizeTint(postProcessing.tintStrength.value), pass)
        }
    }

    private fun ensureTargets(width: Int, height: Int) {
        if (width == targetWidth && height == targetHeight && original != null) return
        closeTargets()
        targetWidth = width
        targetHeight = height

        val halfWidth = maxOf(1, width / 2)
        val halfHeight = maxOf(1, height / 2)
        val quarterWidth = maxOf(1, width / 4)
        val quarterHeight = maxOf(1, height / 4)
        val eighthWidth = maxOf(1, width / 8)
        val eighthHeight = maxOf(1, height / 8)

        original = TextureTarget("asteria-original", width, height, false)
        halfA = TextureTarget("asteria-kawase-half-a", halfWidth, halfHeight, false)
        quarterA = TextureTarget("asteria-kawase-quarter-a", quarterWidth, quarterHeight, false)
        eighth = TextureTarget("asteria-kawase-eighth", eighthWidth, eighthHeight, false)
        quarterB = TextureTarget("asteria-kawase-quarter-b", quarterWidth, quarterHeight, false)
        halfB = TextureTarget("asteria-kawase-half-b", halfWidth, halfHeight, false)
        fullBlur = TextureTarget("asteria-kawase-full", width, height, false)
        gaussianTemp = TextureTarget("asteria-gaussian-temp", width, height, false)

        val device = RenderSystem.getDevice()
        kawaseUbos = Array(BlurUtil.MAX_KAWASE_PASSES) { i ->
            device.createBuffer({ "Asteria Kawase UBO $i" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, KAWASE_UBO_SIZE.toLong())
        }
        gaussianUbos = Array(2) { i ->
            device.createBuffer({ "Asteria Gaussian UBO $i" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, GAUSSIAN_UBO_SIZE.toLong())
        }
        compositeUbo = device.createBuffer({ "Asteria Kawase Composite UBO" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, COMPOSITE_UBO_SIZE.toLong())
        liquidGlassUbo = device.createBuffer({ "Asteria Liquid Glass UBO" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, LIQUID_GLASS_UBO_SIZE.toLong())
    }

    private fun closeTargets() {
        original?.destroyBuffers(); halfA?.destroyBuffers(); quarterA?.destroyBuffers(); eighth?.destroyBuffers()
        quarterB?.destroyBuffers(); halfB?.destroyBuffers(); fullBlur?.destroyBuffers(); gaussianTemp?.destroyBuffers()
        original = null; halfA = null; quarterA = null; eighth = null; quarterB = null; halfB = null; fullBlur = null; gaussianTemp = null
        kawaseUbos.forEach { it?.close() }; kawaseUbos = arrayOfNulls(BlurUtil.MAX_KAWASE_PASSES)
        gaussianUbos.forEach { it?.close() }; gaussianUbos = arrayOfNulls(2)
        compositeUbo?.close(); compositeUbo = null
        liquidGlassUbo?.close(); liquidGlassUbo = null
    }

    private fun renderDoubleKawase(source: RenderTarget, targets: Array<RenderTarget>, passes: Int, offset: Float): RenderTarget {
        val passCount = passes.coerceIn(1, BlurUtil.MAX_KAWASE_PASSES)
        var currentSource = source
        var currentOutput = targets.last()
        for (index in 0 until passCount) {
            currentOutput = targets[index]
            renderKawasePass(index, currentSource, currentOutput, offset)
            currentSource = currentOutput
        }
        return currentOutput
    }

    private fun renderKawasePass(index: Int, source: RenderTarget, destination: RenderTarget, offset: Float) {
        val ubo = kawaseUbos[index] ?: return
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.writeToBuffer(ubo.slice(), writeKawaseInfoBuffer(source.width.toFloat(), source.height.toFloat(), offset))
        encoder.createRenderPass({ "Asteria double Kawase pass $index" }, destination.colorTextureView ?: return, OptionalInt.empty()).use { pass ->
            bindDefaults(pass)
            pass.setPipeline(kawasePipeline)
            pass.bindTexture("InputSampler", source.colorTextureView ?: return, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            pass.setUniform("KawaseInfo", ubo)
            pass.draw(0, 3)
        }
    }

    private fun renderGaussian(source: RenderTarget, temp: RenderTarget, destination: RenderTarget, radius: Int, sigma: Float): RenderTarget {
        renderGaussianPass(0, source, temp, 1.0f, 0.0f, radius, sigma)
        renderGaussianPass(1, temp, destination, 0.0f, 1.0f, radius, sigma)
        return destination
    }

    private fun renderGaussianPass(index: Int, source: RenderTarget, destination: RenderTarget, directionX: Float, directionY: Float, radius: Int, sigma: Float) {
        val ubo = gaussianUbos[index] ?: return
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.writeToBuffer(ubo.slice(), writeGaussianInfoBuffer(source.width.toFloat(), source.height.toFloat(), directionX, directionY, radius.toFloat(), sigma))
        encoder.createRenderPass({ "Asteria Gaussian blur pass $index" }, destination.colorTextureView ?: return, OptionalInt.empty()).use { pass ->
            bindDefaults(pass)
            pass.setPipeline(gaussianPipeline)
            pass.bindTexture("InputSampler", source.colorTextureView ?: return, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            pass.setUniform("GaussianInfo", ubo)
            pass.draw(0, 3)
        }
    }

    private fun renderComposite(originalTarget: RenderTarget, blurTarget: RenderTarget, destination: RenderTarget, tintStrength: Float, pass: Pass) {
        val ubo = compositeUbo ?: return
        val postProcessing = ModuleManager.postProcessing
        val guiScale = Minecraft.getInstance().window.guiScale.toFloat()
        val opacity = postProcessingOpacity(pass)
        val blurBoxes = postProcessingBoxes(guiScale, pass)
        val clickGuiVisible = pass == Pass.CLICK_GUI
        val interfaceModule = ModuleManager.interfaceModule
        val hudInterfaceShadow = !clickGuiVisible && !Minecraft.getInstance().options.hideGui &&
            (interfaceModule.watermark.value || interfaceModule.keybinds.value || interfaceModule.potions.value || NotificationManager.hasVisibleNotifications())
        val useInterfaceShadow = clickGuiVisible || hudInterfaceShadow
        val shadowEnabled = if (useInterfaceShadow) interfaceModule.shadowOpacity.value > 0 else postProcessing.shadow.value
        val shadowRadius = if (hudInterfaceShadow) interfaceModule.shadowSize.value * 1.35f else if (clickGuiVisible) interfaceModule.shadowSize.value.toFloat() else postProcessing.shadowRadius.value * 0.45f
        val shadowOpacity = if (hudInterfaceShadow) interfaceModule.shadowOpacity.value / 100.0f * 0.82f else if (clickGuiVisible) interfaceModule.shadowOpacity.value / 100.0f else ShadowUtil.OPACITY * 0.45f
        val shadowSmoothness = if (useInterfaceShadow) interfaceModule.shadowSmoothness.value / 100.0f else 1.0f
        val shadowOffsetX = if (useInterfaceShadow) 0.0f else ShadowUtil.OFFSET_X * 0.35f
        val shadowOffsetY = if (useInterfaceShadow) 0.0f else ShadowUtil.OFFSET_Y * 0.35f

        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.writeToBuffer(
            ubo.slice(),
            writeCompositeInfoBuffer(
                destination.width.toFloat(),
                destination.height.toFloat(),
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                opacity,
                tintStrength,
                shadowEnabled,
                shadowRadius,
                shadowOpacity,
                shadowSmoothness,
                shadowOffsetX,
                shadowOffsetY,
                blurBoxes
            )
        )
        
        // ClickGUI is authored from CSS border-radius values, so its blur mask must always
        // use circular rounded rectangles. A global squircle setting would otherwise leave
        // a differently shaped blur/tint layer behind the standard rounded GUI fill.
        val selectedPipeline = if (AsteriaClickGui.shouldRender()) {
            compositePipeline
        } else {
            when (postProcessing.roundingType.value) {
                RoundingType.SQUIRCLE -> compositeSquirclePipeline
                RoundingType.CURRENT -> compositePipeline
            }
        }
        
        encoder.createRenderPass({ "Asteria double Kawase composite" }, destination.colorTextureView ?: return, OptionalInt.empty()).use { pass ->
            bindDefaults(pass)
            pass.setPipeline(selectedPipeline)
            pass.bindTexture("OriginalSampler", originalTarget.colorTextureView ?: return, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            pass.bindTexture("BlurSampler", blurTarget.colorTextureView ?: return, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            pass.setUniform("CompositeInfo", ubo)
            pass.draw(0, 3)
        }
    }

    private fun renderLiquidGlass(
        originalTarget: RenderTarget,
        blurTarget: RenderTarget,
        destination: RenderTarget,
        postProcessing: PostProcessingModule,
        pass: Pass,
    ) {
        val ubo = liquidGlassUbo ?: return
        val guiScale = Minecraft.getInstance().window.guiScale.toFloat()
        val opacity = postProcessingOpacity(pass) *
            normalizePercent(postProcessing.glassOpacity.value, 1.0f)
        val glassBoxes = postProcessingBoxes(guiScale, pass)
        val clickGuiVisible = pass == Pass.CLICK_GUI
        val interfaceModule = ModuleManager.interfaceModule
        val hudInterfaceShadow = !clickGuiVisible && !Minecraft.getInstance().options.hideGui &&
            (interfaceModule.watermark.value || interfaceModule.keybinds.value || interfaceModule.potions.value || NotificationManager.hasVisibleNotifications())
        val useInterfaceShadow = clickGuiVisible || hudInterfaceShadow
        val shadowEnabled = if (useInterfaceShadow) interfaceModule.shadowOpacity.value > 0 else postProcessing.shadow.value
        val shadowRadius = if (hudInterfaceShadow) interfaceModule.shadowSize.value * 1.35f else if (clickGuiVisible) interfaceModule.shadowSize.value.toFloat() else postProcessing.shadowRadius.value * 0.45f
        val shadowOpacity = if (hudInterfaceShadow) interfaceModule.shadowOpacity.value / 100.0f * 0.82f else if (clickGuiVisible) interfaceModule.shadowOpacity.value / 100.0f else ShadowUtil.OPACITY * 0.45f
        val shadowSmoothness = if (useInterfaceShadow) interfaceModule.shadowSmoothness.value / 100.0f else 1.0f
        val shadowOffsetX = if (useInterfaceShadow) 0.0f else ShadowUtil.OFFSET_X * 0.35f
        val shadowOffsetY = if (useInterfaceShadow) 0.0f else ShadowUtil.OFFSET_Y * 0.35f

        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.writeToBuffer(
            ubo.slice(),
            writeLiquidGlassInfoBuffer(
                destination.width.toFloat(),
                destination.height.toFloat(),
                opacity,
                normalizePercent(postProcessing.glassRefraction.value, 1.0f),
                normalizePercent(postProcessing.glassChromaticAberration.value, 1.0f),
                normalizePercent(postProcessing.glassSaturation.value, 2.0f),
                normalizePercent(postProcessing.glassHighlight.value, 1.0f),
                shadowEnabled,
                shadowRadius,
                shadowOpacity,
                shadowSmoothness,
                shadowOffsetX,
                shadowOffsetY,
                glassBoxes,
            ),
        )
        encoder.createRenderPass({ "Asteria liquid glass composite" }, destination.colorTextureView ?: return, OptionalInt.empty()).use { pass ->
            bindDefaults(pass)
            pass.setPipeline(liquidGlassPipeline)
            pass.bindTexture("OriginalSampler", originalTarget.colorTextureView ?: return, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            pass.bindTexture("BlurSampler", blurTarget.colorTextureView ?: return, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            pass.setUniform("LiquidGlassInfo", ubo)
            pass.draw(0, 3)
        }
    }

    private fun bindDefaults(pass: RenderPass) {
        RenderSystem.bindDefaultUniforms(pass)
    }

    private fun normalizeTint(value: Float): Float {
        return if (value > 1.0f) (value / 100.0f).coerceIn(0.0f, 1.0f) else value.coerceIn(0.0f, 1.0f)
    }

    private fun postProcessingBoxes(guiScale: Float, pass: Pass): List<AsteriaOverlay.BlurBox> {
        return when (pass) {
            Pass.HUD -> {
                // The HUD must disappear completely with F1. World overlays are intentionally
                // kept below, so their backgrounds can still be post-processed independently.
                val hudBoxes = if (Minecraft.getInstance().options.hideGui) emptyList() else HudOverlay.blurBoxes(guiScale)
                (hudBoxes + ModuleManager.nameTags.blurBoxes(guiScale) + ModuleManager.predictions.blurBoxes(guiScale) + ModuleManager.trapEsp.blurBoxes(guiScale)).take(MAX_BLUR_BOXES)
            }
            Pass.CLICK_GUI -> (AsteriaClickGui.blurBoxes(guiScale) + CosmeticsMenu.blurBoxes(guiScale)).take(MAX_BLUR_BOXES)
        }
    }

    private fun hasWorldOverlayPostProcessing(guiScale: Float): Boolean {
        return ModuleManager.nameTags.blurBoxes(guiScale).isNotEmpty() ||
            ModuleManager.predictions.blurBoxes(guiScale).isNotEmpty() ||
            ModuleManager.trapEsp.blurBoxes(guiScale).isNotEmpty()
    }

    private fun postProcessingOpacity(pass: Pass): Float {
        return when (pass) {
            Pass.HUD -> 1.0f
            Pass.CLICK_GUI -> AnimationUtil.clamp01(
                if (CosmeticsMenu.shouldRender()) CosmeticsMenu.animationProgress() else AsteriaClickGui.animationProgress()
            )
        }
    }

    private fun normalizePercent(value: Float, maximum: Float): Float {
        return (value / 100.0f).coerceIn(0.0f, maximum)
    }

    private fun reusableBuffer(size: Int): ByteBuffer {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
    }

    private fun writeKawaseInfoBuffer(sourceWidth: Float, sourceHeight: Float, offset: Float): ByteBuffer {
        val buffer = kawaseInfoBuffer
        buffer.clear()
        return Std140Builder.intoBuffer(buffer).putVec4(sourceWidth, sourceHeight, offset, 0.0f).putVec4(0.0f, 0.0f, 0.0f, 0.0f).get()
    }

    private fun writeGaussianInfoBuffer(sourceWidth: Float, sourceHeight: Float, directionX: Float, directionY: Float, radius: Float, sigma: Float): ByteBuffer {
        val buffer = gaussianInfoBuffer
        buffer.clear()
        return Std140Builder.intoBuffer(buffer).putVec4(sourceWidth, sourceHeight, directionX, directionY).putVec4(radius, sigma, 0.0f, 0.0f).get()
    }

    private fun writeCompositeInfoBuffer(
        outputWidth: Float,
        outputHeight: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        opacity: Float,
        tintStrength: Float,
        shadowEnabled: Boolean,
        shadowRadius: Float,
        shadowOpacity: Float,
        shadowSmoothness: Float,
        shadowOffsetX: Float,
        shadowOffsetY: Float,
        blurBoxes: List<AsteriaOverlay.BlurBox>
    ): ByteBuffer {
        val buffer = compositeInfoBuffer
        buffer.clear()
        val boxCount = blurBoxes.size.coerceAtMost(MAX_BLUR_BOXES)
        val builder = Std140Builder.intoBuffer(buffer)
            .putVec4(outputWidth, outputHeight, 0.0f, 0.0f)
            .putVec4(x, y, width, height)
            .putVec4(tintStrength, opacity, 0.0f, 1.0f)
            .putVec4(PANEL_CORNER_RADIUS * opacity, RoundingUtil.ROUNDING_RULE.glslId.toFloat(), RoundingUtil.SQUIRCLE_POWER, shadowSmoothness)
            .putVec4(shadowOffsetX, shadowOffsetY, shadowRadius, if (shadowEnabled) shadowOpacity * opacity else 0.0f)
            .putVec4(boxCount.toFloat(), 0.0f, 0.0f, 0.0f)
        for (index in 0 until MAX_BLUR_BOXES) {
            val box = blurBoxes.getOrNull(index)
            if (box == null) builder.putVec4(0.0f, 0.0f, 0.0f, 0.0f)
            else builder.putVec4(box.x, box.y, box.width, box.height)
        }
        for (index in 0 until MAX_BLUR_BOXES) {
            val box = blurBoxes.getOrNull(index)
            if (box == null) builder.putVec4(0.0f, 0.0f, 0.0f, 0.0f)
            else builder.putVec4(box.radius, box.tintStrength, box.opacity, 0.0f)
        }
        return builder.get()
    }

    private fun writeLiquidGlassInfoBuffer(
        outputWidth: Float,
        outputHeight: Float,
        opacity: Float,
        refraction: Float,
        chromaticAberration: Float,
        saturation: Float,
        highlight: Float,
        shadowEnabled: Boolean,
        shadowRadius: Float,
        shadowOpacity: Float,
        shadowSmoothness: Float,
        shadowOffsetX: Float,
        shadowOffsetY: Float,
        glassBoxes: List<AsteriaOverlay.BlurBox>,
    ): ByteBuffer {
        val buffer = liquidGlassInfoBuffer
        buffer.clear()
        val boxCount = glassBoxes.size.coerceAtMost(MAX_BLUR_BOXES)
        val builder = Std140Builder.intoBuffer(buffer)
            .putVec4(outputWidth, outputHeight, 1.0f / outputWidth, 1.0f / outputHeight)
            .putVec4(saturation, opacity, refraction, highlight)
            .putVec4(1.0f, 1.0f, 1.0f, chromaticAberration)
            .putVec4(boxCount.toFloat(), RoundingUtil.ROUNDING_RULE.glslId.toFloat(), shadowSmoothness, 0.0f)
            .putVec4(shadowOffsetX, shadowOffsetY, shadowRadius, if (shadowEnabled) shadowOpacity * opacity else 0.0f)
        for (index in 0 until MAX_BLUR_BOXES) {
            val box = glassBoxes.getOrNull(index)
            if (box == null) builder.putVec4(0.0f, 0.0f, 0.0f, 0.0f)
            else builder.putVec4(box.x, box.y, box.width, box.height)
        }
        for (index in 0 until MAX_BLUR_BOXES) {
            val box = glassBoxes.getOrNull(index)
            if (box == null) builder.putVec4(0.0f, 0.0f, 0.0f, 0.0f)
            else builder.putVec4(box.radius, box.tintStrength, box.opacity, 0.0f)
        }
        return builder.get()
    }
}
