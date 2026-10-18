package io.github.tunlezah.watches.core.settings

import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.model.Keyed
import io.github.tunlezah.watches.core.query.SortSpec

enum class ThemeMode(override val key: String, override val label: String) : Keyed {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
}

/**
 * The three candidate visual directions. All three ship until one is chosen,
 * so the choice can be made on the phone with real content.
 */
enum class Direction(override val key: String, override val label: String, val description: String) : Keyed {
    ATELIER("atelier", "Atelier", "Auction-catalogue: serif display, warm graphite"),
    INSTRUMENT("instrument", "Instrument", "Tool-watch data sheet: expanded grotesk, OLED black"),
    GALLERY("gallery", "Gallery", "Scandinavian minimal: light grotesk, soft cards"),
}

enum class Accent(override val key: String, override val label: String) : Keyed {
    CHAMPAGNE("champagne", "Champagne"),
    LUME("lume", "Lume"),
    ICE_BLUE("ice_blue", "Ice blue"),
    RACING_GREEN("racing_green", "Racing green"),
    BORDEAUX("bordeaux", "Bordeaux"),
    SUNBURST_BLUE("sunburst_blue", "Sunburst blue"),
    TROPICAL_AMBER("tropical_amber", "Tropical amber");

    companion object {
        fun defaultFor(direction: Direction) = when (direction) {
            Direction.ATELIER -> CHAMPAGNE
            Direction.INSTRUMENT -> LUME
            Direction.GALLERY -> SUNBURST_BLUE
        }
    }
}

enum class CollectionLayout(override val key: String, override val label: String) : Keyed {
    GRID("grid", "Grid"),
    LIST("list", "List"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val direction: Direction = Direction.ATELIER,
    val accent: Accent = Accent.CHAMPAGNE,
    val layout: CollectionLayout = CollectionLayout.GRID,
    val sort: SortSpec = SortSpec(),
    val display: DisplayPrefs = DisplayPrefs(),
    /** Lookup sources switched off in Settings. Stored as "off" so a new source starts on. */
    val disabledSources: Set<String> = emptySet(),
    val device: DeviceSettings = DeviceSettings(),
)

/** Settings that belong to this phone, not the collection: never written to a backup, never restored. */
data class DeviceSettings(
    val appLock: Boolean = false,
    /** A minute by default, so a trip to the file picker or camera doesn't ask to unlock again. */
    val lockAfter: LockAfter = LockAfter.ONE_MINUTE,
    val lastBackup: LastBackup? = null,
)

/** When the app locks again after leaving it. */
enum class LockAfter(override val key: String, override val label: String, val seconds: Int) : Keyed {
    IMMEDIATELY("0", "Immediately", 0),
    ONE_MINUTE("60", "After 1 minute", 60),
    FIVE_MINUTES("300", "After 5 minutes", 300),
    FIFTEEN_MINUTES("900", "After 15 minutes", 900),
}

/** The most recent backup made on this phone (or restored onto it). */
data class LastBackup(val at: kotlin.time.Instant, val bytes: Long, val watches: Int)
