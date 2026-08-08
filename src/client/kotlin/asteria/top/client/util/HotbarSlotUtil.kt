package asteria.top.client.util

import asteria.top.client.mixin.MultiPlayerGameModeAccessor
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket

object HotbarSlotUtil {
    private var lastSentSelectedSlot = -1

    fun lastSentSelectedSlot(): Int {
        return lastSentSelectedSlot
    }

    fun noteServerSelectedSlot(slot: Int) {
        if (slot in 0..8) lastSentSelectedSlot = slot
    }

    fun selectSlot(slot: Int, updateClient: Boolean = true): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        if (slot !in 0..8) return false

        val currentSlot = player.inventory.selectedSlot
        if (currentSlot == slot) {
            if (lastSentSelectedSlot == -1) lastSentSelectedSlot = slot
            syncVanillaCarriedIndex(slot)
            return false
        }

        if (lastSentSelectedSlot == slot) {
            if (updateClient) player.inventory.selectedSlot = slot
            syncVanillaCarriedIndex(slot)
            return false
        }

        player.connection.send(ServerboundSetCarriedItemPacket(slot))
        lastSentSelectedSlot = slot
        if (updateClient) player.inventory.selectedSlot = slot
        syncVanillaCarriedIndex(slot)
        return true
    }

    private fun syncVanillaCarriedIndex(slot: Int) {
        (Minecraft.getInstance().gameMode as? MultiPlayerGameModeAccessor)?.setCarriedIndex(slot)
    }
}
