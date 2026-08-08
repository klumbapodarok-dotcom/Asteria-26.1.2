package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AutoTrapAuraContractTest {
    public static void main(String[] args) throws Exception {
        String autoTrap = read("src/client/kotlin/asteria/top/client/module/modules/combat/AutoTrapModule.kt");
        String aura = read("src/client/kotlin/asteria/top/client/module/modules/combat/KillauraModule.kt");
        String rotationManager = read("src/client/kotlin/asteria/top/client/util/combat/CombatRotationManager.kt");
        String manager = read("src/client/kotlin/asteria/top/client/module/ModuleManager.kt");
        String client = read("src/client/kotlin/asteria/top/client/AsteriaClient.kt");
        String localPlayerMixin = read("src/client/kotlin/asteria/top/client/mixin/LocalPlayerMixin.kt");

        require(autoTrap, "class AutoTrapModule", "auto trap module should be ported");
        require(autoTrap, "TargetManager()", "auto trap should use shared target manager");
        require(autoTrap, "TrapPlaceUtil", "auto trap should use shared placement utility");
        require(autoTrap, "fun shouldOverrideAuraRotation", "auto trap should expose rotation priority to aura");
        require(autoTrap, "fun packetYaw", "auto trap should provide packet yaw while placing");
        require(autoTrap, "fun packetPitch", "auto trap should provide packet pitch while placing");
        require(autoTrap, "private val maxYawSpeed = setting(FloatSetting(\"Max yaw speed\", 180.0f, 1.0f, 180.0f, 1.0f))", "auto trap default rotation should expose Aura-style max yaw speed");
        require(autoTrap, "private val maxPitchSpeed = setting(FloatSetting(\"Max pitch speed\", 90.0f, 1.0f, 90.0f, 1.0f))", "auto trap max pitch speed should respect Minecraft pitch bounds");
        require(autoTrap, "CombatRotationManager.requestRotation(", "auto trap should use the shared combat rotation manager");
        require(autoTrap, "priority = 100", "auto trap should outrank aura rotation while placing");
        require(autoTrap, "CombatRotationManager.packetYaw(playerYaw)", "auto trap packet yaw should come from the shared manager");
        require(autoTrap, "CombatRotationManager.packetPitch(playerPitch)", "auto trap packet pitch should come from the shared manager");
        require(autoTrap, "thenByDescending { placementDistanceSq(player, it) }", "auto trap should place farthest blocks from the player first");
        require(autoTrap, "private fun placementDistanceSq(player: Player, pos: BlockPos): Double", "auto trap should centralize player-distance placement ordering");
        require(autoTrap, "BooleanSetting(\"Through Walls\", false)", "auto trap should expose through-walls behavior for raycast placement");
        require(autoTrap, "raycast.value && !throughWalls.value", "through-walls should bypass visible support candidate filtering");
        require(autoTrap, "if (throughWalls.value) return true", "through-walls should bypass final raycast validation");
        require(autoTrap, "allowAwaitingSupport = false", "auto trap should not use air or predicted awaiting blocks as placement support");
        require(autoTrap, "clearPendingPlacement(releaseRotation = false)", "auto trap should not snap rotation back for skipped placement candidates");
        require(autoTrap, "clearPendingPlacement(releaseRotation = true)", "auto trap should release rotation when the trap is complete or reset");
        require(autoTrap, "if (blockSlot == -1) {\n            clearPlacementState()", "auto trap should release rotation and pending state when trap blocks run out");
        require(autoTrap, "private fun shouldWaitForRotationPacket(): Boolean", "auto trap should centralize rotation-wait behavior");
        require(autoTrap, "placeDelay.value > 0 && pendingAge <= 1", "auto trap should place immediately after rotation when delay is zero");
        require(autoTrap, "applyPlacementRotationVariance(", "auto trap placement rotations should not reuse exact yaw/pitch values for different block packets");
        require(autoTrap, "PLACEMENT_ROTATION_VARIANCE_STEP", "auto trap should use bounded deterministic placement rotation variance");
        require(autoTrap, "hitResult.blockPos.x", "placement rotation variance should be tied to the clicked support block");
        require(autoTrap, "private val silentSwap = setting(BooleanSetting(\"Silent Swap\", false))", "auto trap should expose SpearLunge-style Silent Swap");
        require(autoTrap, "return if (silentSwap.value) TrapPlaceUtil.SwitchMode.SILENT else TrapPlaceUtil.SwitchMode.NORMAL", "auto trap Silent Swap should map directly to trap placement silent mode");
        reject(autoTrap, "Switch Mode", "auto trap should not expose the old switch-mode enum setting");
        reject(autoTrap, "BooleanSetting(\"Fast\"", "auto trap should not expose the old Fast setting");

        String trapPlace = read("src/client/kotlin/asteria/top/client/util/combat/TrapPlaceUtil.kt");
        require(trapPlace, "InventoryUtil.swapToSlot(slot)", "silent trap placement should select the block locally before using it");
        require(trapPlace, "InventoryUtil.swapToSlot(currentSlot)", "silent trap placement should restore the original slot in the same tick");
        reject(trapPlace, "InventoryUtil.swapToSlot(slot, updateClient = !silent)", "silent trap placement should not suppress local selected-slot updates before useItemOn");
        reject(trapPlace, "InventoryUtil.swapToSlot(currentSlot, updateClient = false)", "silent trap placement should restore like SpearLunge instead of leaving client slot state stale");

        require(aura, "TargetManager()", "aura should use shared target manager instead of ad hoc entity scans");
        require(aura, "CombatRotationManager.requestRotation(", "aura should use the shared combat rotation manager");
        require(aura, "priority = 50", "aura should request lower priority than trap placement");
        reject(aura, ".minByOrNull { spot(it).distanceTo(player.eyePosition) }", "aura should not directly own target selection ordering");

        require(rotationManager, "object CombatRotationManager", "shared combat rotation manager should exist");
        require(rotationManager, "fun requestRotation(", "shared combat rotation manager should accept module rotation requests");
        require(rotationManager, "private fun step(", "shared combat rotation manager should own Aura Simple-style stepping");
        require(rotationManager, "ModuleManager.moveFix.updateAuraRotation", "shared combat rotation manager should publish MoveFix state");
        require(rotationManager, "fun applyRotationView", "shared combat rotation manager should own Rotation View integration");

        require(manager, "val autoTrap = AutoTrapModule()", "module manager should register auto trap");
        require(manager, "autoTrap,", "auto trap should be present in the module list");
        require(client, "ModuleManager.autoTrap.tick()", "auto trap should tick on client update");
        require(localPlayerMixin, "ModuleManager.autoTrap.packetYaw", "packet yaw should include auto trap rotation override");
        require(localPlayerMixin, "ModuleManager.autoTrap.packetPitch", "packet pitch should include auto trap rotation override");
        require(localPlayerMixin, "CombatRotationManager.applyRotationView(yaw)", "packet yaw mixin should apply shared Rotation View integration");
    }

    private static String read(String path) throws Exception {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            throw new AssertionError("missing file: " + path);
        }
        return Files.readString(file);
    }

    private static void require(String source, String token, String label) {
        if (!source.contains(token)) {
            throw new AssertionError(label + " is missing: " + token);
        }
    }

    private static void reject(String source, String token, String label) {
        if (source.contains(token)) {
            throw new AssertionError(label + ": " + token);
        }
    }
}
