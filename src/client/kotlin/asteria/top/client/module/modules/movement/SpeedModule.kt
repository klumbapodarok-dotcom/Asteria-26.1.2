package asteria.top.client.module.modules.movement

import com.mojang.blaze3d.platform.InputConstants
import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.EnumSetting
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

class SpeedModule : Module(
    name = "Speed",
    category = ModuleCategory.MOVEMENT,
    description = "Collision-based speed bypass for Polar.",
    enabledByDefault = false,
) {
    enum class Mode(val label: String) {
        POLAR("Polar"),
    }

    private val mode = setting(EnumSetting("Mode", Mode.entries.toTypedArray(), Mode.POLAR) { it.label })

    fun tick() {
        if (!enabled) return

        when (mode.value) {
            Mode.POLAR -> tickPolar()
        }
    }

    private fun tickPolar() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        var collisions = 0
        level.entitiesForRendering().forEach { entity ->
            if (entity is LivingEntity && entity !== player && entity !is ArmorStand &&
                player.boundingBox.intersects(entity.boundingBox)
            ) {
                collisions++
            }
        }

        if (collisions > 0) {
            val movement = calculateDirection(mc, 0.08 * collisions)
            player.addDeltaMovement(Vec3(movement.first, 0.0, movement.second))
        }
    }

    private fun calculateDirection(mc: Minecraft, distance: Double): Pair<Double, Double> {
        var forward = 0.0f
        var sideways = 0.0f
        val window = mc.window

        if (InputConstants.isKeyDown(window, mc.options.keyUp.defaultKey.value)) forward++
        if (InputConstants.isKeyDown(window, mc.options.keyDown.defaultKey.value)) forward--
        if (InputConstants.isKeyDown(window, mc.options.keyLeft.defaultKey.value)) sideways++
        if (InputConstants.isKeyDown(window, mc.options.keyRight.defaultKey.value)) sideways--

        var yaw = mc.player?.yRot ?: return 0.0 to 0.0
        if (forward != 0.0f) {
            if (sideways > 0.0f) yaw += if (forward > 0.0f) -45.0f else 45.0f
            else if (sideways < 0.0f) yaw += if (forward > 0.0f) 45.0f else -45.0f
            sideways = 0.0f
            forward = if (forward > 0.0f) 1.0f else -1.0f
        }

        val radians = Math.toRadians((yaw + 90.0f).toDouble())
        val sinYaw = sin(radians)
        val cosYaw = cos(radians)
        val x = forward * distance * cosYaw + sideways * distance * sinYaw
        val z = forward * distance * sinYaw - sideways * distance * cosYaw
        return x to z
    }
}
