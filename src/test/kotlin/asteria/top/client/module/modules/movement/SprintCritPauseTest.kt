package asteria.top.client.module.modules.movement

fun main() {
    val pause = SprintCritPause()

    check(!pause.isActive()) { "pause should start inactive" }

    pause.suppressFor()
    check(pause.isActive()) { "pause should activate for one tick" }

    pause.tick()
    check(!pause.isActive()) { "pause should expire after one tick" }

    pause.suppressFor(1)
    pause.suppressFor(3)
    check(pause.isActive()) { "pause should keep the longer suppression window" }

    pause.tick()
    pause.tick()
    check(pause.isActive()) { "pause should still be active before the final tick" }

    pause.tick()
    check(!pause.isActive()) { "pause should clear after the requested duration" }
}
