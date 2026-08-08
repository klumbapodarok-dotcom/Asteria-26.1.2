package asteria.top.client.command.impl

import asteria.top.client.command.Command
import asteria.top.client.command.CommandManager
import asteria.top.client.module.ModuleManager
import org.lwjgl.glfw.GLFW

class BindCommand : Command("bind", listOf("b")) {
    override fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            CommandManager.printChat("Usage: .bind <module> <key> or .bind clear")
            return
        }

        val moduleName = args[0]

        if (moduleName.equals("clear", ignoreCase = true)) {
            ModuleManager.modules.forEach { it.bind = -1 }
            CommandManager.printChat("Cleared all binds.")
            asteria.top.client.config.ClientConfig.save()
            return
        }

        if (args.size < 2) {
            CommandManager.printChat("Usage: .bind <module> <key> or .bind clear")
            return
        }

        val keyName = args[1].uppercase()

        val module = ModuleManager.modules.find { it.name.equals(moduleName, ignoreCase = true) }
        if (module == null) {
            CommandManager.printChat("§cModule not found: $moduleName")
            return
        }

        if (keyName == "NONE" || keyName == "DELETE" || keyName == "UNBIND") {
            module.bind = GLFW.GLFW_KEY_UNKNOWN
            CommandManager.printChat("Unbound §d${module.name}")
            asteria.top.client.config.ClientConfig.save()
            return
        }

        val keyCode = try {
            val field = GLFW::class.java.getField("GLFW_KEY_$keyName")
            field.getInt(null)
        } catch (e: Exception) {
            GLFW.GLFW_KEY_UNKNOWN
        }

        if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
            module.bind = keyCode
            CommandManager.printChat("Bound §d${module.name}§f to §d$keyName")
            asteria.top.client.config.ClientConfig.save()
        } else {
            CommandManager.printChat("§cUnknown key: $keyName")
        }
    }

    override fun suggest(args: Array<String>): List<String> {
        if (args.isEmpty()) return emptyList()

        if (args.size == 1) {
            val partial = args[0]
            val list = ModuleManager.modules.map { it.name }.toMutableList()
            list.add("clear")
            return list.filter { it.startsWith(partial, ignoreCase = true) }.sorted()
        }

        if (args.size == 2) {
            return emptyList()
        }

        return emptyList()
    }
}
