package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CombatInfrastructureContractTest {
    public static void main(String[] args) throws Exception {
        String targetManager = read("src/client/kotlin/asteria/top/client/util/combat/TargetManager.kt");
        String trapPlace = read("src/client/kotlin/asteria/top/client/util/combat/TrapPlaceUtil.kt");
        String inventory = read("src/client/kotlin/asteria/top/client/util/player/InventoryUtil.kt");
        String sync = read("src/client/kotlin/asteria/top/client/util/combat/CombatActionSync.kt");

        require(targetManager, "class TargetManager", "target manager should be a shared combat utility");
        require(targetManager, "fun searchTargets", "target manager should expose a target search phase");
        require(targetManager, "fun validateTarget", "target manager should retain or release targets through validation");

        require(trapPlace, "object TrapPlaceUtil", "trap placement helper should be shared infrastructure");
        require(trapPlace, "fun getPlacementHitResult", "trap placement should resolve support hit results");
        require(trapPlace, "fun place(", "trap placement should execute block placement");
        require(trapPlace, "OFFHAND_SLOT", "trap placement should support offhand placement");
        require(trapPlace, "OVERHEAD_SUPPORT_DISTANCE_PENALTY", "trap placement should avoid fragile overhead support faces when other supports exist");
        require(trapPlace, "support.side == Direction.DOWN", "trap placement should detect overhead support faces");
        require(trapPlace, "!state.isAir && state.isSolid", "trap placement should never treat air as solid support");
        require(trapPlace, "awaiting.containsKey(pos) && !state.canBeReplaced()", "awaiting support should only count after a real block appears");

        require(inventory, "object InventoryUtil", "inventory helper should be available outside modules");
        require(inventory, "fun swapToSlot", "inventory helper should support hotbar switching");
        require(inventory, "fun swapToOffhand", "inventory helper should support managed offhand movement");

        require(sync, "object CombatActionSync", "combat action sync should be shared infrastructure");
        require(sync, "fun canPlaceThisTick", "placement should be guarded against same-tick attack conflicts");
        require(sync, "fun markPlacement", "successful placements should update sync state");
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
}
