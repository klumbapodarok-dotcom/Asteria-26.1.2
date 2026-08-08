package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.mixin.AbstractArrowAccessor
import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.render.AsteriaGuiRenderer
import asteria.top.client.render.FontRenderer
import asteria.top.client.util.GuiBoxUtil
import asteria.top.client.util.ProjectionUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.Gizmos
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.entity.projectile.arrow.SpectralArrow
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

// Landing marker uses the exact NameTagsModule template: a small rect on the left (item icon
// instead of a player head) separated by a gap from a second rect (item name + red countdown,
// in the name/health text slots).
class PredictionsModule : Module(
    name = "Predictions",
    category = ModuleCategory.VISUALS,
    description = "Shows where thrown projectiles will land",
) {
    private val showPearls = setting(BooleanSetting("Ender Pearls", true))
    private val showArrows = setting(BooleanSetting("Arrows", true))
    private val showSnowballs = setting(BooleanSetting("Snowballs", true))
    private val showEggs = setting(BooleanSetting("Eggs", true))
    private val showPotions = setting(BooleanSetting("Potions", true))
    private val showBottles = setting(BooleanSetting("Experience Bottles", true))
    private val showTridents = setting(BooleanSetting("Tridents", true))
    private val scanRange = setting(FloatSetting("Scan Range", 96.0f, 16.0f, 256.0f, 4.0f))
    private val scale = setting(FloatSetting("Scale", 1.0f, 0.5f, 3.0f, 0.1f))
    private val backgroundAlpha = setting(FloatSetting("Background Alpha", 0.5f, 0.0f, 1.0f, 0.05f))
    private val showLine = setting(BooleanSetting("Trajectory Line", true))
    private val lineWidth = setting(FloatSetting("Line Width", 4.0f, 0.5f, 8.0f, 0.1f))

    companion object {
        private const val TAG_RADIUS = 4.5f
        private const val TAG_VERTICAL_OFFSET = 3.0f
        private const val ICON_RECT_GAP = 1.5f
        private const val GROUND_OFFSET = 0.15
        private const val MAX_STEPS = 220
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val TIME_COLOR = 0xFFFF5555.toInt()
        // Same accent color as the Watermark HUD widget's metric icons (StandardHudWidgets.kt).
        private const val LINE_RGB = 0x91B7FF

        @Volatile
        private var currentBlurBoxes: List<AsteriaOverlay.BlurBox> = emptyList()
    }

    private class PredictedPoint(
        val entity: Entity,
        val landing: Vec3,
        val stack: ItemStack,
        val name: String,
    )

    private val activePoints = mutableListOf<PredictedPoint>()

    override fun onDisable() {
        activePoints.clear()
    }

    // Called every render frame from GuiMixin, alongside NameTagsModule
    fun onRenderWithEntities(graphics: GuiGraphicsExtractor, partialTick: Float) {
        val mc = Minecraft.getInstance()
        val player = mc.player
        val level = mc.level
        if (!enabled || player == null || level == null) return

        refreshPoints(level, player)
        if (activePoints.isEmpty()) {
            currentBlurBoxes = emptyList()
            return
        }

        val scaleVal = scale.value
        val bgAlpha = backgroundAlpha.value
        val window = mc.window
        val screenW = window.guiScaledWidth.toFloat()
        val screenH = window.guiScaledHeight.toFloat()
        val cameraPos = mc.gameRenderer.mainCamera.position()

        data class Marker(
            val distanceSq: Double,
            val stack: ItemStack,
            val name: String,
            val timeText: String,
            val tagX: Float, val tagY: Float, val tagWidth: Float, val tagHeight: Float,
            val textX: Float, val textY: Float, val nameWidth: Float, val fontSize: Float,
            val iconRectX: Float, val iconRectSize: Float,
        )

        val markers = mutableListOf<Marker>()
        for (point in activePoints) {
            val anchor = ProjectionUtil.project(point.landing.add(0.0, GROUND_OFFSET, 0.0)) ?: continue
            val distanceSq = point.landing.distanceToSqr(cameraPos)

            val fontSize = 8.0f * scaleVal
            val gap = 2.0f * scaleVal
            val timeText = " ${"%.1f".format(remainingSeconds(level, point))}"

            val nameWidth = FontRenderer.width(FontRenderer.Face.SfMedium, point.name, fontSize)
            val timeWidth = FontRenderer.width(FontRenderer.Face.SfMedium, timeText, fontSize)
            val tagWidth = nameWidth + timeWidth + gap * 2.0f
            val tagHeight = fontSize + gap * 2.0f

            val iconRectSize = tagHeight
            val iconGap = ICON_RECT_GAP * scaleVal
            val totalWidth = iconRectSize + iconGap + tagWidth

            val groupX = anchor.x - totalWidth / 2.0f
            val tagX = groupX + iconRectSize + iconGap
            val tagY = anchor.y - tagHeight - TAG_VERTICAL_OFFSET * scaleVal

            if (groupX + totalWidth < 0 || groupX > screenW || tagY + tagHeight < 0 || tagY > screenH) continue

            val textX = tagX + gap
            val textY = tagY + (tagHeight - fontSize) / 2.0f
            markers += Marker(distanceSq, point.stack, point.name, timeText, tagX, tagY, tagWidth, tagHeight, textX, textY, nameWidth, fontSize, groupX, iconRectSize)
        }

        val guiScale = window.guiScale.toFloat()
        val blurBoxes = mutableListOf<AsteriaOverlay.BlurBox>()
        for (marker in markers) {
            blurBoxes += AsteriaOverlay.BlurBox(marker.tagX * guiScale, marker.tagY * guiScale, marker.tagWidth * guiScale, marker.tagHeight * guiScale, TAG_RADIUS * scaleVal * guiScale, tintStrength = bgAlpha)
            blurBoxes += AsteriaOverlay.BlurBox(marker.iconRectX * guiScale, marker.tagY * guiScale, marker.iconRectSize * guiScale, marker.iconRectSize * guiScale, TAG_RADIUS * scaleVal * guiScale, tintStrength = bgAlpha)
        }
        currentBlurBoxes = blurBoxes

        for (marker in markers.sortedByDescending { it.distanceSq }) {
            if (!ModuleManager.postProcessing.enabled) {
                val boxes = mutableListOf<GuiBoxUtil.Box>()
                val bgColor = AsteriaGuiRenderer.alpha(0x000000, bgAlpha)
                AsteriaGuiRenderer.rect(boxes, marker.tagX, marker.tagY, marker.tagWidth, marker.tagHeight, TAG_RADIUS * scaleVal, bgColor)
                AsteriaGuiRenderer.rect(boxes, marker.iconRectX, marker.tagY, marker.iconRectSize, marker.iconRectSize, TAG_RADIUS * scaleVal, bgColor)
                boxes.forEach { GuiBoxUtil.draw(graphics, it) }
            }

            val iconPadding = (marker.iconRectSize - marker.fontSize) / 2.0f
            val pose = graphics.pose()
            pose.pushMatrix()
            pose.translate(marker.iconRectX + iconPadding, marker.tagY + iconPadding)
            pose.scale(marker.fontSize / 16.0f)
            graphics.item(marker.stack, 0, 0)
            pose.popMatrix()

            FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, marker.name, marker.textX, marker.textY, marker.fontSize, WHITE)
            FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, marker.timeText, marker.textX + marker.nameWidth, marker.textY, marker.fontSize, TIME_COLOR)
        }
    }

    fun blurBoxes(guiScale: Float): List<AsteriaOverlay.BlurBox> {
        if (!enabled || !ModuleManager.postProcessing.enabled) return emptyList()
        return currentBlurBoxes
    }

    // Called every world-render frame (Fabric's BEFORE_GIZMOS event, see AsteriaClient.kt),
    // alongside Backtrack/AutoTrap's gizmo rendering.
    fun renderGizmos() {
        val mc = Minecraft.getInstance()
        val level = mc.level
        if (!enabled || !showLine.value || level == null || activePoints.isEmpty()) return

        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val width = lineWidth.value

        mc.levelRenderer.collectPerFrameGizmos().use {
            for (point in activePoints) {
                val projectile = point.entity as? Projectile ?: continue
                if (projectile.isRemoved || hasLanded(projectile)) continue

                val path = livePath(projectile, level, partialTick)
                if (path.size < 2) continue

                // Fades from fully opaque at the projectile to fully transparent at the
                // predicted landing point, so the trail smoothly dissolves ahead of it.
                val segments = path.size - 1
                for (i in 0 until segments) {
                    val t = i.toFloat() / segments.toFloat()
                    val alpha = ((1.0f - t) * 255.0f).toInt().coerceIn(0, 255)
                    val color = (alpha shl 24) or LINE_RGB
                    Gizmos.line(path[i], path[i + 1], color, width).setAlwaysOnTop()
                }
            }
        }
    }

    private fun livePath(projectile: Projectile, level: Level, partialTick: Float): List<Vec3> {
        val path = mutableListOf<Vec3>()
        var pos = projectile.getPosition(partialTick)
        var motion = projectile.deltaMovement
        path += pos
        for (step in 0 until MAX_STEPS) {
            val prev = pos
            pos = pos.add(motion)
            path += pos
            val ray = level.clip(ClipContext(prev, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile))
            if (ray.type == HitResult.Type.BLOCK) break
            motion = applyMotion(projectile, motion, pos, level)
            if (pos.y < -64 || pos.y > 512) break
        }
        return path
    }

    private fun refreshPoints(level: Level, player: Player) {
        // Keep the original prediction until the projectile actually disappears or lands.
        activePoints.removeIf {
            val projectile = it.entity as? Projectile
            it.entity.isRemoved || projectile == null || hasLanded(projectile)
        }

        val maxRangeSq = (scanRange.value * scanRange.value).toDouble()
        val searchBox = player.boundingBox.inflate(scanRange.value.toDouble())
        level.getEntities(player, searchBox) { it is Projectile }.forEach { entity ->
            val projectile = entity as Projectile
            if (hasLanded(projectile) || !isSupported(projectile)) return@forEach
            if (player.distanceToSqr(entity) > maxRangeSq) return@forEach
            if (activePoints.any { it.entity === entity }) return@forEach

            val sim = simulate(level, projectile)
            if (!sim.hit) return@forEach
            if (player.position().distanceToSqr(sim.location) > maxRangeSq) return@forEach

            val stack = stackForProjectile(projectile)
            activePoints += PredictedPoint(entity, sim.location, stack, stack.hoverName.string)
        }
    }

    private fun remainingSeconds(level: Level, point: PredictedPoint): Float {
        val projectile = point.entity as? Projectile ?: return 0.0f
        val sim = simulate(level, projectile)
        if (!sim.hit) return 0.0f
        val partialTick = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        return (sim.ticks / 20.0f - partialTick / 20.0f).coerceAtLeast(0.0f)
    }

    private class SimResult(val hit: Boolean, val location: Vec3, val ticks: Int)

    private fun simulate(level: Level, projectile: Projectile): SimResult {
        var pos = projectile.position()
        var motion = projectile.deltaMovement
        for (step in 0 until MAX_STEPS) {
            val prev = pos
            pos = pos.add(motion)
            val ray = level.clip(ClipContext(prev, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile))
            if (ray.type == HitResult.Type.BLOCK) return SimResult(true, ray.location, step + 1)
            motion = applyMotion(projectile, motion, pos, level)
            if (pos.y < -64 || pos.y > 512) break
        }
        return SimResult(false, pos, MAX_STEPS)
    }

    private fun applyMotion(projectile: Projectile, motion: Vec3, pos: Vec3, level: Level): Vec3 {
        var drag = 0.99
        var gravity = 0.03
        when (projectile) {
            is AbstractArrow -> gravity = 0.05
            is ThrownExperienceBottle -> {
                gravity = 0.07
                drag = 0.95
            }
            is AbstractThrownPotion -> {
                gravity = 0.05
                drag = 0.95
            }
        }

        val inWater = level.getFluidState(BlockPos.containing(pos)).typeHolder().`is`(FluidTags.WATER)
        if (inWater) drag *= 0.8

        return motion.multiply(drag, drag, drag).add(0.0, -gravity, 0.0)
    }

    private fun isSupported(projectile: Projectile): Boolean {
        return when (projectile) {
            is ThrownEnderpearl -> showPearls.value
            is Snowball -> showSnowballs.value
            is ThrownEgg -> showEggs.value
            is AbstractThrownPotion -> showPotions.value
            is ThrownExperienceBottle -> showBottles.value
            is ThrownTrident -> showTridents.value
            is SpectralArrow -> showArrows.value
            is AbstractArrow -> showArrows.value
            else -> false
        }
    }

    private fun hasLanded(projectile: Projectile): Boolean {
        if (projectile.onGround()) return true
        return projectile is AbstractArrow &&
            (projectile as AbstractArrowAccessor).asteriaIsInGround()
    }

    private fun stackForProjectile(projectile: Projectile): ItemStack {
        return when (projectile) {
            is ThrownEnderpearl -> ItemStack(Items.ENDER_PEARL)
            is Snowball -> ItemStack(Items.SNOWBALL)
            is ThrownEgg -> ItemStack(Items.EGG)
            is ThrownExperienceBottle -> ItemStack(Items.EXPERIENCE_BOTTLE)
            is ThrownTrident -> ItemStack(Items.TRIDENT)
            is SpectralArrow -> ItemStack(Items.SPECTRAL_ARROW)
            is AbstractThrownPotion -> ItemStack(Items.SPLASH_POTION)
            is AbstractArrow -> ItemStack(Items.ARROW)
            else -> ItemStack.EMPTY
        }
    }
}
