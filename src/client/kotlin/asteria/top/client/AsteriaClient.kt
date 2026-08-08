package asteria.top.client

import asteria.top.client.command.CommandManager
import asteria.top.client.config.ClientConfig
import asteria.top.client.module.ModuleManager
import asteria.top.client.render.HandShaderRenderer
import asteria.top.client.render.ChamsRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents

object AsteriaClient : ClientModInitializer {
	override fun onInitializeClient() {
		ClientConfig.init()
		ClientConfig.load()
		CommandManager.init()

		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.backtrack.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.autoTrap.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.killaura.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.antiWeb.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.spearLunge.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.speed.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.sprint.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.windHop.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.fakePlayer.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.fakeLag.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.trapEsp.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ModuleManager.particles.tick() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { HandShaderRenderer.updateUniform() })
		ClientTickEvents.START_CLIENT_TICK.register(ClientTickEvents.StartTick { ChamsRenderer.updateUniform() })

		ClientLifecycleEvents.CLIENT_STOPPING.register(ClientLifecycleEvents.ClientStopping { ClientConfig.save() })

		LevelRenderEvents.BEFORE_GIZMOS.register { context -> ModuleManager.fakeLag.renderGizmos() }
		LevelRenderEvents.BEFORE_GIZMOS.register { context -> ModuleManager.autoTrap.renderGizmos() }
		LevelRenderEvents.BEFORE_GIZMOS.register { context -> ModuleManager.backtrack.renderGizmos(context) }
		LevelRenderEvents.BEFORE_GIZMOS.register { context -> ModuleManager.predictions.renderGizmos() }
		LevelRenderEvents.BEFORE_GIZMOS.register { context -> ModuleManager.trajectories.renderGizmos(context) }
		LevelRenderEvents.BEFORE_GIZMOS.register { context -> ModuleManager.blockOverlay.onRender(context) }

		LevelRenderEvents.END_MAIN.register { context -> ModuleManager.targetEsp.renderGizmos(context) }
		LevelRenderEvents.END_MAIN.register { context -> ModuleManager.particles.render(context) }
		LevelRenderEvents.END_MAIN.register { context -> ModuleManager.liquidFog.render(context) }
	}
}
