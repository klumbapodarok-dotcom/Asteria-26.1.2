package asteria.top.client.gui.hud

import asteria.top.client.render.FontRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

class ArmorHudWidget : HudWidget(
    id = "armor",
    title = "Armor",
    iconGlyph = "K",
    x = Float.NaN,
    y = Float.NaN,
    width = 108.0f,
    height = 56.0f,
    enabledByDefault = false,
) {
    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        return (enabled && armorItems(mc).any { !it.isEmpty }) || preview
    }

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val armor = armorItems(mc)
        HudStyle.panel(graphics, bounds)
        armor.forEachIndexed { index, stack ->
            val slotX = bounds.x + 7.0f + index * 25.0f
            val slotY = bounds.y + 12.0f
            if (!stack.isEmpty) {
                graphics.item(stack, slotX.toInt(), slotY.toInt())
                graphics.itemDecorations(mc.font, stack, slotX.toInt(), slotY.toInt())
                val durabilityText = durabilityPercent(stack)
                if (durabilityText != null) {
                    FontRenderer.drawCentered(
                        graphics,
                        FontRenderer.Face.SfSemibold,
                        durabilityText,
                        slotX + 8.0f,
                        bounds.y + 34.0f,
                        7.0f,
                        HudStyle.TEXT,
                    )
                }
            }
        }
    }

    private fun durabilityPercent(stack: ItemStack): String? {
        if (!stack.isDamageableItem) return null
        val maxDamage = stack.maxDamage.takeIf { it > 0 } ?: return null
        val remaining = (maxDamage - stack.damageValue).coerceIn(0, maxDamage)
        return "${((remaining.toFloat() / maxDamage.toFloat()) * 100.0f).roundToInt()}%"
    }

    private fun armorItems(mc: Minecraft): List<ItemStack> {
        val player = mc.player ?: return List(4) { ItemStack.EMPTY }
        return listOf(
            player.getItemBySlot(EquipmentSlot.HEAD),
            player.getItemBySlot(EquipmentSlot.CHEST),
            player.getItemBySlot(EquipmentSlot.LEGS),
            player.getItemBySlot(EquipmentSlot.FEET),
        )
    }
}
