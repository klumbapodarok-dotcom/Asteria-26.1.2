package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SpearLungeContractTest {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/movement/SpearLungeModule.kt"));
        String hotbarSlotUtil = Files.readString(Path.of("src/client/kotlin/asteria/top/client/util/HotbarSlotUtil.kt"));
        String gameModeAccessor = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/MultiPlayerGameModeAccessor.kt"));
        String mixins = Files.readString(Path.of("src/client/resources/asteria.client.mixins.json"));

        require(source, "description = \"Uses a Lunge spear and swaps back in the same tick when Silent Swap is enabled.\"", "SpearLunge description should explain Silent Swap same-tick restore");
        require(source, "DataComponents.PIERCING_WEAPON", "SpearLunge should use the vanilla piercing weapon component");
        require(source, "BooleanSetting(\"Silent Swap\", false)", "SpearLunge should expose a Silent Swap setting");
        require(source, "IntSetting(\"Packets\", 1, 0, 20, 1)", "SpearLunge should expose a 0-20 packet count slider");
        reject(source, "BooleanSetting(\"Slow\"", "SpearLunge should not expose the old Slow setting name");
        require(source, "private val silentSwap = setting(BooleanSetting(\"Silent Swap\", false))", "SpearLunge should name the setting Silent Swap");
        require(source, "private val packetCount = setting(IntSetting(\"Packets\", 1, 0, 20, 1))", "SpearLunge should own the packet count setting");
        reject(source, "private val slow", "SpearLunge should not keep the old slow setting field");
        require(source, "private var pendingRestoreSlot = -1", "SpearLunge should track delayed slot restoration when Silent Swap is off");
        require(source, "if (pendingRestoreSlot >= 0)", "SpearLunge should process delayed restoration before normal lunge logic");
        require(source, "repeat(packetCount.value)", "SpearLunge should send the configured number of piercing attack packets per swap");
        require(source, "piercingAttack(piercingWeapon)", "SpearLunge should execute the vanilla STAB packet path");
        require(source, "if (!silentSwap.value && spearSlot != originalSlot && attacked)", "SpearLunge should delay restoration only when Silent Swap is off after a real slot swap and successful attack");
        require(source, "pendingRestoreSlot = originalSlot", "SpearLunge should restore the original slot on the next tick when Silent Swap is off");
        require(source, "restoreOriginalSlot(originalSlot)", "SpearLunge Silent Swap should restore the original slot in the same tick");
        require(source, "HotbarSlotUtil.selectSlot(spearSlot)", "SpearLunge should use the shared hotbar slot switch helper");
        require(source, "HotbarSlotUtil.selectSlot(slot)", "SpearLunge should use the shared helper for slot restoration");
        reject(source, "ServerboundSetCarriedItemPacket", "SpearLunge should not send raw selected-slot packets");
        reject(source, "private fun selectHotbarSlot", "SpearLunge should not own slot switch packet logic");
        require(source, "stack.`is`(ItemTags.SPEARS) && hasLunge(stack, level.registryAccess())", "SpearLunge should only choose Lunge-enchanted spears");
        reject(source, "mc.gameMode?.attack(player, target)", "SpearLunge should not use normal entity attacks");
        reject(source, "EntityHitResult", "SpearLunge should not depend on crosshair entity attacks");
        reject(source, "attackPending", "SpearLunge should not delay the lunge to a later tick");

        require(hotbarSlotUtil, "object HotbarSlotUtil", "Hotbar slot helper should be centralized");
        require(hotbarSlotUtil, "private var lastSentSelectedSlot = -1", "Hotbar slot helper should track the last sent server slot");
        require(hotbarSlotUtil, "fun lastSentSelectedSlot(): Int", "Hotbar slot helper should expose tracked server slot for diagnostics");
        require(hotbarSlotUtil, "if (slot !in 0..8) return false", "Hotbar slot helper should reject invalid hotbar slots");
        require(hotbarSlotUtil, "if (currentSlot == slot)", "Hotbar slot helper should no-op when already on the requested slot");
        require(hotbarSlotUtil, "if (lastSentSelectedSlot == -1) lastSentSelectedSlot = slot", "Hotbar slot helper should seed tracker when already on the slot");
        require(hotbarSlotUtil, "syncVanillaCarriedIndex(slot)", "Hotbar slot helper should sync vanilla carried slot state");
        require(hotbarSlotUtil, "if (lastSentSelectedSlot == slot)", "Hotbar slot helper should skip duplicate server slot packets");
        require(hotbarSlotUtil, "if (updateClient) player.inventory.selectedSlot = slot", "Hotbar slot helper should optionally fix client slot without sending duplicates");
        require(hotbarSlotUtil, "player.connection.send(ServerboundSetCarriedItemPacket(slot))", "Hotbar slot helper should send selected-slot packets");
        require(hotbarSlotUtil, "lastSentSelectedSlot = slot", "Hotbar slot helper should update tracker after sending");
        require(hotbarSlotUtil, "private fun syncVanillaCarriedIndex(slot: Int)", "Hotbar slot helper should isolate vanilla carried slot sync");

        require(gameModeAccessor, "@Mixin(MultiPlayerGameMode::class)", "GameMode accessor should target vanilla game mode");
        require(gameModeAccessor, "@Accessor(\"carriedIndex\")", "GameMode accessor should expose carriedIndex");
        require(gameModeAccessor, "fun setCarriedIndex(carriedIndex: Int)", "GameMode accessor should provide carriedIndex setter");
        require(mixins, "\"MultiPlayerGameModeAccessor\"", "GameMode accessor should be registered as a client mixin");

        String client = Files.readString(Path.of("src/client/kotlin/asteria/top/client/AsteriaClient.kt"));
        require(client, "ModuleManager.spearLunge.tick()", "SpearLunge should be ticked by the client initializer");
        reject(client, "ModuleManager.spearReach", "SpearReach should not be ticked after removal");

        String manager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/ModuleManager.kt"));
        require(manager, "val spearLunge = SpearLungeModule()", "SpearLunge should be instantiated");
        require(manager, "spearLunge,", "SpearLunge should be registered in module list");
        reject(manager, "SpearReachModule", "SpearReach should not be imported or instantiated");
        reject(manager, "spearReach", "SpearReach should not be registered after removal");

        String mouse = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/MouseHandlerMixin.kt"));
        reject(mouse, "ModuleManager.spearReach", "Mouse input should not call removed SpearReach");
    }

    private static void require(String source, String token, String label) {
        if (!source.contains(token)) {
            throw new AssertionError(label + " is missing: " + token);
        }
    }

    private static void reject(String source, String token, String label) {
        if (source.contains(token)) {
            throw new AssertionError(label + " should not contain: " + token);
        }
    }
}
