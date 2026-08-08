package asteria.top.client.mixin

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import asteria.top.client.module.ModuleManager
import asteria.top.client.util.combat.CombatRotationManager
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At

@Mixin(LocalPlayer::class)
abstract class LocalPlayerMixin {
    @Unique
    private var glacierLastPacketYaw = Float.NaN

    @ModifyExpressionValue(
        method = ["sendPosition", "tick"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F")],
    )
    private fun glacierAuraPacketYaw(original: Float): Float {
        val aura = ModuleManager.killaura
        val windHopYaw = ModuleManager.windHop.packetYaw(ModuleManager.killaura.packetYaw(original))
        val desiredYaw = ModuleManager.autoTrap.packetYaw(windHopYaw)
        val packetRotationActive = aura.hasPacketRotation() || ModuleManager.windHop.hasPacketRotation() || ModuleManager.autoTrap.hasPacketRotation()
        if (!packetRotationActive) {
            glacierLastPacketYaw = original
            return original
        }

        val baseYaw = if (glacierLastPacketYaw.isNaN()) original else glacierLastPacketYaw
        val yaw = baseYaw + Mth.wrapDegrees(desiredYaw - baseYaw)
        glacierLastPacketYaw = yaw
        CombatRotationManager.applyRotationView(yaw)
        return yaw
    }

    @ModifyExpressionValue(
        method = ["sendPosition", "tick"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F")],
    )
    private fun glacierAuraPacketPitch(original: Float): Float {
        val windHopPitch = ModuleManager.windHop.packetPitch(ModuleManager.killaura.packetPitch(original))
        return ModuleManager.autoTrap.packetPitch(windHopPitch)
    }
}
