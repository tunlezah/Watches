package io.github.tunlezah.watches.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.spec.SpecGroupSheet
import io.github.tunlezah.watches.core.spec.SpecRow
import io.github.tunlezah.watches.ui.theme.WatchTheme

/** Spoken form of units, so TalkBack says "42 millimetres" rather than "42 m m". */
private fun spokenUnit(unit: String): String = unit
    .replace("mm", "millimetres").replace("bph", "beats per hour").replace("Hz", "hertz")
    .replace("ATM", "atmospheres").replace(Regex("""\bm\b"""), "metres").replace(Regex("""\bh\b"""), "hours")
    .replace("·", ",")

@Composable
fun SpecGroupView(
    sheet: SpecGroupSheet,
    number: Int,
    revealedSecrets: Set<String>,
    onReveal: (SpecRow) -> Unit,
    onCopy: (SpecRow) -> Unit,
    modifier: Modifier = Modifier,
    title: String = sheet.group.label,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val rows: @Composable () -> Unit = {
        sheet.rows.forEachIndexed { i, row ->
            if (i > 0 || !style.groupCards) {
                if (!style.dottedLeaders) HorizontalDivider(color = c.line, thickness = 1.dp)
            }
            SpecRowView(row, revealed = row.keys.first() in revealedSecrets, onReveal = { onReveal(row) }, onCopy = { onCopy(row) })
        }
    }
    if (style.groupCards) {
        Column(
            modifier.fillMaxWidth().clip(style.cardShape).background(c.surface).padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(title, style = t.type.groupTitle, color = c.fg, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).semantics { heading() })
            rows()
        }
    } else if (style.numberedGroups) {
        Column(modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(number.toString().padStart(2, '0'), style = t.type.label, color = c.accent)
                Spacer(Modifier.width(10.dp))
                Text(title.uppercase(), style = t.type.groupTitle, color = c.fg, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(1.dp).background(c.line))
                Spacer(Modifier.width(10.dp))
                Text("${sheet.rows.size.toString().padStart(2, '0')} FIELDS", style = t.type.label, color = c.fg3)
            }
            rows()
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            Text(title, style = t.type.groupTitle, color = c.fg, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
            rows()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpecRowView(row: SpecRow, revealed: Boolean, onReveal: () -> Unit, onCopy: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val hidden = row.secret && !revealed
    val valueText = if (hidden) "•••• ••••" else row.value.value
    val unit = if (hidden) null else row.value.unit
    val source = when {
        hidden -> "Tap to reveal"
        row.userEdited -> "Edited"
        row.sourceLabel != null -> "${row.sourceLabel}${if (row.sourceUrl != null) " ↗" else ""}"
        else -> null
    }
    val sourceColor = if (row.userEdited) c.accent else c.fg3
    val spoken = buildString {
        append(row.label).append(", ")
        append(if (hidden) "hidden, double tap to reveal" else valueText)
        if (unit != null) append(" ").append(spokenUnit(unit))
        if (!hidden && row.sourceLabel != null) append(", from ").append(row.sourceLabel)
        if (row.userEdited) append(", edited by you")
    }
    val value = buildAnnotatedString {
        append(valueText)
        if (unit != null) withStyle(SpanStyle(color = c.fg3, fontFamily = t.type.specUnit.fontFamily, fontSize = t.type.specUnit.fontSize)) { append(" $unit") }
    }
    // Only a hidden serial number does anything on a tap; every value copies on a long press.
    val press = if (row.secret) {
        Modifier.combinedClickable(onClick = onReveal, onLongClick = { if (!hidden) onCopy() }, onLongClickLabel = "Copy")
    } else {
        Modifier.pointerInput(row.value) { detectTapGestures(onLongPress = { onCopy() }) }
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .then(press)
        .clearAndSetSemantics {
            contentDescription = spoken
            onLongClick("Copy value") { if (!hidden) onCopy(); true }
        }
        .padding(vertical = 11.dp)

    if (row.longText && !hidden) {
        Column(rowModifier) {
            Text(t.label(row.label), style = t.type.label, color = c.fg3)
            Spacer(Modifier.height(6.dp))
            Text(row.value.value, style = t.type.body, color = c.fg)
        }
        return
    }

    if (style.dottedLeaders) {
        Row(rowModifier, verticalAlignment = Alignment.Bottom) {
            Text(row.label, style = t.type.specLabel, color = c.fg2)
            if (source != null) {
                Spacer(Modifier.width(6.dp))
                Text(source.uppercase(), style = t.type.specSource, color = sourceColor, modifier = Modifier.padding(bottom = 2.dp))
            }
            Box(
                Modifier.weight(1f).widthIn(min = 12.dp).height(14.dp).padding(horizontal = 6.dp).drawBehind {
                    drawLine(c.line, Offset(0f, size.height - 3f), Offset(size.width, size.height - 3f), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)))
                },
            )
            Text(value, style = t.type.specValue, color = c.fg, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 200.dp))
        }
    } else {
        Row(rowModifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (style.groupCards) {
                Column(Modifier.weight(1f)) {
                    Text(row.label, style = t.type.specLabel, color = c.fg2)
                    if (source != null) Text(source, style = t.type.specSource, color = sourceColor)
                }
            } else {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.label, style = t.type.specLabel, color = c.fg2)
                    if (source != null) Text(source, style = t.type.specSource, color = sourceColor, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text(value, style = t.type.specValue, color = c.fg, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
        }
    }
}
