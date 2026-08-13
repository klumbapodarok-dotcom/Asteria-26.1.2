package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.ColorSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.module.setting.MultiBooleanSetting
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.TextureRenderer
import asteria.top.client.util.GuiShapeUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class ArrowsModule : Module(
    name = "Arrows",
    category = ModuleCategory.VISUALS,
    description = "Shows players around the crosshair",
) {
    companion object {
        private const val RADAR_RADIUS = 45.0f
        private const val RADAR_Y = 44.0f
        private const val INVENTORY_OFFSET_Y = 110.0f
        private const val CHAT_OFFSET_Y = 9.0f
        private const val SMOOTH_RADIUS_SPEED = 7.0f
        private const val SMOOTH_YAW_SPEED = 12.0f

        private const val LABEL_FONT_SIZE = 6.5f
        private const val LABEL_GAP_ABOVE = 4.0f
        private const val LABEL_LINE_HEIGHT = 7.0f

        private const val HEALTH_PADDING = 0.3f
        private const val HEALTH_GAP_BELOW = 2.0f

        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val HEALTH_BG = 0xB2141414.toInt()

        private val ARROW_TEXTURE = Identifier.fromNamespaceAndPath("asteria", "textures/gui/arrow.png")
    }

    private val toggles = setting(
        MultiBooleanSetting(
            "Settings",
            BooleanSetting("View nicks", true),
            BooleanSetting("View health", true),
            BooleanSetting("View distance", true),
        )
    )
    private val ignoreNaked = setting(BooleanSetting("Игнорировать голых", false))
    private val arrowSize = setting(FloatSetting("Размер", 22.0f, 12.0f, 40.0f, 1.0f))
    private val color = setting(
        ColorSetting("Цвет", 0x88FF82, Triple("Красный", "Зелёный", "Синий"))
    )

    private var smoothRadius = RADAR_RADIUS
    private var smoothYaw = 0.0f
    private var lastFrameNanos = 0L

    // Called every render frame from GuiMixin, alongside NameTagsModule
    fun onRenderWithEntities(graphics: GuiGraphicsExtractor, partialTick: Float) {
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (!enabled || player == null || mc.level == null || mc.debugOverlay.showDebugScreen()) return

        val deltaSeconds = frameDeltaSeconds()
        val window = mc.window
        val centerX = window.guiScaledWidth / 2.0f
        val centerY = window.guiScaledHeight / 2.0f
        val targetRadius = targetRadius(mc)
        val cameraPos = mc.gameRenderer.mainCamera.position()

        for (other in mc.level!!.players()) {
            if (!shouldRender(other, player)) continue

            val diffX = (other.xOld + (other.x - other.xOld) * partialTick) - cameraPos.x
            val diffZ = (other.zOld + (other.z - other.zOld) * partialTick) - cameraPos.z

            val yawRad = Math.toRadians(smoothYaw.toDouble())
            val cosYaw = cos(yawRad)
            val sinYaw = sin(yawRad)
            val rotatedX = -(diffZ * cosYaw - diffX * sinYaw)
            val rotatedZ = -(diffX * cosYaw + diffZ * sinYaw)
            val angle = Math.toDegrees(atan2(rotatedX, rotatedZ)).toFloat()

            val angleRad = Math.toRadians(angle.toDouble())
            val drawX = (centerX + smoothRadius * cos(angleRad)).toFloat()
            val drawY = (centerY + smoothRadius * sin(angleRad)).toFloat()

            if (toggles.enabled("View nicks") || toggles.enabled("View distance")) {
                renderLabels(graphics, player, other, drawX, drawY)
            }
            if (toggles.enabled("View health")) {
                renderHealth(graphics, other, drawX, drawY, angle)
            }
            renderArrow(graphics, drawX, drawY, angle)
        }

        smoothRadius = lerp(smoothRadius, targetRadius, SMOOTH_RADIUS_SPEED, deltaSeconds)
        smoothYaw = lerpAngle(smoothYaw, mc.gameRenderer.mainCamera.yaw(), SMOOTH_YAW_SPEED, deltaSeconds)
    }

    private fun targetRadius(mc: Minecraft): Float {
        var radius = RADAR_Y
        if (mc.screen is InventoryScreen) radius += INVENTORY_OFFSET_Y
        if (mc.screen is ChatScreen) radius += CHAT_OFFSET_Y
        return radius
    }

    private fun shouldRender(other: Player, self: Player): Boolean {
        if (other === self || !other.isAlive) return false
        return !ignoreNaked.value || other.armorValue != 0
    }

    // Labels are drawn axis-aligned (not rotated with the radial angle) and centered
    // on the arrow's x so they stay readable and stay put directly above the icon.
    private fun renderLabels(graphics: GuiGraphicsExtractor, self: Player, other: Player, x: Float, y: Float) {
        val lines = buildList {
            if (toggles.enabled("View nicks")) add(other.name.string)
            if (toggles.enabled("View distance")) {
                val dx = other.x - self.x
                val dz = other.z - self.z
                add("${sqrt(dx * dx + dz * dz).toInt()}m")
            }
        }
        if (lines.isEmpty()) return

        val iconHalf = arrowSize.value / 2.0f
        var lineY = y - iconHalf - LABEL_GAP_ABOVE - lines.size * LABEL_LINE_HEIGHT
        for (line in lines) {
            val width = FontRenderer.width(FontRenderer.Face.SfMedium, line, LABEL_FONT_SIZE)
            FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, line, x - width / 2.0f, lineY, LABEL_FONT_SIZE, WHITE)
            lineY += LABEL_LINE_HEIGHT
        }
    }

    private fun renderHealth(graphics: GuiGraphicsExtractor, other: Player, x: Float, y: Float, angle: Float) {
        val ratio = (other.health / other.maxHealth).coerceIn(0.0f, 1.0f)
        val fillColor = healthColor(ratio)

        val size = arrowSize.value
        val barWidth = size * 0.55f
        val barHeight = size * 0.14f
        val barTop = size / 2.0f + HEALTH_GAP_BELOW
        val fillWidth = (barWidth - HEALTH_PADDING * 2.0f) * ratio

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.rotate(Math.toRadians(angle.toDouble()).toFloat())

        GuiShapeUtil.roundedFill(graphics, -barWidth / 2.0f, barTop, barWidth / 2.0f, barTop + barHeight, barHeight * 0.4f, HEALTH_BG)
        GuiShapeUtil.roundedFill(
            graphics,
            -barWidth / 2.0f + HEALTH_PADDING,
            barTop + HEALTH_PADDING,
            -barWidth / 2.0f + HEALTH_PADDING + fillWidth,
            barTop + barHeight - HEALTH_PADDING,
            barHeight * 0.4f,
            fillColor,
        )

        pose.popMatrix()
    }

    private fun renderArrow(graphics: GuiGraphicsExtractor, x: Float, y: Float, angle: Float) {
        val size = arrowSize.value
        val half = size / 2.0f

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.rotate(Math.toRadians(angle.toDouble()).toFloat())

        TextureRenderer.draw(graphics, ARROW_TEXTURE, -half, -half, size, size, arrowColor())

        pose.popMatrix()
    }

    private fun arrowColor(): Int {
        return color.argb
    }

    // Mirrors the original: full health = full set color, low health = a darkened shade of it.
    private fun healthColor(ratio: Float): Int {
        val base = arrowColor()
        return lerpColor(darken(base, 0.35f), base, ratio)
    }

    private fun darken(color: Int, amount: Float): Int {
        val factor = 1.0f - amount.coerceIn(0.0f, 1.0f)
        val r = ((color shr 16) and 0xFF) * factor
        val g = ((color shr 8) and 0xFF) * factor
        val b = (color and 0xFF) * factor
        return (color and 0xFF000000.toInt()) or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0.0f, 1.0f)
        val a = lerpChannel((from shr 24) and 0xFF, (to shr 24) and 0xFF, f)
        val r = lerpChannel((from shr 16) and 0xFF, (to shr 16) and 0xFF, f)
        val g = lerpChannel((from shr 8) and 0xFF, (to shr 8) and 0xFF, f)
        val b = lerpChannel(from and 0xFF, to and 0xFF, f)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpChannel(from: Int, to: Int, t: Float): Int {
        return (from + (to - from) * t).roundToInt().coerceIn(0, 255)
    }

    private fun frameDeltaSeconds(): Float {
        val now = System.nanoTime()
        val delta = if (lastFrameNanos == 0L) 0.0f else ((now - lastFrameNanos) / 1_000_000_000.0f).coerceIn(0.0f, 0.5f)
        lastFrameNanos = now
        return delta
    }

    private fun lerp(current: Float, target: Float, speed: Float, dt: Float): Float {
        val t = (dt * speed).coerceIn(0.0f, 1.0f)
        return current + (target - current) * t
    }

    private fun lerpAngle(current: Float, target: Float, speed: Float, dt: Float): Float {
        val t = (dt * speed).coerceIn(0.0f, 1.0f)
        return current + wrapDegrees(target - current) * t
    }

    private fun wrapDegrees(value: Float): Float {
        var wrapped = value % 360.0f
        if (wrapped >= 180.0f) wrapped -= 360.0f
        if (wrapped < -180.0f) wrapped += 360.0f
        return wrapped
    }
}
