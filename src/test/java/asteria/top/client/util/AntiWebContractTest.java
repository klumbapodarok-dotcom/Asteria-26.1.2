package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AntiWebContractTest {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/movement/AntiWebModule.kt"));
        String manager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/ModuleManager.kt"));
        String client = Files.readString(Path.of("src/client/kotlin/asteria/top/client/AsteriaClient.kt"));

        require(source, "class AntiWebModule : Module(", "AntiWeb module should exist");
        require(source, "name = \"AntiWeb\"", "AntiWeb should expose a module name");
        require(source, "category = ModuleCategory.MOVEMENT", "AntiWeb should live in movement");
        require(source, "description = \"Applies controlled motion while you are stuck in cobwebs.\"", "AntiWeb should explain its behavior");
        require(source, "FloatSetting(\"Y Motion\", 0.995f, 0.0f, 2.0f, 0.01f)", "AntiWeb should mirror Asteria Y motion defaults");
        require(source, "FloatSetting(\"XZ Motion\", 0.19175f, 0.0f, 1.0f, 0.005f)", "AntiWeb should mirror Asteria horizontal motion defaults");
        require(source, "fun tick()", "AntiWeb should be ticked by the client");
        require(source, "if (!enabled) return", "AntiWeb should only run while enabled");
        require(source, "if (!isInWeb(mc)) return", "AntiWeb should only apply inside cobwebs");
        require(source, "if (!hasMotionKeyDown(mc)) return", "AntiWeb should only apply motion while a movement key is pressed");
        require(source, "mc.options.keyJump.isDown", "AntiWeb should use the jump key for upward motion");
        require(source, "mc.options.keyShift.isDown", "AntiWeb should use the sneak key for downward motion");
        require(source, "mc.options.keyUp.isDown", "AntiWeb should use forward key input");
        require(source, "mc.options.keyDown.isDown", "AntiWeb should use backward key input");
        require(source, "mc.options.keyLeft.isDown", "AntiWeb should use left key input");
        require(source, "mc.options.keyRight.isDown", "AntiWeb should use right key input");
        require(source, "player.deltaMovement = Vec3(horizontal.x, verticalMotion(mc), horizontal.z)", "AntiWeb should replace web motion with configured motion");
        require(source, "Blocks.COBWEB", "AntiWeb should detect cobweb blocks");
        require(source, "player.boundingBox.intersects(AABB(pos))", "AntiWeb should detect actual player-box web collision");

        require(manager, "import asteria.top.client.module.modules.movement.AntiWebModule", "ModuleManager should import AntiWeb");
        require(manager, "val antiWeb = AntiWebModule()", "ModuleManager should instantiate AntiWeb");
        require(manager, "antiWeb,", "ModuleManager should register AntiWeb");
        require(client, "ModuleManager.antiWeb.tick()", "AsteriaClient should tick AntiWeb");
    }

    private static void require(String source, String token, String label) {
        if (!source.contains(token)) {
            throw new AssertionError(label + " is missing: " + token);
        }
    }
}
