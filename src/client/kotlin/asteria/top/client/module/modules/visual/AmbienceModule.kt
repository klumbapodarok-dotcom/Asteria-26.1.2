package asteria.top.client.module.modules.visual

import asteria.top.client.module.Module
import asteria.top.client.module.ModuleCategory
import asteria.top.client.module.setting.BooleanSetting
import asteria.top.client.module.setting.ColorSetting
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

    enum class FogColorMode(val label: String) {
        INTERFACE("Интерфейс"),
        CUSTOM("Свой"),
    }

    enum class SkyMode(val label: String, val shaderMode: Int) {
        NORMAL("Обычное", -1),
        AURORA("Aurora", 0),
        FOG_BLUR("FogBlur", 2),
        PLASMA("Plasma", 3),
        SAKURA("Sakura", 4),
        SUMMER("Summer", 5),
        BLACK_HOLE("BlackHole", 6),
        NEBULA("Nebula", 7),
    }

    enum class SkyColorMode(val label: String) {
        INTERFACE("Интерфейс"),
        CUSTOM("Свой"),
    }

    val timeMode = setting(EnumSetting("Время", TimeMode.entries.toTypedArray(), TimeMode.NONE) { it.label })
    val skyMode = setting(EnumSetting("Небо", SkyMode.entries.toTypedArray(), SkyMode.NORMAL) { it.label })
    val skyQuality = setting(
        IntSetting("Качество", 2, 1, 3, 1).visibleWhen { skyMode.value != SkyMode.NORMAL }
    )
    val showStars = setting(
        BooleanSetting("Звёзды", true).visibleWhen { skyMode.value != SkyMode.NORMAL }
    )
    val skyColorMode = setting(
        EnumSetting("Цвет неба", SkyColorMode.entries.toTypedArray(), SkyColorMode.INTERFACE) { it.label }
            .visibleWhen { skyMode.value != SkyMode.NORMAL }
    )
    val skyColor = setting(
        ColorSetting(
            "Пользовательский цвет неба",
            0x80FF80,
            Triple("Красный неба", "Зелёный неба", "Синий неба"),
        ).also {
            it.visibleWhen { skyMode.value != SkyMode.NORMAL && skyColorMode.value == SkyColorMode.CUSTOM }
        }
    )
    val fogMode = setting(EnumSetting("Туман", FogMode.entries.toTypedArray(), FogMode.NOTHING) { it.label })
    val fogStart = setting(
        FloatSetting("Начало тумана", 0.5f, 0.1f, 1.5f, 0.1f).visibleWhen { fogMode.value == FogMode.OVERRIDE }
    )
    val fogEnd = setting(
        FloatSetting("Конец тумана", 1.0f, 0.1f, 1.5f, 0.1f).visibleWhen { fogMode.value == FogMode.OVERRIDE }
    )
    val fogColorMode = setting(
        EnumSetting("Цвет", FogColorMode.entries.toTypedArray(), FogColorMode.INTERFACE) { it.label }
            .visibleWhen { fogMode.value == FogMode.OVERRIDE }
    )
    val fogColor = setting(
        ColorSetting("Пользовательский цвет тумана", 0x8073E1, Triple("Красный", "Зелёный", "Синий")).also {
            it.visibleWhen { fogMode.value == FogMode.OVERRIDE && fogColorMode.value == FogColorMode.CUSTOM }
        }
    )

    fun shouldRenderSky(): Boolean = enabled && skyMode.value != SkyMode.NORMAL

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
