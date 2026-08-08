package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class KillauraSprintResetContractTest {
    public static void main(String[] args) throws Exception {
        String aura = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/combat/KillauraModule.kt"));

        require(aura, "private const val ATTACK_READY_COOLDOWN = 0.92f", "Aura should centralize its attack cooldown threshold");
        require(aura, "if (shouldPrepareSprintReset(player)) {\n            ModuleManager.sprint.suppressForCriticalHit(1)\n            return\n        }", "Aura should reset sprint one tick before the attack-ready tick");
        require(aura, "private fun shouldPrepareSprintReset(player: Player): Boolean", "Aura should isolate sprint-reset pre-timing");
        require(aura, "private fun attackCooldownReadyNextTick(player: Player): Boolean", "Aura should isolate next-tick cooldown prediction");
        require(aura, "SprintResetUtil.attackCooldownReadyNextTick(player, ATTACK_READY_COOLDOWN)", "Aura should use the shared sprint reset utility");
        require(aura, "player.getAttackStrengthScale(0.0f) < ATTACK_READY_COOLDOWN &&\n            player.getAttackStrengthScale(1.0f) >= ATTACK_READY_COOLDOWN", "Aura should suppress sprint when the next tick reaches the attack threshold");
        require(aura, "if (player.getAttackStrengthScale(0.0f) < ATTACK_READY_COOLDOWN) return false", "Aura's attack gate should use the shared cooldown threshold");
        reject(aura, "prepareSnapRotation", "Snap preparation should be removed from sprint reset");
        reject(aura, "AimMode.SNAP", "Snap rotation should be removed from sprint reset");
        reject(aura, "if (player.getAttackStrengthScale(0.0f) < 0.92f) return false", "Aura should not keep a separate hardcoded attack cooldown threshold");
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
