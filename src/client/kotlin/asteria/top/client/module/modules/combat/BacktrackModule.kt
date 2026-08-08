package asteria.top.client.module.modules.combat

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.setting.FloatSetting
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.GizmoStyle
import java.util.Collections

class BacktrackModule : Module(
    name = "Backtrack",
    category = ModuleCategory.COMBAT,
    description = "Delays incoming position updates of the target to attack their past position.",
    enabledByDefault = false,
) {
    private val maxRange = setting(FloatSetting("Max Range", 6.0f, 0.0f, 10.0f, 0.01f))
    private val delay = setting(FloatSetting("Delay", 200.0f, 0.0f, 12000.0f, 1.0f, "ms"))
    private val fillAlpha = setting(FloatSetting("Fill Alpha", 40.0f, 0.0f, 255.0f, 1.0f))

    private val packets = Collections.synchronizedList(ArrayList<QueuedPacket>())
    var target: Entity? = null
        private set
    var trackedPosition: Vec3? = null
        private set
    // Ghost snapshot – mirrors Java's ghostPosition / yaw / pitch
    var ghostPosition: Vec3? = null
        private set
    var ghostYaw = 0.0f
        private set
    var ghostPitch = 0.0f
        private set
    var hasGhostSnapshot = false
        private set
    private var lastEnabled = false

    fun tick() {
        val mc = Minecraft.getInstance()
        if (!enabled || mc.player == null || mc.level == null) {
            if (lastEnabled) {
                resetState(release = true, clear = false)
                lastEnabled = false
            }
            return
        }
        lastEnabled = true

        val auraTarget = ModuleManager.killaura.target
        if (auraTarget != null) {
            setTarget(auraTarget)
        }

        val enemy = target
        if (hasValidTarget() && enemy != null) {
            // Update ghost snapshot instead of moving the entity (mirrors Java behavior)
            if (trackedPosition != null) {
                ghostPosition = trackedPosition
                ghostYaw = enemy.yRot
                ghostPitch = enemy.xRot
                hasGhostSnapshot = true
            }
            releasePackets(all = false)
        } else {
            target = null
            trackedPosition = null
            ghostPosition = null
            ghostYaw = 0.0f
            ghostPitch = 0.0f
            hasGhostSnapshot = false
            releasePackets(all = false)
        }
    }

    override fun onDisable() {
        // Flush all pending packets first (Java does this) then clear state
        resetState(release = true, clear = true)
    }

    fun setTarget(newTarget: Entity) {
        if (!isValidTarget(newTarget)) {
            return
        }
        if (newTarget !== target) {
            // Reset any previous ghost state and initialise a fresh snapshot
            resetState(release = true, clear = false)
            trackedPosition = newTarget.trackingPosition()
            // Initialise ghost snapshot to the target's current state
            ghostPosition = newTarget.position()
            ghostYaw = newTarget.yRot
            ghostPitch = newTarget.xRot
            hasGhostSnapshot = true
        }
        target = newTarget
    }

    private fun resetState(release: Boolean, clear: Boolean) {
        if (clear) {
            // Flush all pending packets before discarding the queue, mirroring original Java behavior.
            releasePackets(all = true)
            synchronized(packets) { packets.clear() }
        } else if (release) {
            releasePackets(all = true)
        }
        target = null
        trackedPosition = null
        // Clear ghost snapshot state
        ghostPosition = null
        ghostYaw = 0.0f
        ghostPitch = 0.0f
        hasGhostSnapshot = false
    }

    private fun isValidTarget(entity: Entity): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (entity !is Player || entity === player || !entity.isAlive) {
            return false
        }

        val box = if (entity === target && trackedPosition != null) getTrackedBox() else entity.boundingBox
        val dist = squaredDistanceToNearestPoint(player.position(), box)
        val max = maxRange.value
        return dist <= max * max
    }

    private fun hasValidTarget(): Boolean {
        val enemy = target
        return enemy != null && enemy.isAlive && isValidTarget(enemy)
    }

    private fun shouldDelay(packet: Packet<*>): Boolean {
        return packet is ClientboundPingPacket
                || packet is ClientboundRotateHeadPacket
                || packet is ClientboundMoveEntityPacket
                || packet is ClientboundTeleportEntityPacket
                || packet is ClientboundEntityPositionSyncPacket
                || packet is ClientboundSetEntityMotionPacket
                || packet is ClientboundLevelParticlesPacket
                || packet is ClientboundExplodePacket
    }

    fun interceptIncomingPacket(packet: Packet<*>): Boolean {
        val mc = Minecraft.getInstance()
        if (!enabled || mc.player == null || mc.level == null) {
            return false
        }

        if (packet is ClientboundPlayerPositionPacket || packet is ClientboundDisconnectPacket) {
            resetState(release = true, clear = false)
            return false
        }

        if (packet is ClientboundSetHealthPacket) {
            if (packet.health <= 0.0f) {
                resetState(release = true, clear = false)
                return false
            }
        }

        // Removed: packets.isEmpty() && !hasValidTarget() check to allow packet queuing before target acquisition like in old source

        updateTrackedPosition(packet)

        if (packet is ClientboundSoundPacket || packet is ClientboundSetHealthPacket || packet is ClientboundAnimatePacket) {
            return false
        }

        if (shouldDelay(packet)) {
            synchronized(packets) {
                packets.add(QueuedPacket(packet, System.currentTimeMillis()))
            }
            return true
        }

        return false
    }

    private fun updateTrackedPosition(packet: Packet<*>) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val enemy = target ?: return

        var newPos: Vec3? = null

        if (packet is ClientboundMoveEntityPacket) {
            val entity = packet.getEntity(level)
            if (entity === enemy) {
                val codec = enemy.positionCodec
                newPos = codec.decode(packet.xa.toLong(), packet.ya.toLong(), packet.za.toLong())
            }
        } else if (packet is ClientboundTeleportEntityPacket) {
            if (packet.id() == enemy.id) {
                newPos = packet.change().position()
            }
        } else if (packet is ClientboundEntityPositionSyncPacket) {
            if (packet.id() == enemy.id) {
                newPos = packet.values().position()
            }
        }

        if (newPos == null) {
            return
        }

        trackedPosition = newPos
    }

    private fun releasePackets(all: Boolean) {
        val mc = Minecraft.getInstance()
        val connection = mc.player?.connection ?: run {
            synchronized(packets) {
                packets.clear()
            }
            return
        }

        val now = System.currentTimeMillis()
        var shouldReleaseAll = all
        synchronized(packets) {
            if (!shouldReleaseAll && packets.isNotEmpty()) {
                val oldest = packets.first()
                if ((now - oldest.timestamp) >= delay.value.toLong()) {
                    shouldReleaseAll = true
                }
            }

            val iterator = packets.iterator()
            while (iterator.hasNext()) {
                val queued = iterator.next()
                if (!shouldReleaseAll && (now - queued.timestamp) < delay.value.toLong()) {
                    continue
                }
                try {
                    @Suppress("UNCHECKED_CAST")
                    (queued.packet as Packet<net.minecraft.network.PacketListener>).handle(connection)
                } catch (ignored: Exception) {
                }
                iterator.remove()
            }
        }
    }

    private fun squaredDistanceToNearestPoint(point: Vec3, box: AABB): Double {
        val dx = Mth.clamp(point.x, box.minX, box.maxX)
        val dy = Mth.clamp(point.y, box.minY, box.maxY)
        val dz = Mth.clamp(point.z, box.minZ, box.maxZ)
        return point.distanceToSqr(dx, dy, dz)
    }

    fun getTrackedBox(): AABB {
        val enemy = target ?: return AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val pos = trackedPosition ?: return enemy.boundingBox
        return enemy.boundingBox.move(pos.subtract(enemy.position()))
    }

    fun renderGizmos(context: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        val enemy = target ?: return

        if (!enabled || mc.level == null) {
            return
        }

        // Render using the ghost snapshot (Java‑style) rather than a stale tracked box
        if (!hasGhostSnapshot) return
        val renderPos = ghostPosition ?: return
        // Use the already‑checked enemy reference for the box
        val box = enemy.boundingBox.move(renderPos.subtract(enemy.position()))
        val strokeColor = 0x88FF82 or (0xFF shl 24)
        val alpha = fillAlpha.value.toInt()
        val fillColor = (0x88FF82 and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
        val style = GizmoStyle.strokeAndFill(strokeColor, 2.0f, fillColor)

        mc.levelRenderer.collectPerFrameGizmos().use {
            Gizmos.cuboid(box, style, false).setAlwaysOnTop()
        }
    }

    private data class QueuedPacket(val packet: Packet<*>, val timestamp: Long)
}
