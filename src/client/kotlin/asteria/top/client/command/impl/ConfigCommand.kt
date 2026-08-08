package asteria.top.client.command.impl

import asteria.top.client.command.Command
import asteria.top.client.command.CommandManager
import asteria.top.client.config.ClientConfig

class ConfigCommand : Command("config", listOf("cfg")) {
    override fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            CommandManager.printChat("Usage: .config <save|load|delete> <name>")
            return
        }

        val action = args[0].lowercase()
        if (args.size < 2 && action != "list" && action != "reset") {
            CommandManager.printChat("Usage: .config $action <name>")
            return
        }

        when (action) {
            "save" -> {
                val name = args[1]
                ClientConfig.saveConfig(name)
                CommandManager.printChat("Saved config: §d$name.nmss")
            }
            "load" -> {
                val name = args[1]
                ClientConfig.loadConfig(name)
                CommandManager.printChat("Loaded config: §d$name.nmss")
            }
            "delete" -> {
                val name = args[1]
                if (ClientConfig.deleteConfig(name)) {
                    CommandManager.printChat("Deleted config: §d$name.nmss")
                } else {
                    CommandManager.printChat("§cCould not delete config: $name.nmss")
                }
            }
            "list" -> {
                val configs = ClientConfig.listConfigs()
                if (configs.isEmpty()) {
                    CommandManager.printChat("No configs found.")
                } else {
                    CommandManager.printChat("Configs: §d${configs.joinToString(", ")}")
                }
            }
            "reset" -> {
                ClientConfig.resetConfig()
                CommandManager.printChat("Reset all configurations to defaults.")
            }
            else -> {
                CommandManager.printChat("§cUnknown action: $action. Use save, load, delete, list, or reset.")
            }
        }
    }

    override fun suggest(args: Array<String>): List<String> {
        if (args.isEmpty()) return emptyList()

        if (args.size == 1) {
            val partial = args[0].lowercase()
            return listOf("save", "load", "delete", "list", "reset").filter { it.startsWith(partial) }
        }

        if (args.size == 2) {
            val action = args[0].lowercase()
            if (action == "load" || action == "delete") {
                val partial = args[1]
                return ClientConfig.listConfigs().filter { it.startsWith(partial, ignoreCase = true) }
            }
        }

        return emptyList()
    }
}
