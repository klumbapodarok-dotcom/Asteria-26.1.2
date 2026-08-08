package asteria.top.client.module.modules.combat

class CriticalAttackQueue {
    private var pending = false

    fun queueIfNeeded(playerIsSprinting: Boolean): Boolean {
        if (pending) return true
        if (!playerIsSprinting) return true

        pending = true
        return false
    }

    fun consumePending(): Boolean {
        if (!pending) return false
        pending = false
        return true
    }

    fun reset() {
        pending = false
    }
}
