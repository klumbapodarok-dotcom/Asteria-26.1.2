package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory

class RotationViewModule : Module(
    name = "Rotation View",
    category = ModuleCategory.VISUALS,
    description = "Shows Aura packet rotations on the local player model.",
    enabledByDefault = false,
)
