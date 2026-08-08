package asteria.top.client.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import asteria.top.client.gui.hud.HudStyle
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.modules.visual.HandShaderModule
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

object HandShaderRenderer {
    private const val INFO_UBO_SIZE = 64
    private val infoData = ByteBuffer.allocateDirect(INFO_UBO_SIZE).order(ByteOrder.nativeOrder())
    private var infoUbo: GpuBuffer? = null

    val handRenderType: RenderType by lazy {
        val pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/hand_shader"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/handshader/hand_shader"))
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("EMISSIVE")
                .withUniform("BlockOverlayInfo", UniformType.UNIFORM_BUFFER)
                .build()
        )

        val setup = RenderSetup.builder(pipeline)
            .useLightmap()
            .createRenderSetup()

        RenderType.create("hand_shader", setup)
    }

    val plasmaHandRenderType: RenderType by lazy {
        val pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/plasma_hand_shader"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/handshader/plasma_shader"))
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("EMISSIVE")
                .withUniform("BlockOverlayInfo", UniformType.UNIFORM_BUFFER)
                .build()
        )

        val setup = RenderSetup.builder(pipeline)
            .useLightmap()
            .createRenderSetup()

        RenderType.create("plasma_hand_shader", setup)
    }

    private val itemRenderTypeCache = ConcurrentHashMap<Identifier, RenderType>()
    private val plasmaItemRenderTypeCache = ConcurrentHashMap<Identifier, RenderType>()

    @JvmStatic
    fun getItemHandRenderType(texture: Identifier): RenderType {
        return itemRenderTypeCache.getOrPut(texture) {
            val nameSuffix = texture.path.replace('/', '_').replace('.', '_')
            val pipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/item_hand_shader_$nameSuffix"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/handshader/hand_shader_overlay"))
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("EMISSIVE")
                    .withUniform("BlockOverlayInfo", UniformType.UNIFORM_BUFFER)
                    .build()
            )

            val setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", texture)
                .createRenderSetup()

            RenderType.create("item_hand_shader_$nameSuffix", setup)
        }
    }

    @JvmStatic
    fun getPlasmaItemHandRenderType(texture: Identifier): RenderType {
        return plasmaItemRenderTypeCache.getOrPut(texture) {
            val nameSuffix = texture.path.replace('/', '_').replace('.', '_')
            val pipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/plasma_item_hand_shader_$nameSuffix"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/handshader/plasma_shader_overlay"))
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("EMISSIVE")
                    .withUniform("BlockOverlayInfo", UniformType.UNIFORM_BUFFER)
                    .build()
            )

            val setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", texture)
                .createRenderSetup()

            RenderType.create("plasma_item_hand_shader_$nameSuffix", setup)
        }
    }

    @JvmStatic
    fun getHandRenderType(preset: HandShaderModule.ShaderPreset): RenderType {
        return when (preset) {
            HandShaderModule.ShaderPreset.DEFAULT -> handRenderType
            HandShaderModule.ShaderPreset.PLASMA -> plasmaHandRenderType
        }
    }

    @JvmStatic
    fun getItemHandRenderType(texture: Identifier, preset: HandShaderModule.ShaderPreset): RenderType {
        return when (preset) {
            HandShaderModule.ShaderPreset.DEFAULT -> getItemHandRenderType(texture)
            HandShaderModule.ShaderPreset.PLASMA -> getPlasmaItemHandRenderType(texture)
        }
    }

    /**
     * Called once per tick to write current module settings + time into the UBO.
     * Must be called OUTSIDE any active render pass (this runs at tick time).
     */
    @JvmStatic
    fun updateUniform() {
        val module = ModuleManager.handShader
        if (!module.enabled) return


        if (infoUbo == null) {
            infoUbo = RenderSystem.getDevice().createBuffer(
                { "Asteria hand shader info" },
                GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
                INFO_UBO_SIZE.toLong(),
            )
        }
        val buffer = infoUbo!!

        val theme = HudStyle.ACCENT
        val red = ((theme shr 16) and 0xFF) / 255.0f
        val green = ((theme shr 8) and 0xFF) / 255.0f
        val blue = (theme and 0xFF) / 255.0f

        infoData.clear()
        val encoded = Std140Builder.intoBuffer(infoData)
            .putVec4(red, green, blue, 1.0f)
            .putVec4(min(red * 1.8f, 1.0f), min(green * 1.8f, 1.0f), min(blue * 1.8f, 1.0f), 1.0f)
            .putVec4(
                (System.currentTimeMillis() % 100000L) / 1000.0f,
                module.waveSpeed.value,
                module.waveScale.value,
                0.0f,
            )
            .putVec4(module.glow.value, module.fill.value, module.alphaSetting.value, 0.0f)
            .get()
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), encoded)
    }

    /**
     * Called from the [RenderTypeMixin] right before [RenderPass.drawIndexed].
     * Only binds the pre‑written UBO – does NOT write data (which would crash
     * because a render pass is already active).
     */
    @JvmStatic
    fun bindUniform(pass: RenderPass) {
        val buffer = infoUbo ?: return
        pass.setUniform("BlockOverlayInfo", buffer)
    }
}
