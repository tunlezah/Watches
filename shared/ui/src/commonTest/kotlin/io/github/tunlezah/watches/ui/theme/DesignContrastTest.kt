package io.github.tunlezah.watches.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.tunlezah.watches.core.settings.Accent
import io.github.tunlezah.watches.core.settings.Direction
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The colour rules in docs/DESIGN.md, for every design, mode and accent. */
class DesignContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb)
    }

    private fun every(check: (name: String, colors: WatchColors) -> Unit) {
        for (d in Direction.entries) for (dark in listOf(true, false)) for (a in Accent.entries) {
            check("${d.label} ${if (dark) "dark" else "light"} ${a.label}", Palettes.colors(d, a, dark))
        }
    }

    private fun atLeast(min: Double, fg: Color, bg: Color, what: String) {
        val r = ratio(fg, bg)
        assertTrue(r >= min, "$what is ${round(r * 100) / 100}:1, needs $min:1")
    }

    @Test fun textReadsOnEverySurface() = every { name, c ->
        for ((label, bg) in listOf("bg" to c.bg, "surface" to c.surface, "surface2" to c.surface2)) {
            atLeast(4.5, c.fg, bg, "$name fg on $label")
            atLeast(4.5, c.fg2, bg, "$name fg2 on $label")
            atLeast(4.5, c.fg3, bg, "$name fg3 on $label")
        }
    }

    @Test fun accentsReadAsTextAndCarryTheirLabel() = every { name, c ->
        atLeast(4.5, c.accent, c.bg, "$name accent on bg")
        atLeast(4.5, c.accent, c.surface, "$name accent on surface")
        atLeast(4.5, c.onAccent, c.accent, "$name label on accent")
    }

    @Test fun statusColoursRead() = every { name, c ->
        atLeast(4.5, c.danger, c.bg, "$name danger")
        atLeast(4.5, c.warning, c.bg, "$name warning")
        atLeast(4.5, c.success, c.bg, "$name success")
    }

    @Test fun controlEdgesAreVisible() = every { name, c ->
        atLeast(3.0, c.lineStrong, c.bg, "$name control edge")
    }

    @Test fun everyDesignSuggestsFourAccentsHouseColourFirst() {
        for (d in Direction.entries) {
            val suggested = Accent.suggestedFor(d)
            assertEquals(4, suggested.distinct().size)
            assertEquals(Accent.defaultFor(d), suggested.first())
        }
    }
}
