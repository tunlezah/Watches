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
)
