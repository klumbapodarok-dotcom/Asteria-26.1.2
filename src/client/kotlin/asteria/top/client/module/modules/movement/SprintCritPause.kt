package asteria.top.client.module.modules.movement

class SprintCritPause {
    private var ticksRemaining = 0

    fun suppressFor(ticks: Int = 1) {
        if (ticks > ticksRemaining) {
            ticksRemaining = ticks
        }
    }

    fun tick() {
        if (ticksRemaining > 0) {
            ticksRemaining -= 1
        }
    }

    fun isActive(): Boolean {
        return ticksRemaining > 0
    }

    fun reset() {
        ticksRemaining = 0
    }
}
