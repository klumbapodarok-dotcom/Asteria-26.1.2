package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FakePlayerContractTest {
    public static void main(String[] args) throws Exception {
        String fakePlayer = read("src/client/kotlin/asteria/top/client/module/modules/player/FakePlayerModule.kt");
        String autoTrap = read("src/client/kotlin/asteria/top/client/module/modules/combat/AutoTrapModule.kt");
        String manager = read("src/client/kotlin/asteria/top/client/module/ModuleManager.kt");
        String client = read("src/client/kotlin/asteria/top/client/AsteriaClient.kt");

        require(fakePlayer, "class FakePlayerModule : Module(", "Fake Player module should exist");
        require(fakePlayer, "name = \"Fake Player\"", "Fake Player should expose the expected module name");
        require(fakePlayer, "category = ModuleCategory.PLAYER", "Fake Player should live in player category");
        require(fakePlayer, "RemotePlayer", "Fake Player should spawn a client-side remote player");
        require(fakePlayer, "fun tick()", "Fake Player should maintain the spawned clone on client ticks");
        require(fakePlayer, "override fun onEnable()", "Fake Player should spawn on enable");
        require(fakePlayer, "override fun onDisable()", "Fake Player should remove on disable");
        reject(fakePlayer, "Sync rotation", "Fake Player should not expose Sync rotation");
        reject(fakePlayer, "Sync inventory", "Fake Player should not expose Sync inventory");
        reject(fakePlayer, "syncRotation", "Fake Player should not keep sync rotation state");
        reject(fakePlayer, "syncInventory", "Fake Player should not keep sync inventory state");
        reject(fakePlayer, "setEnabled(false)", "Fake Player should not silently disable itself when spawn fails");

        reject(autoTrap, "Ignore Moving", "AutoTrap should not expose Ignore Moving");
        reject(autoTrap, "ignoreMoving", "AutoTrap should not keep ignore-moving state");
        reject(autoTrap, "deltaMovement.lengthSqr()", "AutoTrap should target players regardless of movement");

        require(manager, "import asteria.top.client.module.modules.player.FakePlayerModule", "ModuleManager should import FakePlayer");
        require(manager, "val fakePlayer = FakePlayerModule()", "ModuleManager should instantiate FakePlayer");
        require(manager, "fakePlayer,", "ModuleManager should register FakePlayer");
        require(client, "ModuleManager.fakePlayer.tick()", "AsteriaClient should tick FakePlayer");
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
