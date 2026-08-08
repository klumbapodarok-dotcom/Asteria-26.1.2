package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WindHopContractTest {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/movement/WindHopModule.kt"));
        String manager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/ModuleManager.kt"));
        String client = Files.readString(Path.of("src/client/kotlin/asteria/top/client/AsteriaClient.kt"));
        String lunge = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/movement/SpearLungeModule.kt"));
        String overlay = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/AsteriaOverlay.kt"));
        String localPlayerMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/LocalPlayerMixin.kt"));

        require(source, "class WindHopModule : Module(", "WindHop module should exist");
        require(source, "name = \"WindHop\"", "WindHop should expose a module name");
        require(source, "category = ModuleCategory.MOVEMENT", "WindHop should live in movement");
        require(source, "description = \"Looks straight down, uses a wind charge, then restores your view and slot.\"", "WindHop should explain pitch and slot restoration");
        require(source, "enum class Mode(val label: String)", "WindHop should expose an Auto/Bind mode enum");
        require(source, "AUTO(\"Auto\")", "WindHop should support Auto mode");
        require(source, "BIND(\"Bind\")", "WindHop should support Bind mode");
        require(source, "val mode = setting(EnumSetting(\"Mode\", Mode.entries.toTypedArray(), Mode.AUTO) { it.label })", "WindHop mode should default to Auto like SpearLunge");
        require(source, "private val autoJump = setting(BooleanSetting(\"Auto Jump\", false))", "WindHop should expose Auto Jump");
        require(source, "private val silentSwap = setting(BooleanSetting(\"Silent Swap\", false))", "WindHop should expose Silent Swap");
        require(source, "private var pendingRestoreSlot = -1", "WindHop should support delayed slot restore when Silent Swap is off");
        require(source, "private var forcedPacketYaw: Float? = null", "WindHop should store a forced packet yaw");
        require(source, "private var forcedPacketPitch: Float? = null", "WindHop should store a forced packet pitch");
        require(source, "override fun onBindPressed(): Boolean", "WindHop should handle keybind presses");
        require(source, "if (mode.value == Mode.BIND && !enabled)", "WindHop bind should arm Bind mode as a one-shot");
        require(source, "setEnabled(true)", "WindHop bind should arm the module");
        require(source, "if (mode.value == Mode.AUTO && !player.onGround()) return false", "WindHop Auto mode should only use wind charges on ground");
        require(source, "Items.WIND_CHARGE", "WindHop should find wind charges");
        require(source, "HotbarSlotUtil.selectSlot(windChargeSlot)", "WindHop should use shared swap logic");
        require(source, "HotbarSlotUtil.selectSlot(slot)", "WindHop should restore through shared swap logic");
        require(source, "val originalPitch = player.xRot", "WindHop should save current pitch");
        require(source, "player.xRot = 90.0f", "WindHop should pitch down before use");
        require(source, "mc.gameMode?.useItem(player, InteractionHand.MAIN_HAND)", "WindHop should use the wind charge through vanilla useItem");
        require(source, "if (autoJump.value && player.onGround()) player.jumpFromGround()", "WindHop Auto Jump should jump after each successful grounded use");
        require(source, "player.swing(InteractionHand.MAIN_HAND)", "WindHop should swing after successful use");
        require(source, "player.xRot = originalPitch", "WindHop should restore pitch after use");
        require(source, "if (!silentSwap.value && windChargeSlot != originalSlot && used)", "WindHop should delay restore only when Silent Swap is off");
        require(source, "if (restoreNow) restoreOriginalSlot(originalSlot)", "WindHop Silent Swap should restore in the same tick");
        require(source, "ModuleManager.moveFix.correctedYaw(player.yRot)", "WindHop should respect MoveFix yaw while forcing pitch");
        require(source, "forcedPacketYaw = windHopYaw", "WindHop should publish the wind-hop yaw to movement packets");
        require(source, "forcedPacketPitch = 90.0f", "WindHop should publish the wind-hop pitch to movement packets");
        require(source, "fun packetYaw(original: Float): Float", "WindHop should expose packet yaw override");
        require(source, "fun packetPitch(original: Float): Float", "WindHop should expose packet pitch override");

        require(manager, "import asteria.top.client.module.modules.movement.WindHopModule", "ModuleManager should import WindHop");
        require(manager, "val windHop = WindHopModule()", "ModuleManager should instantiate WindHop");
        require(manager, "windHop,", "ModuleManager should register WindHop");
        reject(manager, "SpearMotionModule", "SpearMotion should be removed from ModuleManager");
        reject(manager, "spearMotion", "SpearMotion instance should be removed from ModuleManager");

        require(client, "ModuleManager.windHop.tick()", "AsteriaClient should tick WindHop");
        reject(client, "spearMotion", "AsteriaClient should not tick SpearMotion");

        reject(lunge, "spearMotion", "SpearLunge should not reference removed SpearMotion");
        require(overlay, "\"silent swap\" -> \"Doesn't queue swap packets\"", "Silent Swap setting should explain packet behavior");
        require(overlay, "\"auto jump\" -> \"Jumps after using wind charge\"", "Auto Jump setting should explain wind charge jump behavior");
        require(overlay, "private val expandedModuleNames = mutableSetOf<String>()", "ClickGUI should allow multiple expanded modules");
        reject(overlay, "private var expandedModuleName", "ClickGUI should not use single-module expansion state");
        require(overlay, "else collapseExpandedModules()", "ClickGUI should collapse expanded modules when closing");
        require(overlay, "if (openingModule) expandedModuleNames.add(module.name) else expandedModuleNames.remove(module.name)", "ClickGUI right-click should toggle only the clicked module expansion");
        reject(overlay, "expandedModuleName?.takeIf", "ClickGUI should not close another module when opening one");
        require(overlay, "expandedOffsetBeforeIndex(index)", "ClickGUI module expansion should offset by module index");
        reject(overlay, "expandedOffsetBeforeRow", "ClickGUI module expansion should not offset whole rows");
        require(localPlayerMixin, "ModuleManager.windHop.packetYaw(ModuleManager.killaura.packetYaw(original))", "LocalPlayer packet yaw should include WindHop override");
        require(localPlayerMixin, "ModuleManager.windHop.packetPitch(ModuleManager.killaura.packetPitch(original))", "LocalPlayer packet pitch should include WindHop override");
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
