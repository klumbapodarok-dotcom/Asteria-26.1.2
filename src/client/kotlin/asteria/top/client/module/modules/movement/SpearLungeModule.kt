package asteria.top.client.module.modules.movement

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.IntSetting
import asteria.top.client.util.HotbarSlotUtil
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments

class SpearLungeModule : Module(
    name = "SpearLunge",
    category = ModuleCategory.MOVEMENT,
    description = "Uses a Lunge spear and swaps back in the same tick when Silent Swap is enabled.",
    enabledByDefault = false,
) {
    enum class Mode(val label: String) {
        AUTO("Auto"),
        BIND("Bind"),
    }

    val mode = setting(EnumSetting("Mode", Mode.entries.toTypedArray(), Mode.AUTO) { it.label })
    private val silentSwap = setting(BooleanSetting("Silent Swap", false))
    private val packetCount = setting(IntSetting("Packets", 1, 0, 20, 1))
    private var pendingRestoreSlot = -1

    fun tick() {
        if (pendingRestoreSlot >= 0) {
            val slot = pendingRestoreSlot
            pendingRestoreSlot = -1
            restoreOriginalSlot(slot)
            if (mode.value == Mode.BIND) setEnabled(false)
            return
        }

        if (!enabled) return

        lungeOnce()
        if (mode.value == Mode.BIND && pendingRestoreSlot < 0) setEnabled(false)
    }

    override fun onBindPressed(): Boolean {
        if (mode.value == Mode.BIND && !enabled) {
            setEnabled(true)
            return true
        }

        return super.onBindPressed()
    }

    private fun lungeOnce(): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        if (player.isUsingItem) return false

        val inventory = player.inventory
        val spearSlot = (0..8).firstOrNull { slot ->
            val stack = inventory.getItem(slot)
            stack.`is`(ItemTags.SPEARS) && hasLunge(stack, level.registryAccess())
        } ?: return false

        val originalSlot = inventory.selectedSlot
        if (spearSlot != originalSlot) HotbarSlotUtil.selectSlot(spearSlot)

        var restoreNow = true
        return try {
            val attacked = performSpearLunge()
            if (!silentSwap.value && spearSlot != originalSlot && attacked) {
                pendingRestoreSlot = originalSlot
                restoreNow = false
            }
            attacked
        } finally {
            if (restoreNow) restoreOriginalSlot(originalSlot)
        }
    }

    private fun performSpearLunge(): Boolean {
        if (packetCount.value <= 0) return false

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val piercingWeapon = player.mainHandItem.get(DataComponents.PIERCING_WEAPON) ?: return false

        val gameMode = mc.gameMode ?: return false
        repeat(packetCount.value) {
            gameMode.piercingAttack(piercingWeapon)
        }

        player.swing(InteractionHand.MAIN_HAND)
        return true
    }

    private fun restoreOriginalSlot(slot: Int) {
        HotbarSlotUtil.selectSlot(slot)
    }

    private fun hasLunge(stack: ItemStack, registryAccess: net.minecraft.core.RegistryAccess): Boolean {
        val enchantments = registryAccess.lookupOrThrow(Registries.ENCHANTMENT)
        val lunge = enchantments.getOrThrow(Enchantments.LUNGE)
        return EnchantmentHelper.getItemEnchantmentLevel(lunge, stack) > 0
    }
}
