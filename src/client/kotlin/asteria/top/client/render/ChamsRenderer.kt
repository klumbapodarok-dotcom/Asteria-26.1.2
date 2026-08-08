package asteria.top.client.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import asteria.top.client.gui.hud.HudStyle
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.modules.visual.ChamsModule
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

object ChamsRenderer {
    private const val INFO_UBO_SIZE = 64
    private val infoData = ByteBuffer.allocateDirect(INFO_UBO_SIZE).order(ByteOrder.nativeOrder())
    private var infoUbo: GpuBuffer? = null

    val chamsRenderType: RenderType by lazy {
        val pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/chams_shader"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/handshader/hand_shader"))
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("EMISSIVE")
                .withUniform("BlockOverlayInfo", UniformType.UNIFORM_BUFFER)
                .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .build()
        )

        val setup = RenderSetup.builder(pipeline)
            .useLightmap()
            .createRenderSetup()

        RenderType.create("chams_shader", setup)
    }

    val chamsThroughWallsRenderType: RenderType by lazy {
        val pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/chams_through_walls_shader"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/handshader/hand_shader"))
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("EMISSIVE")
                .withUniform("BlockOverlayInfo", UniformType.UNIFORM_BUFFER)
                .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build()
        )

        val setup = RenderSetup.builder(pipeline)
            .useLightmap()
            .createRenderSetup()

        RenderType.create("chams_through_walls_shader", setup)
    }

    val plasmaRenderType: RenderType by lazy {
        val pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/plasma_shader"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/handshader/plasma_shader"))
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("EMISSIVE")
                .withUniform("BlockOverlayInfo", UniformType.UNIFORM_BUFFER)
                .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .build()
        )

        val setup = RenderSetup.builder(pipeline)
            .useLightmap()
            .createRenderSetup()

        RenderType.create("plasma_shader", setup)
    }

    val plasmaThroughWallsRenderType: RenderType by lazy {
        val pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/plasma_through_walls_shader"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/handshader/plasma_shader"))
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("EMISSIVE")
                .withUniform("BlockOverlayInfo", UniformType.UNIFORM_BUFFER)
                .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build()
        )

        val setup = RenderSetup.builder(pipeline)
            .useLightmap()
            .createRenderSetup()

        RenderType.create("plasma_through_walls_shader", setup)
    }

    @JvmStatic
    fun getChamsRenderType(throughWalls: Boolean, preset: ChamsModule.ShaderPreset): RenderType {
        return when (preset) {
            ChamsModule.ShaderPreset.DEFAULT -> if (throughWalls) chamsThroughWallsRenderType else chamsRenderType
            ChamsModule.ShaderPreset.PLASMA -> if (throughWalls) plasmaThroughWallsRenderType else plasmaRenderType
        }
    }

    @JvmStatic
    fun updateUniform() {
        val module = ModuleManager.chams
        if (!module.enabled) return

        if (infoUbo == null) {
            infoUbo = RenderSystem.getDevice().createBuffer(
                { "Asteria chams shader info" },
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
        val fillVal = module.filled.value / 100.0f
        val encoded = Std140Builder.intoBuffer(infoData)
            .putVec4(red, green, blue, 1.0f)
            .putVec4(min(red * 1.8f, 1.0f), min(green * 1.8f, 1.0f), min(blue * 1.8f, 1.0f), 1.0f)
            .putVec4(
                (System.currentTimeMillis() % 100000L) / 1000.0f,
                module.waveSpeed.value,
                module.waveScale.value,
                0.0f,
            )
            .putVec4(module.glow.value, fillVal, module.alphaSetting.value, 0.0f)
            .get()
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), encoded)
    }

    @JvmStatic
    fun bindUniform(pass: RenderPass) {
        val buffer = infoUbo ?: return
        pass.setUniform("BlockOverlayInfo", buffer)
    }
}
