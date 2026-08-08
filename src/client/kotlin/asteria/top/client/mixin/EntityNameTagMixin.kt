package asteria.top.client.mixin

import asteria.top.client.module.ModuleManager
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.player.AvatarRenderer
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

// shouldShowName is overridden at every level of the renderer hierarchy (EntityRenderer ->
// LivingEntityRenderer -> AvatarRenderer for players), and Java virtual dispatch always calls the
// most-derived override. Cancelling only the EntityRenderer base method never actually fired for
// players or mobs since AvatarRenderer/LivingEntityRenderer's own overrides ran instead — the
// vanilla nametag kept showing. Each concrete renderer needs its own injection.

@Mixin(EntityRenderer::class)
abstract class EntityNameTagMixin {
    @Inject(method = ["shouldShowName"], at = [At("HEAD")], cancellable = true)
    private fun asteriaSuppressVanillaNameTag(entity: Entity, distanceSq: Double, cir: CallbackInfoReturnable<Boolean>) {
        if (ModuleManager.nameTags.suppressesVanillaTag(entity)) {
            cir.returnValue = false
        }
    }
}

@Mixin(LivingEntityRenderer::class)
abstract class LivingEntityNameTagMixin {
    @Inject(method = ["shouldShowName"], at = [At("HEAD")], cancellable = true)
    private fun asteriaSuppressVanillaNameTag(entity: LivingEntity, distanceSq: Double, cir: CallbackInfoReturnable<Boolean>) {
        if (ModuleManager.nameTags.suppressesVanillaTag(entity)) {
            cir.returnValue = false
        }
    }
}

@Mixin(AvatarRenderer::class)
abstract class AvatarNameTagMixin {
    @Inject(method = ["shouldShowName"], at = [At("HEAD")], cancellable = true)
    private fun asteriaSuppressVanillaNameTag(entity: Avatar, distanceSq: Double, cir: CallbackInfoReturnable<Boolean>) {
        if (ModuleManager.nameTags.suppressesVanillaTag(entity)) {
            cir.returnValue = false
        }
    }
}
