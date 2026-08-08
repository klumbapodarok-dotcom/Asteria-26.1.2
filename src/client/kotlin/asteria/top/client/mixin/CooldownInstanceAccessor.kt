package asteria.top.client.mixin

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

// Targets net.minecraft.world.item.ItemCooldowns$CooldownInstance by string, since that
// record is package-private and can't be named directly from this package. Mixin still
// weaves this interface onto every instance, so any value pulled out of
// ItemCooldownsAccessor#getCooldownsMap() can be cast to it at the call site.
@Mixin(targets = ["net.minecraft.world.item.ItemCooldowns\$CooldownInstance"])
interface CooldownInstanceAccessor {
    @Accessor("startTime")
    fun getStartTime(): Int

    @Accessor("endTime")
    fun getEndTime(): Int
}
