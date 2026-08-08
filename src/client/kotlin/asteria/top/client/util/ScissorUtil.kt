package asteria.top.client.util

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

object ScissorUtil {
    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
    private val stack = ArrayDeque<Bounds>()

    fun withScissor(
        graphics: GuiGraphicsExtractor,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        block: () -> Unit,
    ) {
        if (width <= 0.0f || height <= 0.0f) return

        val requested = Bounds(floor(x).toInt(), floor(y).toInt(), ceil(x + width).toInt(), ceil(y + height).toInt())
        val parent = stack.lastOrNull()
        val bounds = if (parent == null) requested else Bounds(
            max(parent.left, requested.left),
            max(parent.top, requested.top),
            minOf(parent.right, requested.right),
            minOf(parent.bottom, requested.bottom),
        )
        if (bounds.right <= bounds.left || bounds.bottom <= bounds.top) return
        stack.addLast(bounds)
        graphics.enableScissor(bounds.left, bounds.top, bounds.right, bounds.bottom)
        try {
            block()
        } finally {
            stack.removeLast()
            val restore = stack.lastOrNull()
            if (restore == null) graphics.disableScissor()
            else graphics.enableScissor(restore.left, restore.top, restore.right, restore.bottom)
        }
    }
}
