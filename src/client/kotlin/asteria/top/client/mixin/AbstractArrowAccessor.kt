package asteria.top.client.mixin

import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(AbstractArrow::class)
interface AbstractArrowAccessor {
    @Invoker("isInGround")
    fun asteriaIsInGround(): Boolean
}
