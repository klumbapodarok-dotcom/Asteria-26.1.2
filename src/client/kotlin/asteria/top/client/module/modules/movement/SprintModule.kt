package asteria.top.client.module.modules.movement

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.EnumSetting
import net.minecraft.client.Minecraft

class SprintModule : Module(
    name = "Sprint",
    category = ModuleCategory.MOVEMENT,
    description = "Automatic sprint handling.",
    enabledByDefault = true,
) {
    enum class Mode(val label: String) {
        LEGIT("Legit"),
        NONE("None"),
    }

    val mode = setting(EnumSetting("Mode", Mode.entries.toTypedArray(), Mode.LEGIT) { it.label })
    private val critPause = SprintCritPause()

    fun suppressForCriticalHit(ticks: Int = 1) {
        if (!enabled || mode.value == Mode.NONE) return
        critPause.suppressFor(ticks)
        stopSprinting(Minecraft.getInstance().player)
    }

    fun tick() {
        if (!enabled || mode.value == Mode.NONE) {
            critPause.reset()
            return
        }
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        if (critPause.isActive()) {
            critPause.tick()
            stopSprinting(player)
            return
        }

        if (shouldSprint(mc)) {
            player.isSprinting = true
        } else if (player.isSprinting) {
            player.isSprinting = false
        }
    }

    override fun onDisable() {
        critPause.reset()
        stopSprinting(Minecraft.getInstance().player)
    }

    fun shouldSprint(mc: Minecraft = Minecraft.getInstance()): Boolean {
        val player = mc.player ?: return false
        if (!enabled || mode.value == Mode.NONE) return false

        // Don't sprint if player has no food
        if (player.foodData.foodLevel <= 6) return false

        val forward = mc.options.keyUp.isDown
        val backward = mc.options.keyDown.isDown
        val hasForward = forward && !backward

        return hasForward
    }

    private fun stopSprinting(player: net.minecraft.client.player.LocalPlayer?) {
        if (player == null) return
        player.isSprinting = false
    }
}
