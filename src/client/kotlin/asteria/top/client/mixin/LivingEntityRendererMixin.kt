package asteria.top.client.mixin

import asteria.top.client.module.ModuleManager
import asteria.top.client.util.combat.CombatRotationManager
import asteria.top.client.render.ChamsRenderState
import asteria.top.client.render.ChamsRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.world.entity.LivingEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(LivingEntityRenderer::class)
abstract class LivingEntityRendererMixin {

    @Inject(method = ["extractRenderState"], at = [At("TAIL")])
    private fun glacierApplyRotationView(entity: LivingEntity, state: LivingEntityRenderState, tickDelta: Float, ci: CallbackInfo) {
        if (!ModuleManager.rotationView.enabled) return
        if (entity !== Minecraft.getInstance().player) return
        if (!CombatRotationManager.hasRotation()) return

        state.xRot = CombatRotationManager.packetPitch(state.xRot)
    }

    @Inject(method = ["extractRenderState"], at = [At("TAIL")])
    private fun glacierApplyFakeLagRenderState(entity: LivingEntity, state: LivingEntityRenderState, tickDelta: Float, ci: CallbackInfo) {
        ModuleManager.fakeLag.applyRenderState(entity, state)
    }

    @Inject(method = ["extractRenderState"], at = [At("TAIL")])
    private fun glacierApplyChamsRenderState(entity: LivingEntity, state: LivingEntityRenderState, tickDelta: Float, ci: CallbackInfo) {
        if (state is ChamsRenderState) {
            val client = Minecraft.getInstance()
            state.isSelf = (entity === client.player)
            state.isPlayer = (entity is net.minecraft.world.entity.player.Player)
            state.isMob = (entity is net.minecraft.world.entity.monster.Enemy)
            state.isAnimal = (entity is net.minecraft.world.entity.animal.Animal)
        }
    }

    @Inject(method = ["getRenderType"], at = [At("RETURN")], cancellable = true)
    private fun getChamsRenderType(
        state: LivingEntityRenderState,
        isBodyVisible: Boolean,
        translucent: Boolean,
        showOutline: Boolean,
        cir: CallbackInfoReturnable<RenderType>
    ) {
        val chams = ModuleManager.chams
        if (!chams.enabled) return

        if (state is ChamsRenderState) {
            val shouldApply = (state.isSelf && chams.targets.enabled("Self")) ||
                    (!state.isSelf && state.isPlayer && chams.targets.enabled("Players")) ||
                    (state.isMob && chams.targets.enabled("Mobs")) ||
                    (state.isAnimal && chams.targets.enabled("Animals"))
            if (shouldApply) {
                cir.returnValue = ChamsRenderer.getChamsRenderType(chams.throughWalls.value, chams.shaderPreset.value)
            }
        }
    }
}
