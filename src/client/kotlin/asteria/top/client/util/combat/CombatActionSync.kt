package asteria.top.client.util.combat

import net.minecraft.world.entity.player.Player

object CombatActionSync {
    private var lastAttackTick = Int.MIN_VALUE
    private var lastPlacementTick = Int.MIN_VALUE

    fun canAttackThisTick(player: Player?): Boolean {
        val tick = resolveTick(player)
        return tick == Int.MIN_VALUE || lastPlacementTick != tick
    }

    fun canPlaceThisTick(player: Player?): Boolean {
        val tick = resolveTick(player)
        return tick == Int.MIN_VALUE || lastAttackTick != tick
    }

    fun markAttack(player: Player?) {
        val tick = resolveTick(player)
        if (tick != Int.MIN_VALUE) lastAttackTick = tick
    }

    fun markPlacement(player: Player?) {
        val tick = resolveTick(player)
        if (tick != Int.MIN_VALUE) lastPlacementTick = tick
    }

    private fun resolveTick(player: Player?): Int {
        return player?.tickCount ?: Int.MIN_VALUE
    }
}
