package asteria.top.client.render

import asteria.top.client.util.GuiBoxUtil
import kotlin.math.max

object AsteriaGuiRenderer {
    const val PILL_RADIUS = 999.0f

    fun color(hex: String): Int {
        return 0xFF000000.toInt() or hex.removePrefix("#").toInt(16)
    }

    fun alpha(color: Int, alpha: Float): Int {
        val resolved = (alpha.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
        return (color and 0x00FFFFFF) or (resolved shl 24)
    }

    fun rect(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
    ) {
        boxes += GuiBoxUtil.Box(x, y, width, height, radius, color, blur = false)
    }

    fun outlineRect(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
    ) {
        if (asteria.top.client.module.ModuleManager.postProcessing.liquidGlass.value) return
        boxes += GuiBoxUtil.Box(x, y, width, height, radius, color, blur = false, shape = GuiBoxUtil.Shape.ROUNDED_OUTLINE)
    }

    fun tintRect(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
    ) {
        boxes += GuiBoxUtil.Box(x, y, width, height, radius, color, blur = false, postProcessTint = true)
    }

    fun pill(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
    ) {
        if (width <= 0.0f || height <= 0.0f) return

        val radius = minOf(width, height) * 0.5f
        val centerY = y + height * 0.5f
        if (width <= height) {
            circle(boxes, x + width * 0.5f, centerY, radius, color)
            return
        }

        rect(boxes, x + radius, y, width - radius * 2.0f, height, 0.0f, color)
        circle(boxes, x + radius, centerY, radius, color)
        circle(boxes, x + width - radius, centerY, radius, color)
    }

    fun circle(
        boxes: MutableList<GuiBoxUtil.Box>,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int,
    ) {
        if (radius <= 0.0f) return
        val diameter = radius * 2.0f
        boxes += GuiBoxUtil.Box(centerX - radius, centerY - radius, diameter, diameter, radius, color, blur = false, shape = GuiBoxUtil.Shape.CIRCLE)
    }

    fun blurRect(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        _tint: Int,
    ) {
        boxes += GuiBoxUtil.Box(x, y, width, height, radius, 0, blur = true)
    }

    fun dropdownTile(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tint: Int,
    ) {
        blurRect(boxes, x, y, width, height, 10.0f, tint)
    }

    fun panel(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
    ) {
        blurRect(boxes, x, y, width, height, 18.0f, color)
    }

    fun moduleRow(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
    ) {
        blurRect(boxes, x, y, width, height, 10.0f, color)
    }

    fun dropdownGroup(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
    ) {
        blurRect(boxes, x, y, width, height, 14.0f, color)
    }

    fun range(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        thumbDiameter: Float,
        progress: Float,
        fillColor: Int,
        trackColor: Int,
    ) {
        val pct = progress.coerceIn(0.0f, 1.0f)
        val fillWidth = width * pct
        val thumbX = (x + fillWidth - thumbDiameter * 0.5f).coerceIn(x, x + width - thumbDiameter)
        val thumbY = y + height * 0.5f - thumbDiameter * 0.5f

        rect(boxes, x, y, width, height, PILL_RADIUS, trackColor)
        if (fillWidth > 0.0f) {
            rect(boxes, x, y, max(height, fillWidth), height, PILL_RADIUS, fillColor)
        }
        rect(boxes, thumbX, thumbY, thumbDiameter, thumbDiameter, PILL_RADIUS, fillColor)
    }

    fun switch(
        boxes: MutableList<GuiBoxUtil.Box>,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        knobDiameter: Float,
        checked: Boolean,
        uncheckedColor: Int,
        checkedColor: Int,
        knobColor: Int,
        knobShadow: Int,
    ) {
        val knobOffset = (height - knobDiameter) * 0.5f
        val knobX = if (checked) x + width - knobDiameter - knobOffset else x + knobOffset
        val knobY = y + knobOffset
        rect(boxes, x, y, width, height, PILL_RADIUS, if (checked) checkedColor else uncheckedColor)
        rect(boxes, knobX + 0.6f, knobY + 1.0f, knobDiameter, knobDiameter, PILL_RADIUS, knobShadow)
        rect(boxes, knobX, knobY, knobDiameter, knobDiameter, PILL_RADIUS, knobColor)
    }

}
