package asteria.top.client.util.combat

import asteria.top.client.module.ModuleManager
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

object CombatRotationManager {
    private var serverYaw = 0.0f
    private var serverPitch = 0.0f
    private var active = false
    private var activeOwner: Any? = null
    private var activePriority = Int.MIN_VALUE
    private var activeTick = Int.MIN_VALUE
    private var activeAimPosition: Vec3? = null
    private var activeTargetCenter: Vec3? = null

    fun requestRotation(
        owner: Any,
        priority: Int,
        desired: CombatRotation,
        maxYawSpeed: Float,
        maxPitchSpeed: Float,
        aimPosition: Vec3?,
        targetCenter: Vec3?,
    ): CombatRotation {
        val player = Minecraft.getInstance().player
        val tick = player?.tickCount ?: Int.MIN_VALUE
        if (tick != activeTick) {
            activeTick = tick
            activePriority = Int.MIN_VALUE
            if (!active) resetToPlayer()
        }

        if (active && activeOwner !== owner && priority < activePriority) {
            publishMoveFix()
            return CombatRotation(serverYaw, serverPitch)
        }

        if (!active) resetToPlayer()

        val next = step(serverYaw, serverPitch, desired.yaw, desired.pitch, maxYawSpeed, maxPitchSpeed)
        serverYaw = next.yaw
        serverPitch = next.pitch
        active = true
        activeOwner = owner
        activePriority = priority
        activeAimPosition = aimPosition
        activeTargetCenter = targetCenter
        publishMoveFix()
        return next
    }

    fun clear(owner: Any) {
        if (activeOwner !== owner) return
        active = false
        activeOwner = null
        activePriority = Int.MIN_VALUE
        activeAimPosition = null
        activeTargetCenter = null
        resetToPlayer()
        ModuleManager.moveFix.updateAuraRotation(false, serverYaw, null, null)
    }

    fun clearAll() {
        active = false
        activeOwner = null
        activePriority = Int.MIN_VALUE
        activeAimPosition = null
        activeTargetCenter = null
        resetToPlayer()
        ModuleManager.moveFix.updateAuraRotation(false, serverYaw, null, null)
    }

    fun hasRotation(): Boolean {
        return active
    }

    fun packetYaw(playerYaw: Float): Float {
        return if (active) Mth.wrapDegrees(serverYaw) else playerYaw
    }

    fun packetPitch(playerPitch: Float): Float {
        return if (active) serverPitch else playerPitch
    }

    fun applyRotationView(yaw: Float) {
        if (!active || !ModuleManager.rotationView.enabled) return
        Minecraft.getInstance().player?.let { player ->
            player.setYHeadRot(yaw)
            player.setYBodyRot(yaw)
        }
    }

    private fun publishMoveFix() {
        ModuleManager.moveFix.updateAuraRotation(active, serverYaw, activeAimPosition, activeTargetCenter)
    }

    private fun resetToPlayer() {
        Minecraft.getInstance().player?.let { player ->
            serverYaw = Mth.wrapDegrees(player.yRot)
            serverPitch = player.xRot
        }
    }

    private fun step(
        currentYaw: Float,
        currentPitch: Float,
        targetYaw: Float,
        targetPitch: Float,
        maxYaw: Float,
        maxPitch: Float,
    ): CombatRotation {
        val yawDelta = Mth.wrapDegrees(targetYaw - currentYaw)
        val pitchDelta = Mth.wrapDegrees(targetPitch - currentPitch)
        val yaw = currentYaw + yawDelta.coerceIn(-maxYaw, maxYaw)
        val pitch = (currentPitch + pitchDelta.coerceIn(-maxPitch, maxPitch)).coerceIn(-89.0f, 89.0f)
        return CombatRotation(Mth.wrapDegrees(yaw), pitch)
    }
}
