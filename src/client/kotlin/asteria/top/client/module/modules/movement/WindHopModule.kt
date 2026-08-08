package asteria.top.client.module.modules.movement

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.util.HotbarSlotUtil
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items

class WindHopModule : Module(
    name = "WindHop",
    category = ModuleCategory.MOVEMENT,
    description = "Looks straight down, uses a wind charge, then restores your view and slot.",
    enabledByDefault = false,
) {
    enum class Mode(val label: String) {
        AUTO("Auto"),
        BIND("Bind"),
    }

    val mode = setting(EnumSetting("Mode", Mode.entries.toTypedArray(), Mode.AUTO) { it.label })
    private val autoJump = setting(BooleanSetting("Auto Jump", false))
    private val silentSwap = setting(BooleanSetting("Silent Swap", false))
    private var pendingRestoreSlot = -1
    private var forcedPacketYaw: Float? = null
    private var forcedPacketPitch: Float? = null

    fun tick() {
        clearPacketRotation()

        if (pendingRestoreSlot >= 0) {
            val slot = pendingRestoreSlot
            pendingRestoreSlot = -1
            restoreOriginalSlot(slot)
            if (mode.value == Mode.BIND) setEnabled(false)
            return
        }

        if (!enabled) return

        windHopOnce()
        if (mode.value == Mode.BIND && pendingRestoreSlot < 0) setEnabled(false)
    }

    override fun onBindPressed(): Boolean {
        if (mode.value == Mode.BIND && !enabled) {
            setEnabled(true)
            return true
        }

        return super.onBindPressed()
    }

    fun packetYaw(original: Float): Float {
        return forcedPacketYaw?.let { Mth.wrapDegrees(it) } ?: original
    }

    fun packetPitch(original: Float): Float {
        return forcedPacketPitch ?: original
    }

    fun hasPacketRotation(): Boolean {
        return forcedPacketYaw != null || forcedPacketPitch != null
    }

    private fun windHopOnce(): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (player.isUsingItem) return false
        if (mode.value == Mode.AUTO && !player.onGround()) return false

        val inventory = player.inventory
        val windChargeSlot = (0..8).firstOrNull { slot ->
            inventory.getItem(slot).`is`(Items.WIND_CHARGE)
        } ?: return false

        val originalSlot = inventory.selectedSlot
        if (windChargeSlot != originalSlot) HotbarSlotUtil.selectSlot(windChargeSlot)

        var restoreNow = true
        return try {
            val used = performWindHop()
            if (!silentSwap.value && windChargeSlot != originalSlot && used) {
                pendingRestoreSlot = originalSlot
                restoreNow = false
            }
            used
        } finally {
            if (restoreNow) restoreOriginalSlot(originalSlot)
        }
    }

    private fun performWindHop(): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val originalPitch = player.xRot
        val originalYaw = player.yRot

        return try {
            val windHopYaw = ModuleManager.moveFix.correctedYaw(player.yRot)
            forcedPacketYaw = windHopYaw
            forcedPacketPitch = 90.0f
            player.yRot = windHopYaw
            player.xRot = 90.0f
            val result = mc.gameMode?.useItem(player, InteractionHand.MAIN_HAND) ?: return false
            if (!result.consumesAction()) return false

            if (autoJump.value && player.onGround()) player.jumpFromGround()
            player.swing(InteractionHand.MAIN_HAND)
            true
        } finally {
            player.yRot = originalYaw
            player.xRot = originalPitch
        }
    }

    private fun restoreOriginalSlot(slot: Int) {
        HotbarSlotUtil.selectSlot(slot)
    }

    private fun clearPacketRotation() {
        forcedPacketYaw = null
        forcedPacketPitch = null
    }
}
