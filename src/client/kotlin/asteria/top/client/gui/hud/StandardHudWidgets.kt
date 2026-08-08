package asteria.top.client.gui.hud

import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.mixin.CooldownInstanceAccessor
import asteria.top.client.mixin.ItemCooldownsAccessor
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.MsdfIconRenderer
import asteria.top.client.render.RoundedTextureRenderer
import asteria.top.client.render.TextureRenderer
import asteria.top.client.util.AnimationUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil

private const val POTION_ROW_ENTER_ANIMATION_DURATION = 180L
private const val POTION_ROW_EXIT_ANIMATION_DURATION = 180L
private const val POTION_EMPTY_TEXT = "Нету активных зелей"
private const val POTION_EMPTY_TEXT_SIZE = 8.5f
private const val POTION_EMPTY_HORIZONTAL_PADDING = 14.0f
private const val POTION_EMPTY_HEIGHT = 20.0f

private const val COOLDOWN_ROW_ENTER_ANIMATION_DURATION = 180L
private const val COOLDOWN_ROW_EXIT_ANIMATION_DURATION = 180L
private const val COOLDOWN_EMPTY_TEXT = "Нету активных кулдаунов"
private const val COOLDOWN_EMPTY_TEXT_SIZE = 8.5f
private const val COOLDOWN_EMPTY_HORIZONTAL_PADDING = 14.0f
private const val COOLDOWN_EMPTY_HEIGHT = 20.0f
// Row metrics are kept identical to PotionsHudWidget's template (icon size/x, name x,
// row height/offset, content-width formula) so the two widgets read as one family.
private const val COOLDOWN_ROW_HEIGHT = 14.0f
private const val COOLDOWN_ICON_SIZE = 8.5f
private const val COOLDOWN_ICON_NATIVE_SIZE = 16.0f
private const val COOLDOWN_ICON_X = 8.5f
private const val COOLDOWN_NAME_X = 22.0f

class WatermarkHudWidget : HudWidget(
    id = "watermark",
    title = "Watermark",
    iconGlyph = "R",
    x = 7.0f,
    y = 7.0f,
    width = 178.0f,
    height = 20.0f,
) {
    private val avatar = Identifier.fromNamespaceAndPath("asteria", "textures/gui/zywo_avatar.png")
    private val fpsIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/monitoroutline.png")
    private val pingIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/layersoutline.png")
    private val tpsIcon = Identifier.fromNamespaceAndPath("asteria", "icons/msdf/funneloutline.png")

    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        return (enabled && ModuleManager.interfaceModule.watermark.value) || preview
    }

    override fun update(mc: Minecraft, preview: Boolean) {
        val textSize = 8.5f
        val iconSize = 10.0f
        val playerName = "Zywo"
        val fps = "${mc.fps} FPS"
        val ping = "${ping(mc)} PING"
        val tps = "20 TPS"
        bounds.width = 5.75f +
            12.75f + FontRenderer.width(FontRenderer.Face.SfRegular, playerName, textSize) + 4.5f +
            iconSize + 2.75f + FontRenderer.width(FontRenderer.Face.SfRegular, fps, textSize) + 4.5f +
            iconSize + 2.75f + FontRenderer.width(FontRenderer.Face.SfRegular, ping, textSize) + 4.5f +
            iconSize + 2.75f + FontRenderer.width(FontRenderer.Face.SfRegular, tps, textSize) +
            5.75f
        bounds.height = 20.0f
    }

    override fun blurBoxes(guiScale: Float, tintStrength: Float): List<AsteriaOverlay.BlurBox> {
        return listOf(
            AsteriaOverlay.BlurBox(
                bounds.x * guiScale,
                bounds.y * guiScale,
                bounds.width * guiScale,
                bounds.height * guiScale,
                7.75f * guiScale,
                0.65f,
            )
        )
    }

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val playerName = "Zywo"
        val fps = "${mc.fps} FPS"
        val ping = "${ping(mc)} PING"
        val tps = "20 TPS"

        // The translucent foreground keeps the CSS-like black tint while allowing
        // the post-processing blur to remain visible underneath it.
        if (!ModuleManager.postProcessing.enabled) {
            HudStyle.rect(graphics, bounds.x, bounds.y, bounds.width, bounds.height, 7.75f, 0xCC000000.toInt())
        }

        val iconSize = 10.0f
        val textSize = 8.5f
        val textY = bounds.y + 5.75f
        val iconY = bounds.y + 5.0f
        val accent = 0xFF91B7FF.toInt()
        var cursor = bounds.x + 5.75f

        RoundedTextureRenderer.draw(graphics, avatar, cursor, iconY, iconSize, iconSize, iconSize * 0.5f)
        cursor += 12.75f
        FontRenderer.draw(graphics, FontRenderer.Face.SfRegular, playerName, cursor, textY, textSize, HudStyle.TEXT)
        cursor += FontRenderer.width(FontRenderer.Face.SfRegular, playerName, textSize) + 4.5f

        cursor = drawMetric(graphics, fpsIcon, fps, cursor, iconY, textY, iconSize, textSize, accent)
        cursor = drawMetric(graphics, pingIcon, ping, cursor + 4.5f, iconY, textY, iconSize, textSize, accent)
        drawMetric(graphics, tpsIcon, tps, cursor + 4.5f, iconY, textY, iconSize, textSize, accent)
    }

    private fun drawMetric(
        graphics: GuiGraphicsExtractor,
        icon: Identifier,
        value: String,
        x: Float,
        iconY: Float,
        textY: Float,
        iconSize: Float,
        textSize: Float,
        color: Int,
    ): Float {
        MsdfIconRenderer.draw(graphics, icon, x, iconY, iconSize, iconSize, color)
        val textX = x + iconSize + 2.75f
        FontRenderer.draw(graphics, FontRenderer.Face.SfRegular, value, textX, textY, textSize, HudStyle.TEXT)
        return textX + FontRenderer.width(FontRenderer.Face.SfRegular, value, textSize)
    }

    private fun ping(mc: Minecraft): Int {
        val player = mc.player ?: return 0
        return mc.connection?.getPlayerInfo(player.uuid)?.latency?.coerceAtLeast(0) ?: 0
    }
}

class CoordinatesHudWidget : HudWidget(
    id = "coordinates",
    title = "Coordinates",
    iconGlyph = "H",
    x = 7.0f,
    y = 96.0f,
    width = 128.0f,
    height = 25.0f,
    enabledByDefault = false,
) {
    override fun update(mc: Minecraft, preview: Boolean) {
        val text = coordinates(mc)
        bounds.width = maxOf(128.0f, FontRenderer.width(FontRenderer.Face.SfRegular, text, 8.5f) + 38.0f)
    }

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        HudStyle.panel(graphics, bounds)
        HudStyle.icon(graphics, iconGlyph, bounds.x + 9.0f, bounds.y + 7.0f, 10.0f)
        FontRenderer.draw(
            graphics,
            FontRenderer.Face.SfRegular,
            coordinates(mc),
            bounds.x + 27.0f,
            bounds.y + 8.0f,
            8.5f,
            HudStyle.TEXT,
        )
    }

    private fun coordinates(mc: Minecraft): String {
        val position = mc.player?.blockPosition() ?: return "X 0  Y 0  Z 0"
        return "X ${position.x}  Y ${position.y}  Z ${position.z}"
    }
}

class PotionsHudWidget : HudWidget(
    id = "potions",
    title = "Potions",
    iconGlyph = "B",
    x = 142.0f,
    y = 39.0f,
    width = 64.0f,
    height = 20.0f,
) {
    private val rowAnimations = linkedMapOf<String, RowState>()
    private var renderRows = emptyList<RowState>()

    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        val hasRows = mc.player?.activeEffects?.isNotEmpty() == true
        val chatPreview = mc.screen is ChatScreen
        return (enabled && ModuleManager.interfaceModule.potions.value && (hasRows || chatPreview)) || preview
    }

    override fun update(mc: Minecraft, preview: Boolean) {
        renderRows = collectRows(effectRows(mc, preview))
        val rows = renderRows
        bounds.height = if (rows.isEmpty()) POTION_EMPTY_HEIGHT else 6.0f + rows.size.toFloat() * 14.0f
        val contentWidth = rows.maxOfOrNull {
            FontRenderer.width(FontRenderer.Face.SfRegular, it.name, 8.5f) +
                FontRenderer.width(FontRenderer.Face.SfRegular, it.duration, 8.5f) + 37.0f
        } ?: 0.0f
        bounds.width = if (rows.isEmpty()) {
            maxOf(
                64.0f,
                FontRenderer.width(FontRenderer.Face.SfRegular, POTION_EMPTY_TEXT, POTION_EMPTY_TEXT_SIZE) +
                    POTION_EMPTY_HORIZONTAL_PADDING,
            )
        } else {
            maxOf(64.0f, contentWidth)
        }
    }

    override fun blurBoxes(guiScale: Float, tintStrength: Float): List<AsteriaOverlay.BlurBox> {
        return listOf(
            AsteriaOverlay.BlurBox(
                bounds.x * guiScale,
                bounds.y * guiScale,
                bounds.width * guiScale,
                bounds.height * guiScale,
                7.75f * guiScale,
                0.65f,
            )
        )
    }

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val rows = renderRows
        val showEmptyState = preview || mc.screen is ChatScreen
        if (rows.isEmpty() && !showEmptyState) return

        if (!ModuleManager.postProcessing.enabled) {
            HudStyle.rect(
                graphics,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                7.75f,
                fadeColor(0xCC000000.toInt(), visibilityAlpha),
            )
        }
        if (rows.isEmpty()) {
            FontRenderer.drawCentered(
                graphics,
                FontRenderer.Face.SfRegular,
                POTION_EMPTY_TEXT,
                bounds.x + bounds.width * 0.5f,
                bounds.y + 5.75f,
                POTION_EMPTY_TEXT_SIZE,
                fadeColor(0xFF9A9A9A.toInt(), visibilityAlpha),
            )
            return
        }

        var rowOffset = 0.0f
        rows.forEach { row ->
            val rowAlpha = row.animation.value.coerceIn(0.0f, 1.0f) * visibilityAlpha
            if (rowAlpha <= 0.001f) return@forEach
            val rowY = bounds.y + 5.75f + rowOffset
            val text = fadeColor(HudStyle.TEXT, rowAlpha)
            TextureRenderer.draw(graphics, row.icon, bounds.x + 8.5f, rowY + 0.5f, 8.5f, 8.5f, fadeColor(0xFFFFFFFF.toInt(), rowAlpha))
            FontRenderer.draw(graphics, FontRenderer.Face.SfRegular, row.name, bounds.x + 22.0f, rowY, 8.5f, text)
            val durationWidth = FontRenderer.width(FontRenderer.Face.SfRegular, row.duration, 8.5f)
            FontRenderer.draw(
                graphics,
                FontRenderer.Face.SfRegular,
                row.duration,
                bounds.x + bounds.width - durationWidth - 7.0f,
                rowY,
                8.5f,
                text,
            )
            rowOffset += 14.0f
        }
    }

    private fun effectRows(mc: Minecraft, preview: Boolean): List<EffectRow> {
        if (!enabled && !preview) return emptyList()
        val effects = mc.player?.activeEffects
            ?.sortedBy { Component.translatable(it.descriptionId).string.lowercase() }
            ?.map {
                val level = if (it.amplifier > 0) " ${it.amplifier + 1}" else ""
                EffectRow(
                    Component.translatable(it.descriptionId).string + level,
                    formatDuration(it.duration),
                    potionIcon(it.descriptionId),
                )
            }
            .orEmpty()
        return effects
    }

    private fun collectRows(activeRows: List<EffectRow>): List<RowState> {
        val activeRowsByKey = activeRows.associateBy { rowKey(it) }
        activeRows.forEach { row ->
            val state = rowAnimations.computeIfAbsent(rowKey(row)) { RowState(row.name, row.duration) }
            state.name = row.name
            state.duration = row.duration
            state.icon = row.icon
        }
        val rows = mutableListOf<RowState>()
        val iterator = rowAnimations.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val row = entry.value
            val active = activeRowsByKey.containsKey(entry.key)
            row.animation.update()
            row.animation.run(
                if (active) 1.0f else 0.0f,
                if (active) POTION_ROW_ENTER_ANIMATION_DURATION else POTION_ROW_EXIT_ANIMATION_DURATION,
                { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) },
                true,
            )
            if (!active && row.animation.value <= 0.001f) iterator.remove() else rows += row
        }
        return rows.sortedBy { row ->
            activeRows.indexOfFirst { rowKey(it) == rowKey(row.name) }.let {
                if (it < 0) Int.MAX_VALUE else it
            }
        }
        /*
        val activeRowsByKey = activeRows.associateBy { rowKey(it) }
        activeRows.forEach { row ->
            val state = rowAnimations.computeIfAbsent(rowKey(row)) { RowState(row.name, row.duration) }
            state.name = row.name
            state.duration = row.duration
            state.icon = row.icon
        }

        val rows = mutableListOf<RowState>()
        val iterator = rowAnimations.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val row = entry.value
            val active = activeRowsByKey.containsKey(entry.key)
            row.animation.update()
            row.animation.run(if (active) 1.0f else 0.0f,
                if (active) POTION_ROW_ENTER_ANIMATION_DURATION else POTION_ROW_EXIT_ANIMATION_DURATION,
                if (active) AnimationUtil::easeOutSoftBack else AnimationUtil::easeInBack,
                true,
            )
            if (!active && row.animation.value <= 0.01f) iterator.remove()
            else rows += row
        }

        return rows.sortedBy { row ->
            activeRows.indexOfFirst { rowKey(it) == rowKey(row.name) }.let {
                if (it < 0) Int.MAX_VALUE else it
            }
        }
        */
    }

    private fun rowKey(row: EffectRow): String {
        return rowKey(row.name)
    }

    private fun rowKey(name: String): String {
        return name
    }

    private fun formatDuration(ticks: Int): String {
        if (ticks < 0) return "∞"
        val seconds = ceil(ticks / 20.0).toInt()
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    private fun potionIcon(descriptionId: String): Identifier {
        val path = descriptionId.substringAfterLast('.')
        return Identifier.fromNamespaceAndPath("minecraft", "textures/mob_effect/$path.png")
    }

    private data class EffectRow(val name: String, val duration: String, val icon: Identifier)

    private class RowState(
        var name: String,
        var duration: String,
        var icon: Identifier = Identifier.fromNamespaceAndPath("minecraft", "textures/mob_effect/speed.png"),
    ) {
        val animation = AnimationUtil.TimedAnimation(0.0f)
    }

    private fun fadeColor(color: Int, alphaMultiplier: Float): Int {
        val alpha = ((color ushr 24) * alphaMultiplier.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
}

// Same list-widget template as PotionsHudWidget: icon on the left, item name,
// then seconds remaining on the right. Live cooldown data isn't exposed by any
// public Minecraft API — ItemCooldowns only exposes isOnCooldown()/getCooldownPercent()
// for a single stack, not a list of what's currently cooling down or how much time is
// actually left — so ItemCooldownsAccessor/CooldownInstanceAccessor (mixin @Accessor
// interfaces) reach into its private cooldowns map and tickCount field directly.
class CooldownsHudWidget : HudWidget(
    id = "cooldowns",
    title = "Cooldowns",
    iconGlyph = "C",
    x = 142.0f,
    y = 64.0f,
    width = 84.0f,
    height = 20.0f,
) {
    private val rowAnimations = linkedMapOf<String, RowState>()
    private var renderRows = emptyList<RowState>()

    override fun visible(mc: Minecraft, preview: Boolean): Boolean {
        val hasRows = activeCooldowns(mc).isNotEmpty()
        val chatPreview = mc.screen is ChatScreen
        return (enabled && ModuleManager.interfaceModule.cooldowns.value && (hasRows || chatPreview)) || preview
    }

    override fun update(mc: Minecraft, preview: Boolean) {
        renderRows = collectRows(cooldownRows(mc, preview))
        val rows = renderRows
        bounds.height = if (rows.isEmpty()) COOLDOWN_EMPTY_HEIGHT else 6.0f + rows.size.toFloat() * COOLDOWN_ROW_HEIGHT
        val contentWidth = rows.maxOfOrNull {
            FontRenderer.width(FontRenderer.Face.SfRegular, it.name, 8.5f) +
                FontRenderer.width(FontRenderer.Face.SfRegular, it.duration, 8.5f) + 37.0f
        } ?: 0.0f
        bounds.width = if (rows.isEmpty()) {
            maxOf(
                84.0f,
                FontRenderer.width(FontRenderer.Face.SfRegular, COOLDOWN_EMPTY_TEXT, COOLDOWN_EMPTY_TEXT_SIZE) +
                    COOLDOWN_EMPTY_HORIZONTAL_PADDING,
            )
        } else {
            maxOf(84.0f, contentWidth)
        }
    }

    override fun blurBoxes(guiScale: Float, tintStrength: Float): List<AsteriaOverlay.BlurBox> {
        return listOf(
            AsteriaOverlay.BlurBox(
                bounds.x * guiScale,
                bounds.y * guiScale,
                bounds.width * guiScale,
                bounds.height * guiScale,
                7.75f * guiScale,
                0.65f,
            )
        )
    }

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val rows = renderRows
        val showEmptyState = preview || mc.screen is ChatScreen
        if (rows.isEmpty() && !showEmptyState) return

        if (!ModuleManager.postProcessing.enabled) {
            HudStyle.rect(
                graphics,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                7.75f,
                fadeColor(0xCC000000.toInt(), visibilityAlpha),
            )
        }
        if (rows.isEmpty()) {
            FontRenderer.drawCentered(
                graphics,
                FontRenderer.Face.SfRegular,
                COOLDOWN_EMPTY_TEXT,
                bounds.x + bounds.width * 0.5f,
                bounds.y + 5.75f,
                COOLDOWN_EMPTY_TEXT_SIZE,
                fadeColor(0xFF9A9A9A.toInt(), visibilityAlpha),
            )
            return
        }

        var rowOffset = 0.0f
        rows.forEach { row ->
            val rowAlpha = row.animation.value.coerceIn(0.0f, 1.0f) * visibilityAlpha
            if (rowAlpha <= 0.001f) return@forEach
            val rowY = bounds.y + 5.75f + rowOffset
            val text = fadeColor(HudStyle.TEXT, rowAlpha)
            if (!row.icon.isEmpty) {
                // graphics.item() only ever draws at its native 16x16 — scale it down
                // around the icon's top-left corner to match Potions' 8.5x8.5 icon.
                val iconScale = COOLDOWN_ICON_SIZE / COOLDOWN_ICON_NATIVE_SIZE
                graphics.pose().pushMatrix()
                graphics.pose().translate(bounds.x + COOLDOWN_ICON_X, rowY + 0.5f)
                graphics.pose().scale(iconScale, iconScale)
                graphics.item(row.icon, 0, 0)
                graphics.pose().popMatrix()
            }
            FontRenderer.draw(graphics, FontRenderer.Face.SfRegular, row.name, bounds.x + COOLDOWN_NAME_X, rowY, 8.5f, text)
            val durationWidth = FontRenderer.width(FontRenderer.Face.SfRegular, row.duration, 8.5f)
            FontRenderer.draw(
                graphics,
                FontRenderer.Face.SfRegular,
                row.duration,
                bounds.x + bounds.width - durationWidth - 7.0f,
                rowY,
                8.5f,
                text,
            )
            rowOffset += COOLDOWN_ROW_HEIGHT
        }
    }

    private fun activeCooldowns(mc: Minecraft): Map<Identifier, Any> {
        val accessor = mc.player?.cooldowns as? ItemCooldownsAccessor ?: return emptyMap()
        return accessor.getCooldownsMap()
    }

    private fun cooldownRows(mc: Minecraft, preview: Boolean): List<CooldownRow> {
        if (!enabled && !preview) return emptyList()
        val player = mc.player ?: return emptyList()
        val accessor = player.cooldowns as? ItemCooldownsAccessor ?: return emptyList()
        val tickCount = accessor.getTickCount()
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        return accessor.getCooldownsMap().entries
            .mapNotNull { (group, instanceObj) ->
                val instance = instanceObj as? CooldownInstanceAccessor ?: return@mapNotNull null
                val remainingTicks = instance.getEndTime() - tickCount - partialTick
                if (remainingTicks <= 0.0f) return@mapNotNull null
                val item = BuiltInRegistries.ITEM.getOptional(group).orElse(null) ?: return@mapNotNull null
                val stack = item.defaultInstance
                CooldownRow(group.toString(), item.getName(stack).string, formatDuration(remainingTicks), stack)
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun collectRows(activeRows: List<CooldownRow>): List<RowState> {
        val activeRowsByKey = activeRows.associateBy { it.key }
        activeRows.forEach { row ->
            val state = rowAnimations.computeIfAbsent(row.key) { RowState(row.key, row.name, row.duration, row.icon) }
            state.name = row.name
            state.duration = row.duration
            state.icon = row.icon
        }
        val rows = mutableListOf<RowState>()
        val iterator = rowAnimations.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val row = entry.value
            val active = activeRowsByKey.containsKey(entry.key)
            row.animation.update()
            row.animation.run(
                if (active) 1.0f else 0.0f,
                if (active) COOLDOWN_ROW_ENTER_ANIMATION_DURATION else COOLDOWN_ROW_EXIT_ANIMATION_DURATION,
                { value -> AnimationUtil.apply(AnimationUtil.Mode.FADE, value) },
                true,
            )
            if (!active && row.animation.value <= 0.001f) iterator.remove() else rows += row
        }
        return rows.sortedBy { row ->
            activeRows.indexOfFirst { it.key == row.key }.let { if (it < 0) Int.MAX_VALUE else it }
        }
    }

    private fun formatDuration(remainingTicks: Float): String {
        val seconds = ceil(remainingTicks / 20.0).toInt().coerceAtLeast(0)
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    private data class CooldownRow(val key: String, val name: String, val duration: String, val icon: ItemStack)

    private class RowState(
        val key: String,
        var name: String,
        var duration: String,
        var icon: ItemStack = ItemStack.EMPTY,
    ) {
        val animation = AnimationUtil.TimedAnimation(0.0f)
    }

    private fun fadeColor(color: Int, alphaMultiplier: Float): Int {
        val alpha = ((color ushr 24) * alphaMultiplier.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
}

class InventoryHudWidget : HudWidget(
    id = "inventory",
    title = "Inventory",
    iconGlyph = "L",
    x = 142.0f,
    y = 96.0f,
    width = 164.0f,
    height = 56.0f,
    enabledByDefault = false,
) {
    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val player = mc.player ?: return
        HudStyle.panel(graphics, bounds)
        val slotSize = 18.0f
        for (index in 0 until 27) {
            val column = index % 9
            val row = index / 9
            val slotX = bounds.x + 1.0f + column * slotSize
            val slotY = bounds.y + 1.0f + row * slotSize
            HudStyle.rect(graphics, slotX, slotY, 17.0f, 17.0f, 2.0f, 0x12000000)
            val stack = player.inventory.getItem(index + 9)
            if (!stack.isEmpty) {
                graphics.item(stack, slotX.toInt(), slotY.toInt())
                graphics.itemDecorations(mc.font, stack, slotX.toInt(), slotY.toInt())
            }
        }
    }
}

class HotbarHudWidget : HudWidget(
    id = "hotbar",
    title = "Hotbar",
    iconGlyph = "H",
    x = Float.NaN,
    y = Float.NaN,
    width = 184.0f,
    height = 24.0f,
    enabledByDefault = false,
    movable = false,
) {
    private var animatedSelectedSlot = -1.0f

    override fun render(graphics: GuiGraphicsExtractor, mc: Minecraft, preview: Boolean) {
        val player = mc.player ?: return
        HudStyle.panel(graphics, bounds)
        val selected = player.inventory.selectedSlot
        if (animatedSelectedSlot < 0.0f) {
            animatedSelectedSlot = selected.toFloat()
        } else {
            animatedSelectedSlot += (selected - animatedSelectedSlot) * 0.28f
        }
        val selectedSlotX = bounds.x + 2.0f + animatedSelectedSlot * 20.0f
        HudStyle.rect(graphics, selectedSlotX, bounds.y + 2.0f, 20.0f, 20.0f, 5.0f, 0x5988FF82)
        for (slot in 0 until 9) {
            val slotX = bounds.x + 2.0f + slot * 20.0f
            val stack = player.inventory.getItem(slot)
            if (!stack.isEmpty) {
                graphics.item(stack, (slotX + 2.0f).toInt(), (bounds.y + 4.0f).toInt())
                graphics.itemDecorations(mc.font, stack, (slotX + 2.0f).toInt(), (bounds.y + 4.0f).toInt())
            }
        }
    }
}
