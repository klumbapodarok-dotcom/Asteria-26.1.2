package asteria.top.client.util

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.ceil
import kotlin.math.floor

object GuiBoxUtil {
    enum class Shape {
        ROUNDED_RECT,
        BOTTOM_ROUNDED_RECT,
        ROUNDED_OUTLINE,
        CIRCLE,
    }

    data class Box(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
        val color: Int,
        val blur: Boolean,
        val shape: Shape = Shape.ROUNDED_RECT,
        val postProcessTint: Boolean = false,
        val clip: Clip? = null,
    )

    data class Clip(val x: Float, val y: Float, val right: Float, val bottom: Float)

    data class BlurRegion(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
    )

    fun draw(graphics: GuiGraphicsExtractor, box: Box) {
        if (box.blur) return
        box.clip?.let { clip ->
            graphics.enableScissor(
                floor(clip.x).toInt(),
                floor(clip.y).toInt(),
                ceil(clip.right).toInt(),
                ceil(clip.bottom).toInt(),
            )
        }
        when (box.shape) {
            Shape.ROUNDED_RECT -> GuiShapeUtil.roundedFill(
                graphics,
                box.x,
                box.y,
                box.x + box.width,
                box.y + box.height,
                box.radius,
                box.color,
            )
            Shape.BOTTOM_ROUNDED_RECT -> {
                graphics.enableScissor(
                    floor(box.x).toInt(),
                    floor(box.y).toInt(),
                    ceil(box.x + box.width).toInt(),
                    ceil(box.y + box.height).toInt(),
                )
                GuiShapeUtil.roundedFill(
                    graphics,
                    box.x,
                    box.y - box.radius,
                    box.x + box.width,
                    box.y + box.height,
                    box.radius,
                    box.color,
                )
                graphics.disableScissor()
            }
            Shape.ROUNDED_OUTLINE -> GuiShapeUtil.roundedStroke(
                graphics,
                box.x,
                box.y,
                box.x + box.width,
                box.y + box.height,
                box.radius,
                box.color,
            )
            Shape.CIRCLE -> GuiShapeUtil.circleFill(
                graphics,
                box.x + box.width * 0.5f,
                box.y + box.height * 0.5f,
                minOf(box.width, box.height) * 0.5f,
                box.color,
            )
        }
        if (box.clip != null) graphics.disableScissor()
    }

    fun toBlurRegions(boxes: List<Box>, guiScale: Float): List<BlurRegion> {
        return boxes.asSequence()
            .filter { it.blur }
            .mapNotNull {
                val x = maxOf(it.x, it.clip?.x ?: it.x)
                val y = maxOf(it.y, it.clip?.y ?: it.y)
                val right = minOf(it.x + it.width, it.clip?.right ?: it.x + it.width)
                val bottom = minOf(it.y + it.height, it.clip?.bottom ?: it.y + it.height)
                if (right <= x || bottom <= y) null else BlurRegion(x * guiScale, y * guiScale, (right - x) * guiScale, (bottom - y) * guiScale, it.radius * guiScale)
            }
            .toList()
    }
}
