package asteria.top.client.mixin

import asteria.top.client.module.ModuleManager
import net.minecraft.client.player.LocalPlayer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(LocalPlayer::class)
abstract class RotationMovementMixin {
    @Unique
    private var glacierOriginalYaw = Float.NaN

    @Inject(method = ["aiStep"], at = [At("HEAD")])
    private fun glacierRotationMovementPre(ci: CallbackInfo) {
        val player = this as Any as LocalPlayer
        if (!ModuleManager.moveFix.activeForAura()) return

        glacierOriginalYaw = player.yRot
        player.yRot = ModuleManager.moveFix.beginMovementCorrection(player.yRot)
    }

    @Inject(method = ["aiStep"], at = [At("RETURN")])
    private fun glacierRotationMovementPost(ci: CallbackInfo) {
        val player = this as Any as LocalPlayer
        if (!glacierOriginalYaw.isNaN()) {
            player.yRot = glacierOriginalYaw
            glacierOriginalYaw = Float.NaN
            ModuleManager.moveFix.endMovementCorrection()
        }
    }
}
