package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.format.DateFormat
import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.format.WaterResistanceUnit
import io.github.tunlezah.watches.core.model.keyedOf
import io.github.tunlezah.watches.core.query.SortOrder
import io.github.tunlezah.watches.core.query.SortSpec
import io.github.tunlezah.watches.core.settings.Accent
import io.github.tunlezah.watches.core.settings.AppSettings
import io.github.tunlezah.watches.core.settings.CollectionLayout
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.core.settings.ThemeMode
import io.github.tunlezah.watches.data.db.SettingDao
import io.github.tunlezah.watches.data.db.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Settings live in the database so they travel with backups. */
class SettingsRepository(private val dao: SettingDao) {

    val settings: Flow<AppSettings> = dao.observeAll()
        .map { rows -> decode(rows.associate { it.key to it.value }) }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val before = current()
        val after = transform(before)
        val old = encode(before)
        for ((k, v) in encode(after)) if (old[k] != v) dao.put(SettingEntity(k, v))
    }

    private fun encode(s: AppSettings): Map<String, String> = mapOf(
        THEME to s.themeMode.key,
        DIRECTION to s.direction.key,
        ACCENT to s.accent.key,
        LAYOUT to s.layout.key,
        SORT to s.sort.order.key,
        SORT_REVERSED to s.sort.reversed.toString(),
        DATE_FORMAT to s.display.dateFormat.key,
        WR_UNIT to s.display.waterResistanceUnit.key,
        CURRENCY to s.display.defaultCurrency,
        DISABLED_SOURCES to s.disabledSources.sorted().joinToString(","),
    )

    private fun decode(m: Map<String, String>): AppSettings {
        val d = AppSettings()
        val direction = keyedOf<Direction>(m[DIRECTION]) ?: d.direction
        return AppSettings(
            themeMode = keyedOf<ThemeMode>(m[THEME]) ?: d.themeMode,
            direction = direction,
            accent = keyedOf<Accent>(m[ACCENT]) ?: Accent.defaultFor(direction),
            layout = keyedOf<CollectionLayout>(m[LAYOUT]) ?: d.layout,
            sort = SortSpec(keyedOf<SortOrder>(m[SORT]) ?: d.sort.order, m[SORT_REVERSED] == "true"),
            display = DisplayPrefs(
                dateFormat = keyedOf<DateFormat>(m[DATE_FORMAT]) ?: d.display.dateFormat,
                waterResistanceUnit = keyedOf<WaterResistanceUnit>(m[WR_UNIT]) ?: d.display.waterResistanceUnit,
                defaultCurrency = m[CURRENCY] ?: d.display.defaultCurrency,
            ),
            disabledSources = m[DISABLED_SOURCES].orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
        )
    }

    private companion object {
        const val THEME = "ui.theme"
        const val DIRECTION = "ui.direction"
        const val ACCENT = "ui.accent"
        const val LAYOUT = "collection.layout"
        const val SORT = "collection.sort"
        const val SORT_REVERSED = "collection.sort_reversed"
        const val DATE_FORMAT = "display.date_format"
        const val WR_UNIT = "display.water_resistance_unit"
        const val CURRENCY = "display.currency"
        const val DISABLED_SOURCES = "lookup.disabled_sources"
    }
}
