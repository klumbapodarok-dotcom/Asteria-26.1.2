package asteria.top.client.module.modules.player

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.network.protocol.Packet
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FakeLagModule : Module(
    name = "Fake Lag",
    category = ModuleCategory.PLAYER,
    description = "Queues outgoing packets until the normal release timer expires.",
    enabledByDefault = false,
) {
    enum class Mode(val label: String) {
        NORMAL("Normal"),
    }

    private companion object {
        private const val GHOST_BASE_COLOR = 0xFF88FF82.toInt()
        private const val GHOST_FILL_COLOR = 0x4088FF82
        private const val GHOST_BLOOM_COLOR = 0x2088FF82
        private const val GHOST_STROKE_WIDTH = 2.25f
        private const val DEFAULT_TRANSITION_MS = 280L
        private const val MIN_TRANSITION_MS = 50L
        private const val MAX_TRANSITION_MS = 600L
        private const val TRANSITION_DELAY_RATIO = 0.6
    }

    val mode = setting(EnumSetting("Mode", Mode.entries.toTypedArray(), Mode.NORMAL) { it.label })
    private val resetTime = setting(FloatSetting("Reset Time", 100.0f, 0.0f, 2500.0f, 10.0f, "ms"))
    private val releaseOnInteract = setting(BooleanSetting("Release on interact", true))
    private val render = setting(BooleanSetting("Render", true))
    private val fillAlpha = setting(FloatSetting("Fill alpha", 140.0f, 0.0f, 255.0f, 1.0f))

    private val queuedPackets = ArrayDeque<Packet<*>>()
    private var queuedAtMs = 0L
    private var flushing = false
    private var lastEnabled = false

    private var lastPos = Vec3.ZERO
    private var frozenYaw = 0.0f
    private var frozenPitch = 0.0f
    private var renderFromPos = Vec3.ZERO
    private var renderToPos = Vec3.ZERO
    private var renderFromYaw = 0.0f
    private var renderToYaw = 0.0f
    private var renderFromPitch = 0.0f
    private var renderToPitch = 0.0f
    private var renderSwitchAt = 0L
    private var renderTransitionInitialized = false
    private var lastFakeLagDelayMs = 0L

    fun tick() {
        val mc = Minecraft.getInstance()
        if (!enabled || mc.player == null || mc.level == null) {
            lastEnabled = false
            if (queuedPackets.isNotEmpty()) {
                flushQueuedPackets()
            }
            clearRenderTransition()
            return
        }

        val player = mc.player ?: run {
            lastEnabled = false
            queuedPackets.clear()
            queuedAtMs = 0L
            clearRenderTransition()
            return
        }

        if (!lastEnabled) {
            captureRenderAnchor(player, true)
            lastEnabled = true
        }

        if (queuedPackets.isNotEmpty() && System.currentTimeMillis() - queuedAtMs >= resetTime.value.toLong()) {
            flushQueuedPackets()
        }
    }

    fun interceptOutgoingPacket(packet: Packet<*>): Boolean {
        val mc = Minecraft.getInstance()
        if (!enabled || mc.player == null || mc.level == null) {
            return false
        }

        mc.player?.let { captureRenderAnchor(it, false) }

        if (mode.value == Mode.NORMAL && releaseOnInteract.value && isReleaseOnInteractPacket(packet)) {
            flushQueuedPackets()
            return false
        }

        if (flushing) {
            return false
        }

        if (queuedPackets.isEmpty()) {
            queuedAtMs = System.currentTimeMillis()
        }
        queuedPackets.add(packet)
        return true
    }

    fun flushQueuedPackets() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: run {
            queuedPackets.clear()
            queuedAtMs = 0L
            clearRenderTransition()
            return
        }

        if (queuedPackets.isEmpty()) {
            queuedAtMs = 0L
            return
        }

        lastFakeLagDelayMs = max(0L, System.currentTimeMillis() - queuedAtMs)
        flushing = true
        try {
            while (queuedPackets.isNotEmpty()) {
                player.connection.send(queuedPackets.removeFirst())
            }
        } finally {
            flushing = false
            queuedAtMs = 0L
        }

        updateReleasedRenderState(player)
    }

    fun flushNow() {
        flushQueuedPackets()
    }

    fun applyRenderState(entity: LivingEntity, state: LivingEntityRenderState) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!enabled || !render.value || entity !== player) {
            return
        }
        captureRenderAnchor(player, false)
    }

    fun renderGizmos() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!enabled || !render.value || mc.level == null) {
            return
        }

        captureRenderAnchor(player, false)
        val box = computeGhostBox(player) ?: return
        val alphaFactor = computeAnimatedAlphaFactor()
        mc.levelRenderer.collectPerFrameGizmos().use {
            renderGhostBox(box, alphaFactor)
        }
    }

    private fun captureRenderAnchor(player: LivingEntity, force: Boolean) {
        if (!force && renderTransitionInitialized) {
            return
        }

        val newPos = player.position()
        val newYaw = player.yRot
        val newPitch = player.xRot
        lastPos = newPos
        frozenYaw = newYaw
        frozenPitch = newPitch
        startTransition(newPos, newYaw, newPitch, true)
    }

    private fun updateReleasedRenderState(player: LivingEntity) {
        val newPos = player.position()
        val newYaw = player.yRot
        val newPitch = player.xRot
        lastPos = newPos
        frozenYaw = newYaw
        frozenPitch = newPitch
        startTransition(newPos, newYaw, newPitch, false)
    }

    private fun clearRenderTransition() {
        renderTransitionInitialized = false
        renderFromPos = Vec3.ZERO
        renderToPos = Vec3.ZERO
        renderFromYaw = 0.0f
        renderToYaw = 0.0f
        renderFromPitch = 0.0f
        renderToPitch = 0.0f
        renderSwitchAt = 0L
    }

    private fun startTransition(newPos: Vec3, newYaw: Float, newPitch: Float, force: Boolean) {
        if (force || !renderTransitionInitialized) {
            renderFromPos = newPos
            renderToPos = newPos
            renderFromYaw = newYaw
            renderToYaw = newYaw
            renderFromPitch = newPitch
            renderToPitch = newPitch
            renderSwitchAt = System.currentTimeMillis()
            renderTransitionInitialized = true
            return
        }

        renderFromPos = getInterpolatedPos()
        renderFromYaw = getInterpolatedYaw()
        renderFromPitch = getInterpolatedPitch()
        renderToPos = newPos
        renderToYaw = newYaw
        renderToPitch = newPitch
        renderSwitchAt = System.currentTimeMillis()
    }

    private fun getInterpolatedPos(): Vec3 {
        if (!renderTransitionInitialized) {
            return lastPos
        }

        val t = getTransitionProgress()
        return Vec3(
            lerp(renderFromPos.x, renderToPos.x, t),
            lerp(renderFromPos.y, renderToPos.y, t),
            lerp(renderFromPos.z, renderToPos.z, t),
        )
    }

    private fun getInterpolatedYaw(): Float {
        if (!renderTransitionInitialized) {
            return frozenYaw
        }

        val t = getTransitionProgress()
        return renderFromYaw + wrapDegrees(renderToYaw - renderFromYaw) * t
    }

    private fun getInterpolatedPitch(): Float {
        if (!renderTransitionInitialized) {
            return frozenPitch
        }

        val t = getTransitionProgress()
        return renderFromPitch + (renderToPitch - renderFromPitch) * t
    }

    private fun getTransitionProgress(): Float {
        val elapsed = System.currentTimeMillis() - renderSwitchAt
        val duration = getAnimationDurationMs().coerceAtLeast(1L)
        val progress = min(1.0f, elapsed / duration.toFloat())
        return easeInOutCubic(progress)
    }

    private fun getAnimationDurationMs(): Long {
        val delay = max(0L, lastFakeLagDelayMs)
        if (delay <= 0L) {
            return DEFAULT_TRANSITION_MS
        }

        val scaled = max(0L, (delay * TRANSITION_DELAY_RATIO).toLong())
        return max(MIN_TRANSITION_MS, min(MAX_TRANSITION_MS, scaled))
    }

    private fun computeAnimatedAlphaFactor(): Float {
        if (!renderTransitionInitialized) {
            return 1.0f
        }

        val factor = 1.0f - 0.15f * getTransitionWave()
        return factor.coerceIn(0.15f, 1.0f)
    }

    private fun getTransitionWave(): Float {
        val progress = min(1.0f, (System.currentTimeMillis() - renderSwitchAt) / getAnimationDurationMs().toFloat())
        return 1.0f - abs(progress * 2.0f - 1.0f)
    }

    private fun computeGhostBox(player: LivingEntity): AABB? {
        val renderPos = getInterpolatedPos()
        val offset = renderPos.subtract(player.position())
        val box = player.boundingBox.move(offset)
        return if (box.hasNaN()) null else box
    }

    override fun onDisable() {
        if (queuedPackets.isNotEmpty()) {
            flushQueuedPackets()
        }
        clearRenderTransition()
    }

    private fun renderGhostBox(box: AABB, alphaFactor: Float) {
        val fill = withAlpha(GHOST_FILL_COLOR, (fillAlpha.value * alphaFactor).roundToInt())
        val stroke = withAlpha(GHOST_BASE_COLOR, (255.0f * alphaFactor).roundToInt())
        val style = GizmoStyle.strokeAndFill(stroke, GHOST_STROKE_WIDTH, fill)
        Gizmos.cuboid(box, style, false).setAlwaysOnTop()
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    private fun lerp(a: Double, b: Double, t: Float): Double {
        return a + (b - a) * t
    }

    private fun wrapDegrees(value: Float): Float {
        var wrapped = value % 360.0f
        if (wrapped >= 180.0f) wrapped -= 360.0f
        if (wrapped < -180.0f) wrapped += 360.0f
        return wrapped
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4.0f * t * t * t
        } else {
            val k = -2.0f * t + 2.0f
            1.0f - (k * k * k) / 2.0f
        }
    }

    private fun isReleaseOnInteractPacket(packet: Packet<*>): Boolean {
        val name = packet.javaClass.simpleName
        return name == "PlayerInteractEntityC2SPacket" ||
            name == "ServerboundInteractPacket" ||
            name == "UpdateSelectedSlotC2SPacket" ||
            name == "ServerboundSetCarriedItemPacket" ||
            name == "HandSwingC2SPacket" ||
            name == "ServerboundSwingPacket" ||
            name == "PlayerInteractBlockC2SPacket" ||
            name == "ServerboundUseItemOnPacket" ||
            name == "PlayerInteractItemC2SPacket" ||
            name == "ServerboundUseItemPacket" ||
            name == "ClickSlotC2SPacket" ||
            name == "ServerboundContainerClickPacket" ||
            name == "PlayerActionC2SPacket" ||
            name == "ServerboundPlayerActionPacket"
    }
}
