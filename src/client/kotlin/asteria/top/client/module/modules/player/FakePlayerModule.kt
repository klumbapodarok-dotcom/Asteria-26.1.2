package asteria.top.client.module.modules.player

import com.mojang.authlib.GameProfile
import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.MultiBooleanSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.CombatRules
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

class FakePlayerModule : Module(
    name = "Fake Player",
    category = ModuleCategory.PLAYER,
    description = "Spawns Oliwier Szczepanik who you can abuse",
    enabledByDefault = false,
) {
    companion object {
        private val FAKE_PLAYER_UUID: UUID = UUID.fromString("188-33-42-7-1337")
        private const val FAKE_NAME = "oliwierszczepanik"

        private var instance: FakePlayerModule? = null

        @JvmStatic
        fun handleLocalAttack(target: Entity): Boolean {
            val module = instance ?: return false
            if (!module.enabled) return false
            return module.handleLocalAttackInternal(target)
        }
    }

    enum class ArmorType(val label: String) {
        LEATHER("Leather"),
        CHAINMAIL("Chainmail"),
        IRON("Iron"),
        GOLDEN("Golden"),
        DIAMOND("Diamond"),
        NETHERITE("Netherite"),
    }

    private val copyInventory = setting(BooleanSetting("Copy Inventory", false))
    private val armor = setting(
        MultiBooleanSetting(
            "Armor",
            BooleanSetting("Helmet", true),
            BooleanSetting("Chestplate", true),
            BooleanSetting("Leggings", true),
            BooleanSetting("Boots", true),
        )
    )
    private val armorType = setting(EnumSetting("Armor Type", ArmorType.entries.toTypedArray(), ArmorType.NETHERITE) { it.label })

    private var fakePlayer: RemotePlayer? = null

    init {
        instance = this
    }

    override fun onEnable() {
        spawnFakePlayer()
    }

    override fun onDisable() {
        removeFakePlayer()
    }

    fun tick() {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null) {
            removeFakePlayer()
            return
        }
        val fake = fakePlayer ?: return
        if (fake.level() !== mc.level || fake.isRemoved) {
            fakePlayer = null
            spawnFakePlayer()
            return
        }
        if (copyInventory.value) {
            copyInventory(fake)
        }
    }

    private fun spawnFakePlayer() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        try {
            removeFakePlayer()

            val fake = RemotePlayer(level, GameProfile(FAKE_PLAYER_UUID, FAKE_NAME))
            fake.copyPosition(player)
            fake.setYRot(player.yRot)
            fake.setXRot(player.xRot)
            fake.setYHeadRot(player.yHeadRot)
            fake.setYBodyRot(player.yBodyRot)
            fake.health = 20.0f
            fake.absorptionAmount = 0.0f
            fake.customName = Component.literal(FAKE_NAME)
            fake.isCustomNameVisible = true
            if (copyInventory.value) {
                copyInventory(fake)
            }
            equipArmor(fake)

            level.addEntity(fake)
            fake.addEffect(MobEffectInstance(MobEffects.REGENERATION, 9999, 2))
            fake.addEffect(MobEffectInstance(MobEffects.ABSORPTION, 9999, 4))
            fake.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 9999, 1))
            fakePlayer = fake
        } catch (throwable: Throwable) {
            removeFakePlayer()
            throwable.printStackTrace()
        }
    }

    private fun removeFakePlayer() {
        val level = Minecraft.getInstance().level
        val fake = fakePlayer
        if (level != null && fake != null && fake.level() === level) {
            level.removeEntity(fake.id, Entity.RemovalReason.DISCARDED)
        }
        fakePlayer = null
    }

    private fun copyInventory(fake: RemotePlayer) {
        val player = Minecraft.getInstance().player ?: return
        fake.setItemInHand(InteractionHand.MAIN_HAND, player.mainHandItem.copy())
        fake.setItemInHand(InteractionHand.OFF_HAND, player.offhandItem.copy())
        val size = minOf(player.inventory.containerSize, fake.inventory.containerSize)
        for (slot in 0 until size) {
            fake.inventory.setItem(slot, player.inventory.getItem(slot).copy())
        }
        fake.inventory.selectedSlot = player.inventory.selectedSlot
    }

    private fun handleLocalAttackInternal(target: Entity): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        val fake = fakePlayer ?: return false
        if (target !== fake || fake.level() !== level || fake.isRemoved) return false
        if (fake.hurtTime > 0) return true

        var baseDamage = weaponDamage(player.mainHandItem)
        val cooldown = player.getAttackStrengthScale(0.5f)
        baseDamage *= 0.2f + cooldown * cooldown * 0.8f

        val critical = player.fallDistance > 0.0 &&
            !player.onGround() &&
            !player.onClimbable() &&
            !player.isInWater &&
            !player.hasEffect(MobEffects.BLINDNESS) &&
            !player.isPassenger &&
            cooldown > 0.9f
        if (critical) baseDamage *= 1.5f

        val source = level.damageSources().playerAttack(player)
        val damage = CombatRules.getDamageAfterAbsorb(fake, baseDamage, source, armorValue(), armorToughness())
        level.playSound(player, fake.x, fake.y, fake.z, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 1.0f)

        if (critical) {
            level.playSound(player, fake.x, fake.y, fake.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f)
            ClientboundEntityEventPacket(fake, 36.toByte()).handle(player.connection)
        } else if (cooldown > 0.9f) {
            level.playSound(player, fake.x, fake.y, fake.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 1.0f)
        } else {
            level.playSound(player, fake.x, fake.y, fake.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.0f)
        }

        fake.handleEntityEvent(2.toByte())
        fake.hurtTime = 10
        fake.hurtDuration = 10
        val nextHealth = fake.health - damage
        if (nextHealth <= 0.0f) {
            fake.health = 20.0f
            fake.absorptionAmount = 0.0f
            ClientboundEntityEventPacket(fake, 35.toByte()).handle(player.connection)
        } else {
            fake.health = nextHealth
        }
        return true
    }

    private fun equipArmor(fake: RemotePlayer) {
        fake.setItemSlot(EquipmentSlot.HEAD, armorStack("Helmet", "helmet"))
        fake.setItemSlot(EquipmentSlot.CHEST, armorStack("Chestplate", "chestplate"))
        fake.setItemSlot(EquipmentSlot.LEGS, armorStack("Leggings", "leggings"))
        fake.setItemSlot(EquipmentSlot.FEET, armorStack("Boots", "boots"))
    }

    private fun armorStack(settingName: String, piece: String): ItemStack {
        return if (armor.enabled(settingName)) ItemStack(armorItem(armorType.value, piece)) else ItemStack.EMPTY
    }

    private fun armorItem(type: ArmorType, piece: String): Item {
        return when (type) {
            ArmorType.LEATHER -> when (piece) {
                "helmet" -> Items.LEATHER_HELMET
                "chestplate" -> Items.LEATHER_CHESTPLATE
                "leggings" -> Items.LEATHER_LEGGINGS
                else -> Items.LEATHER_BOOTS
            }
            ArmorType.CHAINMAIL -> when (piece) {
                "helmet" -> Items.CHAINMAIL_HELMET
                "chestplate" -> Items.CHAINMAIL_CHESTPLATE
                "leggings" -> Items.CHAINMAIL_LEGGINGS
                else -> Items.CHAINMAIL_BOOTS
            }
            ArmorType.IRON -> when (piece) {
                "helmet" -> Items.IRON_HELMET
                "chestplate" -> Items.IRON_CHESTPLATE
                "leggings" -> Items.IRON_LEGGINGS
                else -> Items.IRON_BOOTS
            }
            ArmorType.GOLDEN -> when (piece) {
                "helmet" -> Items.GOLDEN_HELMET
                "chestplate" -> Items.GOLDEN_CHESTPLATE
                "leggings" -> Items.GOLDEN_LEGGINGS
                else -> Items.GOLDEN_BOOTS
            }
            ArmorType.DIAMOND -> when (piece) {
                "helmet" -> Items.DIAMOND_HELMET
                "chestplate" -> Items.DIAMOND_CHESTPLATE
                "leggings" -> Items.DIAMOND_LEGGINGS
                else -> Items.DIAMOND_BOOTS
            }
            ArmorType.NETHERITE -> when (piece) {
                "helmet" -> Items.NETHERITE_HELMET
                "chestplate" -> Items.NETHERITE_CHESTPLATE
                "leggings" -> Items.NETHERITE_LEGGINGS
                else -> Items.NETHERITE_BOOTS
            }
        }
    }

    private fun armorValue(): Float {
        return selectedArmorStats().sumOf { it.first.toDouble() }.toFloat()
    }

    private fun armorToughness(): Float {
        return selectedArmorStats().sumOf { it.second.toDouble() }.toFloat()
    }

    private fun selectedArmorStats(): List<Pair<Float, Float>> {
        val material = armorType.value
        return buildList {
            if (armor.enabled("Helmet")) add(armorStats(material, "helmet"))
            if (armor.enabled("Chestplate")) add(armorStats(material, "chestplate"))
            if (armor.enabled("Leggings")) add(armorStats(material, "leggings"))
            if (armor.enabled("Boots")) add(armorStats(material, "boots"))
        }
    }

    private fun armorStats(type: ArmorType, piece: String): Pair<Float, Float> {
        val armorPoints = when (type) {
            ArmorType.LEATHER -> when (piece) {
                "helmet" -> 1.0f
                "chestplate" -> 3.0f
                "leggings" -> 2.0f
                else -> 1.0f
            }
            ArmorType.GOLDEN -> when (piece) {
                "helmet" -> 2.0f
                "chestplate" -> 5.0f
                "leggings" -> 3.0f
                else -> 1.0f
            }
            ArmorType.CHAINMAIL, ArmorType.IRON -> when (piece) {
                "helmet" -> 2.0f
                "chestplate" -> 6.0f
                "leggings" -> 5.0f
                else -> 2.0f
            }
            ArmorType.DIAMOND, ArmorType.NETHERITE -> when (piece) {
                "helmet" -> 3.0f
                "chestplate" -> 8.0f
                "leggings" -> 6.0f
                else -> 3.0f
            }
        }
        val toughness = when (type) {
            ArmorType.DIAMOND -> 2.0f
            ArmorType.NETHERITE -> 3.0f
            else -> 0.0f
        }
        return armorPoints to toughness
    }

    private fun weaponDamage(stack: ItemStack): Float {
        return when (stack.item) {
            Items.WOODEN_SWORD, Items.GOLDEN_SWORD -> 4.0f
            Items.STONE_SWORD -> 5.0f
            Items.IRON_SWORD -> 6.0f
            Items.DIAMOND_SWORD -> 7.0f
            Items.NETHERITE_SWORD -> 8.0f
            Items.WOODEN_AXE, Items.GOLDEN_AXE -> 7.0f
            Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE -> 9.0f
            Items.NETHERITE_AXE -> 10.0f
            Items.MACE -> 6.0f
            Items.TRIDENT -> 9.0f
            Items.IRON_SPEAR -> 6.0f
            Items.GOLDEN_SPEAR -> 4.0f
            Items.DIAMOND_SPEAR -> 7.0f
            Items.NETHERITE_SPEAR -> 8.0f
            else -> 1.0f
        }
    }
}
