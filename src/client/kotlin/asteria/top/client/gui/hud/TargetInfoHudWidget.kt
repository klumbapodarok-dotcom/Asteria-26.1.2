package asteria.top.client.gui.hud

import asteria.top.client.module.ModuleManager
import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.AsteriaGuiRenderer
import asteria.top.client.render.PlayerHeadRenderer
import asteria.top.client.render.TextureRenderer
import asteria.top.client.util.AnimationUtil
import asteria.top.client.util.GuiBoxUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import kotlin.math.abs
import kotlin.math.ceil

private const val TARGET_HEAD_WIDTH = 55.0f
private const val TARGET_DOT_Y_OFFSET = 1.0f
private val dotTexture = Identifier.fromNamespaceAndPath("asteria", "textures/glow.png")

class TargetInfoHudWidget : HudWidget(
    id = "target_info",
    title = "Target Info",
    iconGlyph = "P",
    x = 142.0f,
    y = 159.0f,
    width = 150.0f,
    height = 55.0f,
    enabledByDefault = false,
) {
    private val showAnimation = AnimationUtil.TimedAnimation(0.0f)
    private val healthBarAnimation = TargetBarAnimation()
    private val absorptionBarAnimation = TargetBarAnimation()
    private var retainedTarget: LivingEntity? = null
    private var lastTargetId = Int.MIN_VALUE
    private var lastVisible = false

    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        return (enabled && ModuleManager.killaura.target != null) || preview
    }

    override fun update(mc: Minecraft, preview: Boolean) {
        val current = ModuleManager.killaura.target ?: if (preview) mc.player else retainedTarget
        val name = current?.name?.string ?: "Target"
        val hpText = current?.let { ceil((it.health + absorption(it)).toDouble()).toInt().toString() } ?: "20"
        val badgeWidth = maxOf(20.0f, FontRenderer.width(FontRenderer.Face.SfSemibold, hpText, 10.0f) + 12.0f)
        val nameWidth = FontRenderer.width(FontRenderer.Face.SfRegular, name, 10.5f)
        val contentWidth = maxOf(116.0f, 10.0f + 14.0f + nameWidth + 8.0f + badgeWidth + 10.0f)
        bounds.width = TARGET_HEAD_WIDTH + contentWidth
        bounds.height = 55.0f
    }

    override fun blurBoxes(guiScale: Float, tintStrength: Float): List<AsteriaOverlay.BlurBox> {
        val contentWidth = bounds.width - TARGET_HEAD_WIDTH
        return super.blurBoxes(guiScale, tintStrength) + AsteriaOverlay.BlurBox(
            (bounds.x + TARGET_HEAD_WIDTH + 8.0f) * guiScale,
            (bounds.y + 39.0f) * guiScale,
            (contentWidth - 21.0f) * guiScale,
            6.0f * guiScale,
            3.0f * guiScale,
            tintStrength,
        )
    }

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val current = ModuleManager.killaura.target ?: if (preview) mc.player else null
        val visible = current != null
        showAnimation.update()
        showAnimation.run(if (visible) 1.0f else 0.0f,
            if (visible) 220L else 360L,
            if (visible) AnimationUtil::easeOutQuad else AnimationUtil::easeOutSine,
            true,
        )
        if (visible) retainedTarget = current
        val target = current ?: retainedTarget
        if (retainedTarget == null || showAnimation.value <= 0.01f) {
            if (!visible) {
                if (showAnimation.value <= 0.01f) {
                    retainedTarget = null
                    healthBarAnimation.snap(0.0f)
                    absorptionBarAnimation.snap(0.0f)
                    lastVisible = false
                    lastTargetId = Int.MIN_VALUE
                }
            }
            return
        }

        val currentTargetId = current?.id ?: Int.MIN_VALUE
        val targetChanged = visible && (!lastVisible || currentTargetId != lastTargetId)
        if (targetChanged) {
            val initialMaxHealth = current.maxHealth.coerceAtLeast(1.0f)
            val initialHealth = (current.health / initialMaxHealth).coerceIn(0.0f, 1.0f)
            val initialAbsorption = (absorption(current) / initialMaxHealth).coerceIn(0.0f, 1.0f)
            healthBarAnimation.snap(initialHealth)
            absorptionBarAnimation.snap(initialAbsorption)
        }
        lastVisible = visible
        lastTargetId = currentTargetId

        val name = target?.name?.string ?: "Target"
        val health = target?.health ?: 20.0f
        val absorption = target?.let { absorption(it) } ?: 0.0f
        val maxHealth = target?.maxHealth?.coerceAtLeast(1.0f) ?: 20.0f
        val ratio = (health / maxHealth).coerceIn(0.0f, 1.0f)
        val absorptionRatio = (absorption / maxHealth).coerceIn(0.0f, 1.0f)
        val hpText = ceil((health + absorption).toDouble()).toInt().toString()
        val badgeTextSize = 10.0f
        val badgeWidth = maxOf(20.0f, FontRenderer.width(FontRenderer.Face.SfSemibold, hpText, badgeTextSize) + 12.0f)
        val nameWidth = FontRenderer.width(FontRenderer.Face.SfRegular, name, 10.5f)
        val headWidth = TARGET_HEAD_WIDTH
        val contentWidth = maxOf(116.0f, 10.0f + 14.0f + nameWidth + 8.0f + badgeWidth + 10.0f)
        val panelHeight = 55.0f
        bounds.width = headWidth + contentWidth
        bounds.height = panelHeight

        healthBarAnimation.run(ratio, if (ratio < healthBarAnimation.value) 260L else 320L)
        absorptionBarAnimation.run(absorptionRatio, if (absorptionRatio < absorptionBarAnimation.value) 260L else 320L)

        val panelScale = 1.0f
        val renderX = bounds.x + bounds.width * (1.0f - panelScale) * 0.5f
        val renderY = bounds.y + bounds.height * (1.0f - panelScale) * 0.5f
        val renderHeadWidth = headWidth * panelScale
        val renderContentWidth = contentWidth * panelScale
        val renderPanelHeight = panelHeight * panelScale

        drawTargetPanel(graphics, renderX, renderY, renderHeadWidth, renderContentWidth, renderPanelHeight)
        drawTargetHead(graphics, target, renderX + 7.0f * panelScale, renderY + 7.0f * panelScale, 41.0f * panelScale)

        val contentX = renderX + renderHeadWidth
        val badgeX = renderX + renderHeadWidth + renderContentWidth - 10.0f * panelScale - badgeWidth
        HudStyle.rect(graphics, badgeX, renderY + 9.0f * panelScale, badgeWidth, 13.0f, 6.5f, 0x4088FF82)
        FontRenderer.drawCentered(
            graphics,
            FontRenderer.Face.SfSemibold,
            hpText,
            badgeX + badgeWidth * 0.5f,
            renderY + 11.0f * panelScale,
            badgeTextSize,
            HudStyle.TEXT,
        )

        val nameLeft = contentX + 12.0f
        val nameRight = badgeX - 8.0f
        drawLeftAlignedDotName(graphics, name, nameLeft, nameRight, renderY + 15.0f * panelScale)

        val barX = contentX + 8.0f
        val barY = renderY + 39.0f * panelScale
        val barW = renderContentWidth - 21.0f * panelScale
        HudStyle.panel(graphics, HudBounds(barX, barY, barW, 6.0f), 3.0f)
        val healthFillWidth = barW * healthBarAnimation.value.coerceIn(0.0f, 1.0f)
        if (healthFillWidth > 0.01f) {
            HudStyle.pill(graphics, barX, barY, healthFillWidth, 6.0f, HudStyle.ACCENT)
        }
        val absorptionW = barW * absorptionBarAnimation.value.coerceIn(0.0f, 1.0f)
        if (absorptionW > 0.01f) {
            HudStyle.pill(graphics, barX + barW - absorptionW, barY, absorptionW, 6.0f, 0xFFFFC94A.toInt())
        }
    }

    private fun absorption(target: LivingEntity): Float {
        return target.absorptionAmount.coerceAtLeast(0.0f)
    }
}

private fun drawTargetPanel(graphics: GuiGraphicsExtractor, x: Float, y: Float, headWidth: Float, contentWidth: Float, panelHeight: Float) {
    HudStyle.panel(graphics, HudBounds(x, y, headWidth, panelHeight), 10.0f)
    HudStyle.panel(graphics, HudBounds(x + headWidth, y, contentWidth, panelHeight), 10.0f)
}

private fun drawLeftAlignedDotName(graphics: GuiGraphicsExtractor, name: String, left: Float, right: Float, centerY: Float) {
    val nameSize = 10.5f
    val dotSize = 8.5f
    val nameWidth = FontRenderer.width(FontRenderer.Face.SfRegular, name, nameSize)
    val dotX = left - 2.5f
    val nameX = left + dotSize + 2.0f
    val clampedNameX = nameX.coerceAtMost(right - nameWidth)
    TextureRenderer.draw(graphics, dotTexture, dotX, centerY - dotSize * 0.5f + TARGET_DOT_Y_OFFSET, dotSize, dotSize, HudStyle.ACCENT)
    FontRenderer.draw(graphics, FontRenderer.Face.SfRegular, name, clampedNameX, centerY - 5.0f, nameSize, HudStyle.TEXT)
}

private fun drawTargetHead(graphics: GuiGraphicsExtractor, target: LivingEntity?, x: Float, y: Float, size: Float) {
    val headDrawSize = size
    val headDrawX = x
    val headDrawY = y
    val radius = maxOf(3.5f, size * 0.12f)
    when (target) {
        is AbstractClientPlayer -> {
            PlayerHeadRenderer.drawStableHead(graphics, target, headDrawX, headDrawY, headDrawSize, radius)
            drawHeadStroke(graphics, headDrawX, headDrawY, headDrawSize, radius)
        }
        null -> HudStyle.icon(graphics, "P", x + 12.0f, y + 12.0f, 13.0f)
        else -> Unit
    }
}

private fun drawHeadStroke(graphics: GuiGraphicsExtractor, x: Float, y: Float, size: Float, radius: Float) {
    val boxes = mutableListOf<GuiBoxUtil.Box>()
    AsteriaGuiRenderer.outlineRect(boxes, x, y, size, size, radius, 0x1AFFFFFF)
    boxes.forEach { GuiBoxUtil.draw(graphics, it) }
}

private class TargetBarAnimation {
    var value = 0.0f
        private set
    private var from = 0.0f
    private var target = 0.0f
    private var startMillis = 0L
    private var durationMillis = 1L

    fun snap(next: Float) {
        val clamped = next.coerceIn(0.0f, 1.0f)
        value = clamped
        from = clamped
        target = clamped
        startMillis = System.currentTimeMillis()
        durationMillis = 1L
    }

    fun run(next: Float, duration: Long) {
        update()
        val clamped = next.coerceIn(0.0f, 1.0f)
        if (abs(clamped - target) > 0.001f) {
            from = value
            target = clamped
            startMillis = System.currentTimeMillis()
            durationMillis = duration.coerceAtLeast(1L)
        }
        update()
    }

    private fun update() {
        val elapsed = (System.currentTimeMillis() - startMillis).toFloat() / durationMillis.toFloat()
        val eased = AnimationUtil.easeOutSoftBack(elapsed)
        value = from + (target - from) * eased
    }
}
