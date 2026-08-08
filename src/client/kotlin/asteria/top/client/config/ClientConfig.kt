package asteria.top.client.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import asteria.top.client.command.CommandManager
import asteria.top.client.module.ModuleManager
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.module.setting.IntSetting
import asteria.top.client.module.setting.MultiBooleanSetting
import asteria.top.client.module.setting.ModuleSetting
import asteria.top.client.gui.hud.HudManager
import asteria.top.client.gui.hud.NotificationManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

object ClientConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir: Path = Paths.get("C:/Asteria/configs")

    fun init() {
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
        }
    }

    private fun getPath(name: String): Path {
        val cleanName = if (name.endsWith(".nmss")) name else "$name.nmss"
        return configDir.resolve(cleanName)
    }

    fun load() {
        loadConfig("lastconfig")
    }

    fun save() {
        saveConfig("lastconfig")
    }

    fun resetConfig() {
        ModuleManager.modules.forEach { it.reset() }
        save()
    }

    fun listConfigs(): List<String> {
        if (!Files.exists(configDir)) return emptyList()
        return Files.list(configDir)
            .filter { it.fileName.toString().endsWith(".nmss") }
            .map { it.fileName.toString().removeSuffix(".nmss") }
            .toList()
    }

    fun loadConfig(name: String) {
        val path = getPath(name)
        if (!Files.isRegularFile(path)) return
        runCatching {
            val root = JsonParser.parseReader(Files.newBufferedReader(path)).asJsonObject
            root.getAsJsonObject("hud")?.let(HudManager::loadLayout)
            root.getAsJsonObject("notifications")?.let(NotificationManager::loadLayout)
            val modules = root.getAsJsonObject("modules") ?: return
            ModuleManager.modules.forEach { module ->
                val moduleJson = modules.getAsJsonObject(module.name) ?: return@forEach
                moduleJson.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean?.let(module::setEnabled)
                moduleJson.get("bind")?.takeIf { it.isJsonPrimitive }?.asInt?.let { module.bind = it }
                val settings = moduleJson.getAsJsonObject("settings") ?: return@forEach
                module.settings.forEach { setting ->
                    settings.get(setting.name)?.let { loadSetting(setting, it) }
                }
            }
        }.onFailure {
            CommandManager.printChat("§cFailed to load config '$name': ${it.message}")
        }
    }

    fun saveConfig(name: String) {
        val path = getPath(name)
        runCatching {
            Files.createDirectories(path.parent)
            Files.newBufferedWriter(path).use { writer ->
                gson.toJson(rootJson(), writer)
            }
        }.onFailure {
            CommandManager.printChat("§cFailed to save config '$name': ${it.message}")
        }
    }

    fun deleteConfig(name: String): Boolean {
        val path = getPath(name)
        if (Files.isRegularFile(path)) {
            return runCatching { Files.delete(path); true }.getOrDefault(false)
        }
        return false
    }

    private fun rootJson(): JsonObject {
        val root = JsonObject()
        val modules = JsonObject()
        ModuleManager.modules.forEach { module ->
            val moduleJson = JsonObject()
            moduleJson.addProperty("enabled", module.enabled)
            moduleJson.addProperty("bind", module.bind)
            val settings = JsonObject()
            module.settings.forEach { setting ->
                settings.add(setting.name, settingJson(setting))
            }
            moduleJson.add("settings", settings)
            modules.add(module.name, moduleJson)
        }
        root.add("modules", modules)
        root.add("hud", HudManager.saveLayout())
        root.add("notifications", NotificationManager.saveLayout())
        return root
    }

    private fun settingJson(setting: ModuleSetting<*>): JsonElement {
        return when (setting) {
            is BooleanSetting -> primitive(setting.value)
            is FloatSetting -> primitive(setting.value)
            is IntSetting -> primitive(setting.value)
            is EnumSetting<*> -> primitive(setting.value.name)
            is MultiBooleanSetting -> JsonObject().also { objectValue ->
                setting.value.forEach { option ->
                    objectValue.addProperty(option.name, option.value)
                }
            }
            else -> primitive(setting.displayValue())
        }
    }

    private fun loadSetting(setting: ModuleSetting<*>, element: JsonElement) {
        when (setting) {
            is BooleanSetting -> if (element.isJsonPrimitive) setting.setRaw(element.asBoolean)
            is FloatSetting -> if (element.isJsonPrimitive) setting.setRaw(element.asFloat)
            is IntSetting -> if (element.isJsonPrimitive) setting.setRaw(element.asInt)
            is EnumSetting<*> -> loadEnum(setting, element)
            is MultiBooleanSetting -> if (element.isJsonObject) {
                val objectValue = element.asJsonObject
                setting.value.forEach { option ->
                    objectValue.get(option.name)?.takeIf { it.isJsonPrimitive }?.asBoolean?.let(option::setRaw)
                }
            }
        }
    }

    private fun loadEnum(setting: EnumSetting<*>, element: JsonElement) {
        if (!element.isJsonPrimitive) return
        val name = element.asString
        val index = setting.values.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (index >= 0) setting.selectIndex(index)
    }

    private fun primitive(value: Boolean) = com.google.gson.JsonPrimitive(value)
    private fun primitive(value: Float) = com.google.gson.JsonPrimitive(value)
    private fun primitive(value: Int) = com.google.gson.JsonPrimitive(value)
    private fun primitive(value: String) = com.google.gson.JsonPrimitive(value)
}
