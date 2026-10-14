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

/** How each direction lays things out, beyond colour and type. */
@Immutable
data class DirectionStyle(
    val direction: Direction,
    /** Labels set in capitals with tracking (Atelier small caps, Instrument mono). */
    val uppercaseLabels: Boolean,
    /** Titles set in capitals (Instrument). */
    val uppercaseTitles: Boolean,
    /** Collection tiles: hairline frame (Instrument) or filled card (Gallery). */
    val tileFrame: Boolean,
    val tileCard: Boolean,
    /** Spec groups sit in filled cards (Gallery) instead of hairline lists. */
    val groupCards: Boolean,
    /** Dotted leaders between label and value, and numbered group headers (Instrument). */
    val dottedLeaders: Boolean,
    val numberedGroups: Boolean,
    /** Floating pill navigation bar (Gallery). */
    val floatingNav: Boolean,
    val tileShape: Shape,
    val photoShape: Shape,
    val cardShape: Shape,
    val buttonShape: Shape,
    val chipShape: Shape,
    val heroShape: Shape,
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
        direction, uppercaseLabels = true, uppercaseTitles = false, tileFrame = false, tileCard = false,
        groupCards = false, dottedLeaders = false, numberedGroups = false, floatingNav = false,
        tileShape = RoundedCornerShape(2.dp), photoShape = RoundedCornerShape(2.dp), cardShape = RectangleShape,
        buttonShape = RectangleShape, chipShape = RectangleShape, heroShape = RectangleShape, heroInset = 0.dp, screenPadding = 20.dp,
    )
    Direction.INSTRUMENT -> DirectionStyle(
        direction, uppercaseLabels = true, uppercaseTitles = true, tileFrame = true, tileCard = false,
        groupCards = false, dottedLeaders = true, numberedGroups = true, floatingNav = false,
        tileShape = RoundedCornerShape(2.dp), photoShape = RectangleShape, cardShape = RoundedCornerShape(2.dp),
        buttonShape = RoundedCornerShape(2.dp), chipShape = RoundedCornerShape(2.dp), heroShape = RectangleShape, heroInset = 0.dp, screenPadding = 16.dp,
    )
    Direction.GALLERY -> DirectionStyle(
        direction, uppercaseLabels = false, uppercaseTitles = false, tileFrame = false, tileCard = true,
        groupCards = true, dottedLeaders = false, numberedGroups = false, floatingNav = true,
        tileShape = RoundedCornerShape(22.dp), photoShape = RoundedCornerShape(16.dp), cardShape = RoundedCornerShape(22.dp),
        buttonShape = RoundedCornerShape(24.dp), chipShape = RoundedCornerShape(22.dp), heroShape = RoundedCornerShape(28.dp), heroInset = 12.dp, screenPadding = 16.dp,
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
