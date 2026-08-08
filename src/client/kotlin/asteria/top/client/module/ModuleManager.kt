package asteria.top.client.module

import asteria.top.client.module.modules.combat.BacktrackModule
import asteria.top.client.module.modules.combat.AutoTrapModule
import asteria.top.client.module.modules.combat.KillauraModule
import asteria.top.client.module.modules.movement.AntiWebModule
import asteria.top.client.module.modules.movement.MoveFixModule
import asteria.top.client.module.modules.movement.SpearLungeModule
import asteria.top.client.module.modules.movement.SpeedModule
import asteria.top.client.module.modules.movement.SprintModule
import asteria.top.client.module.modules.movement.WindHopModule
import asteria.top.client.module.modules.player.FakePlayerModule
import asteria.top.client.module.modules.player.FakeLagModule
import asteria.top.client.module.modules.visual.AmbienceModule
import asteria.top.client.module.modules.visual.ArrowsModule
import asteria.top.client.module.modules.visual.BlockOverlayModule
import asteria.top.client.module.modules.visual.HandShaderModule
import asteria.top.client.module.modules.visual.InterfaceModule
import asteria.top.client.module.modules.visual.ChamsModule
import asteria.top.client.module.modules.visual.NameTagsModule
import asteria.top.client.module.modules.visual.LiquidFogModule
import asteria.top.client.module.modules.visual.PostProcessingModule
import asteria.top.client.module.modules.visual.ParticlesModule
import asteria.top.client.module.modules.visual.PredictionsModule
import asteria.top.client.module.modules.visual.RotationViewModule
import asteria.top.client.module.modules.visual.TargetEspModule
import asteria.top.client.module.modules.visual.Test1Module
import asteria.top.client.module.modules.visual.TrajectoriesModule
import asteria.top.client.module.modules.visual.TrapEspModule
import asteria.top.client.module.modules.visual.ViewModelModule

object ModuleManager {
    val postProcessing = PostProcessingModule()
    val liquidFog = LiquidFogModule()
    val ambience = AmbienceModule()
    val arrows = ArrowsModule()
    val targetEsp = TargetEspModule()
    val rotationView = RotationViewModule()
    val nameTags = NameTagsModule()
    val predictions = PredictionsModule()
    val particles = ParticlesModule()
    val trajectories = TrajectoriesModule()
    val trapEsp = TrapEspModule()
    val killaura = KillauraModule()
    val autoTrap = AutoTrapModule()
    val backtrack = BacktrackModule()
    val moveFix = MoveFixModule()
    val antiWeb = AntiWebModule()
    val spearLunge = SpearLungeModule()
    val speed = SpeedModule()
    val sprint = SprintModule()
    val windHop = WindHopModule()
    val fakePlayer = FakePlayerModule()
    val fakeLag = FakeLagModule()
    val blockOverlay = BlockOverlayModule()
    val handShader = HandShaderModule()
    val interfaceModule = InterfaceModule()
    val chams = ChamsModule()
    val viewModel = ViewModelModule()
    val test1 = Test1Module()

    val modules: List<Module> = listOf(
        killaura,
        autoTrap,
        backtrack,
        moveFix,
        antiWeb,
        spearLunge,
        speed,
        sprint,
        windHop,
        fakePlayer,
        fakeLag,
        postProcessing,
        interfaceModule,
        targetEsp,
        ambience,
        arrows,
        nameTags,
        predictions,
        particles,
        trajectories,
        trapEsp,
        test1,
    )

    fun modulesIn(category: ModuleCategory): List<Module> {
        return modules.filter { it.category == category }
    }

    fun handleKey(key: Int): Boolean {
        var handled = false
        modules.forEach { module ->
            if (module.bind == key) {
                if (module.onBindPressed()) {
                    handled = true
                }
            }
        }
        return handled
    }
}
