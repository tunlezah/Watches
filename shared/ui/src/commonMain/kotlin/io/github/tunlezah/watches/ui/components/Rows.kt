package io.github.tunlezah.watches.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

/*
 * The grouped-list pattern every settings-like screen uses: a small heading, then one card holding
 * rows separated by inset hairlines, then an optional footnote. Rows are at least 56 dp tall, the
 * whole row is the touch target, and a value or switch sits on the trailing edge.
 */

@Composable
fun RowGroup(title: String?, modifier: Modifier = Modifier, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(t.label(title), style = t.type.label, color = c.fg3,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() })
        }
        Column(Modifier.fillMaxWidth().clip(style.cardShape).background(c.surface)) { content() }
        if (footer != null) {
            Text(footer, style = t.type.bodySmall, color = c.fg3, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
    }
}

/** A hairline between rows, inset to line up with the row text when the rows have icons. */
@Composable
fun RowDivider(inset: Boolean = true) {
    val c = WatchTheme.colors
    Box(Modifier.fillMaxWidth().padding(start = if (inset) 56.dp else 16.dp).height(1.dp).background(c.line.copy(alpha = 0.6f)))
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    tone: Tone = Tone.Normal,
    enabled: Boolean = true,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val tint = when (tone) {
        Tone.Normal -> c.accent
        Tone.Danger -> c.danger
        Tone.Warning -> c.warning
        Tone.Success -> c.success
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (enabled) tint else c.fg3, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = t.type.body, color = when {
                !enabled -> c.fg3
                tone == Tone.Danger -> c.danger
                else -> c.fg
            })
            if (detail != null) {
                Text(detail, style = t.type.bodySmall, color = when (tone) {
                    Tone.Warning -> c.warning
                    Tone.Danger -> c.danger
                    else -> c.fg2
                },
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(value, style = t.type.bodySmall, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 200.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (onClick != null && chevron) {
            Spacer(Modifier.width(8.dp))
            Icon(WatchIcons.Forward, null, tint = c.fg3, modifier = Modifier.size(16.dp))
        }
    }
}

enum class Tone { Normal, Success, Warning, Danger }

/** A row whose trailing control is a switch; the whole row is one toggle for touch and screen readers. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, detail: String? = null, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = WatchTheme.colors
    val haptics = LocalHapticFeedback.current
    ListRow(
        title = title,
        detail = detail,
        icon = icon,
        enabled = enabled,
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { on ->
            haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onChange(on)
        }),
        trailing = {
            Switch(
                checked = checked, onCheckedChange = null, enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.accent, checkedThumbColor = c.onAccent,
                    uncheckedTrackColor = c.surface2, uncheckedThumbColor = c.fg3, uncheckedBorderColor = c.lineStrong,
                ),
            )
        },
    )
}

/** Figures side by side, each with a small label underneath: the collection at a glance. */
@Composable
fun FigureRow(figures: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        figures.forEachIndexed { i, (value, label) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 4.dp).background(c.line))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = t.type.stat.copy(fontSize = t.type.specValue.fontSize), color = c.fg, maxLines = 1)
                Text(t.label(label), style = t.type.label, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A message card with an icon: success after a backup, a warning before a destructive choice, an error. */
@Composable
fun Callout(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Normal,
    icon: ImageVector = when (tone) {
        Tone.Success -> WatchIcons.CheckCircle
        Tone.Warning, Tone.Danger -> WatchIcons.Warning
        Tone.Normal -> WatchIcons.Info
    },
    actions: (@Composable () -> Unit)? = null,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val tint = when (tone) {
        Tone.Danger -> c.danger
        Tone.Warning -> c.warning
        Tone.Success -> c.success
        Tone.Normal -> c.accent
    }
    Column(
        modifier.fillMaxWidth().clip(style.cardShape).background(tint.copy(alpha = if (c.isDark) 0.12f else 0.09f))
            .padding(16.dp).animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = t.type.button, color = c.fg)
                if (message != null) Text(message, style = t.type.bodySmall, color = c.fg2)
            }
        }
        if (actions != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(start = 26.dp), horizontalArrangement = Arrangement.End) { actions() }
        }
    }
}

/** A thin determinate progress bar with its label, percentage and detail, for long-running work. */
@Composable
fun ProgressPanel(title: String, detail: String?, fraction: Float?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val shown by animateFloatAsState(fraction ?: 0f, spring(stiffness = 300f), label = "progress")
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            if (fraction != null) progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = t.type.button, color = c.fg, modifier = Modifier.weight(1f))
            if (fraction != null) Text("${(fraction * 100).toInt()}%", style = t.type.stat.copy(fontSize = t.type.body.fontSize), color = c.fg2)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) {
            if (fraction != null) {
                Box(Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.accent))
            } else {
                IndeterminateBar(c.accent)
            }
        }
        if (detail != null || action != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(detail.orEmpty(), style = t.type.bodySmall, color = c.fg3, modifier = Modifier.weight(1f))
                action?.invoke()
            }
        }
    }
}

@Composable
private fun IndeterminateBar(color: Color) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "indeterminate")
    val x by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200)),
        label = "x",
    )
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
        val w = size.width * 0.3f
        val start = (size.width + w) * x - w
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(start.coerceAtLeast(0f), 0f),
            size = androidx.compose.ui.geometry.Size((w + minOf(0f, start)).coerceAtMost(size.width - start.coerceAtLeast(0f)).coerceAtLeast(0f), size.height))
    }
}

/** A round tinted badge holding an icon, for the lead of a status card. */
@Composable
fun IconBadge(icon: ImageVector, tone: Tone = Tone.Normal, size: Int = 44) {
    val c = WatchTheme.colors
    val tint = when (tone) {
        Tone.Danger -> c.danger
        Tone.Warning -> c.warning
        Tone.Success -> c.success
        Tone.Normal -> c.accent
    }
    Box(Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

/**
 * A screen's main action, pinned to the bottom: a solid strip with a short fade above it, so the
 * list scrolling underneath never shows through the button. Lists keep bottom padding for it.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.BottomActionBar(content: @Composable () -> Unit) {
    val c = WatchTheme.colors
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(24.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, c.bg))))
        Box(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
    }
}
