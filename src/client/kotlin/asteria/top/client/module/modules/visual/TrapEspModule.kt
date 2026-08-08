package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.gui.AsteriaOverlay
import asteria.top.client.render.AsteriaGuiRenderer
import asteria.top.client.render.FontRenderer
import asteria.top.client.util.GuiBoxUtil
import asteria.top.client.util.ProjectionUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.Locale

// Ported from Asteria12111's TrapESP.java: watches outgoing interact packets for trap/plast
// items, snapshots the blocks around the click point, and waits for the server to actually place
// a matching structure there before showing a timer for how much longer it'll last. Detection
// logic (packet parsing, block-change confirmation, duration rules) is a 1:1 port; the marker
// itself was replaced with the exact NameTags/Predictions template per request — an item-icon
// rect on the left, separated by a gap from a name + red countdown rect — instead of the
// original's animated card (blur/shadow/progress-ring/appear sweep), which had no equivalent
// renderer in this project to port faithfully.
class TrapEspModule : Module(
    name = "TrapESP",
    category = ModuleCategory.VISUALS,
    description = "Показывает оставшееся время действия трапок и пластов",
) {
    private val scale = setting(FloatSetting("Scale", 1.0f, 0.5f, 3.0f, 0.1f))
    private val backgroundAlpha = setting(FloatSetting("Background Alpha", 0.5f, 0.0f, 1.0f, 0.05f))

    companion object {
        private const val TRAP_DURATION_MS = 15_000L
        private const val LARGE_TRAP_DURATION_MS = 30_000L
        private const val PLAST_HORIZONTAL_DURATION_MS = 60_000L
        private const val PLAST_VERTICAL_DURATION_MS = 20_000L
        private const val PLAST_CONFIRM_STABLE_MS = 250L
        private const val PLAST_CONFIRM_TIMEOUT_MS = 2_500L
        private const val PLAST_SCAN_RADIUS = 6

        private const val TAG_RADIUS = 4.5f
        private const val TAG_VERTICAL_OFFSET = 3.0f
        private const val ICON_RECT_GAP = 1.5f
        private const val GROUND_OFFSET = 0.15
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val TIME_COLOR = 0xFFFF5555.toInt()

        @Volatile
        private var currentBlurBoxes: List<AsteriaOverlay.BlurBox> = emptyList()
    }

    private enum class MarkerType(val label: String, val isTrap: Boolean) {
        TRAP("Трапка", true),
        LARGE_TRAP("Трапка", true),
        PLAST_HORIZONTAL("Пласт", false),
        PLAST_VERTICAL("Пласт", false),
    }

    private class TimedMarker(
        val type: MarkerType,
        val pos: Vec3,
        val stack: ItemStack,
        val level: Level,
        val startedAtMs: Long,
        val durationMs: Long,
    ) {
        val expiresAtMs get() = startedAtMs + durationMs
    }

    private class PendingTrap(
        val anchor: BlockPos,
        val stack: ItemStack,
        val level: Level,
        val startedAtMs: Long,
        val baseline: Map<BlockPos, BlockState>,
    ) {
        val lastChangedBlocks = mutableSetOf<BlockPos>()
        var lastChangeAtMs = 0L
    }

    private class PendingPlast(
        val anchor: BlockPos,
        val side: Direction,
        val stack: ItemStack,
        val level: Level,
        val startedAtMs: Long,
        val baseline: Map<BlockPos, BlockState>,
    ) {
        val lastChangedBlocks = mutableSetOf<BlockPos>()
        var lastChangeAtMs = 0L
    }

    private val markers = mutableListOf<TimedMarker>()
    private val pendingTraps = mutableListOf<PendingTrap>()
    private val pendingPlasts = mutableListOf<PendingPlast>()

    override fun onDisable() {
        markers.clear()
        pendingTraps.clear()
        pendingPlasts.clear()
    }

    // Called from ConnectionMixin for every outgoing packet.
    fun onOutgoingPacket(packet: Packet<*>) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        if (packet is ServerboundUseItemOnPacket) {
            val stack = player.getItemInHand(packet.hand)
            registerUse(stack, packet.hitResult)
            return
        }

        if (packet is ServerboundUseItemPacket) {
            val stack = player.getItemInHand(packet.hand)
            if (!isTrackedItem(stack)) return
            val target = mc.hitResult
            if (target is BlockHitResult) {
                registerUse(stack, target)
            } else {
                val pos = player.eyePosition.add(player.lookAngle.scale(3.0))
                registerUse(stack, pos, Direction.UP)
            }
        }
    }

    private fun registerUse(stack: ItemStack, hit: BlockHitResult) {
        val side = hit.direction
        if (stack.`is`(Items.DRIED_KELP)) {
            queuePlastConfirmation(stack, hit.blockPos.relative(side), side)
            return
        }
        if (isTrapItem(stack)) {
            queueTrapConfirmation(stack, hit.blockPos.relative(side))
        }
    }

    private fun registerUse(stack: ItemStack, pos: Vec3, side: Direction) {
        if (stack.`is`(Items.DRIED_KELP)) {
            // A plast is only registered after its blocks actually appear in the world.
            queuePlastConfirmation(stack, BlockPos.containing(pos), side)
            return
        }
        if (isTrapItem(stack)) {
            queueTrapConfirmation(stack, BlockPos.containing(pos))
        }
    }

    private fun queueTrapConfirmation(stack: ItemStack, anchor: BlockPos) {
        val level = Minecraft.getInstance().level ?: return
        val now = System.currentTimeMillis()
        for (pending in pendingTraps) {
            if (pending.level === level &&
                pending.anchor.distSqr(anchor) <= 9.0 &&
                now - pending.startedAtMs < PLAST_CONFIRM_TIMEOUT_MS
            ) {
                return
            }
        }

        val baseline = snapshotBlocks(level, anchor, PLAST_SCAN_RADIUS)
        pendingTraps += PendingTrap(anchor.immutable(), stack.copy(), level, now, baseline)
    }

    private fun queuePlastConfirmation(stack: ItemStack, anchor: BlockPos, side: Direction) {
        val level = Minecraft.getInstance().level ?: return
        val now = System.currentTimeMillis()
        for (pending in pendingPlasts) {
            if (pending.level === level &&
                pending.anchor.distSqr(anchor) <= 4.0 &&
                now - pending.startedAtMs < PLAST_CONFIRM_TIMEOUT_MS
            ) {
                return
            }
        }

        val baseline = snapshotBlocks(level, anchor, PLAST_SCAN_RADIUS)
        pendingPlasts += PendingPlast(anchor.immutable(), side, stack.copy(), level, now, baseline)
    }

    private fun snapshotBlocks(level: Level, anchor: BlockPos, radius: Int): Map<BlockPos, BlockState> {
        val baseline = mutableMapOf<BlockPos, BlockState>()
        val min = anchor.offset(-radius, -radius, -radius)
        val max = anchor.offset(radius, radius, radius)
        for (pos in BlockPos.betweenClosed(min, max)) {
            val immutable = pos.immutable()
            baseline[immutable] = level.getBlockState(immutable)
        }
        return baseline
    }

    // Called every client tick (see AsteriaClient.kt).
    fun tick() {
        val level = Minecraft.getInstance().level
        if (level == null || (pendingPlasts.isEmpty() && pendingTraps.isEmpty())) return

        val now = System.currentTimeMillis()
        val plastIterator = pendingPlasts.iterator()
        while (plastIterator.hasNext()) {
            val pending = plastIterator.next()
            if (pending.level !== level || now - pending.startedAtMs > PLAST_CONFIRM_TIMEOUT_MS) {
                plastIterator.remove()
                continue
            }

            val changedBlocks = findChangedBlocks(pending.level, pending.baseline)
            if (changedBlocks.isEmpty()) {
                pending.lastChangedBlocks.clear()
                pending.lastChangeAtMs = 0L
                continue
            }

            if (changedBlocks != pending.lastChangedBlocks) {
                pending.lastChangedBlocks.clear()
                pending.lastChangedBlocks += changedBlocks
                pending.lastChangeAtMs = now
                continue
            }

            if (pending.lastChangeAtMs > 0L && now - pending.lastChangeAtMs >= PLAST_CONFIRM_STABLE_MS) {
                confirmPlast(pending, changedBlocks)
                plastIterator.remove()
            }
        }

        processPendingTraps(level, now)
    }

    private fun processPendingTraps(level: Level, now: Long) {
        val iterator = pendingTraps.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (pending.level !== level || now - pending.startedAtMs > PLAST_CONFIRM_TIMEOUT_MS) {
                iterator.remove()
                continue
            }

            val changedBlocks = findChangedBlocks(pending.level, pending.baseline)
            if (changedBlocks.isEmpty()) {
                pending.lastChangedBlocks.clear()
                pending.lastChangeAtMs = 0L
                continue
            }

            if (changedBlocks != pending.lastChangedBlocks) {
                pending.lastChangedBlocks.clear()
                pending.lastChangedBlocks += changedBlocks
                pending.lastChangeAtMs = now
                continue
            }

            if (pending.lastChangeAtMs > 0L && now - pending.lastChangeAtMs >= PLAST_CONFIRM_STABLE_MS) {
                confirmTrap(pending, changedBlocks)
                iterator.remove()
            }
        }
    }

    private fun findChangedBlocks(level: Level, baseline: Map<BlockPos, BlockState>): Set<BlockPos> {
        val changed = mutableSetOf<BlockPos>()
        for ((pos, before) in baseline) {
            val current = level.getBlockState(pos)
            if (!current.isAir && current != before) {
                changed += pos
            }
        }
        return changed
    }

    private fun confirmTrap(pending: PendingTrap, changedBlocks: Set<BlockPos>) {
        val bounds = StructureBounds.of(changedBlocks) ?: return
        val center = bounds.center()
        val largeTrap = isLargeTrapItem(pending.stack) ||
            (bounds.sizeX() >= 5 && bounds.sizeY() >= 4 && bounds.sizeZ() >= 5 && changedBlocks.size >= 36)
        val type = if (largeTrap) MarkerType.LARGE_TRAP else MarkerType.TRAP
        val durationMs = if (largeTrap) LARGE_TRAP_DURATION_MS else TRAP_DURATION_MS

        markers.removeIf {
            it.level === pending.level && it.type.isTrap &&
                it.pos.distanceToSqr(center) <= 16.0 &&
                kotlin.math.abs(it.startedAtMs - pending.startedAtMs) <= PLAST_CONFIRM_TIMEOUT_MS
        }
        markers += TimedMarker(type, center, pending.stack, pending.level, System.currentTimeMillis(), durationMs)
    }

    private fun confirmPlast(pending: PendingPlast, changedBlocks: Set<BlockPos>) {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        for (pos in changedBlocks) {
            minX = minOf(minX, pos.x)
            minY = minOf(minY, pos.y)
            minZ = minOf(minZ, pos.z)
            maxX = maxOf(maxX, pos.x)
            maxY = maxOf(maxY, pos.y)
            maxZ = maxOf(maxZ, pos.z)
        }

        val sizeX = maxX - minX + 1
        val sizeY = maxY - minY + 1
        val sizeZ = maxZ - minZ + 1
        // Determine the real plane of the server-created structure. A horizontal
        // plast is thinnest along Y; a vertical one is thinnest along X or Z.
        val horizontal = sizeY <= minOf(sizeX, sizeZ)
        val type = if (horizontal) MarkerType.PLAST_HORIZONTAL else MarkerType.PLAST_VERTICAL
        val durationMs = if (horizontal) PLAST_HORIZONTAL_DURATION_MS else PLAST_VERTICAL_DURATION_MS
        val centerY = if (horizontal) maxY + 1.0 else (minY + maxY + 1.0) * 0.5
        val center = Vec3((minX + maxX + 1.0) * 0.5, centerY, (minZ + maxZ + 1.0) * 0.5)

        // A staged server update must still produce exactly one card.
        markers.removeIf {
            it.level === pending.level && !it.type.isTrap &&
                it.pos.distanceToSqr(center) <= 9.0 &&
                kotlin.math.abs(it.startedAtMs - pending.startedAtMs) <= PLAST_CONFIRM_TIMEOUT_MS
        }
        markers += TimedMarker(type, center, pending.stack, pending.level, System.currentTimeMillis(), durationMs)
    }

    private fun isTrackedItem(stack: ItemStack): Boolean {
        return stack.`is`(Items.DRIED_KELP) || isTrapItem(stack)
    }

    private fun isTrapItem(stack: ItemStack): Boolean {
        if (stack.`is`(Items.NETHERITE_SCRAP) || stack.`is`(Items.POPPED_CHORUS_FRUIT)) return true
        val name = stack.hoverName.string.lowercase(Locale.ROOT)
        return name.contains("трап") || name.contains("trap")
    }

    private fun isLargeTrapItem(stack: ItemStack): Boolean {
        if (stack.`is`(Items.POPPED_CHORUS_FRUIT)) return true
        val name = stack.hoverName.string.lowercase(Locale.ROOT)
        return name.contains("дракон") || name.contains("dragon") || name.contains("30")
    }

    // Called every render frame from GuiMixin, alongside NameTags/Predictions.
    fun onRenderWithEntities(graphics: GuiGraphicsExtractor, partialTick: Float) {
        val mc = Minecraft.getInstance()
        val level = mc.level
        if (!enabled || level == null) return

        val now = System.currentTimeMillis()
        markers.removeIf { it.level !== level || now >= it.expiresAtMs }
        if (markers.isEmpty()) {
            currentBlurBoxes = emptyList()
            return
        }

        val scaleVal = scale.value
        val bgAlpha = backgroundAlpha.value
        val window = mc.window
        val screenW = window.guiScaledWidth.toFloat()
        val screenH = window.guiScaledHeight.toFloat()
        val cameraPos = mc.gameRenderer.mainCamera.position()

        data class Marker(
            val distanceSq: Double,
            val stack: ItemStack,
            val name: String,
            val timeText: String,
            val tagX: Float, val tagY: Float, val tagWidth: Float, val tagHeight: Float,
            val textX: Float, val textY: Float, val nameWidth: Float, val fontSize: Float,
            val iconRectX: Float, val iconRectSize: Float,
        )

        val renderMarkers = mutableListOf<Marker>()
        for (timed in markers) {
            val anchor = ProjectionUtil.project(timed.pos.add(0.0, GROUND_OFFSET, 0.0)) ?: continue
            val distanceSq = timed.pos.distanceToSqr(cameraPos)

            val fontSize = 8.0f * scaleVal
            val gap = 2.0f * scaleVal
            val remainingSeconds = ((timed.expiresAtMs - now) / 1000.0f).coerceAtLeast(0.0f)
            val timeText = " ${"%.1f".format(remainingSeconds)}"

            val nameWidth = FontRenderer.width(FontRenderer.Face.SfMedium, timed.type.label, fontSize)
            val timeWidth = FontRenderer.width(FontRenderer.Face.SfMedium, timeText, fontSize)
            val tagWidth = nameWidth + timeWidth + gap * 2.0f
            val tagHeight = fontSize + gap * 2.0f

            val iconRectSize = tagHeight
            val iconGap = ICON_RECT_GAP * scaleVal
            val totalWidth = iconRectSize + iconGap + tagWidth

            val groupX = anchor.x - totalWidth / 2.0f
            val tagX = groupX + iconRectSize + iconGap
            val tagY = anchor.y - tagHeight - TAG_VERTICAL_OFFSET * scaleVal

            if (groupX + totalWidth < 0 || groupX > screenW || tagY + tagHeight < 0 || tagY > screenH) continue

            val textX = tagX + gap
            val textY = tagY + (tagHeight - fontSize) / 2.0f
            renderMarkers += Marker(distanceSq, timed.stack, timed.type.label, timeText, tagX, tagY, tagWidth, tagHeight, textX, textY, nameWidth, fontSize, groupX, iconRectSize)
        }

        val guiScale = window.guiScale.toFloat()
        val blurBoxes = mutableListOf<AsteriaOverlay.BlurBox>()
        for (marker in renderMarkers) {
            blurBoxes += AsteriaOverlay.BlurBox(marker.tagX * guiScale, marker.tagY * guiScale, marker.tagWidth * guiScale, marker.tagHeight * guiScale, TAG_RADIUS * scaleVal * guiScale, tintStrength = bgAlpha)
            blurBoxes += AsteriaOverlay.BlurBox(marker.iconRectX * guiScale, marker.tagY * guiScale, marker.iconRectSize * guiScale, marker.iconRectSize * guiScale, TAG_RADIUS * scaleVal * guiScale, tintStrength = bgAlpha)
        }
        currentBlurBoxes = blurBoxes

        for (marker in renderMarkers.sortedByDescending { it.distanceSq }) {
            if (!ModuleManager.postProcessing.enabled) {
                val boxes = mutableListOf<GuiBoxUtil.Box>()
                val bgColor = AsteriaGuiRenderer.alpha(0x000000, bgAlpha)
                AsteriaGuiRenderer.rect(boxes, marker.tagX, marker.tagY, marker.tagWidth, marker.tagHeight, TAG_RADIUS * scaleVal, bgColor)
                AsteriaGuiRenderer.rect(boxes, marker.iconRectX, marker.tagY, marker.iconRectSize, marker.iconRectSize, TAG_RADIUS * scaleVal, bgColor)
                boxes.forEach { GuiBoxUtil.draw(graphics, it) }
            }

            val iconPadding = (marker.iconRectSize - marker.fontSize) / 2.0f
            val pose = graphics.pose()
            pose.pushMatrix()
            pose.translate(marker.iconRectX + iconPadding, marker.tagY + iconPadding)
            pose.scale(marker.fontSize / 16.0f)
            graphics.item(marker.stack, 0, 0)
            pose.popMatrix()

            FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, marker.name, marker.textX, marker.textY, marker.fontSize, WHITE)
            FontRenderer.draw(graphics, FontRenderer.Face.SfMedium, marker.timeText, marker.textX + marker.nameWidth, marker.textY, marker.fontSize, TIME_COLOR)
        }
    }

    fun blurBoxes(guiScale: Float): List<AsteriaOverlay.BlurBox> {
        if (!enabled || !ModuleManager.postProcessing.enabled) return emptyList()
        return currentBlurBoxes
    }

    private class StructureBounds(
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
    ) {
        fun sizeX() = maxX - minX + 1
        fun sizeY() = maxY - minY + 1
        fun sizeZ() = maxZ - minZ + 1

        fun center(): Vec3 = Vec3((minX + maxX + 1.0) * 0.5, (minY + maxY + 1.0) * 0.5, (minZ + maxZ + 1.0) * 0.5)

        companion object {
            fun of(blocks: Set<BlockPos>): StructureBounds? {
                if (blocks.isEmpty()) return null
                var minX = Int.MAX_VALUE
                var minY = Int.MAX_VALUE
                var minZ = Int.MAX_VALUE
                var maxX = Int.MIN_VALUE
                var maxY = Int.MIN_VALUE
                var maxZ = Int.MIN_VALUE
                for (pos in blocks) {
                    minX = minOf(minX, pos.x)
                    minY = minOf(minY, pos.y)
                    minZ = minOf(minZ, pos.z)
                    maxX = maxOf(maxX, pos.x)
                    maxY = maxOf(maxY, pos.y)
                    maxZ = maxOf(maxZ, pos.z)
                }
                return StructureBounds(minX, minY, minZ, maxX, maxY, maxZ)
            }
        }
    }
}
