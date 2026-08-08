package asteria.top.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NormalRotationMoveFixContractTest {
    public static void main(String[] args) throws Exception {
        String aura = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/combat/KillauraModule.kt"));
        require(aura, "SIMPLE(\"Simple\")", "simple aim mode");
        require(aura, "POLAR(\"Polar\")", "polar aim mode");
        reject(aura, "SNAP(\"Snap\")", "snap aim mode should be removed");
        require(aura, "EnumSetting(\"Rotation\", AimMode.entries.toTypedArray(), AimMode.SIMPLE)", "simple rotation default");
        require(aura, "FloatSetting(\"Distance\", 3.0f, 2.5f, 6.0f, 0.1f)", "distance setting should remain");
        require(aura, "FloatSetting(\"Pre distance\", 0.3f, 0.0f, 30.0f, 0.1f)", "pre distance max should remain 30 blocks");
        require(aura, "MultiBooleanSetting(\n            \"Targets\"", "target settings should remain");
        require(aura, "MultiBooleanSetting(\n            \"Options\",\n            BooleanSetting(\"Only crits\", true),\n            BooleanSetting(\"Smart crits\", true),\n            BooleanSetting(\"Raytrace\", true),\n        )", "aura options should contain crit toggles and raytrace");
        require(aura, "if (options.enabled(\"Raytrace\") && !raytraceValid(locked)) {\n            return\n        }", "raytrace should gate attacks");
        require(aura, "private fun raytraceValid(target: LivingEntity): Boolean", "raytrace helper should be isolated");
        reject(aura, "Shield break", "shield break option should be removed");
        reject(aura, "Always shield", "always shield option should be removed");
        reject(aura, "Ignore walls", "ignore walls option should be removed");
        reject(aura, "No attack if eat", "no attack while eating option should be removed");
        reject(aura, "SMOOTH(\"Smooth\")", "old smooth rotation should be removed");
        reject(aura, "MINEBLAZE(\"MineBlaze\")", "old mineblaze rotation should be removed");
        require(aura, "AimMode.SIMPLE -> spot(entity)", "simple should use closest hitbox point");
        require(aura, "AimMode.POLAR -> PolarRotationUtil.aimPoint(entity, currentRotation())", "polar should use the polar multipoint utility");
        require(aura, "CombatRotationManager.requestRotation(", "aura should use the shared combat rotation manager");
        require(aura, "maxYawSpeed = maxYawSpeed.value", "simple should use dynamic yaw step");
        require(aura, "maxPitchSpeed = maxPitchSpeed.value", "simple should use dynamic pitch step");
        require(aura, "updateCombatRotation(aimPoint, locked)", "aura should publish targeting data through the shared rotation manager");
        reject(aura, "snapRotationTicks", "snap packet tick state should be removed");
        reject(aura, "snapAttackTarget", "snap target state should be removed");
        reject(aura, "snapActive", "snap active helper should be removed");
        reject(aura, "finishSnapAttack", "snap cleanup helper should be removed");
        reject(aura, "prepareSnapRotation", "snap preparation helper should be removed");
        reject(aura, "snapAttackReady", "snap preparation state should be removed");
        reject(aura, "AimMode.SNAP", "snap branches should be removed");
        require(aura, "fun packetYaw(playerYaw: Float): Float", "aura should expose packet yaw override");
        require(aura, "fun hasPacketRotation(): Boolean", "aura should expose packet rotation activity");
        require(aura, "return enabled && target != null && CombatRotationManager.hasRotation()", "aura should report active packet rotation while targeting");
        require(aura, "return if (hasPacketRotation()) CombatRotationManager.packetYaw(playerYaw) else playerYaw", "aura should override packet yaw through the shared manager while active");
        require(aura, "fun packetPitch(playerPitch: Float): Float", "aura should expose packet pitch override");
        String rotationManager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/util/combat/CombatRotationManager.kt"));
        require(rotationManager, "return CombatRotation(Mth.wrapDegrees(yaw), pitch)", "rotation steps should never publish 360-degree yaw discontinuities");
        require(rotationManager, "val pitch = (currentPitch + pitchDelta.coerceIn(-maxPitch, maxPitch)).coerceIn(-89.0f, 89.0f)", "rotation pitch should clamp to valid view limits");
        require(rotationManager, "ModuleManager.moveFix.updateAuraRotation", "rotation manager should publish MoveFix integration");
        require(rotationManager, "fun applyRotationView", "rotation manager should publish Rotation View integration");
        reject(aura, "player.yRot = serverYaw", "aura should not force local client yaw");
        reject(aura, "player.xRot = serverPitch", "aura should not force local client pitch");
        reject(aura, "player.yHeadRot = serverYaw", "aura should not force local head yaw");
        reject(aura, "player.yBodyRot = serverYaw", "aura should not force local body yaw");

        String moveFix = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/movement/MoveFixModule.kt"));
        String buildGradle = Files.readString(Path.of("build.gradle.kts"));
        require(buildGradle, "sourceSets.named(\"client\")", "Gradle should configure the client Kotlin source set");
        require(buildGradle, "kotlin.srcDir(\"src/client/kotlin\")", "client Kotlin sources must compile into the client runtime classpath");

        reject(moveFix, "ADVANCED(\"Advanced\")", "MoveFix advanced mode should be removed");
        reject(moveFix, "BooleanSetting(\"Auto walk\", false)", "MoveFix auto walk option should be removed");
        reject(moveFix, "BooleanSetting(\"Targeting\"", "MoveFix targeting toggle should be removed");
        reject(moveFix, "targeting.value", "MoveFix should not gate Aura correction behind a dead targeting boolean");
        require(moveFix, "fun activeForAura(): Boolean", "MoveFix aura activation");
        reject(moveFix, "fun advanced(): Boolean", "MoveFix advanced helper should be removed");
        reject(moveFix, "fun shouldAutoWalkAdvanced(): Boolean", "MoveFix auto walk helper should be removed");
        require(moveFix, "fun freeCorrection(): Boolean", "MoveFix free correction mode");
        require(moveFix, "fun correctedYaw(playerYaw: Float): Float", "MoveFix corrected yaw");
        require(moveFix, "return Mth.wrapDegrees(if (activeForAura()) auraYaw else playerYaw)", "MoveFix should apply normalized Aura yaw during movement while input remapping preserves camera direction");
        require(moveFix, "private var movementCameraYaw: Float? = null", "MoveFix should remember camera yaw before Aura movement yaw is applied");
        require(moveFix, "fun beginMovementCorrection(playerYaw: Float): Float", "MoveFix should expose a movement-yaw entry point");
        require(moveFix, "fun endMovementCorrection()", "MoveFix should clear saved camera yaw after movement");
        require(moveFix, "fun correctedInput(", "MoveFix should expose corrected movement input");
        require(moveFix, "return when (mode.value)", "MoveFix should split Focus and Free paths explicitly");
        require(moveFix, "Mode.FOCUS ->", "Focus mode branch should exist");
        require(moveFix, "if (activeForAura() && forward == 1.0f && strafe == 0.0f)", "Focus MoveFix should use forward-only targeting correction");
        require(moveFix, "solveForTarget(targetYaw, auraAimPosition)", "Focus MoveFix should steer toward Aura aim point");
        require(moveFix, "Mode.FREE ->", "Free mode branch should exist");
        require(moveFix, "Mode.FREE -> correctFreeInput(playerYaw, targetYaw, forward, strafe)", "Free MoveFix should remap WASD to preserve camera-relative movement");
        require(moveFix, "val desired = movementDirection(movementCameraYaw ?: playerYaw, forward, strafe)", "Free MoveFix should use saved camera yaw as the desired movement direction");
        require(moveFix, "return resolveDirectionalInputForAngle(targetYaw, desired)", "Free MoveFix should solve WASD input against Aura yaw");
        require(moveFix, "fun auraTargetCenter(): Vec3?", "MoveFix should expose Aura target center");

        String inputMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/KeyboardInputMixin.kt"));
        require(inputMixin, "@Mixin(KeyboardInput::class)", "KeyboardInput mixin should target movement input");
        require(inputMixin, "@ModifyExpressionValue(", "KeyboardInput mixin should modify the constructed Input");
        require(inputMixin, "at = [At(value = \"NEW\", target = \"(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;\")]", "KeyboardInput mixin should hook the Input constructor");
        require(inputMixin, "ModuleManager.moveFix.correctedInput", "KeyboardInput mixin should use MoveFix corrected input");
        reject(inputMixin, "ClientInputAccessor", "KeyboardInput mixin should not patch stale ClientInput fields after tick");

        String mixins = Files.readString(Path.of("src/client/resources/asteria.client.mixins.json"));
        reject(mixins, "\"ClientInputAccessor\"", "ClientInput accessor should not be registered");
        require(mixins, "\"KeyboardInputMixin\"", "KeyboardInput mixin should be registered");
        require(mixins, "\"LivingEntityMoveFixMixin\"", "LivingEntity MoveFix hooks should be registered");
        require(mixins, "\"LocalPlayerMixin\"", "LocalPlayer rotation mixin should be registered");
        require(mixins, "\"LivingEntityRendererMixin\"", "rotation view renderer mixin should be registered");
        require(mixins, "\"RotationMovementMixin\"", "rotation movement yaw swap should be registered");

        String localPlayerMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/LocalPlayerMixin.kt"));
        require(localPlayerMixin, "@Mixin(LocalPlayer::class)", "LocalPlayer mixin should target vanilla movement packets");
        require(localPlayerMixin, "method = [\"sendPosition\", \"tick\"]", "LocalPlayer mixin should hook sendPosition and tick");
        require(localPlayerMixin, "private var glacierLastPacketYaw = Float.NaN", "LocalPlayer packet yaw should track the last emitted packet yaw");
        require(localPlayerMixin, "aura.packetYaw(original)", "LocalPlayer mixin should replace packet yaw");
        require(localPlayerMixin, "aura.hasPacketRotation() || ModuleManager.windHop.hasPacketRotation() || ModuleManager.autoTrap.hasPacketRotation()", "LocalPlayer packet yaw should only smooth active packet rotation overrides");
        require(localPlayerMixin, "ModuleManager.killaura.packetPitch(original)", "LocalPlayer mixin should replace packet pitch");
        require(localPlayerMixin, "return original", "LocalPlayer packet yaw should leave vanilla yaw untouched when no packet rotation override is active");
        require(localPlayerMixin, "baseYaw + Mth.wrapDegrees(desiredYaw - baseYaw)", "LocalPlayer packet yaw should emit the closest equivalent yaw instead of wrapping across 360 degrees");
        require(localPlayerMixin, "CombatRotationManager.applyRotationView(yaw)", "LocalPlayer mixin should sync visual head/body yaw through shared manager");

        String windHop = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/movement/WindHopModule.kt"));
        require(windHop, "fun hasPacketRotation(): Boolean", "WindHop should expose whether it is forcing packet rotation");
        require(windHop, "return forcedPacketYaw != null || forcedPacketPitch != null", "WindHop packet rotation activity should be explicit");
        require(windHop, "return forcedPacketYaw?.let { Mth.wrapDegrees(it) } ?: original", "inactive WindHop should not normalize vanilla player yaw");

        String livingMoveFixMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/LivingEntityMoveFixMixin.kt"));
        require(livingMoveFixMixin, "method = [\"jumpFromGround\"]", "LivingEntity MoveFix should patch jump yaw");
        require(livingMoveFixMixin, "method = [\"updateFallFlyingMovement\"]", "LivingEntity MoveFix should patch fall-flying movement");
        require(livingMoveFixMixin, "ModuleManager.moveFix.correctedYaw(original)", "jump yaw should use corrected Aura yaw");
        require(livingMoveFixMixin, "ModuleManager.killaura.packetPitch(original)", "fall-flying pitch should use Aura pitch");
        require(livingMoveFixMixin, "getLookAngle()Lnet/minecraft/world/phys/Vec3;", "fall-flying look vector should be corrected");
        require(livingMoveFixMixin, "Vec3.directionFromRotation(", "fall-flying vector should be rebuilt from Aura rotation");

        String rotationMovementMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/RotationMovementMixin.kt"));
        require(rotationMovementMixin, "@Mixin(LocalPlayer::class)", "rotation movement mixin should target LocalPlayer");
        require(rotationMovementMixin, "method = [\"aiStep\"]", "rotation movement mixin should hook tickMovement equivalent");
        require(rotationMovementMixin, "this as Any as LocalPlayer", "rotation movement mixin should use mixin-safe Kotlin cast");
        require(rotationMovementMixin, "glacierOriginalYaw = player.yRot", "rotation movement mixin should store client yaw");
        require(rotationMovementMixin, "player.yRot = ModuleManager.moveFix.beginMovementCorrection(player.yRot)", "rotation movement mixin should apply Aura yaw while saving camera yaw");
        require(rotationMovementMixin, "player.yRot = glacierOriginalYaw", "rotation movement mixin should restore client yaw");
        require(rotationMovementMixin, "ModuleManager.moveFix.endMovementCorrection()", "rotation movement mixin should clear saved camera yaw");

        String client = Files.readString(Path.of("src/client/kotlin/asteria/top/client/AsteriaClient.kt"));
        require(client, "ClientTickEvents.START_CLIENT_TICK.register", "Aura should publish rotation before movement input is built");
        reject(client, "ModuleManager.spearMotion.tick()", "removed SpearMotion should not be ticked");

        String moduleManager = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/ModuleManager.kt"));
        require(moduleManager, "val rotationView = RotationViewModule()", "rotation view module should be instantiated");
        require(moduleManager, "rotationView,", "rotation view module should be registered");

        String rotationView = Files.readString(Path.of("src/client/kotlin/asteria/top/client/module/modules/visual/RotationViewModule.kt"));
        require(rotationView, "name = \"Rotation View\"", "rotation view module name");
        require(rotationView, "category = ModuleCategory.VISUALS", "rotation view should be visual");

        String rendererMixin = Files.readString(Path.of("src/client/kotlin/asteria/top/client/mixin/LivingEntityRendererMixin.kt"));
        require(rendererMixin, "@Mixin(LivingEntityRenderer::class)", "renderer mixin should target living entity renderer");
        require(rendererMixin, "method = [\"extractRenderState\"]", "renderer mixin should hook render-state extraction");
        require(rendererMixin, "if (!ModuleManager.rotationView.enabled)", "renderer mixin should be gated by rotation view module");
        require(rendererMixin, "entity !== Minecraft.getInstance().player", "renderer mixin should only affect local player");
        require(rendererMixin, "if (!CombatRotationManager.hasRotation()) return", "renderer mixin should only project active combat rotations");
        reject(rendererMixin, "state.bodyRot = packetYaw", "renderer mixin should not fight vanilla body/head yaw extraction");
        reject(rendererMixin, "state.yRot = 0.0f", "renderer mixin should not force relative head yaw");
        require(rendererMixin, "state.xRot = CombatRotationManager.packetPitch(state.xRot)", "renderer mixin should show shared combat rotation pitch");

        String overlay = Files.readString(Path.of("src/client/kotlin/asteria/top/client/gui/AsteriaOverlay.kt"));
        require(overlay, "private var bindingModule: asteria.top.client.module.Module? = null", "overlay should track module waiting for bind");
        require(overlay, "bindingModule?.let { module ->", "overlay should capture the next key for binding");
        require(overlay, "module.bind = if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) -1 else key", "overlay should support clearing and setting binds");
        require(overlay, "Bind: ${bindName(module.bind)}", "overlay should display module bind");
        require(overlay, "bindingModule = module", "overlay should enter bind mode from module settings");
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
