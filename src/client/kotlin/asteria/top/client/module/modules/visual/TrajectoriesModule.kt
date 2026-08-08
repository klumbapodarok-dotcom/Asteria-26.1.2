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
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.render.WorldGeometryEmitter
import asteria.top.client.render.WorldLineRenderer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.OptionalDouble
import java.util.OptionalInt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Port of Asteria12111's Trajectories.
 *
 * Shows the predicted impact point for a projectile still held by the local player.
 * The marker is intentionally rendered in world space (not as a HUD billboard), so its
 * size and perspective remain correct from every camera angle.
 *
 * All prediction logic and marker geometry (ribbon polyline, camera-facing impact hemisphere,
 * ground potion disc) is a 1:1 port. The original composited its glow through HandGlowRenderer,
 * an offscreen capture/blur pass built on the 1.21 render layer stack that does not exist in this
 * engine; the same look is reproduced here by re-emitting the geometry as widened additive passes,
 * which is how this project's own TargetESP renders its glows.
 */
class TrajectoriesModule : Module(
    name = "Trajectories",
    category = ModuleCategory.VISUALS,
    description = "Показывает место приземления снаряда",
) {
    // Every other tuning value is now fixed at the setting values this client was played with;
    // only the trajectory line itself stays toggleable.
    private val showLine = setting(BooleanSetting("Линия", true))

    private val renderModelView = Matrix4f()
    private val dynamicColor = Vector4f(1.0f, 1.0f, 1.0f, 1.0f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    private data class Impact(
        val position: Vec3,
        val markerColor: Int,
        val potionRadius: Double,
        val potionCenter: Vec3,
        val entityHit: Boolean,
        val path: List<Vec3>,
    )

    private data class PathCut(val position: Vec3, val path: List<Vec3>)

    fun renderGizmos(context: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        val player = mc.player
        val level = mc.level
        if (!enabled || player == null || level == null) return
        // The prediction is built from the camera, which in third person sits behind the
        // player and produces a trajectory that doesn't match what the shot would do.
        if (!mc.options.cameraType.isFirstPerson) return

        val camera = mc.gameRenderer.mainCamera
        val impacts = predictImpacts(player, camera.position(), camera.xRot(), camera.yaw(), level)
        if (impacts.isEmpty()) return

        renderModelView.set(context.levelState().cameraRenderState.viewRotationMatrix)
        val cameraPos = camera.position()

        if (showLine.value) {
            // Glow first so the solid core draws on top of it, mirroring the original's
            // capture-then-composite order.
            renderTrajectoryGlow(impacts, cameraPos)
            renderTrajectoryLine(impacts, cameraPos)
        }
        renderImpactMarkers(impacts, cameraPos)

        if (impacts.none { it.potionRadius > 0.0 }) return
        renderPotionGlow(impacts, cameraPos)
    }

    private fun renderTrajectoryLine(impacts: List<Impact>, cameraPos: Vec3) {
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        val emitter = WorldGeometryEmitter(builder)
        for (impact in impacts) {
            WorldLineRenderer.polyline(emitter, impact.path, cameraPos, impact.markerColor, LINE_WIDTH)
        }
        builder.build()?.use { mesh -> drawMesh(mesh, additivePipeline, "Asteria Trajectories line") }
    }

    // Drawn through a depth-tested pipeline so the ball sits on the block it hits instead of
    // shining through the world, and offset out along the surface normal by its own radius so
    // the depth test doesn't bury the near half inside that block.
    private fun renderImpactMarkers(impacts: List<Impact>, cameraPos: Vec3) {
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        val emitter = WorldGeometryEmitter(builder)
        for (impact in impacts) {
            emitImpactSphere(emitter, impact.position, cameraPos, impact.markerColor)
        }
        builder.build()?.use { mesh -> drawMesh(mesh, markerPipeline, "Asteria Trajectories marker") }
    }

    // The original fed a single mask into an external blur/bloom compositor. Reproduce that
    // falloff directly: a few progressively wider, dimmer additive copies of the same ribbon,
    // with the flow constants driving a travelling brightness wave.
    private fun renderTrajectoryGlow(impacts: List<Impact>, cameraPos: Vec3) {
        val intensity = (0.45f + GLOW_STRENGTH * 0.50f) * GLOW_OPACITY
        if (intensity <= 0.001f) return

        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        val emitter = WorldGeometryEmitter(builder)
        val steps = 4
        val time = System.nanoTime() / 1_000_000_000.0

        for (impact in impacts) {
            for (step in 0 until steps) {
                val spread = (step + 1).toFloat() / steps
                val width = LINE_WIDTH + GLOW_WIDTH * spread
                val falloff = (1.0f - spread) * (1.0f - spread)
                val colors = flowColors(impact.path.size, impact.markerColor, intensity * falloff, time, FLOW_STRENGTH, FLOW_SPEED)
                WorldLineRenderer.gradientPolyline(emitter, impact.path, cameraPos, colors, width)
            }
        }
        builder.build()?.use { mesh -> drawMesh(mesh, additivePipeline, "Asteria Trajectories glow") }
    }

    private fun renderPotionGlow(impacts: List<Impact>, cameraPos: Vec3) {
        val intensity = (0.65f + POTION_GLOW_STRENGTH * 0.48f) * POTION_GLOW_OPACITY
        if (intensity <= 0.001f) return

        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR)
        val emitter = WorldGeometryEmitter(builder)
        val time = System.nanoTime() / 1_000_000_000.0
        val wave = 0.5 - 0.5 * cos(time * POTION_FLOW_SPEED * Math.PI * 2.0)
        val flow = (1.0 + POTION_FLOW_STRENGTH * wave * 0.35).toFloat()

        for (impact in impacts) {
            if (impact.potionRadius <= 0.0) continue
            val color = scaleAlpha(impact.markerColor, (intensity * flow).coerceAtMost(1.0f))
            // Exactly the potion's own effect radius — no extra spread.
            emitPotionRadius(emitter, impact.potionCenter, cameraPos, impact.potionRadius, color)
        }
        builder.build()?.use { mesh -> drawMesh(mesh, potionGlowPipeline, "Asteria Trajectories potion glow") }
    }

    /** Travelling brightness wave along the path, matching the original's flow settings. */
    private fun flowColors(pointCount: Int, color: Int, intensity: Float, time: Double, strength: Float, speed: Float): IntArray {
        val colors = IntArray(pointCount)
        val divisor = max(1, pointCount - 1)
        for (i in 0 until pointCount) {
            val pathPhase = i / divisor.toDouble() * 1.35
            val blend = (0.5 - 0.5 * cos((time * speed - pathPhase) * Math.PI * 2.0)).toFloat()
            val alphaScale = (intensity * (1.0f + strength * blend * 0.5f)).coerceIn(0.0f, 1.0f)
            colors[i] = scaleAlpha(color, alphaScale)
        }
        return colors
    }

    private fun scaleAlpha(color: Int, scale: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * scale.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    private fun drawMesh(mesh: MeshData, pipeline: RenderPipeline, name: String) {
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

    private fun predictImpacts(player: Player, cameraPos: Vec3, pitch: Float, yaw: Float, level: Level): List<Impact> {
        val mainHand = player.mainHandItem
        val offHand = player.offhandItem
        val held = if (isSupportedItem(mainHand.item)) mainHand else if (isSupportedItem(offHand.item)) offHand else ItemStack.EMPTY
        if (held.`is`(Items.CROSSBOW)) {
            val projectiles = held.get(DataComponents.CHARGED_PROJECTILES)
            val chargedMultishot = projectiles != null && projectiles.items().size >= 3
            if (chargedMultishot || hasMultishotEnchantment(held)) {
                val impacts = mutableListOf<Impact>()
                for (yawOffset in floatArrayOf(-10.0f, 0.0f, 10.0f)) {
                    val impact = predictImpact(player, cameraPos, pitch, yaw, yawOffset, level)
                    if (impact != null) impacts += impact
                }
                return synchronizeMultishotEntityHit(impacts, pitch, yaw)
            }
        }
        val impact = predictImpact(player, cameraPos, pitch, yaw, 0.0f, level) ?: return emptyList()
        return listOf(impact)
    }

    private fun hasMultishotEnchantment(stack: ItemStack): Boolean {
        if (!stack.isEnchanted) return false
        return stack.enchantments.entrySet().any { it.key.`is`(Enchantments.MULTISHOT) }
    }

    private fun predictImpact(player: Player, cameraPos: Vec3, pitch: Float, yaw: Float, yawOffset: Float, level: Level): Impact? {
        val active = player.useItem
        val mainHand = player.mainHandItem
        val offHand = player.offhandItem
        // Use the real camera direction. The player's yaw/pitch may be replaced by aura
        // rotations, while arrows launched from the client follow the view in front of the user.
        val look = Vec3.directionFromRotation(pitch, yaw + yawOffset).normalize()

        val held = if (isSupportedItem(mainHand.item)) mainHand else if (isSupportedItem(offHand.item)) offHand else ItemStack.EMPTY
        if (held.isEmpty) return null
        val heldItem = held.item
        val potionRadius = if (held.`is`(Items.LINGERING_POTION)) {
            LINGERING_POTION_RADIUS
        } else if (held.`is`(Items.SPLASH_POTION)) {
            SPLASH_POTION_RADIUS
        } else {
            0.0
        }

        val holdingBow = heldItem is BowItem
        val drawingBow = player.isUsingItem && active.item is BowItem

        val start: Vec3
        var velocity: Vec3
        val gravity: Double
        val drag: Double

        if (holdingBow) {
            // A bow always has a preview. While drawing, use vanilla's fixed 72,000-tick
            // use duration; outside the use action show the full-power landing point.
            val elapsedUseTicks = if (drawingBow) max(1, 72_000 - player.useItemRemainingTicks) else 20
            val pull = BowItem.getPowerForTime(elapsedUseTicks)
            start = cameraPos.add(look.scale(0.35))
            velocity = look.scale((pull * 3.0f).toDouble())
            gravity = 0.05
            drag = 0.99
        } else if (held.`is`(Items.CROSSBOW)) {
            val projectiles = held.get(DataComponents.CHARGED_PROJECTILES)
            val firework = projectiles != null && projectiles.contains(Items.FIREWORK_ROCKET)
            start = cameraPos.add(look.scale(0.35))
            velocity = look.scale(if (firework) 1.6 else 3.15)
            gravity = if (firework) 0.0 else 0.05
            drag = if (firework) 1.0 else 0.99
        } else if (held.`is`(Items.TRIDENT)) {
            start = cameraPos.add(look.scale(0.35))
            velocity = look.scale(2.5)
            gravity = 0.05
            drag = 0.99
        } else if (held.`is`(Items.SPLASH_POTION) || held.`is`(Items.LINGERING_POTION)) {
            // Render potion range by the same rule as the other held
            // projectiles: begin at the crosshair and trace every flight step.
            start = cameraPos.add(look.scale(0.35))
            velocity = look.scale(0.5)
            gravity = 0.05
            drag = 0.95
        } else if (held.`is`(Items.EXPERIENCE_BOTTLE)) {
            val throwDirection = Vec3.directionFromRotation(pitch - 20.0f, yaw + yawOffset).normalize()
            start = cameraPos.add(0.0, -0.1, 0.0)
            velocity = inheritShooterVelocity(throwDirection.scale(0.7), player)
            gravity = 0.07
            drag = 0.95
        } else {
            // Pearls, snowballs and eggs share the same initial speed and gravity.
            start = cameraPos.add(look.scale(0.35))
            velocity = look.scale(1.5)
            gravity = 0.03
            drag = 0.99
        }

        var position = start
        val path = mutableListOf(position)
        for (tick in 0 until 400) {
            val next = position.add(velocity)
            val blockHit = level.clip(ClipContext(position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            val entityHit = ProjectileUtil.getEntityHitResult(
                player,
                position,
                next,
                AABB(position, next).inflate(1.0),
                { isProjectileTarget(player, it) },
                position.distanceToSqr(next),
            )

            var hit: HitResult = blockHit
            if (entityHit != null &&
                (blockHit.type == HitResult.Type.MISS || position.distanceToSqr(entityHit.location) < position.distanceToSqr(blockHit.location))
            ) {
                hit = entityHit
            }

            if (hit.type != HitResult.Type.MISS) {
                path += hit.location
                val normal = resolveSurfaceNormal(hit, velocity)
                val markerColor = if (hit is EntityHitResult) ENTITY_COLOR else OUTLINE_COLOR
                val markerCenter = resolveMarkerCenter(hit, normal)
                val potionCenter = if (potionRadius > 0.0) resolvePotionGroundCenter(hit.location, player, level) else markerCenter
                return Impact(markerCenter, markerColor, potionRadius, potionCenter, hit is EntityHitResult, path)
            }

            position = next
            path += position
            velocity = velocity.scale(drag).add(0.0, -gravity, 0.0)
            if (position.y < level.minY - 8) return null
        }
        return null
    }

    private fun synchronizeMultishotEntityHit(impacts: List<Impact>, pitch: Float, yaw: Float): List<Impact> {
        val lockedHit = impacts.filter { it.entityHit }.minByOrNull { pathLength(it.path) } ?: return impacts

        // Treat the depth of the first entity hit as an invisible camera-facing block.
        // The real hit remains untouched; sibling trajectories stop where they cross it.
        val planeNormal = Vec3.directionFromRotation(pitch, yaw).normalize()
        return impacts.map { impact ->
            if (impact === lockedHit) {
                withSharedEntityColor(impact, impact.position, impact.path)
            } else {
                val cut = cutPathAtPlane(impact.path, lockedHit.position, planeNormal)
                if (cut != null) {
                    withSharedEntityColor(impact, cut.position, cut.path)
                } else {
                    // A real wall may stop this ray before the shared plane. Keep that safe
                    // collision position, but the whole multishot still shares the red state.
                    withSharedEntityColor(impact, impact.position, impact.path)
                }
            }
        }
    }

    private fun withSharedEntityColor(source: Impact, position: Vec3, path: List<Vec3>): Impact {
        return Impact(position, ENTITY_COLOR, source.potionRadius, source.potionCenter, true, path)
    }

    private fun cutPathAtPlane(path: List<Vec3>, planePoint: Vec3, planeNormal: Vec3): PathCut? {
        if (path.size < 2) return null
        val clipped = mutableListOf(path.first())
        for (i in 1 until path.size) {
            val from = path[i - 1]
            val to = path[i]
            val fromDistance = from.subtract(planePoint).dot(planeNormal)
            val toDistance = to.subtract(planePoint).dot(planeNormal)
            if (fromDistance <= 0.0 && toDistance >= 0.0) {
                val denominator = toDistance - fromDistance
                val t = if (abs(denominator) < 1.0e-9) 0.0 else max(0.0, min(1.0, -fromDistance / denominator))
                val intersection = from.add(to.subtract(from).scale(t))
                clipped += intersection
                return PathCut(intersection, clipped.toList())
            }
            clipped += to
        }
        return null
    }

    private fun pathLength(path: List<Vec3>): Double {
        var length = 0.0
        for (i in 1 until path.size) length += path[i - 1].distanceTo(path[i])
        return length
    }

    private fun inheritShooterVelocity(launchVelocity: Vec3, player: Player): Vec3 {
        val playerVelocity = player.deltaMovement
        return launchVelocity.add(
            playerVelocity.x,
            if (player.onGround()) 0.0 else playerVelocity.y,
            playerVelocity.z,
        )
    }

    private fun isSupportedItem(item: Item): Boolean {
        return item is BowItem ||
            item === Items.CROSSBOW ||
            item === Items.TRIDENT ||
            item === Items.ENDER_PEARL ||
            item === Items.SNOWBALL ||
            item === Items.EGG ||
            item === Items.SPLASH_POTION ||
            item === Items.LINGERING_POTION ||
            item === Items.EXPERIENCE_BOTTLE
    }

    private fun isProjectileTarget(shooter: Player, entity: Entity): Boolean {
        return entity !== shooter && !entity.isSpectator && entity.isPickable && entity.isAlive
    }

    private fun resolveSurfaceNormal(hit: HitResult, velocity: Vec3): Vec3 {
        if (hit is BlockHitResult) {
            return Vec3(
                hit.direction.stepX.toDouble(),
                hit.direction.stepY.toDouble(),
                hit.direction.stepZ.toDouble(),
            )
        }
        if (hit is EntityHitResult) {
            // Entity markers must stay vertical: discard the vertical component so looking
            // at a head or feet never tilts the circle toward the floor or ceiling.
            var normal = hit.location.subtract(hit.entity.boundingBox.center)
            normal = Vec3(normal.x, 0.0, normal.z)
            if (normal.lengthSqr() > 1.0e-8) return normal.normalize()
            val horizontalVelocity = Vec3(-velocity.x, 0.0, -velocity.z)
            if (horizontalVelocity.lengthSqr() > 1.0e-8) return horizontalVelocity.normalize()
        }
        return if (velocity.lengthSqr() > 1.0e-8) velocity.normalize().scale(-1.0) else Vec3(0.0, 1.0, 0.0)
    }

    private fun resolveMarkerCenter(hit: HitResult, normal: Vec3): Vec3 {
        // Keep the marker centred on the exact ray collision point instead of snapping it to the
        // centre of the impacted block face: the ball sits half-buried in the surface, and the
        // depth test clips the buried half instead of letting it shine through.
        return hit.location.add(normal.scale(SURFACE_OFFSET))
    }

    private fun resolvePotionGroundCenter(impactPosition: Vec3, player: Player, level: Level): Vec3 {
        val rayStart = impactPosition.add(0.0, 0.75, 0.0)
        val rayEnd = impactPosition.add(0.0, -6.0, 0.0)
        val groundHit = level.clip(ClipContext(rayStart, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        return if (groundHit.type != HitResult.Type.MISS) groundHit.location else impactPosition
    }

    private fun emitImpactSphere(
        emitter: WorldGeometryEmitter,
        center: Vec3,
        cameraPos: Vec3,
        color: Int,
        radius: Double = MARKER_RADIUS,
    ) {
        val relativeCenter = center.subtract(cameraPos)
        var viewDirection = cameraPos.subtract(center)
        viewDirection = if (viewDirection.lengthSqr() < 1.0e-8) Vec3(0.0, 0.0, 1.0) else viewDirection.normalize()

        for (latitude in 0 until SPHERE_LATITUDE_SEGMENTS) {
            val latitude0 = -Math.PI * 0.5 + Math.PI * latitude / SPHERE_LATITUDE_SEGMENTS
            val latitude1 = -Math.PI * 0.5 + Math.PI * (latitude + 1) / SPHERE_LATITUDE_SEGMENTS

            for (longitude in 0 until SPHERE_LONGITUDE_SEGMENTS) {
                val longitude0 = Math.PI * 2.0 * longitude / SPHERE_LONGITUDE_SEGMENTS
                val longitude1 = Math.PI * 2.0 * (longitude + 1) / SPHERE_LONGITUDE_SEGMENTS

                val normal00 = sphereNormal(latitude0, longitude0)
                val normal01 = sphereNormal(latitude0, longitude1)
                val normal11 = sphereNormal(latitude1, longitude1)
                val normal10 = sphereNormal(latitude1, longitude0)

                // The marker is rendered without world depth. Emit only the camera-facing
                // hemisphere so its back side cannot shine through and flatten the ball.
                val patchNormal = normal00.add(normal01).add(normal11).add(normal10)
                if (patchNormal.dot(viewDirection) <= 0.0) continue

                emitter.emitQuad(
                    relativeCenter.add(normal00.scale(radius)),
                    relativeCenter.add(normal01.scale(radius)),
                    relativeCenter.add(normal11.scale(radius)),
                    relativeCenter.add(normal10.scale(radius)),
                    shadeSphereColor(color, normal00.dot(viewDirection)),
                    shadeSphereColor(color, normal01.dot(viewDirection)),
                    shadeSphereColor(color, normal11.dot(viewDirection)),
                    shadeSphereColor(color, normal10.dot(viewDirection)),
                )
            }
        }
    }

    private fun sphereNormal(latitude: Double, longitude: Double): Vec3 {
        val latitudeRadius = cos(latitude)
        return Vec3(
            latitudeRadius * cos(longitude),
            sin(latitude),
            latitudeRadius * sin(longitude),
        )
    }

    // Brighter than the original's 0.42 floor: the ball reads as a solid white bead with only
    // a hint of shading toward its silhouette, instead of a grey sphere.
    private fun shadeSphereColor(color: Int, facing: Double): Int {
        val light = 0.82 + max(0.0, facing) * 0.18
        val alpha = (((color ushr 24) and 0xFF) * (0.92 + max(0.0, facing) * 0.08)).toInt()
        val red = (((color ushr 16) and 0xFF) * light).toInt()
        val green = (((color ushr 8) and 0xFF) * light).toInt()
        val blue = ((color and 0xFF) * light).toInt()
        return (min(255, alpha) shl 24) or
            (min(255, red) shl 16) or
            (min(255, green) shl 8) or
            min(255, blue)
    }

    private fun emitPotionRadius(
        emitter: WorldGeometryEmitter,
        center: Vec3,
        cameraPos: Vec3,
        radius: Double,
        color: Int,
    ) {
        // One horizontal textured quad produces a continuous radial alpha falloff with
        // no visible geometry bands. World depth clips it against terrain and walls.
        val relativeCenter = center.subtract(cameraPos).add(0.0, POTION_GROUND_OFFSET, 0.0)
        val glowColor = (min(210, (color ushr 24) and 0xFF) shl 24) or (color and 0x00FFFFFF)
        emitter.emitTexturedQuad(
            relativeCenter.add(-radius, 0.0, -radius),
            relativeCenter.add(-radius, 0.0, radius),
            relativeCenter.add(radius, 0.0, radius),
            relativeCenter.add(radius, 0.0, -radius),
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,
            glowColor,
        )
    }

    companion object {
        private const val MARKER_RADIUS = 0.19
        private const val SURFACE_OFFSET = 0.006

        // Fixed values, matching the settings this client was played with.
        private const val LINE_WIDTH = 4.05
        private const val GLOW_WIDTH = 21.0
        private const val GLOW_STRENGTH = 1.0f
        private const val GLOW_OPACITY = 1.0f
        private const val FLOW_STRENGTH = 1.2f
        private const val FLOW_SPEED = 0.72f
        private const val POTION_GLOW_STRENGTH = 2.0f
        private const val POTION_GLOW_OPACITY = 1.0f
        private const val POTION_FLOW_STRENGTH = 0.8f
        private const val POTION_FLOW_SPEED = 0.45f
        private const val SPHERE_LATITUDE_SEGMENTS = 16
        private const val SPHERE_LONGITUDE_SEGMENTS = 24
        private const val SPLASH_POTION_RADIUS = 4.0
        private const val LINGERING_POTION_RADIUS = 3.0
        private const val POTION_GROUND_OFFSET = 0.012
        private const val OUTLINE_COLOR = 0xE8FFFFFF.toInt()
        private const val ENTITY_COLOR = 0xF0FF3B3B.toInt()

        // Matches the original's POSITION_COLOR_QUADS_ADDITIVE layer: additive, no cull,
        // no depth test (the marker is deliberately drawn through the world).
        private val additivePipeline: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/trajectories_additive"))
                .withVertexShader(Identifier.withDefaultNamespace("core/position_color"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/position_color"))
                .withColorTargetState(ColorTargetState(BlendFunction.ADDITIVE))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build()
        )

        // The impact ball is depth tested so blocks occlude it, unlike the line.
        private val markerPipeline: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/trajectories_marker"))
                .withVertexShader(Identifier.withDefaultNamespace("core/position_color"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/position_color"))
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
                .build()
        )

        // TRANSLUCENT (not additive) — same blend mode as markerPipeline, so the ring reads
        // with the same real alpha/brightness as the ball instead of washing out to solid
        // white wherever it overlaps itself or a bright background.
        private val potionGlowPipeline: RenderPipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/trajectories_potion_glow"))
                .withVertexShader(Identifier.withDefaultNamespace("core/position_tex_color"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("asteria", "core/trajectories_potion"))
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .build()
        )
    }
}
