package asteria.top.client.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.AbstractClientPlayer
import kotlin.math.max

object PlayerHeadRenderer {
    fun draw(graphics: GuiGraphicsExtractor, x: Float, y: Float, size: Float) {
        val player = Minecraft.getInstance().player ?: return
        draw(graphics, player, x, y, size)
    }

    fun draw(graphics: GuiGraphicsExtractor, player: AbstractClientPlayer, x: Float, y: Float, size: Float) {
        draw(graphics, player, x, y, size, max(3.5f, size * 0.12f))
    }

    fun draw(
        graphics: GuiGraphicsExtractor,
        player: AbstractClientPlayer,
        x: Float,
        y: Float,
        size: Float,
        radius: Float,
        includeHatLayer: Boolean = false,
        color: Int = 0xFFFFFFFF.toInt(),
    ) {
        if (includeHatLayer) {
            drawHead(graphics, player, x, y, size, radius, color = color)
            return
        }
        val skin = player.skin.body().texturePath()
        RoundedTextureRenderer.skinRegion(graphics, skin, x, y, size, size, radius, 8.0f, 8.0f, color = color)
    }

    fun drawHead(
        graphics: GuiGraphicsExtractor,
        player: AbstractClientPlayer,
        x: Float,
        y: Float,
        size: Float,
        radius: Float,
        gap: Float = 0.0f,
        color: Int = 0xFFFFFFFF.toInt(),
    ) {
        val skin = player.skin.body().texturePath()
        val superGap = gap * 2.0f
        RoundedTextureRenderer.skinRegion(graphics, skin, x + gap, y + gap, size - superGap, size - superGap, radius, 8.0f, 8.0f, color = color)
        RoundedTextureRenderer.skinRegion(
            graphics,
            skin,
            x,
            y,
            size,
            size,
            radius,
            40.0f,
            8.0f,
            color = color,
        )
    }

    fun drawStableHead(
        graphics: GuiGraphicsExtractor,
        player: AbstractClientPlayer,
        x: Float,
        y: Float,
        size: Float,
        radius: Float,
    ) {
        val skin = player.skin.body().texturePath()
        RoundedTextureRenderer.skinRegion(graphics, skin, x, y, size, size, radius, 8.0f, 8.0f)
    }
}
