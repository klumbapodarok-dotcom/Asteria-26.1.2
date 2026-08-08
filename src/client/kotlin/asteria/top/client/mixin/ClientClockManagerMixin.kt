package asteria.top.client.mixin

import asteria.top.client.module.ModuleManager
import net.minecraft.client.ClientClockManager
import net.minecraft.core.Holder
import net.minecraft.world.clock.WorldClock
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ClientClockManager::class)
class ClientClockManagerMixin {
    @Inject(method = ["getTotalTicks"], at = [At("RETURN")], cancellable = true)
    private fun onGetTotalTicks(clock: Holder<WorldClock>, cir: CallbackInfoReturnable<Long>) {
        val forced = ModuleManager.ambience.forcedTimeTicks(clock) ?: return
        cir.returnValue = forced
    }
}
