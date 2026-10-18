package io.github.tunlezah.watches.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.ui.theme.MastheadRule
import io.github.tunlezah.watches.ui.theme.WatchTheme

/*
 * The small details that make each design itself (docs/DESIGN.md): Atelier's thick–thin rule and
 * accent dash, Instrument's minute track and registration marks, Gallery's soft depth.
 */

/**
 * The title block of a top-level screen: title, an optional line under it, then the design's own
 * rule. [tools] sit at the title's trailing edge.
 */
@Composable
fun Masthead(title: String, subtitle: String? = null, tools: @Composable RowScope.() -> Unit = {}) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.title(title), style = t.type.screenTitle, color = c.fg, modifier = Modifier.weight(1f).semantics { heading() })
            tools()
        }
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = t.type.screenSubtitle, color = if (WatchTheme.style.uppercaseLabels) c.fg3 else c.fg2)
        }
        if (WatchTheme.style.masthead != MastheadRule.NONE) {
            Spacer(Modifier.height(14.dp))
            DesignRule()
        }
    }
}

/** The rule a design draws under a masthead: Atelier's thick–thin pair, Instrument's minute track. */
@Composable
fun DesignRule(modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    when (WatchTheme.style.masthead) {
        MastheadRule.THICK_THIN -> Canvas(modifier.fillMaxWidth().height(5.dp).clearAndSetSemantics { }) {
            val thick = 1.5.dp.toPx()
            val thin = 0.75.dp.toPx()
            drawLine(c.fg3, Offset(0f, thick / 2), Offset(size.width, thick / 2), thick)
            val y = thick + 2.5.dp.toPx() + thin / 2
            drawLine(c.fg3, Offset(0f, y), Offset(size.width, y), thin)
        }
        MastheadRule.MINUTE_TRACK -> Canvas(modifier.fillMaxWidth().height(9.dp).clearAndSetSemantics { }) {
            val step = 6.dp.toPx()
            val hair = 1.dp.toPx()
            val base = size.height - hair / 2
            drawLine(c.line, Offset(0f, base), Offset(size.width, base), hair)
            var i = 0
            var x = hair / 2
            while (x <= size.width) {
                val major = i % 5 == 0
                // The first major tick is a lume pip, like the marker at twelve on a bezel.
                val pip = i == 0
                val height = if (major) size.height else 4.dp.toPx()
                drawLine(
                    if (pip) c.accent else if (major) c.fg3 else c.lineStrong,
                    Offset(x, base), Offset(x, base - height),
                    if (pip) 2.dp.toPx() else hair,
                )
                x += step
                i++
            }
        }
        MastheadRule.NONE -> Unit
    }
}

/** Atelier's short accent dash, before section headings and over the active navigation item. */
@Composable
fun AccentDash(modifier: Modifier = Modifier, width: Dp = 20.dp) {
    Box(modifier.size(width = width, height = 1.5.dp).background(WatchTheme.colors.accent))
}

/** A small status light: round in Atelier and Gallery, a square LED in Instrument. */
@Composable
fun StatusMarker(color: Color = WatchTheme.colors.accent, size: Dp = 6.dp) {
    Box(Modifier.size(size).clip(if (WatchTheme.style.squareMarkers) RectangleShape else CircleShape).background(color))
}

/**
 * A raised surface in the design's way: in Gallery's daylight, a wide, very soft shadow; elsewhere
 * (and at night) a flat tonal step. Clips to [shape] and fills with [color].
 */
@Composable
fun Modifier.surface(shape: Shape = WatchTheme.style.cardShape, color: Color = WatchTheme.colors.surface): Modifier {
    val c = WatchTheme.colors
    val elevation = WatchTheme.style.cardElevation
    if (elevation <= 0.dp || c.isDark) return clip(shape).background(color)
    // A tint is laid over the card colour, so the shadow underneath never shows through it.
    val fill = if (color.alpha < 1f) color.compositeOver(c.surface) else color
    return shadow(elevation, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow).clip(shape).background(fill)
}

/**
 * Instrument's registration marks: four corner brackets framing the subject, like a viewfinder's
 * focus frame. They sit on a centred square, clear of the header and of buttons along the edges.
 */
fun Modifier.registrationMarks(color: Color, arm: Dp = 16.dp): Modifier = drawWithContent {
    drawContent()
    val side = minOf(size.width, size.height) * 0.72f
    val left = (size.width - side) / 2
    val top = (size.height - side) / 2
    val a = arm.toPx()
    val w = 1.5.dp.toPx()
    val corners = listOf(
        Offset(left, top) to Offset(1f, 1f),
        Offset(left + side, top) to Offset(-1f, 1f),
        Offset(left, top + side) to Offset(1f, -1f),
        Offset(left + side, top + side) to Offset(-1f, -1f),
    )
    for ((p, d) in corners) {
        drawLine(color, p, Offset(p.x + a * d.x, p.y), w)
        drawLine(color, p, Offset(p.x, p.y + a * d.y), w)
    }
}
