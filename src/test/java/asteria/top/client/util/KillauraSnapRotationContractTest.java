package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class KillauraSnapRotationContractTest {
    public static void main(String[] args) throws Exception {
        String aura = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/combat/KillauraModule.kt"));

        require(aura, "fun packetYaw(playerYaw: Float): Float {\n        return if (hasPacketRotation()) CombatRotationManager.packetYaw(playerYaw) else playerYaw\n    }", "Aura yaw should override packets through the shared manager while active");
        require(aura, "fun packetPitch(playerPitch: Float): Float {\n        return if (hasPacketRotation()) CombatRotationManager.packetPitch(playerPitch) else playerPitch\n    }", "Aura pitch should override packets through the shared manager while active");
        require(aura, "return enabled && target != null && CombatRotationManager.hasRotation()", "Aura packet rotation should not be gated by a removed snap state");
        require(aura, "updateCombatRotation(aimPoint, locked)", "Aura should publish rotation continuously for remaining modes");
        reject(aura, "SNAP(\"Snap\")", "Snap rotation mode should be removed");
        reject(aura, "AimMode.SNAP", "Snap rotation branches should be removed");
        reject(aura, "snapRotationTicks", "Snap packet tick state should be removed");
        reject(aura, "snapAttackTarget", "Snap prepared target state should be removed");
        reject(aura, "snapAttackReady", "Snap attack readiness state should be removed");
        reject(aura, "snapActive", "Snap activity helper should be removed");
        reject(aura, "prepareSnapRotation", "Snap preparation helper should be removed");
        reject(aura, "finishSnapAttack", "Snap cleanup helper should be removed");
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
