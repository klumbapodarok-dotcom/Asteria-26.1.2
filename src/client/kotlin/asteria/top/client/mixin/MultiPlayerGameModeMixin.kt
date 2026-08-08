package asteria.top.client.mixin

import asteria.top.client.module.ModuleManager
import asteria.top.client.module.modules.player.FakePlayerModule
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(MultiPlayerGameMode::class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = ["attack"], at = [At("HEAD")], cancellable = true)
    private fun asteriaFakePlayerAttack(player: Player, target: Entity, ci: CallbackInfo) {
        if (FakePlayerModule.handleLocalAttack(target)) {
            player.resetAttackStrengthTicker()
            ci.cancel()
        }
    }

    @Inject(method = ["attack"], at = [At("RETURN")])
    private fun asteriaSpawnAttackParticles(player: Player, target: Entity, ci: CallbackInfo) {
        ModuleManager.particles.onAttack(target)
    }
}
