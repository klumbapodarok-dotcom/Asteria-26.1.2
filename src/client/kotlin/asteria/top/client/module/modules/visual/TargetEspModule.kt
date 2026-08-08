package asteria.top.client.module.modules.visual

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.util.AnimationUtil
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

class TargetEspModule : Module(
    name = "Target ESP",
    category = ModuleCategory.VISUALS,
    description = "Renders visual highlights around the current combat target.",
    enabledByDefault = false,
) {
    enum class Mode(val label: String) {
        CRYSTALS("Кристалики"),
        RING("Кружок"),
    }

    private val mode = setting(EnumSetting("Mode", Mode.entries.toTypedArray(), Mode.CRYSTALS) { it.label })
    private val crystalGlowAlpha = setting(FloatSetting("Glow Alpha", 38.0f, 0.0f, 255.0f, 1.0f).visibleWhen { mode.value == Mode.CRYSTALS })
    private val crystalAlpha = setting(FloatSetting("Crystal Alpha", 175.0f, 0.0f, 255.0f, 1.0f).visibleWhen { mode.value == Mode.CRYSTALS })
    private val crystalGlowSize = setting(FloatSetting("Glow Size", 1.9f, 0.5f, 4.0f, 0.05f).visibleWhen { mode.value == Mode.CRYSTALS })
    private val crystalWidth = setting(FloatSetting("Crystal Width", 0.076f, 0.02f, 0.16f, 0.002f).visibleWhen { mode.value == Mode.CRYSTALS })
    private val crystalLength = setting(FloatSetting("Crystal Length", 0.160f, 0.05f, 0.30f, 0.005f).visibleWhen { mode.value == Mode.CRYSTALS })
    private val crystalRedden = setting(BooleanSetting("Redden", true).visibleWhen { mode.value == Mode.CRYSTALS })
    private val ringFillEnabled = setting(BooleanSetting("Ring Fill", true).visibleWhen { mode.value == Mode.RING })
    private val ringRedden = setting(BooleanSetting("Redden", true).visibleWhen { mode.value == Mode.RING })
    private val renderModelView = Matrix4f()
    private val dynamicColor = Vector4f(1.0f, 1.0f, 1.0f, 1.0f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()
    private var ringActiveTargetId = -1
    private val ringAnimation = AnimationUtil.TimedAnimation(0.0f)
    private var ringLastWorldFeetX = 0.0
    private var ringLastWorldFeetY = 0.0
    private var ringLastWorldFeetZ = 0.0
    private var ringLastHeight = 0.0f
    private var ringLastAccent = 0xFF91B7FF.toInt()
    private var ringSmoothX = 0.0
    private var ringSmoothY = 0.0
    private var ringSmoothZ = 0.0
    private var ringHasSmoothPos = false
    private var crystalActiveTargetId = -1
    private val crystalAnimation = AnimationUtil.TimedAnimation(0.0f)
    private var crystalLastCenterX = 0.0
    private var crystalLastCenterY = 0.0
    private var crystalLastCenterZ = 0.0
    private var crystalLastRadius = 0.0f
    private var crystalLastHeight = 0.0f
    private var crystalLastAccent = 0xFF91B7FF.toInt()
    private var crystalSmoothX = 0.0
    private var crystalSmoothY = 0.0
    private var crystalSmoothZ = 0.0
    private var crystalHasSmoothPos = false
    private var lastFrameNanos = 0L

    fun renderGizmos(context: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        if (!enabled || mc.level == null || mc.player == null) return
        // Backtrack's own target only stands in while Killaura is still on —
        // otherwise turning Killaura off wouldn't start the outro at all as
        // long as Backtrack kept tracking something on its own.
        val target = (ModuleManager.killaura.target
            ?: ModuleManager.backtrack.target?.takeIf { ModuleManager.killaura.enabled })?.takeIf { it.isAlive }

        val poseStack = context.poseStack()
        renderModelView.set(context.levelState().cameraRenderState.viewRotationMatrix)
        val cameraPos = mc.gameRenderer.mainCamera.position()
        // Both modes keep rendering for a moment after the target is lost so
        // their grow-and-fade outro can finish; each returns immediately on
        // its own once there's nothing left to animate.
        when (mode.value) {
            Mode.CRYSTALS -> renderCrystalOrbit(target, poseStack, cameraPos)
            Mode.RING -> renderRing(target, poseStack, cameraPos)
        }
    }

    // Blends the shared accent color toward pure red while the target is in
    // its vanilla hurt-flash window (hurtTime counts down from hurtDuration
    // on every hit, client-side, same field the vanilla red damage tint
    // uses) — smoothstep-eased so it fades in/out instead of snapping, and
    // capped at 70% red so it never fully overrides the theme color.
    private fun accentColor(target: Entity, reddenEnabled: Boolean): Int {
        val base = 0xFF91B7FF.toInt()
        val baseR = (base shr 16) and 0xFF
        val baseG = (base shr 8) and 0xFF
        val baseB = base and 0xFF
        if (!reddenEnabled) return base
        val living = target as? LivingEntity ?: return base
        val duration = max(1, living.hurtDuration)
        val fraction = (living.hurtTime.toFloat() / duration).coerceIn(0.0f, 1.0f)
        val eased = fraction * fraction * (3.0f - 2.0f * fraction)
        val redWeight = eased * 0.7f
        val r = (baseR + (255 - baseR) * redWeight).toInt()
        val g = (baseG * (1.0f - redWeight)).toInt()
        val b = (baseB * (1.0f - redWeight)).toInt()
        return rgba(r, g, b, 255)
    }

    // Appear: the ring starts oversized and transparent, then shrinks down to its configured
    // size while fading in. Disappear: the reverse — it grows past its configured size while
    // fading out. Both directions share one continuous TimedAnimation value (0 = hidden/
    // oversized, 1 = settled), so retargeting mid-tween (e.g. the target reappearing right after
    // it's lost, or the module being toggled off right after on) continues smoothly from
    // wherever the animation currently is instead of snapping to the settled state first. World
    // position/height/accent color are cached on every frame the target is alive so the outro
    // has something to render from.
    private fun renderRing(target: Entity?, poseStack: PoseStack, cameraPos: Vec3) {
        if (target != null) {
            ringActiveTargetId = target.id

            val tickDelta = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
            ringLastWorldFeetX = target.xOld + (target.x - target.xOld) * tickDelta
            ringLastWorldFeetY = target.yOld + (target.y - target.yOld) * tickDelta
            ringLastWorldFeetZ = target.zOld + (target.z - target.zOld) * tickDelta
            ringLastHeight = targetHeight(target)
            ringLastAccent = accentColor(target, ringRedden.value)
        } else if (ringActiveTargetId == -1) {
            return
        }

        val active = target != null
        ringAnimation.update()
        ringAnimation.run(
            if (active) 1.0f else 0.0f,
            if (active) RING_APPEAR_MS else RING_DISAPPEAR_MS,
            { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) },
            true,
        )

        if (!active && ringAnimation.value <= 0.001f) {
            ringActiveTargetId = -1
            return
        }

        val now = System.currentTimeMillis()
        val progress = ringAnimation.value
        val animScale = RING_ANIM_SCALE - (RING_ANIM_SCALE - 1.0f) * progress
        val animAlpha = progress

        val feetX = ringLastWorldFeetX - cameraPos.x
        val feetZ = ringLastWorldFeetZ - cameraPos.z
        val timeSeconds = now * 0.001
        val cs = timeSeconds * 2.2
        val sinAnimNext = (sin(cs + 0.45) + 1.0) * 0.5
        val ringRadius = 0.65f * animScale
        val worldRingY = ringLastWorldFeetY + sinAnimNext * ringLastHeight - 0.08
        val ringY = worldRingY - cameraPos.y
        val accent = ringLastAccent

        if (ringFillEnabled.value) {
            val fillBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
            appendRingFill(fillBuilder, feetX, ringY, feetZ, ringRadius, accent, animAlpha)
            fillBuilder.build()?.use { mesh -> renderPositionColorMesh(mesh, poseStack, crystalPipeline, "Asteria TargetESP ring fill") }
        }

        renderRingTorus(feetX, ringY, feetZ, poseStack, ringRadius, accent, animAlpha)
        renderRingOuterGlow(feetX, ringY, feetZ, poseStack, ringRadius, accent, animAlpha)
        renderRingInnerGlow(feetX, ringY, feetZ, poseStack, ringRadius, accent, animAlpha)
    }

    // A small torus (donut) that rides the exact same wave position as the
    // Ring's visible edge (same y, same speed): a near-zero-thickness,
    // fully opaque outline for the fill inside it.
    private fun renderRingTorus(anchorX: Double, anchorY: Double, anchorZ: Double, poseStack: PoseStack, majorRadius: Float, accentColor: Int, animAlpha: Float) {
        val minorRadius = 0.006f
        val centerY = anchorY + minorRadius
        val majorSegments = 40
        val minorSegments = 8
        val tr = brighten((accentColor shr 16) and 0xFF, 0.6f)
        val tg = brighten((accentColor shr 8) and 0xFF, 0.6f)
        val tb = brighten(accentColor and 0xFF, 0.6f)
        val color = rgba(tr, tg, tb, (255 * animAlpha).toInt())

        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        for (i in 0 until majorSegments) {
            val theta0 = i * (Math.PI * 2.0 / majorSegments)
            val theta1 = (i + 1) * (Math.PI * 2.0 / majorSegments)
            for (j in 0 until minorSegments) {
                val phi0 = j * (Math.PI * 2.0 / minorSegments)
                val phi1 = (j + 1) * (Math.PI * 2.0 / minorSegments)

                val p00 = torusPoint(anchorX, centerY, anchorZ, majorRadius, minorRadius, theta0, phi0)
                val p10 = torusPoint(anchorX, centerY, anchorZ, majorRadius, minorRadius, theta1, phi0)
                val p11 = torusPoint(anchorX, centerY, anchorZ, majorRadius, minorRadius, theta1, phi1)
                val p01 = torusPoint(anchorX, centerY, anchorZ, majorRadius, minorRadius, theta0, phi1)

                ringVertex(builder, p00.x, p00.y, p00.z, color)
                ringVertex(builder, p10.x, p10.y, p10.z, color)
                ringVertex(builder, p11.x, p11.y, p11.z, color)

                ringVertex(builder, p00.x, p00.y, p00.z, color)
                ringVertex(builder, p11.x, p11.y, p11.z, color)
                ringVertex(builder, p01.x, p01.y, p01.z, color)
            }
        }
        builder.build()?.use { mesh -> renderPositionColorMesh(mesh, poseStack, crystalPipeline, "Asteria TargetESP ring torus") }
    }

    private fun torusPoint(cx: Double, cy: Double, cz: Double, majorRadius: Float, minorRadius: Float, theta: Double, phi: Double): Vec3 {
        val ringDist = majorRadius + minorRadius * cos(phi)
        val x = cx + cos(theta) * ringDist
        val z = cz + sin(theta) * ringDist
        val y = cy + minorRadius * sin(phi)
        return Vec3(x, y, z)
    }

    // A plain semi-transparent flat fill covering the torus's own interior
    // (radius 0..ringRadius) — the torus itself already reads as the outline
    // (обводка); this is just the fill inside it.
    private fun appendRingFill(builder: com.mojang.blaze3d.vertex.BufferBuilder, anchorX: Double, anchorY: Double, anchorZ: Double, ringRadius: Float, accentColor: Int, animAlpha: Float) {
        val segments = 48
        val fillColor = ringFillColor(animAlpha, accentColor)

        for (index in 0 until segments) {
            val a0 = index * (Math.PI * 2.0 / segments)
            val a1 = (index + 1) * (Math.PI * 2.0 / segments)
            val x0 = anchorX + cos(a0) * ringRadius
            val z0 = anchorZ + sin(a0) * ringRadius
            val x1 = anchorX + cos(a1) * ringRadius
            val z1 = anchorZ + sin(a1) * ringRadius

            ringVertex(builder, anchorX, anchorY, anchorZ, fillColor)
            ringVertex(builder, x0, anchorY, z0, fillColor)
            ringVertex(builder, x1, anchorY, z1, fillColor)
        }
    }

    // Shared so the ring's fill always matches the same colour/alpha.
    private fun ringFillColor(fade: Float, accentColor: Int): Int {
        val gr = brighten((accentColor shr 16) and 0xFF, 0.4f)
        val gg = brighten((accentColor shr 8) and 0xFF, 0.4f)
        val gb = brighten(accentColor and 0xFF, 0.4f)
        return rgba(gr, gg, gb, (75 * fade.coerceIn(0.0f, 1.0f)).toInt())
    }

    // Soft additive glow radiating OUTWARD from the torus's own edge only,
    // flat in X/Z like the fill. This pipeline's ADDITIVE blend appears to
    // add raw RGB regardless of alpha (a flat alpha ramp rendered as one
    // uniformly bright disc with a hard cutoff, not a fade), so the falloff
    // is baked into the RGB brightness itself.
    private fun renderRingOuterGlow(anchorX: Double, anchorY: Double, anchorZ: Double, poseStack: PoseStack, ringRadius: Float, accentColor: Int, animAlpha: Float) {
        val glowWidth = ringRadius * 0.35f
        val segments = 48
        val steps = 10
        val peakBrightness = 0.4f
        val gr = brighten((accentColor shr 16) and 0xFF, 0.6f) * animAlpha
        val gg = brighten((accentColor shr 8) and 0xFF, 0.6f) * animAlpha
        val gb = brighten(accentColor and 0xFF, 0.6f) * animAlpha

        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        for (step in 0 until steps) {
            val t0 = step.toFloat() / steps
            val t1 = (step + 1).toFloat() / steps
            val falloff0 = peakBrightness * (1f - t0) * (1f - t0)
            val falloff1 = peakBrightness * (1f - t1) * (1f - t1)
            val innerStepColor = rgba((gr * falloff0).toInt(), (gg * falloff0).toInt(), (gb * falloff0).toInt(), (255 * falloff0).toInt())
            val outerStepColor = rgba((gr * falloff1).toInt(), (gg * falloff1).toInt(), (gb * falloff1).toInt(), (255 * falloff1).toInt())
            val r0 = ringRadius + glowWidth * t0
            val r1 = ringRadius + glowWidth * t1

            for (index in 0 until segments) {
                val a0 = index * (Math.PI * 2.0 / segments)
                val a1 = (index + 1) * (Math.PI * 2.0 / segments)
                val ix0 = anchorX + cos(a0) * r0
                val iz0 = anchorZ + sin(a0) * r0
                val ox0 = anchorX + cos(a0) * r1
                val oz0 = anchorZ + sin(a0) * r1
                val ix1 = anchorX + cos(a1) * r0
                val iz1 = anchorZ + sin(a1) * r0
                val ox1 = anchorX + cos(a1) * r1
                val oz1 = anchorZ + sin(a1) * r1

                ringVertex(builder, ix0, anchorY, iz0, innerStepColor)
                ringVertex(builder, ox0, anchorY, oz0, outerStepColor)
                ringVertex(builder, ox1, anchorY, oz1, outerStepColor)

                ringVertex(builder, ix0, anchorY, iz0, innerStepColor)
                ringVertex(builder, ox1, anchorY, oz1, outerStepColor)
                ringVertex(builder, ix1, anchorY, iz1, innerStepColor)
            }
        }
        builder.build()?.use { mesh -> renderPositionColorMesh(mesh, poseStack, ringGlowPipeline, "Asteria TargetESP ring outer glow") }
    }

    // Soft additive glow radiating INWARD from the torus's edge, layered on
    // top of the fill — brightens the fill near the ring without touching
    // the fill's own radius/toggle. Same RGB-brightness falloff technique
    // as the outer glow (additive ignores alpha here).
    private fun renderRingInnerGlow(anchorX: Double, anchorY: Double, anchorZ: Double, poseStack: PoseStack, ringRadius: Float, accentColor: Int, animAlpha: Float) {
        val glowWidth = ringRadius * 0.3f
        val segments = 48
        val steps = 8
        val peakBrightness = 0.35f
        val gr = brighten((accentColor shr 16) and 0xFF, 0.6f) * animAlpha
        val gg = brighten((accentColor shr 8) and 0xFF, 0.6f) * animAlpha
        val gb = brighten(accentColor and 0xFF, 0.6f) * animAlpha

        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        for (step in 0 until steps) {
            val t0 = step.toFloat() / steps
            val t1 = (step + 1).toFloat() / steps
            val falloff0 = peakBrightness * (1f - t0) * (1f - t0)
            val falloff1 = peakBrightness * (1f - t1) * (1f - t1)
            val outerStepColor = rgba((gr * falloff0).toInt(), (gg * falloff0).toInt(), (gb * falloff0).toInt(), (255 * falloff0).toInt())
            val innerStepColor = rgba((gr * falloff1).toInt(), (gg * falloff1).toInt(), (gb * falloff1).toInt(), (255 * falloff1).toInt())
            val r0 = ringRadius - glowWidth * t0
            val r1 = ringRadius - glowWidth * t1

            for (index in 0 until segments) {
                val a0 = index * (Math.PI * 2.0 / segments)
                val a1 = (index + 1) * (Math.PI * 2.0 / segments)
                val ox0 = anchorX + cos(a0) * r0
                val oz0 = anchorZ + sin(a0) * r0
                val ix0 = anchorX + cos(a0) * r1
                val iz0 = anchorZ + sin(a0) * r1
                val ox1 = anchorX + cos(a1) * r0
                val oz1 = anchorZ + sin(a1) * r0
                val ix1 = anchorX + cos(a1) * r1
                val iz1 = anchorZ + sin(a1) * r1

                ringVertex(builder, ox0, anchorY, oz0, outerStepColor)
                ringVertex(builder, ix0, anchorY, iz0, innerStepColor)
                ringVertex(builder, ix1, anchorY, iz1, innerStepColor)

                ringVertex(builder, ox0, anchorY, oz0, outerStepColor)
                ringVertex(builder, ix1, anchorY, iz1, innerStepColor)
                ringVertex(builder, ox1, anchorY, oz1, outerStepColor)
            }
        }
        builder.build()?.use { mesh -> renderPositionColorMesh(mesh, poseStack, ringGlowPipeline, "Asteria TargetESP ring inner glow") }
    }

    private fun ringVertex(builder: com.mojang.blaze3d.vertex.BufferBuilder, x: Double, y: Double, z: Double, color: Int) {
        builder.addVertex(x.toFloat(), y.toFloat(), z.toFloat()).setColor(color)
    }

    // Appear: crystals start pushed out well past their configured orbit distance and fade in as
    // they slide inward to it. Disappear: the reverse — they slide back out past that distance
    // while fading out. Same continuous-TimedAnimation approach as the Ring (see its comment):
    // retargeting mid-tween continues from the current value instead of snapping first.
    private fun renderCrystalOrbit(target: Entity?, poseStack: PoseStack, cameraPos: Vec3) {
        if (target != null) {
            crystalActiveTargetId = target.id

            val worldCenter = targetCenter(target)
            crystalLastCenterX = worldCenter.x
            crystalLastCenterY = worldCenter.y
            crystalLastCenterZ = worldCenter.z
            crystalLastRadius = max(0.58f, target.bbWidth * 0.98f)
            crystalLastHeight = max(1.8f, targetHeight(target))
            crystalLastAccent = accentColor(target, crystalRedden.value)
        } else if (crystalActiveTargetId == -1) {
            return
        }

        val active = target != null
        crystalAnimation.update()
        crystalAnimation.run(
            if (active) 1.0f else 0.0f,
            if (active) CRYSTAL_APPEAR_MS else CRYSTAL_DISAPPEAR_MS,
            { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) },
            true,
        )

        if (!active && crystalAnimation.value <= 0.001f) {
            crystalActiveTargetId = -1
            return
        }

        val now = System.currentTimeMillis()
        val progress = crystalAnimation.value
        val animScale = CRYSTAL_ANIM_SCALE - (CRYSTAL_ANIM_SCALE - 1.0f) * progress
        val animAlpha = progress

        val center = Vec3(crystalLastCenterX, crystalLastCenterY, crystalLastCenterZ).subtract(cameraPos)
        val count = 15
        val radius = crystalLastRadius * animScale
        // Reduce the epoch value before converting to Float. Converting the
        // full millisecond timestamp loses sub-second precision and freezes
        // all orbit/rotation animation between frames.
        val time = (now % 1_000_000L) / 1000.0f
        val orbitAngle = time * 0.78f
        val crystalColor = crystalLastAccent
        val baseR = (crystalColor shr 16) and 0xFF
        val baseG = (crystalColor shr 8) and 0xFF
        val baseB = crystalColor and 0xFF
        val visualHeight = crystalLastHeight
        // A jittered grid (one crystal per cell, randomized within it) reads as
        // chaotic scatter while the cell size caps how close neighbors can get,
        // unlike a golden-angle formula which visibly spirals up the cylinder.
        val gridCols = 3
        val gridRows = (count + gridCols - 1) / gridCols
        val cellAngularWidth = (Math.PI * 2.0 / gridCols).toFloat()
        val availableHeight = visualHeight - 0.12f
        // Row-to-row spacing when row centers themselves land ON the top/bottom
        // margins (see verticalFraction below) instead of half a cell short of
        // them — that half-cell was exactly the gap that kept crystals off the
        // feet and head.
        val rowSpacing = if (gridRows > 1) availableHeight / (gridRows - 1) else availableHeight
        // Placements are collected up front so each mesh gets an exclusive pass
        // over the shared Tesselator below.
        val shards = ArrayList<CrystalShard>(count)

        for (index in 0 until count) {
            val col = index % gridCols
            val row = index / gridCols
            // Offsetting alternating rows by half a cell keeps columns from
            // lining up into vertical stripes.
            val rowStagger = if (row % 2 == 0) 0.0f else cellAngularWidth * 0.5f
            val angleJitter = (crystalSeed(index, 2.31f) - 0.5f) * cellAngularWidth * 0.7f
            val angle = orbitAngle + col * cellAngularWidth + rowStagger + angleJitter
            val heightJitter = (crystalSeed(index, 3.77f) - 0.5f) * rowSpacing * 0.55f
            val verticalFraction = if (gridRows > 1) row.toFloat() / (gridRows - 1) else 0.5f
            val vertical = -visualHeight * 0.5f + 0.06f + verticalFraction * availableHeight + heightJitter
            val verticalProgress = verticalFraction
            val localRadius = radius * (0.88f + crystalSeed(index, 4.91f) * 0.18f)
            // A rotating "breathe out" pulse: each crystal gets its own
            // randomized cycle offset, and the bulge only occupies part of
            // that cycle (pulseWindow), so with count crystals staggered
            // across the period roughly count*pulseWindow are mid-pulse at
            // any moment — about 5-7 out of 15 — while the rest sit at rest.
            val pulsePeriodMs = 4200f
            val pulseWindow = 0.4f
            val pulsePhaseMs = crystalSeed(index, 12.07f) * pulsePeriodMs
            val pulseLocal = ((now + pulsePhaseMs.toLong()) % pulsePeriodMs.toLong()) / pulsePeriodMs
            val pulse = if (pulseLocal < pulseWindow) sin((pulseLocal / pulseWindow) * Math.PI.toFloat()) else 0.0f
            // Capped small so crystals never drift far from the model or
            // balloon to an unreasonable size.
            val pulseReach = localRadius * 0.22f * pulse
            val pulseScale = 1.0f + 0.3f * pulse
            val expandedRadius = localRadius + pulseReach
            val offset = Vec3(
                (cos(angle) * expandedRadius).toDouble(),
                vertical.toDouble(),
                (sin(angle) * expandedRadius).toDouble(),
            )
            val centerScale = sin(verticalProgress * Math.PI.toFloat())
            // Crystals near the feet and head are exactly 40% smaller than
            // those near the middle: 0.81 / 1.35 = 0.60.
            val levelScale = 0.81f + centerScale * 0.54f
            val shardWidth = crystalWidth.value * levelScale * pulseScale
            val shardHeight = crystalLength.value * levelScale * pulseScale
            val origin = center.add(offset.x, offset.y, offset.z)
            val phase = index * 1.618f
            val yawDirection = if (crystalSeed(index, 5.23f) > 0.5f) 1.0f else -1.0f
            val pitchDirection = if (crystalSeed(index, 6.47f) > 0.5f) 1.0f else -1.0f
            val rollDirection = if (crystalSeed(index, 7.89f) > 0.5f) 1.0f else -1.0f
            val yaw = phase + time * (0.42f + crystalSeed(index, 8.37f) * 0.32f) * yawDirection
            val pitch = phase * 0.71f + time * (0.31f + crystalSeed(index, 9.53f) * 0.27f) * pitchDirection
            val roll = phase * 1.13f + time * (0.36f + crystalSeed(index, 10.91f) * 0.30f) * rollDirection
            shards += CrystalShard(origin, yaw, pitch, roll, shardWidth, shardHeight)
        }

        // baseB is already 255 (the accent's blue channel is maxed), so any
        // additive glow layer can only push red/green upward toward it —
        // every overlap drifts the result closer to white no matter how low
        // its alpha is. With 15 shards standing close together that overlap
        // is constant, so the glow pass is dropped entirely; the flat
        // translucent body alone is what reads as the actual theme color.
        // Preserve the original 175/160/142/126 shading ratios relative to the
        // top face while letting Crystal Alpha rescale that top value.
        val alphaBase = crystalAlpha.value * animAlpha
        val top = rgba(shade(baseR, 1.28f), shade(baseG, 1.28f), shade(baseB, 1.28f), alphaBase.toInt())
        val side1 = rgba(shade(baseR, 1.16f), shade(baseG, 1.16f), shade(baseB, 1.16f), (alphaBase * 0.914f).toInt())
        val side2 = rgba(shade(baseR, 1.00f), shade(baseG, 1.00f), shade(baseB, 1.00f), (alphaBase * 0.811f).toInt())
        val bottom = rgba(shade(baseR, 0.82f), shade(baseG, 0.82f), shade(baseB, 0.82f), (alphaBase * 0.72f).toInt())

        val bodyBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        shards.forEach { shard ->
            addCrystal(bodyBuilder, shard.origin, shard.yaw, shard.pitch, shard.roll, shard.width, shard.height, top, side1, side2, bottom)
        }
        bodyBuilder.build()?.use { mesh -> renderPositionColorMesh(mesh, poseStack, crystalPipeline, "Asteria TargetESP crystals") }

        // Soft round falloff shader, but crystals sit right against (often
        // slightly inside) the target's own model, so a real depth test
        // would let the mob's mesh occlude the glow —
        // crystalGlowDotPipeline uses ALWAYS_PASS instead, matching the
        // crystal body it's centered on. Sized close to the crystal's own
        // height (not width) so the dot reads as a glow inside the shard
        // instead of a barely-visible fleck next to it.
        // crystalGlowDotPipeline is ADDITIVE, which (like the Ring's glow)
        // adds raw RGB regardless of alpha, so animAlpha has to be baked
        // into the RGB brightness itself or this dot won't fade at all —
        // it'll just pop at full brightness and then vanish outright.
        val glowColor = rgba(
            (baseR * animAlpha).toInt(),
            (baseG * animAlpha).toInt(),
            (baseB * animAlpha).toInt(),
            crystalGlowAlpha.value.toInt(),
        )
        val glowBillboards = shards.map { shard -> Billboard(shard.origin, shard.height * 0.9f * crystalGlowSize.value, glowColor) }
        renderTexturedBillboards(glowBillboards, poseStack, crystalGlowDotPipeline, additive = true)
    }

    private fun crystalSeed(index: Int, salt: Float): Float {
        val value = sin(index * 12.9898f + salt * 78.233f) * 43758.5453f
        return value - floor(value)
    }

    private fun addCrystal(builder: com.mojang.blaze3d.vertex.BufferBuilder, origin: Vec3, yaw: Float, pitch: Float, roll: Float, width: Float, height: Float, topColor: Int, sideColor1: Int, sideColor2: Int, bottomColor: Int) {
        val ex = floatArrayOf(width, 0.0f, -width, 0.0f)
        val ez = floatArrayOf(0.0f, width, 0.0f, -width)
        for (index in 0 until 4) {
            val next = (index + 1) % 4
            addCrystalTriangle(builder, origin, yaw, pitch, roll, 0.0f, height, 0.0f, ex[index], 0.0f, ez[index], ex[next], 0.0f, ez[next], if (index % 2 == 0) topColor else sideColor1)
        }
        for (index in 0 until 4) {
            val next = (index + 1) % 4
            addCrystalTriangle(builder, origin, yaw, pitch, roll, 0.0f, -height, 0.0f, ex[next], 0.0f, ez[next], ex[index], 0.0f, ez[index], if (index % 2 == 0) bottomColor else sideColor2)
        }
    }

    private fun addCrystalTriangle(builder: com.mojang.blaze3d.vertex.BufferBuilder, origin: Vec3, yaw: Float, pitch: Float, roll: Float, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float, color: Int) {
        crystalVertex(builder, origin, yaw, pitch, roll, x1, y1, z1, color)
        crystalVertex(builder, origin, yaw, pitch, roll, x2, y2, z2, color)
        crystalVertex(builder, origin, yaw, pitch, roll, x3, y3, z3, color)
    }

    private fun crystalVertex(builder: com.mojang.blaze3d.vertex.BufferBuilder, origin: Vec3, yaw: Float, pitch: Float, roll: Float, x: Float, y: Float, z: Float, color: Int) {
        val cy = cos(yaw); val sy = sin(yaw)
        var px = x * cy - z * sy
        var pz = x * sy + z * cy
        val cx = cos(pitch); val sx = sin(pitch)
        val py = y * cx - pz * sx
        pz = y * sx + pz * cx
        val cz = cos(roll); val sz = sin(roll)
        val rx = px * cz - py * sz
        val ry = px * sz + py * cz
        builder.addVertex((origin.x + rx).toFloat(), (origin.y + ry).toFloat(), (origin.z + pz).toFloat()).setColor(color)
    }

    private fun renderPositionColorMesh(mesh: MeshData, poseStack: PoseStack, pipeline: RenderPipeline, name: String) {
        val mc = Minecraft.getInstance()
        val vertexBuffer = RenderSystem.getDevice().createBuffer({ name }, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())
        try {
            val mainTarget = mc.mainRenderTarget
            val colorView = RenderSystem.outputColorTextureOverride ?: mainTarget.colorTextureView ?: return
            val depthView = RenderSystem.outputDepthTextureOverride ?: mainTarget.depthTextureView
            val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                renderModelView,
                dynamicColor,
                modelOffset,
                textureMatrix,
            )
            val encoder = RenderSystem.getDevice().createCommandEncoder()
            val pass = if (depthView != null) {
                encoder.createRenderPass({ name }, colorView, OptionalInt.empty(), depthView, OptionalDouble.empty())
            } else {
                encoder.createRenderPass({ name }, colorView, OptionalInt.empty())
            }
            pass.use {
                it.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(it)
                it.setUniform("DynamicTransforms", dynamicTransforms)
                it.setVertexBuffer(0, vertexBuffer)
                it.draw(0, mesh.drawState().vertexCount())
            }
        } finally {
            vertexBuffer.close()
        }
    }

    private fun renderTexturedBillboards(billboards: List<Billboard>, poseStack: PoseStack, pipeline: RenderPipeline, additive: Boolean) {
        if (billboards.isEmpty()) return
        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR)

        for (billboard in billboards) {
            val pose = PoseStack()
            pose.translate(billboard.center.x, billboard.center.y, billboard.center.z)
            pose.mulPose(camera.rotation())
            appendBillboard(builder, pose.last(), billboard.halfSize, billboard.color)
        }

        builder.build()?.use { mesh ->
            val vertexBuffer = RenderSystem.getDevice().createBuffer({ "Asteria TargetESP billboards" }, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())
            try {
                val mainTarget = mc.mainRenderTarget
                val colorView = RenderSystem.outputColorTextureOverride ?: mainTarget.colorTextureView ?: return
                val depthView = RenderSystem.outputDepthTextureOverride ?: mainTarget.depthTextureView
                val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                    renderModelView,
                    dynamicColor,
                    modelOffset,
                    textureMatrix,
                )
                val encoder = RenderSystem.getDevice().createCommandEncoder()
                val pass = if (depthView != null) {
                    encoder.createRenderPass(
                        { "Asteria TargetESP billboards" },
                        colorView,
                        OptionalInt.empty(),
                        depthView,
                        OptionalDouble.empty(),
                    )
                } else {
                    encoder.createRenderPass(
                        { "Asteria TargetESP billboards" },
                        colorView,
                        OptionalInt.empty(),
                    )
                }
                pass.use {
                    it.setPipeline(pipeline)
                    RenderSystem.bindDefaultUniforms(it)
                    it.setUniform("DynamicTransforms", dynamicTransforms)
                    it.setVertexBuffer(0, vertexBuffer)
                    it.draw(0, mesh.drawState().vertexCount())
                }
            } finally {
                vertexBuffer.close()
            }
        }
    }

    private fun appendBillboard(
        builder: com.mojang.blaze3d.vertex.BufferBuilder,
        pose: PoseStack.Pose,
        halfSize: Float,
        color: Int,
    ) {
        vertex(builder, pose, -halfSize, halfSize, 0.0f, 0.0f, 1.0f, color)
        vertex(builder, pose, halfSize, halfSize, 0.0f, 1.0f, 1.0f, color)
        vertex(builder, pose, halfSize, -halfSize, 0.0f, 1.0f, 0.0f, color)
        vertex(builder, pose, -halfSize, halfSize, 0.0f, 0.0f, 1.0f, color)
        vertex(builder, pose, halfSize, -halfSize, 0.0f, 1.0f, 0.0f, color)
        vertex(builder, pose, -halfSize, -halfSize, 0.0f, 0.0f, 0.0f, color)
    }

    private fun vertex(
        builder: com.mojang.blaze3d.vertex.BufferBuilder,
        pose: PoseStack.Pose,
        x: Float,
        y: Float,
        z: Float,
        u: Float,
        v: Float,
        color: Int,
    ) {
        builder.addVertex(pose, x, y, z).setUv(u, v).setColor(color)
    }

    private fun targetCenter(target: Entity): Vec3 {
        val tickDelta = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        return Vec3(
            target.xOld + (target.x - target.xOld) * tickDelta,
            target.yOld + (target.y - target.yOld) * tickDelta + targetHeight(target) * 0.5,
            target.zOld + (target.z - target.zOld) * tickDelta,
        )
    }

    // target.bbHeight is a cached field that can lag the entity's actual pose
    // (crouching, baby variants, freshly spawned mobs); the live bounding box
    // is what collision/rendering already trust, so anchor on that instead.
    private fun targetHeight(target: Entity): Float {
        val box = target.boundingBox
        return (box.maxY - box.minY).toFloat()
    }

    private fun shade(value: Int, factor: Float): Int = (value * factor).toInt().coerceIn(0, 255)

    // Mixes `percent` of white into a color channel. Used to give the shared
    // Target ESP accent a brighter "glow" look on Crystals/Ring/Torus.
    private fun brighten(component: Int, percent: Float): Int = (component + (255 - component) * percent).toInt()

    private fun rgba(red: Int, green: Int, blue: Int, alpha: Int): Int {
        return ((alpha.coerceIn(0, 255) and 0xFF) shl 24) or
            ((red.coerceIn(0, 255) and 0xFF) shl 16) or
            ((green.coerceIn(0, 255) and 0xFF) shl 8) or
            (blue.coerceIn(0, 255) and 0xFF)
    }

    private data class CrystalShard(
        val origin: Vec3,
        val yaw: Float,
        val pitch: Float,
        val roll: Float,
        val width: Float,
        val height: Float,
    )

    private data class Billboard(val center: Vec3, val halfSize: Float, val color: Int)

    companion object {
        private const val RING_APPEAR_MS = 280L
        private const val RING_DISAPPEAR_MS = 280L
        private const val RING_ANIM_SCALE = 1.6f
        private const val CRYSTAL_APPEAR_MS = 380L
        private const val CRYSTAL_DISAPPEAR_MS = 380L
        private const val CRYSTAL_ANIM_SCALE = 1.35f

        private val crystalPipeline: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/target_esp_crystals"))
                .withVertexShader(Identifier.withDefaultNamespace("core/position_color"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/position_color"))
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build()
        )

        // Same as crystalPipeline but additive instead of translucent, for
        // the Ring's outer/inner glow — additive brightens overlapping
        // geometry instead of just blending over it.
        private val ringGlowPipeline: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/target_esp_ring_glow"))
                .withVertexShader(Identifier.withDefaultNamespace("core/position_color"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/position_color"))
                .withColorTargetState(ColorTargetState(BlendFunction.ADDITIVE))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build()
        )

        // Same soft-glow fragment shader as the old ghost renderer, but crystals sit
        // right against (often slightly inside) the target's own model, so a
        // real depth test would let the mob's mesh occlude the glow. Match
        // crystalPipeline's ALWAYS_PASS instead so it draws every frame like
        // the crystal body it's centered on.
        private val crystalGlowDotPipeline: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/target_esp_crystal_glow_dot"))
                .withVertexShader(Identifier.withDefaultNamespace("core/position_tex_color"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/target_ghost"))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
                .withCull(false)
                .withColorTargetState(ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build()
        )
    }
}
