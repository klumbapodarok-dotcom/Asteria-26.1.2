package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.render.BlockOverlayRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.min

class BlockOverlayModule : Module(
    name = "Block Overlay",
    category = ModuleCategory.VISUALS,
    description = "Renders a custom block overlay",
    enabledByDefault = false,
) {
    enum class ShaderPreset(val label: String) {
        DEFAULT("Default"),
        PLASMA("Plasma"),
    }

    val shaderPreset = setting(EnumSetting("Shader Preset", ShaderPreset.entries.toTypedArray(), ShaderPreset.DEFAULT) { it.label })
    val waveSpeed = setting(FloatSetting("Wave Speed", 1.2f, 0.1f, 5.0f, 0.1f))
    val waveScale = setting(FloatSetting("Wave Scale", 1.0f, 1.0f, 3.0f, 0.1f))
    val glow = setting(FloatSetting("Glow", 1.0f, 0.0f, 5.0f, 0.1f))
    val fill = setting(FloatSetting("Fill", 0.6f, 0.0f, 1.0f, 0.01f))
    val alphaSetting = setting(FloatSetting("Alpha", 1.0f, 0.0f, 1.0f, 0.01f))
    val smooth = setting(FloatSetting("Smooth", 0.24f, 0.05f, 0.6f, 0.01f))

    private var previousBlock: BlockPos? = null
    private var currentBlock: BlockPos? = null
    private var displayBox: AABB? = null
    private var targetBox: AABB? = null
    private var overlayAlpha = 0.0f

    override fun onDisable() {
        clearTarget()
        super.onDisable()
    }

    fun onRender(context: LevelRenderContext) {
        if (!enabled) return

        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        if (minecraft.player == null) return

        val hit = getOutlineHitResult()
        var worldBox: AABB? = null
        if (hit != null && hit.type == HitResult.Type.BLOCK) {
            val pos = hit.blockPos
            val state = level.getBlockState(pos)
            if (!state.isAir) {
                val shape = state.getShape(level, pos)
                if (!shape.isEmpty()) {
                    worldBox = shape.bounds().move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).inflate(0.002)
                }
            }

            if (worldBox != null && pos != currentBlock) {
                previousBlock = currentBlock
                currentBlock = pos
            }
        }

        updateAnimation(worldBox)
        val box = displayBox ?: return
        if (overlayAlpha <= 0.001f) return
        BlockOverlayRenderer.render(context, box, currentBlock?.asLong() ?: 1L, overlayAlpha, this)
    }

    private fun updateAnimation(worldBox: AABB?) {
        if (worldBox != null) {
            if (displayBox == null || targetBox == null || previousBlock == null) {
                displayBox = worldBox
                targetBox = worldBox
            } else {
                targetBox = worldBox
                displayBox = lerpBox(displayBox!!, worldBox, smooth.value)
            }
            overlayAlpha = lerpValue(overlayAlpha, 1.0f, min(1.0f, smooth.value * 1.35f))
        } else if (displayBox == null || targetBox == null) {
            clearTarget()
        } else {
            overlayAlpha = lerpValue(overlayAlpha, 0.0f, 0.18f)
            if (overlayAlpha <= 0.02f) clearTarget()
        }
    }

    private fun clearTarget() {
        currentBlock = null
        previousBlock = null
        displayBox = null
        targetBox = null
        overlayAlpha = 0.0f
    }

    private fun getOutlineHitResult(): BlockHitResult? {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        val start = player.getEyePosition(1.0f)
        val end = start.add(player.getViewVector(1.0f).scale(6.0))
        return level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
            .takeIf { it.type == HitResult.Type.BLOCK }
    }

    private fun lerpBox(a: AABB, b: AABB, speed: Float): AABB = AABB(
        a.minX + (b.minX - a.minX) * speed,
        a.minY + (b.minY - a.minY) * speed,
        a.minZ + (b.minZ - a.minZ) * speed,
        a.maxX + (b.maxX - a.maxX) * speed,
        a.maxY + (b.maxY - a.maxY) * speed,
        a.maxZ + (b.maxZ - a.maxZ) * speed,
    )

    private fun lerpValue(current: Float, target: Float, speed: Float): Float = current + (target - current) * speed
}
