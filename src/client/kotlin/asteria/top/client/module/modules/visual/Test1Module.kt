package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory

class Test1Module : Module(
    name = "Test1",
    category = ModuleCategory.VISUALS,
    description = "Temporarily enables the ClickGUI profile card.",
    enabledByDefault = false,
)
