package asteria.top.client.mixin

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import asteria.top.client.module.ModuleManager
import net.minecraft.client.Minecraft
import net.minecraft.client.player.KeyboardInput
import net.minecraft.world.entity.player.Input
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(KeyboardInput::class)
abstract class KeyboardInputMixin {
    @ModifyExpressionValue(
        method = ["tick"],
        at = [At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;")],
    )
    private fun glacierApplyMoveFix(original: Input): Input {
        val player = Minecraft.getInstance().player ?: return original
        val corrected = ModuleManager.moveFix.correctedInput(original, player.yRot) ?: return original
        return corrected.withPassthrough(original)
    }
}
