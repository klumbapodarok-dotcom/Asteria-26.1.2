package asteria.top.client.module.modules.combat

fun main() {
    val queue = CriticalAttackQueue()

    check(queue.queueIfNeeded(playerIsSprinting = false)) { "non-sprinting attacks should not be delayed" }
    check(!queue.consumePending()) { "nothing should be pending after an immediate attack" }

    check(!queue.queueIfNeeded(playerIsSprinting = true)) { "sprinting attacks should be delayed" }
    check(queue.queueIfNeeded(playerIsSprinting = false)) { "queued attacks should be allowed on the next tick" }
    check(queue.consumePending()) { "queued attack should be consumed exactly once" }
    check(!queue.consumePending()) { "queue should clear after consumption" }
}
