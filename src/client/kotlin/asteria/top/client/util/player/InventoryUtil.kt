package asteria.top.client.util.player

import asteria.top.client.util.HotbarSlotUtil
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.ContainerInput

object InventoryUtil {
    fun noteServerSelectedSlot(slot: Int) {
        HotbarSlotUtil.noteServerSelectedSlot(slot)
    }

    fun lastSentSelectedSlot(): Int {
        return HotbarSlotUtil.lastSentSelectedSlot()
    }

    fun swapToSlot(slot: Int, updateClient: Boolean = true): Boolean {
        return HotbarSlotUtil.selectSlot(slot, updateClient)
    }

    fun swapToOffhand(screenSlot: Int): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        if (screenSlot < 0) return false

        val menu = player.containerMenu
        gameMode.handleContainerInput(menu.containerId, screenSlot, 40, ContainerInput.SWAP, player)
        player.connection.send(ServerboundContainerClosePacket(menu.containerId))
        return true
    }

    fun toScreenSlot(inventorySlot: Int): Int {
        if (inventorySlot !in 0..35) return -1
        return if (inventorySlot < 9) inventorySlot + 36 else inventorySlot
    }
}
