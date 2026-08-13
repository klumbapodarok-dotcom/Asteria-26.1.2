package asteria.top.client.module.modules.visual

import asteria.top.client.gui.hud.HudStyle
import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.ColorSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.module.setting.IntSetting
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

class ParticlesModule : Module(
    name = "Particles",
    category = ModuleCategory.VISUALS,
    description = "Улучшенные частицы при атаках и бросках",
) {
    enum class ParticleType(val label: String, fileName: String) {
        BLOOM("Bloom", "firefly"),
        STAR("Star", "star"),
        SNOW("Snow", "snowflake"),
        HEART("Heart", "heart"),
        DOLLAR("Dollar", "dollar"),
        TRIANGLE("Triangle", "triangle"),
        SAKURA("Sakura", "sakura"),
        GENSHIN("Genshin", "genshin"),
        RHOMBUS("Rhombus", "rhombus");

        val texture: Identifier = Identifier.fromNamespaceAndPath("asteria", "textures/particles/$fileName.png")
    }

    enum class ColorMode(val label: String) {
        CLIENT("Клиентский"),
        CUSTOM("Свой"),
    }

    private val spawnOnAttack = setting(BooleanSetting("При атаке", true))
    private val spawnOnThrow = setting(BooleanSetting("При броске", true))
    private val spawnInWorld = setting(BooleanSetting("В мире", false))
    private val particleType = setting(
        EnumSetting("Тип частиц", ParticleType.entries.toTypedArray(), ParticleType.BLOOM) { it.label }
    )
    private val size = setting(FloatSetting("Размер", 0.5f, 0.0f, 1.0f, 0.1f))
    private val amount = setting(IntSetting("Количество", 10, 1, 100, 1))
    private val lifetime = setting(FloatSetting("Время жизни", 2.0f, 0.5f, 10.0f, 0.5f, "s"))
    private val worldRadius = setting(
        IntSetting("Радиус в мире", 12, 2, 50, 1).visibleWhen { spawnInWorld.value }
    )
    private val physics = setting(BooleanSetting("Физика", true))
    private val colorMode = setting(
        EnumSetting("Режим цвета", ColorMode.entries.toTypedArray(), ColorMode.CLIENT) { it.label }
    )
    private val customColor = setting(
        ColorSetting("Цвет", 0xFF4040, Triple("Красный", "Зелёный", "Синий")).also {
            it.visibleWhen { colorMode.value == ColorMode.CUSTOM }
        }
    )

    private val attackParticles = mutableListOf<Particle>()
    private val projectileParticles = mutableListOf<Particle>()
    private val worldParticles = mutableListOf<Particle>()
    private val renderModelView = Matrix4f()
    private val dynamicColor = Vector4f(1.0f, 1.0f, 1.0f, 1.0f)
    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()
    private var lastFrameNanos = System.nanoTime()
    private var trackedLevel: ClientLevel? = null

    fun onAttack(target: Entity) {
        if (!enabled || !spawnOnAttack.value) return
        val height = (target.boundingBox.maxY - target.boundingBox.minY).coerceAtLeast(0.1)
        repeat(amount.value) {
            spawn(
                attackParticles,
                Vec3(target.x, target.y + random(0.0, height), target.z),
                Vec3(random(-6.0, 6.0), random(-6.0, 6.0), random(-6.0, 6.0)),
            )
        }
    }

    fun tick() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        val player = minecraft.player
        if (!enabled || level == null || player == null) {
            clear()
            trackedLevel = level
            return
        }
        if (trackedLevel !== level) {
            clear()
            trackedLevel = level
        }

        if (spawnOnThrow.value) {
            val count = max(1, (amount.value / 10.0f).toInt())
            level.entitiesForRendering().forEach { entity ->
                val projectile = entity is ThrownEnderpearl || entity is AbstractArrow
                if (!projectile || entity is ThrownTrident && entity.onGround()) return@forEach
                if (entity.xOld == entity.x && entity.yOld == entity.y && entity.zOld == entity.z) return@forEach
                repeat(count) {
                    spawn(
                        projectileParticles,
                        entity.position().add(random(-0.2, 0.2), random(-0.2, 0.2), random(-0.2, 0.2)),
                        Vec3(random(-1.0, 1.0), random(-0.3, 0.3), random(-1.0, 1.0)),
                    )
                }
            }
        }

        if (spawnInWorld.value) {
            val radius = worldRadius.value
            val count = max(1, (amount.value / 2.0f).toInt())
            repeat(count) {
                val randomX = player.x + random(-radius.toDouble(), radius.toDouble())
                val randomZ = player.z + random(-radius.toDouble(), radius.toDouble())
                val blockX = Mth.floor(randomX)
                val blockZ = Mth.floor(randomZ)
                val topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ)
                var position = Vec3(
                    blockX + random(0.0, 1.0),
                    player.y + random(player.bbHeight.toDouble(), radius.toDouble()),
                    blockZ + random(0.0, 1.0),
                )
                repeat(64) {
                    if (level.getBlockState(net.minecraft.core.BlockPos.containing(position)).isAir) return@repeat
                    position = position.add(0.0, 1.0, 0.0)
                }
                if (position.y < topY) position = Vec3(position.x, topY + random(0.0, 1.0), position.z)
                spawn(
                    worldParticles,
                    position,
                    Vec3(
                        player.deltaMovement.x + random(-2.0, 2.0),
                        random(-0.2, 0.2),
                        player.deltaMovement.z + random(-2.0, 2.0),
                    ),
                )
            }
        }

        removeExpired(attackParticles)
        removeExpired(projectileParticles)
        removeExpired(worldParticles)
    }

    fun render(context: LevelRenderContext) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        if (!enabled || minecraft.player == null) return
        if (attackParticles.isEmpty() && projectileParticles.isEmpty() && worldParticles.isEmpty()) return

        val nowNanos = System.nanoTime()
        val delta = ((nowNanos - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.1)
        lastFrameNanos = nowNanos
        updateParticles(level, attackParticles, delta)
        updateParticles(level, projectileParticles, delta)
        updateParticles(level, worldParticles, delta)

        renderModelView.set(context.levelState().cameraRenderState.viewRotationMatrix)
        val camera = minecraft.gameRenderer.mainCamera
        val cameraPos = camera.position()
        val renderTime = System.currentTimeMillis()
        val grouped = (attackParticles + projectileParticles + worldParticles).groupBy { it.type }

        for (type in ParticleType.entries) {
            val particles = grouped[type] ?: continue
            val builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX_COLOR,
            )
            for (particle in particles) {
                appendParticle(builder, particle, cameraPos, camera.rotation(), 1.0f, renderTime)
                if (type == ParticleType.BLOOM) {
                    appendParticle(builder, particle, cameraPos, camera.rotation(), 0.5f, renderTime)
                }
            }
            builder.build()?.use { mesh ->
                val vertexBuffer = RenderSystem.getDevice().createBuffer(
                    { "Asteria particles ${type.label}" },
                    GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer(),
                )
                try {
                    draw(type, vertexBuffer, mesh.drawState().vertexCount())
                } finally {
                    vertexBuffer.close()
                }
            }
        }
    }

    private fun appendParticle(
        builder: com.mojang.blaze3d.vertex.BufferBuilder,
        particle: Particle,
        cameraPos: Vec3,
        cameraRotation: org.joml.Quaternionf,
        scale: Float,
        renderTime: Long,
    ) {
        val alpha = particle.alpha(lifetimeMillis(), renderTime)
        if (alpha <= 0.001f) return
        val halfSize = particle.size * scale
        val opacity = (alpha * 255.0f).toInt().coerceIn(0, 255)
        val color = (opacity shl 24) or (particle.color and 0x00FFFFFF)
        val relative = particle.position.subtract(cameraPos)
        val pose = PoseStack()
        pose.translate(relative.x, relative.y, relative.z)
        pose.mulPose(cameraRotation)
        pose.mulPose(Axis.ZP.rotationDegrees(particle.rotation))
        val last = pose.last()

        vertex(builder, last, -halfSize, halfSize, 0.0f, 0.0f, 0.0f, color)
        vertex(builder, last, halfSize, halfSize, 0.0f, 1.0f, 0.0f, color)
        vertex(builder, last, halfSize, -halfSize, 0.0f, 1.0f, 1.0f, color)
        vertex(builder, last, -halfSize, halfSize, 0.0f, 0.0f, 0.0f, color)
        vertex(builder, last, halfSize, -halfSize, 0.0f, 1.0f, 1.0f, color)
        vertex(builder, last, -halfSize, -halfSize, 0.0f, 0.0f, 1.0f, color)
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

    private fun draw(type: ParticleType, vertexBuffer: GpuBuffer, vertexCount: Int) {
        val minecraft = Minecraft.getInstance()
        val target = minecraft.mainRenderTarget
        val colorView = RenderSystem.outputColorTextureOverride ?: target.colorTextureView ?: return
        val depthView = RenderSystem.outputDepthTextureOverride ?: target.depthTextureView
        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            renderModelView,
            dynamicColor,
            modelOffset,
            textureMatrix,
        )
        val texture = minecraft.textureManager.getTexture(type.texture)
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        val pass = if (depthView != null) {
            encoder.createRenderPass(
                { "Asteria particles" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty(),
            )
        } else {
            encoder.createRenderPass({ "Asteria particles" }, colorView, OptionalInt.empty())
        }
        pass.use {
            it.setPipeline(PARTICLE_PIPELINE)
            RenderSystem.bindDefaultUniforms(it)
            it.setUniform("DynamicTransforms", dynamicTransforms)
            it.bindTexture(
                "Sampler0",
                texture.textureView,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            )
            it.setVertexBuffer(0, vertexBuffer)
            it.draw(0, vertexCount)
        }
    }

    private fun spawn(list: MutableList<Particle>, position: Vec3, velocity: Vec3) {
        val particleSize = 0.05f + size.value * 0.2f
        list += Particle(
            type = particleType.value,
            position = position.add(0.0, particleSize.toDouble(), 0.0),
            velocity = velocity.scale(0.05),
            color = resolveColor(list.size * 100),
            size = particleSize,
        )
    }

    private fun resolveColor(offset: Int): Int {
        if (colorMode.value == ColorMode.CUSTOM) {
            return customColor.argb
        }
        val accent = HudStyle.ACCENT
        val firstR = (accent shr 16) and 0xFF
        val firstG = (accent shr 8) and 0xFF
        val firstB = accent and 0xFF
        var phase = ((System.currentTimeMillis() / 10L + offset) % 360L).toInt()
        if (phase >= 180) phase = 360 - phase
        val progress = phase / 180.0f
        val red = Mth.lerp(progress, firstR.toFloat(), firstR * 0.7f).toInt()
        val green = Mth.lerp(progress, firstG.toFloat(), firstG * 0.7f).toInt()
        val blue = Mth.lerp(progress, firstB.toFloat(), firstB * 0.7f).toInt()
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun updateParticles(level: ClientLevel, particles: List<Particle>, delta: Double) {
        particles.forEach { it.update(level, delta, physics.value) }
    }

    private fun removeExpired(particles: MutableList<Particle>) {
        val lifetime = lifetimeMillis()
        particles.removeIf { it.ageMillis() >= lifetime }
    }

    private fun lifetimeMillis(): Long = max(250L, (lifetime.value * 1000.0f).toLong())

    private fun clear() {
        attackParticles.clear()
        projectileParticles.clear()
        worldParticles.clear()
        lastFrameNanos = System.nanoTime()
    }

    override fun onEnable() {
        clear()
    }

    override fun onDisable() {
        clear()
    }

    private inner class Particle(
        val type: ParticleType,
        var position: Vec3,
        var velocity: Vec3,
        val color: Int,
        val size: Float,
    ) {
        var box = particleBox(position, size)
        val rotation = (random(0.0, 360.0) / 15.0).toInt() * 15.0f
        val createdAt = System.currentTimeMillis()

        fun update(level: ClientLevel, delta: Double, applyPhysics: Boolean) {
            val movement = delta * 60.0 * 0.2
            if (!applyPhysics) {
                position = position.add(velocity.scale(movement))
                box = particleBox(position, size)
                return
            }
            velocity = velocity.scale(0.985.pow(delta * 60.0)).subtract(0.0, 0.0035 * delta * 60.0, 0.0)
            moveAxis(level, velocity.x * movement, 0)
            moveAxis(level, velocity.y * movement, 1)
            moveAxis(level, velocity.z * movement, 2)
        }

        private fun moveAxis(level: ClientLevel, amount: Double, axis: Int) {
            if (abs(amount) <= 1.0E-6) return
            val moved = when (axis) {
                0 -> box.move(amount, 0.0, 0.0)
                1 -> box.move(0.0, amount, 0.0)
                else -> box.move(0.0, 0.0, amount)
            }
            if (!level.noBlockCollision(null, moved)) {
                bounce(axis)
                return
            }
            box = moved
            position = when (axis) {
                0 -> position.add(amount, 0.0, 0.0)
                1 -> position.add(0.0, amount, 0.0)
                else -> position.add(0.0, 0.0, amount)
            }
        }

        private fun bounce(axis: Int) {
            var x = velocity.x
            var y = velocity.y
            var z = velocity.z
            when (axis) {
                0 -> x = -x * 0.55
                1 -> {
                    if (y < 0.0) {
                        x *= 0.72
                        z *= 0.72
                    }
                    y = -y * 0.55
                }
                else -> z = -z * 0.55
            }
            velocity = Vec3(stopSmall(x), stopSmall(y), stopSmall(z))
        }

        fun alpha(lifetime: Long, renderTime: Long): Float {
            val age = renderTime - createdAt
            val fadeIn = minOf(400L, max(100L, lifetime / 5L))
            val fadeOutStart = max(fadeIn + 1L, (lifetime * 0.62f).toLong())
            val value = when {
                age < fadeIn -> age / fadeIn.toFloat()
                age > fadeOutStart -> 1.0f - (age - fadeOutStart) / max(1L, lifetime - fadeOutStart).toFloat()
                else -> 1.0f
            }.coerceIn(0.0f, 1.0f)
            val inverse = 1.0f - value
            return 1.0f - inverse * inverse * inverse
        }

        fun ageMillis(): Long = System.currentTimeMillis() - createdAt

        private fun stopSmall(value: Double): Double = if (abs(value) < 0.003) 0.0 else value
    }

    companion object {
        private val PARTICLE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("asteria", "pipeline/particles"))
                .withVertexShader(Identifier.withDefaultNamespace("core/position_tex_color"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/position_tex_color"))
                .withSampler("Sampler0")
                .withColorTargetState(ColorTargetState(BlendFunction.ADDITIVE))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .build()
        )

        private fun particleBox(position: Vec3, size: Float): AABB {
            val half = size / 2.0
            return AABB(
                position.x - half,
                position.y - half,
                position.z - half,
                position.x + half,
                position.y + half,
                position.z + half,
            )
        }

        private fun random(min: Double, max: Double): Double {
            if (max <= min) return min
            return ThreadLocalRandom.current().nextDouble(min, max)
        }
    }
}
