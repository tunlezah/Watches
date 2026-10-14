package io.github.tunlezah.watches.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.data.sample.SampleLook
import kotlin.math.min

/** Colours for the drawn stand-in shown when a watch has no photo, and for generated sample photos. */
@Immutable
data class WatchLook(
    val dial: Color,
    val dialHighlight: Color,
    val bezel: Color,
    val case: Color,
    val strap: Color,
    val index: Color,
    val hand: Color,
    val seconds: Color,
    val chronograph: Boolean,
) {
    companion object {
        private val steel = Color(0xFFAEB3BA)

        fun from(sample: SampleLook) = WatchLook(
            Color(sample.dial), Color(sample.dialHighlight), Color(sample.bezel), Color(sample.case), Color(sample.strap),
            Color(sample.index), Color(sample.hand), Color(sample.seconds), sample.chronograph,
        )

        /** A look guessed from the watch's own description, so placeholders are at least the right colour. */
        fun guess(dialColour: String?, caseMaterial: String?, complications: Set<String>): WatchLook {
            val d = dialColour?.lowercase().orEmpty()
            val (dial, light) = when {
                "blue" in d -> Color(0xFF1D3557) to false
                "green" in d -> Color(0xFF1F4D3A) to false
                "grey" in d || "gray" in d || "slate" in d -> Color(0xFF3A3F46) to false
                "white" in d || "snow" in d -> Color(0xFFE9ECEF) to true
                "silver" in d -> Color(0xFFD9DADC) to true
                "cream" in d || "ivory" in d || "champagne" in d -> Color(0xFFF1EEE7) to true
                "salmon" in d || "pink" in d -> Color(0xFFE8B4A0) to true
                "brown" in d || "chocolate" in d || "tropical" in d -> Color(0xFF4A3424) to false
                "red" in d || "burgundy" in d -> Color(0xFF5E1E26) to false
                else -> Color(0xFF15161A) to false
            }
            val m = caseMaterial?.lowercase().orEmpty()
            val case = when {
                "gold" in m && "white" !in m -> Color(0xFFC9A45C)
                "titanium" in m -> Color(0xFF9EA3A8)
                "ceramic" in m || "carbon" in m || "resin" in m || "dlc" in m -> Color(0xFF2A2B2E)
                else -> steel
            }
            val ink = if (light) Color(0xFF2A2C30) else Color(0xFFECE8DE)
            return WatchLook(
                dial = dial,
                dialHighlight = if (light) Color.White else dial.shade(1.6f),
                bezel = case, case = case, strap = Color(0xFF2A2522), index = ink, hand = ink, seconds = ink,
                chronograph = Complication.CHRONOGRAPH.key in complications,
            )
        }
    }
}

/** Scales a colour's channels, clamped to the valid range. */
fun Color.shade(factor: Float) = Color(
    (red * factor).coerceIn(0f, 1f), (green * factor).coerceIn(0f, 1f), (blue * factor).coerceIn(0f, 1f), alpha,
)

/** Draws a watch head-on, in the classic 10:10 pose, fitted to the shorter side of the draw area. */
fun DrawScope.drawWatch(look: WatchLook, shadow: Color) {
    val s = min(size.width, size.height) / 240f
    val ox = (size.width - 240f * s) / 2f
    val oy = (size.height - 240f * s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    val c = p(120f, 120f)

    // Strap, top and bottom.
    drawRoundRect(look.strap, p(92f, -24f), Size(56f * s, 84f * s), CornerRadius(10f * s))
    drawRoundRect(look.strap, p(92f, 180f), Size(56f * s, 84f * s), CornerRadius(10f * s))
    // Crown.
    drawRoundRect(look.case, p(185f, 110f), Size(12f * s, 20f * s), CornerRadius(3f * s))
    // Soft shadow under the case.
    drawCircle(
        brush = Brush.radialGradient(listOf(shadow, Color.Transparent), center = p(120f, 132f), radius = 92f * s),
        radius = 92f * s, center = p(120f, 132f),
    )
    // Case, bezel, dial.
    drawCircle(look.case, 72f * s, c)
    drawCircle(Color.White.copy(alpha = 0.18f), 72f * s, c, style = Stroke(1.5f * s))
    drawCircle(look.bezel, 65f * s, c)
    drawCircle(
        brush = Brush.radialGradient(listOf(look.dialHighlight, look.dial), center = p(120f, 100f), radius = 70f * s),
        radius = 54f * s, center = c,
    )
    if (look.chronograph) {
        val sub = look.dial.shade(0.8f)
        for ((x, y) in listOf(145f to 120f, 120f to 145f, 95f to 120f)) {
            drawCircle(sub, 13f * s, p(x, y))
            drawCircle(look.index.copy(alpha = 0.25f), 13f * s, p(x, y), style = Stroke(1f * s))
        }
    }
    // Hour markers.
    for (i in 0 until 12) {
        val major = i % 3 == 0
        val w = if (i == 0) 4f else if (major) 3f else 2f
        val h = if (major) 12f else 8f
        rotate(i * 30f, pivot = c) {
            drawRoundRect(look.index, p(120f - w / 2f, 70f), Size(w * s, h * s), CornerRadius(1f * s))
        }
    }
    // Hands at 10:10, then the seconds hand.
    rotate(-60f, pivot = c) { drawRoundRect(look.hand, p(118f, 88f), Size(4f * s, 34f * s), CornerRadius(2f * s)) }
    rotate(60f, pivot = c) { drawRoundRect(look.hand, p(118.5f, 74f), Size(3f * s, 48f * s), CornerRadius(1.5f * s)) }
    rotate(168f, pivot = c) { drawRect(look.seconds, p(119.25f, 66f), Size(1.5f * s, 66f * s)) }
    drawCircle(look.seconds, 4f * s, c)
}

@Composable
fun WatchIllustration(look: WatchLook, shadow: Color, contentDescription: String?, modifier: Modifier = Modifier) {
    Canvas(modifier.then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)) {
        drawWatch(look, shadow)
    }
}
