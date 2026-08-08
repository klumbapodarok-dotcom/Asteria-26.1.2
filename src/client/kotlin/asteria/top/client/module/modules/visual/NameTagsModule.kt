package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.module.setting.MultiBooleanSetting
import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.render.FontRenderer
import asteria.top.client.render.AsteriaGuiRenderer
import asteria.top.client.util.GuiBoxUtil
import asteria.top.client.util.ProjectionUtil
import asteria.top.client.render.PlayerHeadRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.roundToInt

class NameTagsModule : Module(
    name = "Name Tags",
    category = ModuleCategory.VISUALS,
    description = "Renders custom nametags for entities",
    enabledByDefault = false,
) {
    private val targets = setting(
        MultiBooleanSetting(
            "Targets",
            BooleanSetting("Self", false),
            BooleanSetting("Players", true),
            BooleanSetting("Animals", false),
            BooleanSetting("Mobs", false),
            BooleanSetting("Villagers", false),
            BooleanSetting("Items", false),
        )
    )
    private val scale = setting(FloatSetting("Scale", 1.0f, 0.5f, 3.0f, 0.1f))
    private val health = setting(BooleanSetting("Health", true))
    private val backgroundAlpha = setting(FloatSetting("Background Alpha", 0.5f, 0.0f, 1.0f, 0.05f))
    private val armor = setting(BooleanSetting("Armor", false))
    private val enchants = setting(BooleanSetting("Enchantments", false).visibleWhen { armor.value })
    private val heldItems = setting(BooleanSetting("Held Items", false))
    private val potions = setting(BooleanSetting("Potions", false))

    companion object {
        private const val TAG_VERTICAL_OFFSET = 3.0f
        private const val TAG_WORLD_HEAD_OFFSET = 0.18f
        private const val TAG_RADIUS = 4.5f
        private const val NEARBY_RADIUS = 128.0
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val HEALTH_RED = 0xFFFF5555.toInt()
        private const val HEAD_RECT_GAP = 1.5f

        private val ROMAN = arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

        @Volatile
        private var currentBlurBoxes: List<AsteriaOverlay.BlurBox> = emptyList()
    }

    private data class TagEntry(
        val distanceSq: Double,
        val tagX: Float,
        val tagY: Float,
        val tagWidth: Float,
        val tagHeight: Float,
        val textX: Float, val textY: Float,
        val name: String,
        val healthText: String,
        val nameWidth: Float, val fontSize: Float,
        val livingEntity: LivingEntity?,
        val headPlayer: AbstractClientPlayer?,
        val headRectX: Float,
        val headRectSize: Float,
    )

    // Called every render frame from GuiMixin
    fun onRenderWithEntities(graphics: GuiGraphicsExtractor, partialTick: Float) {
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (!enabled || mc.level == null || player == null) return

        val scaleVal = scale.value
        val bgAlpha = backgroundAlpha.value
        val showHp = health.value

        val window = mc.window
        val screenW = window.guiScaledWidth.toFloat()
        val screenH = window.guiScaledHeight.toFloat()
        val cameraPos = mc.gameRenderer.mainCamera.position()
        // entitiesForRendering() tracks the level renderer's own frustum/occlusion cull, which
        // shifts every frame as the camera moves — that made tags flicker in and out while walking.
        // Querying by AABB instead gives a stable set independent of what the 3D pass decided to draw.
        val searchBox = player.boundingBox.inflate(NEARBY_RADIUS)
        val worldEntities = buildList {
            addAll(mc.level!!.players())
            mc.level!!.getEntities(player, searchBox) { it !is Player }.forEach { add(it) }
        }

        val textEntries = mutableListOf<TagEntry>()
        val blurBoxes = mutableListOf<AsteriaOverlay.BlurBox>()

        for (entity in worldEntities) {
            if (entity is ItemEntity) {
                if (!targets.enabled("Items")) continue
                val entry = buildItemEntry(entity, partialTick, cameraPos, scaleVal, screenW, screenH) ?: continue
                textEntries += entry
                blurBoxes += entryBlurBoxes(entry, window.guiScale.toFloat(), scaleVal, bgAlpha)
                continue
            }

            if (entity !is LivingEntity) continue
            if (!shouldRenderEntity(entity, mc)) continue

            // getPosition(partialTick) goes through the entity's InterpolationHandler, which
            // is what the local player uses for prediction/reconciliation smoothing — hand-rolled
            // xOld/x lerp doesn't track that and produced jittery movement, especially on self.
            val basePos = entity.getPosition(partialTick)
            val worldPos = Vec3(basePos.x, basePos.y + entity.bbHeight + TAG_WORLD_HEAD_OFFSET, basePos.z)

            val headAnchor = ProjectionUtil.project(worldPos) ?: continue

            val distanceSq = worldPos.distanceToSqr(cameraPos)
            val fontSize = 8.0f * scaleVal
            val gap = 2.0f * scaleVal

            val name = entity.name.string
            val healthStr = if (showHp) " ${formatHealth(entity)}" else ""

            val nameWidth = FontRenderer.width(FontRenderer.Face.SfMedium, name, fontSize)
            val healthWidth = FontRenderer.width(FontRenderer.Face.SfMedium, healthStr, fontSize)
            val textWidth = nameWidth + healthWidth
            val tagWidth = textWidth + gap * 2.0f
            val tagHeight = fontSize + gap * 2.0f

            // Player heads get their own separate rect to the left of the name/health rect,
            // sized to match it (same height/padding) and offset by a fixed gap.
            val headPlayer = entity as? AbstractClientPlayer
            val headRectSize = tagHeight
            val headGap = HEAD_RECT_GAP * scaleVal
            val totalWidth = if (headPlayer != null) headRectSize + headGap + tagWidth else tagWidth

            val groupX = headAnchor.x - totalWidth / 2.0f
            val tagX = if (headPlayer != null) groupX + headRectSize + headGap else groupX
            val tagY = headAnchor.y - tagHeight - TAG_VERTICAL_OFFSET * scaleVal
            val headRectX = groupX

            // Cull tags fully outside screen
            if (groupX + totalWidth < 0 || groupX > screenW || tagY + tagHeight < 0 || tagY > screenH) continue

            val textX = tagX + gap
            val textY = tagY + (tagHeight - fontSize) / 2.0f
            val entry = TagEntry(distanceSq, tagX, tagY, tagWidth, tagHeight, textX, textY, name, healthStr, nameWidth, fontSize, entity, headPlayer, headRectX, headRectSize)
            textEntries += entry
            blurBoxes += entryBlurBoxes(entry, window.guiScale.toFloat(), scaleVal, bgAlpha)
        }

        currentBlurBoxes = blurBoxes

        for (entry in textEntries.sortedByDescending { it.distanceSq }) {
            if (!ModuleManager.postProcessing.enabled) {
                val boxes = mutableListOf<GuiBoxUtil.Box>()
                val bgColor = AsteriaGuiRenderer.alpha(0x000000, bgAlpha)
                AsteriaGuiRenderer.rect(boxes, entry.tagX, entry.tagY, entry.tagWidth, entry.tagHeight, TAG_RADIUS * scaleVal, bgColor)
                if (entry.headPlayer != null) {
                    AsteriaGuiRenderer.rect(boxes, entry.headRectX, entry.tagY, entry.headRectSize, entry.headRectSize, TAG_RADIUS * scaleVal, bgColor)
                }
                boxes.forEach { GuiBoxUtil.draw(graphics, it) }
            }

            if (entry.headPlayer != null) {
                val headPadding = (entry.headRectSize - entry.fontSize) / 2.0f
                PlayerHeadRenderer.draw(graphics, entry.headPlayer, entry.headRectX + headPadding, entry.tagY + headPadding, entry.fontSize)
            }

            FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, entry.name, entry.textX, entry.textY, entry.fontSize, WHITE)
            if (showHp && entry.healthText.isNotEmpty()) {
                FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, entry.healthText, entry.textX + entry.nameWidth, entry.textY, entry.fontSize, HEALTH_RED)
            }

            val livingEntity = entry.livingEntity ?: continue
            renderExtras(graphics, livingEntity, entry.tagX + entry.tagWidth / 2.0f, entry.tagY + entry.tagHeight + 2.0f * scaleVal, entry.fontSize)
        }
    }

    private fun buildItemEntry(
        entity: ItemEntity,
        partialTick: Float,
        cameraPos: Vec3,
        scaleVal: Float,
        screenW: Float,
        screenH: Float,
    ): TagEntry? {
        val basePos = entity.getPosition(partialTick)
        val worldPos = Vec3(basePos.x, basePos.y + entity.bbHeight + TAG_WORLD_HEAD_OFFSET, basePos.z)

        val headAnchor = ProjectionUtil.project(worldPos) ?: return null
        val distanceSq = worldPos.distanceToSqr(cameraPos)
        val fontSize = 8.0f * scaleVal
        val gap = 2.0f * scaleVal

        val stack = entity.item
        val name = if (stack.count > 1) "${stack.hoverName.string} x${stack.count}" else stack.hoverName.string
        val nameWidth = FontRenderer.width(FontRenderer.Face.SfMedium, name, fontSize)
        val tagWidth = nameWidth + gap * 2.0f
        val tagHeight = fontSize + gap * 2.0f

        val tagX = headAnchor.x - tagWidth / 2.0f
        val tagY = headAnchor.y - tagHeight - TAG_VERTICAL_OFFSET * scaleVal
        if (tagX + tagWidth < 0 || tagX > screenW || tagY + tagHeight < 0 || tagY > screenH) return null

        val textX = tagX + gap
        val textY = tagY + (tagHeight - fontSize) / 2.0f
        return TagEntry(distanceSq, tagX, tagY, tagWidth, tagHeight, textX, textY, name, "", nameWidth, fontSize, null, null, 0.0f, 0.0f)
    }

    private fun entryBlurBoxes(entry: TagEntry, guiScale: Float, scaleVal: Float, bgAlpha: Float): List<AsteriaOverlay.BlurBox> {
        val boxes = mutableListOf(
            AsteriaOverlay.BlurBox(
                entry.tagX * guiScale,
                entry.tagY * guiScale,
                entry.tagWidth * guiScale,
                entry.tagHeight * guiScale,
                TAG_RADIUS * scaleVal * guiScale,
                tintStrength = bgAlpha,
            )
        )
        if (entry.headPlayer != null) {
            boxes += AsteriaOverlay.BlurBox(
                entry.headRectX * guiScale,
                entry.tagY * guiScale,
                entry.headRectSize * guiScale,
                entry.headRectSize * guiScale,
                TAG_RADIUS * scaleVal * guiScale,
                tintStrength = bgAlpha,
            )
        }
        return boxes
    }

    fun blurBoxes(guiScale: Float): List<AsteriaOverlay.BlurBox> {
        if (!enabled || !ModuleManager.postProcessing.enabled) return emptyList()
        return currentBlurBoxes
    }

    // Called from EntityRendererMixin so vanilla's own floating nametag doesn't
    // double up with (and z-fight/flicker against) our custom one.
    fun suppressesVanillaTag(entity: Entity): Boolean {
        if (!enabled || entity !is LivingEntity) return false
        return shouldRenderEntity(entity, Minecraft.getInstance())
    }

    private fun shouldRenderEntity(entity: LivingEntity, mc: Minecraft): Boolean {
        if (entity == mc.player) {
            return targets.enabled("Self") && !mc.options.cameraType.isFirstPerson
        }

        return when (entity) {
            is Player -> targets.enabled("Players")
            is Villager -> targets.enabled("Villagers")
            is Animal -> targets.enabled("Animals")
            is Enemy -> targets.enabled("Mobs")
            else -> false
        }
    }

    // Armor row, held-item names and active potion effects, stacked below the name/health tag.
    private fun renderExtras(graphics: GuiGraphicsExtractor, entity: LivingEntity, centerX: Float, startY: Float, fontSize: Float) {
        var y = startY
        if (armor.value) {
            y = renderArmorRow(graphics, entity, centerX, y)
        }
        if (heldItems.value) {
            y = renderHeldItems(graphics, entity, centerX, y, fontSize * 0.8f)
        }
        if (potions.value) {
            renderPotions(graphics, entity, centerX, y, fontSize * 0.75f)
        }
    }

    private fun renderArmorRow(graphics: GuiGraphicsExtractor, entity: LivingEntity, centerX: Float, y: Float): Float {
        val stacks = listOf(
            entity.getItemBySlot(EquipmentSlot.OFFHAND),
            entity.getItemBySlot(EquipmentSlot.FEET),
            entity.getItemBySlot(EquipmentSlot.LEGS),
            entity.getItemBySlot(EquipmentSlot.CHEST),
            entity.getItemBySlot(EquipmentSlot.HEAD),
            entity.getItemBySlot(EquipmentSlot.MAINHAND),
        ).filterNot { it.isEmpty }
        if (stacks.isEmpty()) return y

        val iconScale = scale.value * 0.5f
        val iconSize = 16.0f * iconScale
        val gap = 1.0f * scale.value
        val totalWidth = stacks.size * iconSize + (stacks.size - 1) * gap
        var x = centerX - totalWidth / 2.0f
        val enchantSize = iconSize * 0.4f
        var maxEnchantLines = 0

        val pose = graphics.pose()
        for (stack in stacks) {
            if (enchants.value && stack.isEnchanted) {
                maxEnchantLines = max(maxEnchantLines, renderEnchantLines(graphics, stack, x + iconSize / 2.0f, y, enchantSize))
            }

            pose.pushMatrix()
            pose.translate(x, y)
            pose.scale(iconScale)
            graphics.item(stack, 0, 0)
            pose.popMatrix()
            x += iconSize + gap
        }

        return y + iconSize + gap
    }

    private fun renderEnchantLines(graphics: GuiGraphicsExtractor, stack: ItemStack, iconCenterX: Float, iconTopY: Float, size: Float): Int {
        val entries = stack.enchantments.entrySet().toList()
        var lineY = iconTopY - size
        for (entry in entries) {
            val text = Enchantment.getFullname(entry.key, entry.intValue).string
            val width = FontRenderer.width(FontRenderer.Face.SfMedium, text, size)
            FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, text, iconCenterX - width / 2.0f, lineY, size, WHITE)
            lineY -= (size + 1.0f)
        }
        return entries.size
    }

    private fun renderHeldItems(graphics: GuiGraphicsExtractor, entity: LivingEntity, centerX: Float, y: Float, fontSize: Float): Float {
        var lineY = y
        val main = entity.mainHandItem
        val off = entity.offhandItem
        if (!main.isEmpty) lineY = renderItemNameLine(graphics, main, centerX, lineY, fontSize)
        if (!off.isEmpty) lineY = renderItemNameLine(graphics, off, centerX, lineY, fontSize)
        return lineY
    }

    private fun renderItemNameLine(graphics: GuiGraphicsExtractor, stack: ItemStack, centerX: Float, y: Float, fontSize: Float): Float {
        val text = stack.hoverName.string
        val width = FontRenderer.width(FontRenderer.Face.SfMedium, text, fontSize)
        FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, text, centerX - width / 2.0f, y, fontSize, WHITE)
        return y + fontSize + 1.5f
    }

    private fun renderPotions(graphics: GuiGraphicsExtractor, entity: LivingEntity, centerX: Float, y: Float, fontSize: Float) {
        val effects = entity.activeEffects.filter { it.duration > 20 }
        if (effects.isEmpty()) return

        var lineY = y
        for (effectInstance in effects) {
            val mobEffect = effectInstance.effect.value()
            val amplifier = effectInstance.amplifier + 1
            val amplifierLabel = if (amplifier in ROMAN.indices && amplifier > 1) " ${ROMAN[amplifier]}" else ""
            val text = "${mobEffect.displayName.string}$amplifierLabel ${formatDuration(effectInstance.duration)}"
            val color = (0xFF shl 24) or (mobEffect.color and 0xFFFFFF)

            val width = FontRenderer.width(FontRenderer.Face.SfMedium, text, fontSize)
            FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, text, centerX - width / 2.0f, lineY, fontSize, color)
            lineY += fontSize + 1.0f
        }
    }

    private fun formatDuration(ticks: Int): String {
        val totalSeconds = ticks / 20
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun formatHealth(entity: LivingEntity): String {
        val hp = max(0.0f, entity.health)
        val rounded = (hp * 10.0f).roundToInt() / 10.0f
        return if (kotlin.math.abs(rounded - rounded.roundToInt()) <= 0.05f) {
            rounded.roundToInt().toString()
        } else {
            rounded.toString()
        }
    }
}
