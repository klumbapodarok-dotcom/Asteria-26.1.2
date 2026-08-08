package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.EnumSetting
import asteria.top.client.module.setting.FloatSetting
import asteria.top.client.module.setting.IntSetting
import net.minecraft.core.Holder
import net.minecraft.world.clock.WorldClock
import net.minecraft.world.clock.WorldClocks
import java.time.LocalTime

class AmbienceModule : Module(
    name = "Ambience",
    category = ModuleCategory.VISUALS,
    description = "Позволяет изменить атмосферу игры",
) {
    enum class TimeMode(val label: String) {
        NONE("Не менять"),
        DAWN("Рассвет"),
        MORNING("Утро"),
        DAY("День"),
        EVENING("Вечер"),
        SUNSET("Заход солнца"),
        NIGHT("Ночь"),
        REAL_TIME("Время из реальной жизни"),
    }

    enum class FogMode(val label: String) {
        NOTHING("Ничего не делать"),
        CLEAR("Очистить"),
        OVERRIDE("Переопределить"),
    }

    val timeMode = setting(EnumSetting("Время", TimeMode.entries.toTypedArray(), TimeMode.NONE) { it.label })
    val fogMode = setting(EnumSetting("Туман", FogMode.entries.toTypedArray(), FogMode.NOTHING) { it.label })
    val fogStart = setting(
        FloatSetting("Начало тумана", 0.5f, 0.1f, 1.5f, 0.1f).visibleWhen { fogMode.value == FogMode.OVERRIDE }
    )
    val fogEnd = setting(
        FloatSetting("Конец тумана", 1.0f, 0.1f, 1.5f, 0.1f).visibleWhen { fogMode.value == FogMode.OVERRIDE }
    )
    val fogColorEnabled = setting(BooleanSetting("Цвет тумана", false))
    val fogColorRed = setting(
        IntSetting("Красный", 200, 0, 255, 1).visibleWhen { fogColorEnabled.value }
    )
    val fogColorGreen = setting(
        IntSetting("Зелёный", 220, 0, 255, 1).visibleWhen { fogColorEnabled.value }
    )
    val fogColorBlue = setting(
        IntSetting("Синий", 255, 0, 255, 1).visibleWhen { fogColorEnabled.value }
    )

    fun forcedTimeTicks(clock: Holder<WorldClock>): Long? {
        if (!enabled || timeMode.value == TimeMode.NONE) return null
        if (!clock.`is`(WorldClocks.OVERWORLD)) return null
        return when (timeMode.value) {
            TimeMode.DAWN -> 23000L
            TimeMode.MORNING -> 1000L
            TimeMode.DAY -> 6000L
            TimeMode.EVENING -> 12000L
            TimeMode.SUNSET -> 13000L
            TimeMode.NIGHT -> 18000L
            TimeMode.REAL_TIME -> realWorldTimeTicks()
            TimeMode.NONE -> null
        }
    }

    private fun realWorldTimeTicks(): Long {
        val totalSeconds = LocalTime.now().toSecondOfDay()
        var offsetSeconds = (totalSeconds - 6 * 3600) % (24 * 3600)
        if (offsetSeconds < 0) offsetSeconds += 24 * 3600
        return ((offsetSeconds / 86400.0) * 24000).toLong()
    }
}
