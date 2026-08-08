package asteria.top.client.util.combat

import net.minecraft.world.entity.player.Player

object SprintResetUtil {
    fun criticalSprintResetRequired(player: Player): Boolean {
        return !player.onGround() && player.fallDistance > 0.0f
    }

    fun attackCooldownReadyNextTick(player: Player, readyCooldown: Float): Boolean {
        return player.getAttackStrengthScale(0.0f) < readyCooldown &&
            player.getAttackStrengthScale(1.0f) >= readyCooldown
    }
}
