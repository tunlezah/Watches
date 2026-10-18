package io.github.tunlezah.watches.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.tunlezah.watches.core.settings.Accent
import io.github.tunlezah.watches.core.settings.Direction

/**
 * Colour tokens. `fg3` (labels, units) is checked to reach at least 4.5:1 on `bg`, `surface`
 * and `surface2` in every direction and mode; accents reach 4.5:1 on `bg`.
 */
@Immutable
data class WatchColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val accent: Color,
    val onAccent: Color,
    val accentTint: Color,
    val danger: Color,
    val shadow: Color,
    /** Backdrop behind watch photos and illustrations. */
    val stageInner: Color,
    val stageOuter: Color,
    /** Edges of controls (field underlines, outlined buttons, switches): about 3:1 on `bg`, unlike `line`, which is for dividers. */
    val lineStrong: Color = line,
    /** Needs attention but isn't an error: an old backup, a warranty ending. 4.5:1 on `bg`. */
    val warning: Color = danger,
    val success: Color = accent,
)

object Palettes {

    /** Dark-mode and light-mode variant of each accent. */
    fun accent(accent: Accent, dark: Boolean): Color = when (accent) {
        Accent.CHAMPAGNE -> if (dark) Color(0xFFD4B47A) else Color(0xFF7A5C24)
        Accent.LUME -> if (dark) Color(0xFFB9F27C) else Color(0xFF467010)
        Accent.ICE_BLUE -> if (dark) Color(0xFFA8D8EA) else Color(0xFF245F78)
        Accent.RACING_GREEN -> if (dark) Color(0xFF5DBB8A) else Color(0xFF1D6644)
        Accent.BORDEAUX -> if (dark) Color(0xFFE58A9B) else Color(0xFF8E2840)
        Accent.SUNBURST_BLUE -> if (dark) Color(0xFF8FA8FF) else Color(0xFF2B49C2)
        Accent.TROPICAL_AMBER -> if (dark) Color(0xFFE6A85A) else Color(0xFF8A5010)
    }

    fun colors(direction: Direction, accent: Accent, dark: Boolean): WatchColors {
        val base = baseColors(direction, accent, dark)
        val strong = when (direction) {
            Direction.ATELIER -> if (dark) Color(0xFF6A6357) else Color(0xFF8F8472)
            Direction.INSTRUMENT -> if (dark) Color(0xFF5B6168) else Color(0xFF7F878F)
            Direction.GALLERY -> if (dark) Color(0xFF58646F) else Color(0xFF838E9C)
        }
        return base.copy(
            lineStrong = strong,
            warning = if (dark) Color(0xFFE8B04B) else Color(0xFF8A5A00),
            success = if (dark) Color(0xFF6CC48A) else Color(0xFF1E6B3E),
        )
    }

    private fun baseColors(direction: Direction, accent: Accent, dark: Boolean): WatchColors {
        val a = accent(accent, dark)
        return when (direction) {
            Direction.ATELIER -> if (dark) {
                WatchColors(true, Color(0xFF121110), Color(0xFF1A1917), Color(0xFF211F1C), Color(0xFF2F2C27),
                    Color(0xFFEDE7DC), Color(0xFFBDB4A5), Color(0xFF958C7D), a, Color(0xFF121110), a.copy(alpha = 0.14f),
                    Color(0xFFE5807A), Color(0x80000000), Color(0xFF26231F), Color(0xFF1A1917))
            } else {
                WatchColors(false, Color(0xFFF5F1E8), Color(0xFFECE6DA), Color(0xFFE6DFD1), Color(0xFFD6CDBD),
                    Color(0xFF1B1915), Color(0xFF4A443A), Color(0xFF6B6355), a, Color(0xFFFFFFFF), a.copy(alpha = 0.12f),
                    Color(0xFFA8332C), Color(0x473C2D14), Color(0xFFF7F3EC), Color(0xFFE6DFD1))
            }
            Direction.INSTRUMENT -> if (dark) {
                WatchColors(true, Color(0xFF000000), Color(0xFF0A0B0C), Color(0xFF141618), Color(0xFF24282C),
                    Color(0xFFF2F4F5), Color(0xFFAAB1B8), Color(0xFF7F878F), a, Color(0xFF000000), a.copy(alpha = 0.14f),
                    Color(0xFFFF8A80), Color(0x99000000), Color(0xFF16181B), Color(0xFF0A0B0C))
            } else {
                WatchColors(false, Color(0xFFF3F4F5), Color(0xFFFFFFFF), Color(0xFFE9EBED), Color(0xFFCDD2D7),
                    Color(0xFF0B0C0D), Color(0xFF3B4248), Color(0xFF59616A), a, Color(0xFFFFFFFF), a.copy(alpha = 0.12f),
                    Color(0xFFB3261E), Color(0x40000000), Color(0xFFFFFFFF), Color(0xFFE9EBED))
            }
            Direction.GALLERY -> if (dark) {
                WatchColors(true, Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF1D242D), Color(0xFF29313C),
                    Color(0xFFEEF2F7), Color(0xFFB0BAC6), Color(0xFF8A96A4), a, Color(0xFF0D1117), a.copy(alpha = 0.16f),
                    Color(0xFFFF8F87), Color(0x80000000), Color(0xFF27313D), Color(0xFF181E26))
            } else {
                WatchColors(false, Color(0xFFF4F6F9), Color(0xFFFFFFFF), Color(0xFFEAEEF3), Color(0xFFDCE2E9),
                    Color(0xFF0E131A), Color(0xFF3D4955), Color(0xFF5A6675), a, Color(0xFFFFFFFF), a.copy(alpha = 0.12f),
                    Color(0xFFB3261E), Color(0x401E2D3C), Color(0xFFFFFFFF), Color(0xFFE3E9F0))
            }
        }
    }
}
