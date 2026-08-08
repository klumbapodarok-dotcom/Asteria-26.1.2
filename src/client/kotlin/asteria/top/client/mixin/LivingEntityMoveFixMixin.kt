package asteria.top.client.mixin

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import asteria.top.client.module.ModuleManager
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(LivingEntity::class)
abstract class LivingEntityMoveFixMixin {
    @ModifyExpressionValue(
        method = ["jumpFromGround"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")],
    )
    private fun glacierFixJumpYaw(original: Float): Float {
        if (!localPlayerWithAuraMoveFix()) return original
        return ModuleManager.moveFix.correctedYaw(original)
    }

    @ModifyExpressionValue(
        method = ["updateFallFlyingMovement"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F")],
    )
    private fun glacierFixFallFlyingPitch(original: Float): Float {
        if (!localPlayerWithAuraMoveFix()) return original
        return ModuleManager.killaura.packetPitch(original)
    }

    @ModifyExpressionValue(
        method = ["updateFallFlyingMovement"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;")],
    )
    private fun glacierFixFallFlyingLookVector(original: Vec3): Vec3 {
        val player = Minecraft.getInstance().player ?: return original
        if ((this as Any) !== player || !ModuleManager.moveFix.activeForAura()) return original
        return Vec3.directionFromRotation(
            ModuleManager.killaura.packetPitch(player.xRot),
            ModuleManager.moveFix.correctedYaw(player.yRot),
        )
    }

    private fun localPlayerWithAuraMoveFix(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return (this as Any) === player && ModuleManager.moveFix.activeForAura()
    }
}
