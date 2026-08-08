package asteria.top.client.util.combat

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

class TargetManager {
    var currentTarget: LivingEntity? = null
        private set

    private var potentialTargets: List<LivingEntity> = emptyList()

    fun lockTarget(target: LivingEntity?) {
        if (currentTarget == null && target != null) {
            currentTarget = target
        }
    }

    fun forceTarget(target: LivingEntity?) {
        currentTarget = target
    }

    fun releaseTarget() {
        currentTarget = null
    }

    fun searchTargets(
        entities: Iterable<Entity>,
        maxDistance: Float,
        comparator: Comparator<LivingEntity>? = null,
    ) {
        val player = Minecraft.getInstance().player
        if (player == null) {
            releaseTarget()
            potentialTargets = emptyList()
            return
        }

        if (currentTarget != null && CombatRotationUtil.spot(currentTarget!!).distanceTo(player.eyePosition) > maxDistance) {
            releaseTarget()
        }

        val sort = comparator ?: compareBy { entity: LivingEntity ->
            CombatRotationUtil.spot(entity).distanceTo(player.eyePosition)
        }

        potentialTargets = entities
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { CombatRotationUtil.spot(it).distanceTo(player.eyePosition) <= maxDistance }
            .sortedWith(sort)
            .toList()
    }

    fun validateTarget(predicate: (LivingEntity) -> Boolean) {
        potentialTargets.firstOrNull(predicate)?.let(::lockTarget)
        if (currentTarget?.let(predicate) != true) {
            releaseTarget()
        }
    }
}
