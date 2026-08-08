package asteria.top.client.command

import asteria.top.client.command.impl.BindCommand
import asteria.top.client.command.impl.ConfigCommand
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object CommandManager {
    val commands = mutableListOf<Command>()

    fun init() {
        commands.add(BindCommand())
        commands.add(ConfigCommand())
    }

    fun handle(message: String): Boolean {
        if (!message.startsWith(".")) return false
        val parts = message.substring(1).split(" ").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return true

        val commandName = parts[0]
        val args = parts.drop(1).toTypedArray()

        val command = commands.find { it.name.equals(commandName, ignoreCase = true) || it.aliases.any { alias -> alias.equals(commandName, ignoreCase = true) } }
        if (command != null) {
            try {
                command.execute(args)
            } catch (e: Exception) {
                printChat("§cError executing command: ${e.message}")
            }
        } else {
            printChat("§cUnknown command: $commandName")
        }
        return true
    }

    fun getSuggestions(message: String): List<String> {
        if (!message.startsWith(".")) return emptyList()
        val withoutPrefix = message.substring(1)
        val parts = withoutPrefix.split(" ")
        val commandName = parts[0]

        if (parts.size == 1) {
            val suggestions = mutableListOf<String>()
            for (command in commands) {
                if (command.name.startsWith(commandName, ignoreCase = true)) {
                    suggestions.add("." + command.name)
                }
                for (alias in command.aliases) {
                    if (alias.length > 1 && alias.startsWith(commandName, ignoreCase = true)) {
                        suggestions.add(".$alias")
                    }
                }
            }
            return suggestions.sorted()
        }

        val command = commands.find { it.name.equals(commandName, ignoreCase = true) || it.aliases.any { alias -> alias.equals(commandName, ignoreCase = true) } }
        if (command != null) {
            val args = parts.drop(1).toTypedArray()
            val argSuggestions = command.suggest(args)
            
            val prefix = "." + parts.dropLast(1).joinToString(" ") + " "
            return argSuggestions.map { prefix + it }
        }

        return emptyList()
    }

    fun printChat(text: String) {
        val mc = Minecraft.getInstance()
        mc.player?.sendSystemMessage(Component.literal("§8[§a§lAsteria§8] §f$text"))
    }
}
