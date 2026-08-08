package asteria.top.client.mixin

import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemCooldowns
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

// CooldownInstance (the map's value type) is package-private in net.minecraft.world.item,
// so it can't be named here — the map is exposed as Map<Identifier, Any> and each value is
// cast to CooldownInstanceAccessor at the call site instead.
@Mixin(ItemCooldowns::class)
interface ItemCooldownsAccessor {
    @Accessor("cooldowns")
    fun getCooldownsMap(): Map<Identifier, Any>

    @Accessor("tickCount")
    fun getTickCount(): Int
}
