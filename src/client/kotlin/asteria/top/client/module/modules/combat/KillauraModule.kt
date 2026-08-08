package asteria.top.client.module.modules.combat

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.module.setting.MultiBooleanSetting
import asteria.top.client.util.combat.CombatActionSync
import asteria.top.client.util.combat.CombatRotation
import asteria.top.client.util.combat.CombatRotationManager
import asteria.top.client.util.combat.CombatRotationUtil
import asteria.top.client.util.combat.PolarRotationUtil
import asteria.top.client.util.combat.SprintResetUtil
import asteria.top.client.util.combat.TargetManager
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

class KillauraModule : Module(
    name = "Aura",
    category = ModuleCategory.COMBAT,
    description = "Target lock, simple internal rotation and attack executor.",
    enabledByDefault = false,
) {
    private companion object {
        private const val ATTACK_READY_COOLDOWN = 0.92f
        private const val POLAR_ATTACK_ALIGNED_TICKS = 2
    }

    enum class AimMode(val label: String) {
        SIMPLE("Simple"),
        POLAR("Polar"),
    }

    val aimMode = setting(EnumSetting("Rotation", AimMode.entries.toTypedArray(), AimMode.SIMPLE) { it.label })
    private val distance = setting(FloatSetting("Distance", 3.0f, 2.5f, 6.0f, 0.1f))
    private val preDistance = setting(FloatSetting("Pre distance", 0.3f, 0.0f, 30.0f, 0.1f))
    private val maxYawSpeed = setting(FloatSetting("Max yaw speed", 180.0f, 1.0f, 180.0f, 1.0f))
    private val maxPitchSpeed = setting(FloatSetting("Max pitch speed", 90.0f, 1.0f, 90.0f, 1.0f))
    private val targets = setting(
        MultiBooleanSetting(
            "Targets",
            BooleanSetting("Players", true),
            BooleanSetting("Mobs", true),
            BooleanSetting("Animals", true),
        )
    )
    val options = setting(
        MultiBooleanSetting(
            "Options",
            BooleanSetting("Only crits", true),
            BooleanSetting("Smart crits", true),
            BooleanSetting("Raytrace", true),
        )
    )
    var target: LivingEntity? = null
        private set

    private var lastAttackMs = 0L
    private var polarAlignedTicks = 0
    private var polarAlignedTargetId = Int.MIN_VALUE
    private var lastAimMode = AimMode.SIMPLE
    private val criticalAttackQueue = CriticalAttackQueue()
    private val targetManager = TargetManager()

    fun attackDistance(): Float {
        return distance.value
    }

    fun attackPreDistance(): Float {
        return preDistance.value
    }

    fun packetYaw(playerYaw: Float): Float {
        return if (hasPacketRotation()) CombatRotationManager.packetYaw(playerYaw) else playerYaw
    }

    fun packetPitch(playerPitch: Float): Float {
        return if (hasPacketRotation()) CombatRotationManager.packetPitch(playerPitch) else playerPitch
    }

    fun hasPacketRotation(): Boolean {
        return enabled && target != null && CombatRotationManager.hasRotation()
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (lastAimMode != aimMode.value) {
            resetPolarAlignment()
            lastAimMode = aimMode.value
        }
        if (!enabled) {
            CombatRotationManager.clear(this)
            resetPolarAlignment()
            target = null
            targetManager.releaseTarget()
            criticalAttackQueue.reset()
            return
        }
        val level = mc.level ?: return
        val gameMode = mc.gameMode ?: return

        val maxDistance = attackDistance() + attackPreDistance()
        if (target != null && (!validTarget(target!!) || spot(target!!).distanceTo(player.eyePosition) > maxDistance)) {
            resetPolarAlignment()
            target = null
            targetManager.releaseTarget()
        }
        if (criticalAttackQueue.consumePending()) {
            val pendingTarget = target ?: run {
                CombatRotationManager.clear(this)
                return
            }
            if (!validTarget(pendingTarget) || spot(pendingTarget).distanceTo(player.eyePosition) > attackDistance()) {
                target = null
                targetManager.releaseTarget()
                CombatRotationManager.clear(this)
                resetPolarAlignment()
                return
            }
            val pendingAimPoint = aimPoint(pendingTarget)
            updateCombatRotation(pendingAimPoint, pendingTarget)
            if (!polarAttackReady(pendingTarget)) return
            if (!canAttack()) return
            if (options.enabled("Raytrace") && !raytraceValid(pendingTarget)) return

            gameMode.attack(player, pendingTarget)
            player.swing(InteractionHand.MAIN_HAND)
            CombatActionSync.markAttack(player)
            lastAttackMs = System.currentTimeMillis()
            return
        }
        targetManager.searchTargets(level.entitiesForRendering(), maxDistance)
        targetManager.validateTarget { validTarget(it) }
        target = targetManager.currentTarget

        val locked = target ?: run {
            CombatRotationManager.clear(this)
            resetPolarAlignment()
            return
        }
        val aimPoint = aimPoint(locked)
        updateCombatRotation(aimPoint, locked)

        if (spot(locked).distanceTo(player.eyePosition) > attackDistance()) {
            return
        }
        if (!polarAttackReady(locked)) {
            return
        }
        if (shouldPrepareSprintReset(player)) {
            ModuleManager.sprint.suppressForCriticalHit(1)
            return
        }
        if (!canAttack()) {
            return
        }
        if (options.enabled("Raytrace") && !raytraceValid(locked)) {
            return
        }

        val critWindow = !player.onGround() && player.fallDistance > 0.0f
        if (critWindow && player.isSprinting) {
            ModuleManager.sprint.suppressForCriticalHit(1)
            criticalAttackQueue.queueIfNeeded(true)
            return
        }

        gameMode.attack(player, locked)
        player.swing(InteractionHand.MAIN_HAND)
        CombatActionSync.markAttack(player)
        lastAttackMs = System.currentTimeMillis()
    }

    private fun validTarget(entity: LivingEntity): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        if (entity === player) return false
        if (!entity.isAlive || entity.health <= 0.0f || entity.isRemoved) return false
        if (entity === player.vehicle) return false
        if (entity is ArmorStand) return false
        return when (entity) {
            is Player -> targets.enabled("Players")
            is Animal -> targets.enabled("Animals")
            is Enemy -> targets.enabled("Mobs")
            else -> false
        }
    }

    private fun canAttack(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        if (!CombatActionSync.canAttackThisTick(player)) return false
        if (System.currentTimeMillis() - lastAttackMs < 75L) return false
        if (player.getAttackStrengthScale(0.0f) < ATTACK_READY_COOLDOWN) return false
        if (!options.enabled("Only crits")) return true
        if (options.enabled("Smart crits") && !Minecraft.getInstance().options.keyJump.isDown && player.onGround()) return true
        if (player.isInWater) return true
        return (!player.onGround() && player.fallDistance > 0.0f) || critsCancelled()
    }

    private fun shouldPrepareSprintReset(player: Player): Boolean {
        if (!player.isSprinting) return false
        if (!criticalSprintResetRequired(player)) return false
        return attackCooldownReadyNextTick(player)
    }

    private fun criticalSprintResetRequired(player: Player): Boolean {
        return SprintResetUtil.criticalSprintResetRequired(player)
    }

    private fun attackCooldownReadyNextTick(player: Player): Boolean {
        if (!CombatActionSync.canAttackThisTick(player)) return false
        if (System.currentTimeMillis() - lastAttackMs < 75L) return false
        return SprintResetUtil.attackCooldownReadyNextTick(player, ATTACK_READY_COOLDOWN) &&
            player.getAttackStrengthScale(0.0f) < ATTACK_READY_COOLDOWN &&
            player.getAttackStrengthScale(1.0f) >= ATTACK_READY_COOLDOWN
    }

    private fun critsCancelled(): Boolean {
        val player = Minecraft.getInstance().player ?: return true
        return player.isInLava || player.isPassenger || player.isNoGravity || player.abilities.flying || player.isFallFlying
    }

    private fun raytraceValid(target: LivingEntity): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        if (!player.hasLineOfSight(target)) return false

        val yaw = ModuleManager.windHop.packetYaw(packetYaw(player.yRot))
        val pitch = ModuleManager.windHop.packetPitch(packetPitch(player.xRot))
        val eye = player.eyePosition
        val direction = Vec3.directionFromRotation(pitch, yaw)
        val end = eye.add(direction.scale(attackDistance().toDouble()))
        return target.boundingBox.inflate(0.05).clip(eye, end).isPresent
    }

    private fun spot(entity: Entity): Vec3 {
        return CombatRotationUtil.spot(entity)
    }

    private fun aimPoint(entity: LivingEntity): Vec3 {
        return when (aimMode.value) {
            AimMode.SIMPLE -> spot(entity)
            AimMode.POLAR -> PolarRotationUtil.aimPoint(entity, currentRotation())
        }
    }

    private fun updateCombatRotation(aimPoint: Vec3, locked: LivingEntity) {
        val player = Minecraft.getInstance().player ?: return
        val desired = CombatRotationUtil.fromVector(aimPoint.subtract(player.eyePosition))
        if (aimMode.value == AimMode.POLAR) {
            val current = currentRotation()
            CombatRotationManager.requestRotation(
                owner = this,
                priority = 50,
                desired = CombatRotation(desired.yaw, desired.pitch),
                maxYawSpeed = PolarRotationUtil.yawSpeed(current, desired),
                maxPitchSpeed = PolarRotationUtil.pitchSpeed(current, desired),
                aimPosition = aimPoint,
                targetCenter = locked.boundingBox.center,
            )
            return
        }
        CombatRotationManager.requestRotation(
            owner = this,
            priority = 50,
            desired = CombatRotation(desired.yaw, desired.pitch),
            maxYawSpeed = maxYawSpeed.value,
            maxPitchSpeed = maxPitchSpeed.value,
            aimPosition = aimPoint,
            targetCenter = locked.boundingBox.center,
        )
    }

    private fun polarAttackReady(target: LivingEntity): Boolean {
        if (aimMode.value != AimMode.POLAR) return true
        if (polarAlignedTargetId != target.id) {
            polarAlignedTargetId = target.id
            polarAlignedTicks = 0
        }

        if (!rotationHitsTarget(target, currentRotation(), 0.11)) {
            polarAlignedTicks = 0
            return false
        }

        polarAlignedTicks += 1
        return polarAlignedTicks >= POLAR_ATTACK_ALIGNED_TICKS
    }

    private fun rotationHitsTarget(target: LivingEntity, rotation: CombatRotation, inflate: Double): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val eye = player.eyePosition
        val direction = Vec3.directionFromRotation(rotation.pitch, rotation.yaw)
        val end = eye.add(direction.scale(attackDistance().toDouble() + 0.25))
        return target.boundingBox.inflate(inflate).clip(eye, end).isPresent
    }

    private fun resetPolarAlignment() {
        polarAlignedTicks = 0
        polarAlignedTargetId = Int.MIN_VALUE
    }

    private fun currentRotation(): CombatRotation {
        val player = Minecraft.getInstance().player ?: return CombatRotation(0.0f, 0.0f)
        return CombatRotation(
            CombatRotationManager.packetYaw(player.yRot),
            CombatRotationManager.packetPitch(player.xRot),
        )
    }

}
