package asteria.top.client.gui.hud

import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.MsdfIconRenderer
import asteria.top.client.render.PlayerHeadRenderer
import asteria.top.client.render.TextureRenderer
import asteria.top.client.util.AnimationUtil
import asteria.top.client.util.GuiShapeUtil
import asteria.top.client.util.ScissorUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// The mock is a 160x45 card drawn at a 12px font, which is the same 0.75-ish
// away from the HUD as the keybind panel's was: taken at face value every part
// of it reads a size too large beside the rest of the HUD. So its proportions
// are kept exactly and its pixels are scaled by [MOCK_SCALE], with only the text
// sizes coming from the HUD's own template instead.
private const val MOCK_SCALE = 0.7f

private const val TARGET_WIDTH = 160.0f * MOCK_SCALE
private const val TARGET_HEIGHT = 45.0f * MOCK_SCALE
private const val TARGET_RADIUS = 13.0f * MOCK_SCALE
private const val TARGET_PADDING = 6.0f * MOCK_SCALE

private const val TARGET_EQUIPMENT_HEIGHT = 12.0f
private const val TARGET_EQUIPMENT_GAP = 3.0f
private const val TARGET_TOTAL_HEIGHT = TARGET_EQUIPMENT_HEIGHT + TARGET_EQUIPMENT_GAP + TARGET_HEIGHT

private const val TARGET_HEAD_SIZE = 33.0f * MOCK_SCALE
private const val TARGET_HEAD_RADIUS = TARGET_HEAD_SIZE * 0.42f
// Everything right of the head hangs off this, the mock's 46px column.
private const val TARGET_CONTENT_X = 46.0f * MOCK_SCALE
// Smallest gap kept between the name and the health readout sharing its row.
private const val TARGET_NAME_GAP = 5.0f * MOCK_SCALE

// A medium cut at 8.5 rather than the mock's regular 12: at HUD sizes the
// regular face has no solid core left between its antialiased edges, and this is
// the size the rest of the HUD's text is set at.
private val TARGET_FACE = FontRenderer.Face.SfMedium
private const val TARGET_TEXT_SIZE = 8.5f

private const val TARGET_BAR_WIDTH = 104.0f * MOCK_SCALE
private const val TARGET_BAR_HEIGHT = 4.5f * 0.65f
// border-radius: 200px in the mock, i.e. a pill however long the fill is. The
// track and both fills are drawn by the same rounded-rect shader so a fill that
// is only a few pixels long keeps the ends the track has.
private const val TARGET_BAR_RADIUS = TARGET_BAR_HEIGHT * 0.5f

// Sized and gapped exactly like the watermark's own metric icons rather than off
// the mock's 11.11x10: both sit beside an 8.5 text row, and drawing the glyph's
// margin-in-texture quad straight at this size (no compensation for the baked
// margin) is what the rest of the HUD's icons already do, so the heart reads the
// same size as everything else instead of standing out larger.
private const val TARGET_ICON_SIZE = 10.0f
private const val TARGET_ICON_GAP = 2.75f
private val TARGET_HEART_ICON = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/heartbeat2line.png")
private val TARGET_GLOW_TEXTURE = Identifier.fromNamespaceAndPath("asteria", "images/hud/target_glow.png")

// Two rows: the name with the health readout beside it, and the bar under them.
// They are centred in the card as one block rather than pinned to its padding,
// so the pair sits level with the middle of the head however the card is sized.
private const val TARGET_ROW_GAP = 6.0f * MOCK_SCALE
private const val TARGET_BLOCK_HEIGHT = TARGET_TEXT_SIZE + TARGET_ROW_GAP + TARGET_BAR_HEIGHT
private const val TARGET_NAME_Y = (TARGET_HEIGHT - TARGET_BLOCK_HEIGHT) * 0.5f
private const val TARGET_BAR_Y = TARGET_NAME_Y + TARGET_TEXT_SIZE + TARGET_ROW_GAP
private const val TARGET_ROW_CENTER_Y = TARGET_NAME_Y + TARGET_TEXT_SIZE * 0.5f

private const val TARGET_ITEM_NATIVE_SIZE = 16.0f
private const val TARGET_SLOT_SIZE = 12.0f
private const val TARGET_SLOT_GAP = 2.0f

private const val TARGET_BACKGROUND = 0xCC000000.toInt()
// box-shadow: 0 0 10px rgba(0, 0, 0, 0.83). Only drawn with the glass off,
// because with it on the card itself lives under the HUD's blur layer and a
// shadow drawn here would darken the glass instead of ringing it.
private const val TARGET_SHADOW_SIZE = 6.0f
private const val TARGET_SHADOW_OPACITY = 0.5f

private const val TARGET_ACCENT = HudStyle.THEME
private const val TARGET_HEALTH_FROM = 0xFF576E99.toInt()
private const val TARGET_HEALTH_TO = 0xFF91B7FF.toInt()
private const val TARGET_ABSORPTION_FROM = 0xFF997A00.toInt()
private const val TARGET_ABSORPTION_TO = 0xFFFFCC00.toInt()
private const val TARGET_BAR_BACKGROUND = 0x2191B7FF

class TargetInfoHudWidget : HudWidget(
    id = "target_info",
    title = "Target Info",
    iconGlyph = "P",
    x = 142.0f,
    y = 159.0f,
    width = TARGET_WIDTH,
    height = TARGET_TOTAL_HEIGHT,
    enabledByDefault = true,
) {
    private val showAnimation = AnimationUtil.TimedAnimation(0.0f)
    private val healthBarAnimation = TargetBarAnimation()
    private val absorptionBarAnimation = TargetBarAnimation()
    private val healthNumberAnimation = TargetNumberAnimation()
    private val headHitAnimation = TargetHeadHitAnimation()
    private val headParticles = ArrayList<TargetHeadParticle>()
    private val particleRandom = Random(0xA57E21A)
    private var retainedTarget: LivingEntity? = null
    private var lastTargetId = Int.MIN_VALUE
    private var lastVisible = false

    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        val hasSubject = ModuleManager.killaura.target != null || mc.screen is ChatScreen
        return (enabled && ModuleManager.interfaceModule.targetHud.value && hasSubject) || preview
    }

    override fun update(mc: Minecraft, preview: Boolean) {
        bounds.width = TARGET_WIDTH
        bounds.height = TARGET_TOTAL_HEIGHT
    }

    override fun blurBoxes(guiScale: Float, tintStrength: Float): List<AsteriaOverlay.BlurBox> {
        return listOf(
            AsteriaOverlay.BlurBox(
                bounds.x * guiScale,
                (bounds.y + TARGET_EQUIPMENT_HEIGHT + TARGET_EQUIPMENT_GAP) * guiScale,
                TARGET_WIDTH * guiScale,
                TARGET_HEIGHT * guiScale,
                TARGET_RADIUS * guiScale,
                0.65f,
            ),
        )
    }

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        // With nothing being attacked the card stands in for itself: in the HUD
        // editor and over the chat screen, which is where it gets moved from, it
        // shows the player's own head, name and health instead of an empty slot.
        val current = ModuleManager.killaura.target ?: if (preview || mc.screen is ChatScreen) mc.player else null
        val visible = current != null
        showAnimation.update()
        showAnimation.run(if (visible) 1.0f else 0.0f,
            if (visible) 180L else 240L,
            if (visible) AnimationUtil::easeOutQuad else AnimationUtil::easeOutSine,
            true,
        )
        if (visible) retainedTarget = current
        val target = current ?: retainedTarget
        if (retainedTarget == null || showAnimation.value <= 0.01f) {
            if (!visible) {
                retainedTarget = null
                healthBarAnimation.snap(0.0f)
                absorptionBarAnimation.snap(0.0f)
                healthNumberAnimation.reset()
                headHitAnimation.reset()
                headParticles.clear()
                lastVisible = false
                lastTargetId = Int.MIN_VALUE
            }
            return
        }

        val currentTargetId = current?.id ?: Int.MIN_VALUE
        val targetChanged = visible && (!lastVisible || currentTargetId != lastTargetId)
        val health = target?.health?.coerceAtLeast(0.0f) ?: 20.0f
        val maxHealth = target?.maxHealth?.coerceAtLeast(1.0f) ?: 20.0f
        val absorption = target?.absorptionAmount?.coerceAtLeast(0.0f) ?: 0.0f
        // Both shares are measured against whichever is larger, the target's own
        // maximum or what it is actually carrying. Measuring each against the
        // maximum on its own let the two add up past the track, which is what
        // forced them to be laid out as two separate bars that overlapped, ran
        // short of the end, or left bare track between them depending on the
        // numbers. Against a shared pool they always tile the track exactly.
        val pool = maxOf(maxHealth, health + absorption)
        val healthRatio = (health / pool).coerceIn(0.0f, 1.0f)
        val absorptionRatio = (absorption / pool).coerceIn(0.0f, 1.0f)

        if (targetChanged) {
            healthBarAnimation.snap(healthRatio)
            absorptionBarAnimation.snap(absorptionRatio)
            healthNumberAnimation.snap((health + absorption).roundToInt())
        } else {
            healthBarAnimation.run(healthRatio, if (healthRatio < healthBarAnimation.value) 240L else 300L)
            absorptionBarAnimation.run(absorptionRatio, if (absorptionRatio < absorptionBarAnimation.value) 220L else 280L)
            healthNumberAnimation.set((health + absorption).roundToInt())
        }
        val hitStarted = headHitAnimation.update(target, targetChanged)
        lastVisible = visible
        lastTargetId = currentTargetId

        val panelScale = 1.0f
        val x = bounds.x
        val y = bounds.y + TARGET_EQUIPMENT_HEIGHT + TARGET_EQUIPMENT_GAP

        // Item icons cannot be faded — graphics.item() has no alpha channel to
        // give them, the same gap CooldownsHudWidget's row icons have. Rather
        // than have the armour row snap to full opacity while the rest of the
        // card is still half-transparent, it is only drawn once the fade has
        // all but finished, so the snap lands close to where the card is
        // already effectively fully shown or hidden instead of standing out
        // through the whole animation.
        if (visibilityAlpha >= 0.97f) {
            drawTargetEquipment(graphics, mc, target, x, bounds.y)
        }

        if (!ModuleManager.postProcessing.enabled) {
            GuiShapeUtil.softShadow(
                graphics,
                x,
                y,
                x + TARGET_WIDTH,
                y + TARGET_HEIGHT,
                TARGET_RADIUS,
                TARGET_SHADOW_SIZE,
                TARGET_SHADOW_OPACITY * visibilityAlpha,
                1.0f,
            )
            HudStyle.rect(graphics, x, y, TARGET_WIDTH, TARGET_HEIGHT, TARGET_RADIUS, fadeColor(TARGET_BACKGROUND, visibilityAlpha))
        }

        val headX = x + TARGET_PADDING
        val headY = y + TARGET_PADDING
        drawTargetHead(
            graphics,
            target,
            headX,
            headY,
            TARGET_HEAD_SIZE,
            visibilityAlpha,
            headHitAnimation.intensity(),
        )
        updateHeadParticles(visible, hitStarted, headX + TARGET_HEAD_SIZE * 0.5f, headY + TARGET_HEAD_SIZE * 0.5f)
        drawHeadParticles(graphics)

        // The readout is measured first: it keeps its place at the end of the bar
        // and the name gets what is left, so a long name is cut rather than run
        // under the health it belongs to.
        val readoutWidth = drawHealthReadout(graphics, x, y, health + absorption)
        FontRenderer.draw(
            graphics,
            TARGET_FACE,
            fitName(target?.name?.string ?: "Target", TARGET_BAR_WIDTH - readoutWidth - TARGET_NAME_GAP),
            x + TARGET_CONTENT_X,
            y + TARGET_NAME_Y,
            TARGET_TEXT_SIZE,
            fadeColor(HudStyle.TEXT, visibilityAlpha),
        )

        drawBars(graphics, x + TARGET_CONTENT_X, y + TARGET_BAR_Y)
    }

    /**
     * The heartbeat glyph and the target's remaining health, hung off the right
     * end of the bar on the name's own row. Returns how wide the pair came out,
     * which is what the name is then fitted around.
     */
    private fun drawHealthReadout(graphics: GuiGraphicsExtractor, x: Float, y: Float, health: Float): Float {
        healthNumberAnimation.set(health.roundToInt())
        val textWidth = healthNumberAnimation.width(TARGET_FACE, TARGET_TEXT_SIZE)
        val width = TARGET_ICON_SIZE + TARGET_ICON_GAP + textWidth
        val right = x + TARGET_CONTENT_X + TARGET_BAR_WIDTH
        MsdfIconRenderer.draw(
            graphics,
            TARGET_HEART_ICON,
            right - width,
            y + TARGET_ROW_CENTER_Y - TARGET_ICON_SIZE * 0.5f,
            TARGET_ICON_SIZE,
            TARGET_ICON_SIZE,
            fadeColor(TARGET_ACCENT, visibilityAlpha),
            edge = MsdfIconRenderer.Edge.CrispRange6,
        )
        healthNumberAnimation.draw(
            graphics,
            right,
            y + TARGET_NAME_Y,
            TARGET_FACE,
            TARGET_TEXT_SIZE,
            visibilityAlpha,
        )
        return width
    }

    private fun updateHeadParticles(visible: Boolean, hitStarted: Boolean, centerX: Float, centerY: Float) {
        val now = System.currentTimeMillis()
        headParticles.removeAll { now - it.bornMillis >= it.lifeMillis }
        if (!visible) return

        // One complete wave per hit: every particle is born in the same frame,
        // with evenly distributed base angles so the burst reads as a single
        // expanding ring instead of a continuous particle emitter.
        if (hitStarted) {
            repeat(12) { index ->
                spawnHeadParticle(now, centerX, centerY, index, 12)
            }
        }
    }

    private fun spawnHeadParticle(now: Long, centerX: Float, centerY: Float, index: Int, count: Int) {
        val circle = Math.PI.toFloat() * 2.0f
        val baseAngle = circle * index.toFloat() / count.toFloat()
        val angle = baseAngle + (particleRandom.nextFloat() - 0.5f) * 0.34f
        val distance = particleRandom.nextFloat() * 8.0f + 16.0f
        val size = particleRandom.nextFloat() * 2.4f + 4.2f
        headParticles += TargetHeadParticle(
            centerX = centerX,
            centerY = centerY,
            velocityX = cos(angle) * distance,
            velocityY = sin(angle) * distance,
            size = size,
            bornMillis = now,
            lifeMillis = 520L,
        )
    }

    private fun drawHeadParticles(graphics: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()
        headParticles.forEach { particle ->
            val progress = ((now - particle.bornMillis).toFloat() / particle.lifeMillis).coerceIn(0.0f, 1.0f)
            val travel = AnimationUtil.easeOutQuad(progress)
            val fade = if (progress < 0.18f) progress / 0.18f else 1.0f - AnimationUtil.easeInSine((progress - 0.18f) / 0.82f)
            val scale = 0.55f + 0.45f * sin(progress * Math.PI).toFloat()
            val size = particle.size * scale
            val centerX = particle.centerX + particle.velocityX * travel
            val centerY = particle.centerY + particle.velocityY * travel
            val outerSize = size * 1.45f
            TextureRenderer.draw(
                graphics,
                TARGET_GLOW_TEXTURE,
                centerX - outerSize * 0.5f,
                centerY - outerSize * 0.5f,
                outerSize,
                outerSize,
                fadeColor(TARGET_ACCENT, fade * visibilityAlpha * 0.58f),
            )
            TextureRenderer.draw(
                graphics,
                TARGET_GLOW_TEXTURE,
                centerX - size * 0.5f,
                centerY - size * 0.5f,
                size,
                size,
                fadeColor(mixWithWhite(TARGET_ACCENT, 0.68f), fade * visibilityAlpha),
            )
        }
    }

    /**
     * The track, the whole of what the target is carrying drawn over it in gold,
     * and its health drawn over that in blue — so the gold is what shows past the
     * end of the blue rather than a bar of its own.
     *
     * Two bars butting end to end is the shape this used to draw, and at these
     * sizes it reads as broken however the numbers are arranged: each pill ends
     * in its own outward curve, so where they meet the two curves touch at a
     * single point and leave crescents of bare track above and below, and where
     * the pair stops short of the end the track's own rounded end sits a few
     * pixels past the fill's, which looks like the bar was cut off. Stacking them
     * instead leaves one pill: one rounded start, one rounded end, and a single
     * cap where the blue gives way to the gold.
     */
    private fun drawBars(graphics: GuiGraphicsExtractor, barX: Float, barY: Float) {
        GuiShapeUtil.roundedFill(
            graphics,
            barX,
            barY,
            barX + TARGET_BAR_WIDTH,
            barY + TARGET_BAR_HEIGHT,
            TARGET_BAR_RADIUS,
            fadeColor(TARGET_BAR_BACKGROUND, visibilityAlpha),
            sharp = true,
        )

        val healthWidth = TARGET_BAR_WIDTH * healthBarAnimation.value.coerceIn(0.0f, 1.0f)
        val absorptionWidth = TARGET_BAR_WIDTH * absorptionBarAnimation.value.coerceIn(0.0f, 1.0f)
        val carriedWidth = (healthWidth + absorptionWidth).coerceAtMost(TARGET_BAR_WIDTH)

        if (absorptionWidth > 0.01f) {
            GuiShapeUtil.roundedHorizontalGradientFill(
                graphics,
                barX,
                barY,
                barX + carriedWidth,
                barY + TARGET_BAR_HEIGHT,
                TARGET_BAR_RADIUS.coerceAtMost(carriedWidth * 0.5f),
                fadeColor(TARGET_ABSORPTION_FROM, visibilityAlpha),
                fadeColor(TARGET_ABSORPTION_TO, visibilityAlpha),
                sharp = true,
            )
        }

        if (healthWidth > 0.01f) {
            GuiShapeUtil.roundedHorizontalGradientFill(
                graphics,
                barX,
                barY,
                barX + healthWidth,
                barY + TARGET_BAR_HEIGHT,
                TARGET_BAR_RADIUS.coerceAtMost(healthWidth * 0.5f),
                fadeColor(TARGET_HEALTH_FROM, visibilityAlpha),
                fadeColor(TARGET_HEALTH_TO, visibilityAlpha),
                sharp = true,
            )
        }
    }

    private fun fitName(name: String, available: Float): String {
        if (FontRenderer.width(TARGET_FACE, name, TARGET_TEXT_SIZE) <= available) return name
        val suffix = "..."
        var end = name.length
        while (end > 0) {
            val candidate = name.substring(0, end) + suffix
            if (FontRenderer.width(TARGET_FACE, candidate, TARGET_TEXT_SIZE) <= available) {
                return candidate
            }
            end--
        }
        return suffix
    }
}

private fun drawTargetEquipment(
    graphics: GuiGraphicsExtractor,
    mc: Minecraft,
    target: LivingEntity?,
    x: Float,
    y: Float,
) {
    val armor = listOf(
        target?.getItemBySlot(EquipmentSlot.HEAD) ?: ItemStack.EMPTY,
        target?.getItemBySlot(EquipmentSlot.CHEST) ?: ItemStack.EMPTY,
        target?.getItemBySlot(EquipmentSlot.LEGS) ?: ItemStack.EMPTY,
        target?.getItemBySlot(EquipmentSlot.FEET) ?: ItemStack.EMPTY,
    ).filterNot { it.isEmpty }
    val mainHand = target?.mainHandItem ?: ItemStack.EMPTY
    val offHand = target?.offhandItem ?: ItemStack.EMPTY
    val hands = (if (target?.mainArm == HumanoidArm.LEFT) {
        listOf(mainHand, offHand)
    } else {
        listOf(offHand, mainHand)
    }).filterNot { it.isEmpty }

    armor.forEachIndexed { index, stack ->
        drawTargetEquipmentSlot(graphics, mc, stack, x + index * (TARGET_SLOT_SIZE + TARGET_SLOT_GAP), y)
    }
    val handsWidth = hands.size * TARGET_SLOT_SIZE + (hands.size - 1).coerceAtLeast(0) * TARGET_SLOT_GAP
    val handsX = x + TARGET_WIDTH - handsWidth
    hands.forEachIndexed { index, stack ->
        drawTargetEquipmentSlot(graphics, mc, stack, handsX + index * (TARGET_SLOT_SIZE + TARGET_SLOT_GAP), y)
    }
}

private fun drawTargetEquipmentSlot(
    graphics: GuiGraphicsExtractor,
    mc: Minecraft,
    stack: ItemStack,
    x: Float,
    y: Float,
) {
    if (stack.isEmpty) return
    val itemScale = TARGET_SLOT_SIZE / TARGET_ITEM_NATIVE_SIZE
    graphics.pose().pushMatrix()
    graphics.pose().translate(x, y)
    graphics.pose().scale(itemScale, itemScale)
    graphics.item(stack, 0, 0)
    graphics.itemDecorations(mc.font, stack, 0, 0)
    graphics.pose().popMatrix()
}

private fun drawTargetHead(
    graphics: GuiGraphicsExtractor,
    target: LivingEntity?,
    x: Float,
    y: Float,
    size: Float,
    alpha: Float,
    hitIntensity: Float,
) {
    val redMix = hitIntensity.coerceIn(0.0f, 1.0f) * 0.38f
    val coolChannel = (255.0f * (1.0f - redMix)).roundToInt().coerceIn(0, 255)
    val color = fadeColor(
        (0xFF shl 24) or (0xFF shl 16) or (coolChannel shl 8) or coolChannel,
        alpha,
    )
    when (target) {
        is AbstractClientPlayer -> PlayerHeadRenderer.draw(
            graphics,
            target,
            x,
            y,
            size,
            TARGET_HEAD_RADIUS,
            includeHatLayer = true,
            color = color,
        )
        else -> Unit
    }
}

private data class TargetHeadParticle(
    val centerX: Float,
    val centerY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val size: Float,
    val bornMillis: Long,
    val lifeMillis: Long,
)

private class TargetHeadHitAnimation {
    private var trackedTargetId = Int.MIN_VALUE
    private var lastHurtTime = 0
    private var pulseStartMillis = 0L

    fun update(target: LivingEntity?, targetChanged: Boolean): Boolean {
        if (target == null) return false
        if (targetChanged || target.id != trackedTargetId) {
            trackedTargetId = target.id
            lastHurtTime = target.hurtTime
            pulseStartMillis = 0L
            return false
        }
        val hitStarted = target.hurtTime > lastHurtTime
        if (hitStarted) pulseStartMillis = System.currentTimeMillis()
        lastHurtTime = target.hurtTime
        return hitStarted
    }

    fun intensity(): Float {
        if (pulseStartMillis == 0L) return 0.0f
        val progress = ((System.currentTimeMillis() - pulseStartMillis) / 360.0f).coerceIn(0.0f, 1.0f)
        if (progress >= 1.0f) {
            pulseStartMillis = 0L
            return 0.0f
        }
        return if (progress < 0.18f) {
            AnimationUtil.easeOutQuad(progress / 0.18f)
        } else {
            1.0f - AnimationUtil.easeOutSine((progress - 0.18f) / 0.82f)
        }
    }

    fun reset() {
        trackedTargetId = Int.MIN_VALUE
        lastHurtTime = 0
        pulseStartMillis = 0L
    }
}

private class TargetNumberAnimation {
    private var current = "20"
    private var outgoing: String? = null
    private var startMillis = 0L
    private val durationMillis = 220L

    fun set(next: Int) {
        val text = next.coerceAtLeast(0).toString()
        if (text == current) return
        outgoing = current
        current = text
        startMillis = System.currentTimeMillis()
    }

    fun snap(next: Int) {
        current = next.coerceAtLeast(0).toString()
        outgoing = null
        startMillis = 0L
    }

    fun reset() = snap(20)

    fun width(face: FontRenderer.Face, size: Float): Float {
        val currentWidth = FontRenderer.width(face, current, size)
        val outgoingWidth = outgoing?.let { FontRenderer.width(face, it, size) } ?: 0.0f
        return maxOf(currentWidth, outgoingWidth)
    }

    fun draw(
        graphics: GuiGraphicsExtractor,
        right: Float,
        baselineY: Float,
        face: FontRenderer.Face,
        size: Float,
        parentAlpha: Float,
    ) {
        val progress = if (startMillis == 0L) 1.0f else {
            ((System.currentTimeMillis() - startMillis).toFloat() / durationMillis).coerceIn(0.0f, 1.0f)
        }
        val eased = AnimationUtil.easeOutQuad(progress)
        val travel = size * 0.72f
        val clipTop = baselineY - 1.0f
        val clipHeight = size + 2.0f

        ScissorUtil.withScissor(graphics, right - width(face, size) - 1.0f, clipTop, width(face, size) + 2.0f, clipHeight) {
            outgoing?.let { old ->
                val oldWidth = FontRenderer.width(face, old, size)
                FontRenderer.draw(
                    graphics,
                    face,
                    old,
                    right - oldWidth,
                    baselineY + travel * eased,
                    size,
                    fadeColor(HudStyle.TEXT, parentAlpha * (1.0f - eased)),
                )
            }

            val currentWidth = FontRenderer.width(face, current, size)
            FontRenderer.draw(
                graphics,
                face,
                current,
                right - currentWidth,
                baselineY - travel * (1.0f - eased),
                size,
                fadeColor(HudStyle.TEXT, parentAlpha * eased),
            )
        }

        if (progress >= 1.0f) {
            outgoing = null
            startMillis = 0L
        }
    }
}

private fun fadeColor(color: Int, alphaMultiplier: Float): Int {
    val alpha = ((color ushr 24) * alphaMultiplier.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
    return (color and 0x00FFFFFF) or (alpha shl 24)
}

private fun mixWithWhite(color: Int, amount: Float): Int {
    val mix = amount.coerceIn(0.0f, 1.0f)
    val red = ((color shr 16) and 0xFF)
    val green = ((color shr 8) and 0xFF)
    val blue = (color and 0xFF)
    val brightRed = (red + (255 - red) * mix).roundToInt()
    val brightGreen = (green + (255 - green) * mix).roundToInt()
    val brightBlue = (blue + (255 - blue) * mix).roundToInt()
    return (0xFF shl 24) or (brightRed shl 16) or (brightGreen shl 8) or brightBlue
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
        val eased = AnimationUtil.easeOutQuad(elapsed)
        value = from + (target - from) * eased
    }
}
