package io.github.tunlezah.watches.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.settings.AppSettings
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.core.settings.ThemeMode

/** How a design draws the tabs above a list. */
enum class TabLook { UNDERLINE, SEGMENT, PILL }

/** The rule a design draws under a top-level title. */
enum class MastheadRule { THICK_THIN, MINUTE_TRACK, NONE }

/** Whether collection tiles are numbered, and how. */
enum class TileNumbers { NONE, CATALOGUE, INDEX }

/** How a design moves: Atelier quiet, Instrument precise, Gallery soft. */
enum class MotionFeel { QUIET, PRECISE, SOFT }

/**
 * Everything about a design beyond colour and type: its case, shapes, rules, ornament and motion.
 * Components read these tokens rather than asking which design is active. See docs/DESIGN.md.
 */
@Immutable
data class DirectionStyle(
    val direction: Direction,
    /** Labels set in capitals with tracking (Atelier small caps, Instrument mono). */
    val uppercaseLabels: Boolean,
    /** Titles set in capitals (Instrument). */
    val uppercaseTitles: Boolean,
    /** Buttons, tabs, chips and navigation labels set in capitals (Atelier tracked, Instrument mono). */
    val uppercaseControls: Boolean,
    /** Collection tiles: hairline frame (Instrument) or filled card (Gallery). */
    val tileFrame: Boolean,
    val tileCard: Boolean,
    /** Width over height of a tile's photo: a tall catalogue plate, a square frame, a pedestal. */
    val tileAspect: Float,
    val tileNumbers: TileNumbers,
    val gridGutter: Dp,
    val gridRowGap: Dp,
    /** Spec groups sit in filled cards (Gallery) instead of hairline lists. */
    val groupCards: Boolean,
    /** Dotted leaders between label and value, and numbered group headers (Instrument). */
    val dottedLeaders: Boolean,
    val numberedGroups: Boolean,
    /** A short accent dash marks section headings and the active navigation item (Atelier). */
    val accentDash: Boolean,
    val masthead: MastheadRule,
    val tabs: TabLook,
    val tabGap: Dp,
    /** Text fields in a soft box (Gallery) rather than on an underline. */
    val boxedFields: Boolean,
    /** Tonal buttons as a tinted fill (Gallery) rather than a frame in the accent. */
    val tonalFill: Boolean,
    /** Icon buttons over content get a 1 dp frame (Instrument) rather than a soft round backing. */
    val framedIcons: Boolean,
    /** Status lights are square (Instrument) rather than round. */
    val squareMarkers: Boolean,
    /** Floating pill navigation bar (Gallery). */
    val floatingNav: Boolean,
    /** Registration marks at the corners of the hero photo (Instrument). */
    val heroMarks: Boolean,
    /** Soft shadow under cards and tiles in light mode (Gallery). */
    val cardElevation: Dp,
    val motion: MotionFeel,
    val tileShape: Shape,
    val photoShape: Shape,
    val cardShape: Shape,
    val buttonShape: Shape,
    val chipShape: Shape,
    val heroShape: Shape,
    val statusShape: Shape,
    val fabShape: Shape,
    val heroInset: Dp,
    val screenPadding: Dp,
)

@Immutable
data class WatchThemeValues(
    val colors: WatchColors,
    val type: WatchTypography,
    val style: DirectionStyle,
) {
    fun label(text: String) = if (style.uppercaseLabels) text.uppercase() else text
    fun title(text: String) = if (style.uppercaseTitles) text.uppercase() else text
    fun control(text: String) = if (style.uppercaseControls) text.uppercase() else text
}

val LocalWatchTheme = staticCompositionLocalOf<WatchThemeValues> { error("WatchTheme not set") }

object WatchTheme {
    val colors: WatchColors @Composable get() = LocalWatchTheme.current.colors
    val type: WatchTypography @Composable get() = LocalWatchTheme.current.type
    val style: DirectionStyle @Composable get() = LocalWatchTheme.current.style
    val values: WatchThemeValues @Composable get() = LocalWatchTheme.current
}

fun styleFor(direction: Direction): DirectionStyle = when (direction) {
    Direction.ATELIER -> DirectionStyle(
        direction, uppercaseLabels = true, uppercaseTitles = false, uppercaseControls = true,
        tileFrame = false, tileCard = false, tileAspect = 0.82f, tileNumbers = TileNumbers.CATALOGUE, gridGutter = 16.dp, gridRowGap = 28.dp,
        groupCards = false, dottedLeaders = false, numberedGroups = false, accentDash = true, masthead = MastheadRule.THICK_THIN,
        tabs = TabLook.UNDERLINE, tabGap = 20.dp, boxedFields = false, tonalFill = false, framedIcons = false, squareMarkers = false,
        floatingNav = false, heroMarks = false, cardElevation = 0.dp, motion = MotionFeel.QUIET,
        tileShape = RectangleShape, photoShape = RectangleShape, cardShape = RectangleShape, buttonShape = RectangleShape,
        chipShape = RectangleShape, heroShape = RectangleShape, statusShape = RectangleShape, fabShape = RectangleShape,
        heroInset = 0.dp, screenPadding = 20.dp,
    )
    Direction.INSTRUMENT -> DirectionStyle(
        direction, uppercaseLabels = true, uppercaseTitles = true, uppercaseControls = true,
        tileFrame = true, tileCard = false, tileAspect = 1f, tileNumbers = TileNumbers.INDEX, gridGutter = 10.dp, gridRowGap = 10.dp,
        groupCards = false, dottedLeaders = true, numberedGroups = true, accentDash = false, masthead = MastheadRule.MINUTE_TRACK,
        tabs = TabLook.SEGMENT, tabGap = 6.dp, boxedFields = false, tonalFill = false, framedIcons = true, squareMarkers = true,
        floatingNav = false, heroMarks = true, cardElevation = 0.dp, motion = MotionFeel.PRECISE,
        tileShape = RoundedCornerShape(2.dp), photoShape = RectangleShape, cardShape = RoundedCornerShape(2.dp),
        buttonShape = RoundedCornerShape(2.dp), chipShape = RoundedCornerShape(2.dp), heroShape = RectangleShape,
        statusShape = RoundedCornerShape(1.dp), fabShape = RoundedCornerShape(2.dp),
        heroInset = 0.dp, screenPadding = 16.dp,
    )
    Direction.GALLERY -> DirectionStyle(
        direction, uppercaseLabels = false, uppercaseTitles = false, uppercaseControls = false,
        tileFrame = false, tileCard = true, tileAspect = 0.86f, tileNumbers = TileNumbers.NONE, gridGutter = 12.dp, gridRowGap = 12.dp,
        groupCards = true, dottedLeaders = false, numberedGroups = false, accentDash = false, masthead = MastheadRule.NONE,
        tabs = TabLook.PILL, tabGap = 8.dp, boxedFields = true, tonalFill = true, framedIcons = false, squareMarkers = false,
        floatingNav = true, heroMarks = false, cardElevation = 10.dp, motion = MotionFeel.SOFT,
        tileShape = RoundedCornerShape(22.dp), photoShape = RoundedCornerShape(16.dp), cardShape = RoundedCornerShape(22.dp),
        buttonShape = RoundedCornerShape(24.dp), chipShape = RoundedCornerShape(22.dp), heroShape = RoundedCornerShape(28.dp),
        statusShape = RoundedCornerShape(50), fabShape = RoundedCornerShape(28.dp),
        heroInset = 12.dp, screenPadding = 16.dp,
    )
}

@Composable
fun WatchTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colors = Palettes.colors(settings.direction, settings.accent, dark)
    val values = WatchThemeValues(colors, typographyFor(settings.direction), styleFor(settings.direction))

    // Material components (dialogs, sheets, pickers, sliders) take their colours from the same tokens.
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent, primaryContainer = colors.accentTint, onPrimaryContainer = colors.fg,
            secondary = colors.accent, onSecondary = colors.onAccent, secondaryContainer = colors.surface2, onSecondaryContainer = colors.fg,
            background = colors.bg, onBackground = colors.fg, surface = colors.surface, onSurface = colors.fg,
            surfaceVariant = colors.surface2, onSurfaceVariant = colors.fg2, outline = colors.line, outlineVariant = colors.line,
            surfaceContainerLowest = colors.bg, surfaceContainerLow = colors.surface, surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface2, surfaceContainerHighest = colors.surface2, error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent, primaryContainer = colors.accentTint, onPrimaryContainer = colors.fg,
            secondary = colors.accent, onSecondary = colors.onAccent, secondaryContainer = colors.surface2, onSecondaryContainer = colors.fg,
            background = colors.bg, onBackground = colors.fg, surface = colors.surface, onSurface = colors.fg,
            surfaceVariant = colors.surface2, onSurfaceVariant = colors.fg2, outline = colors.line, outlineVariant = colors.line,
            surfaceContainerLowest = colors.bg, surfaceContainerLow = colors.surface, surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface2, surfaceContainerHighest = colors.surface2, error = colors.danger,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalWatchTheme provides values, content = content)
    }
}
