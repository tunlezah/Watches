package io.github.tunlezah.watches.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.ui.theme.WatchTheme

/** 48dp square icon button. Every icon-only control carries a spoken label. */
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = WatchTheme.colors.fg2,
    framed: Boolean = false,
    filled: Color? = null,
    enabled: Boolean = true,
) {
    val style = WatchTheme.style
    val shape = when {
        filled != null && style.direction != Direction.INSTRUMENT -> CircleShape
        style.direction == Direction.GALLERY -> CircleShape
        else -> style.buttonShape
    }
    Box(
        modifier
            .size(48.dp)
            .clip(shape)
            .then(if (filled != null) Modifier.background(filled) else Modifier)
            .then(if (framed) Modifier.border(1.dp, WatchTheme.colors.line, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
    }
}

enum class ButtonKind { Primary, Secondary, Tonal, Text, Danger }

@Composable
fun WatchButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = WatchTheme.colors
    val style = WatchTheme.style
    val theme = WatchTheme.values
    val (bg, fg, border) = when (kind) {
        ButtonKind.Primary -> Triple(c.accent, c.onAccent, null)
        ButtonKind.Secondary -> Triple(Color.Transparent, c.fg, c.lineStrong)
        ButtonKind.Tonal -> Triple(c.accentTint, c.accent, null)
        ButtonKind.Text -> Triple(Color.Transparent, c.accent, null)
        ButtonKind.Danger -> Triple(Color.Transparent, c.danger, c.danger.copy(alpha = 0.5f))
    }
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(style.buttonShape)
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.4f))
            .then(if (border != null) Modifier.border(1.dp, border, style.buttonShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (style.direction == Direction.INSTRUMENT) text.uppercase() else text,
            style = theme.type.button,
            color = if (enabled) fg else fg.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A selectable chip. Atelier draws it as an underlined tab, Instrument as a segment, Gallery as a pill.
 * [role] tells screen readers what it is: a tab, one choice of several (radio), or an on/off filter (checkbox).
 */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    underline: Boolean = WatchTheme.style.direction == Direction.ATELIER,
    role: Role = Role.Tab,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val shape = style.chipShape
    val fg by animateColorAsState(
        when {
            selected && (underline || style.direction == Direction.ATELIER) -> c.fg
            selected -> c.accent
            style.direction == Direction.GALLERY -> c.fg2
            else -> c.fg3
        },
        tween(150), label = "chip",
    )
    val mod = modifier
        .heightIn(min = 48.dp)
        .clip(shape)
        .then(
            if (role == Role.Checkbox) Modifier.toggleable(value = selected, role = role, onValueChange = { onClick() })
            else Modifier.selectable(selected = selected, role = role, onClick = onClick),
        )
    when {
        underline -> Box(mod.padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
            Text(text, style = t.type.bodySmall, color = fg, modifier = Modifier.padding(vertical = 12.dp))
            // matchParentSize: inside a horizontally scrolling row, fillMaxWidth alone would measure to zero.
            if (selected) Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.accent))
            }
        }
        style.direction == Direction.INSTRUMENT -> Box(
            mod.border(1.dp, if (selected) c.accent else c.lineStrong, shape)
                .background(if (selected) c.surface2 else Color.Transparent)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text.uppercase(), style = t.type.label, color = fg)
        }
        else -> Box(
            mod.background(if (selected) c.accentTint else c.surface2).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = t.type.button, color = fg)
        }
    }
}

/** Chips that wrap onto lines. One choice by default; [multi] makes each an independent on/off filter. */
@Composable
fun ChipGroup(
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    multi: Boolean = false,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((key, label) in options) {
            SelectChip(label, key in selected, { onToggle(key) }, underline = false, role = if (multi) Role.Checkbox else Role.RadioButton)
        }
    }
}

/**
 * One choice out of two to four, as joined segments of equal width: clearer than loose chips when
 * the options are a set (present / missing / not applicable). [tint] colours a selected segment,
 * so "Missing" can read as a warning.
 */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    spoken: (T, String) -> String = { _, label -> label },
    tint: @Composable (T) -> Color = { WatchTheme.colors.accent },
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val shape = style.chipShape
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).border(1.dp, c.lineStrong, shape)) {
        options.forEachIndexed { i, (value, label) ->
            val on = value == selected
            val color = tint(value)
            val bg by animateColorAsState(if (on) color.copy(alpha = if (c.isDark) 0.22f else 0.14f) else Color.Transparent, tween(150), label = "segment")
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.lineStrong))
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).background(bg)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) })
                    .semantics { contentDescription = spoken(value, label) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (style.direction == Direction.INSTRUMENT) label.uppercase() else label,
                    style = if (style.direction == Direction.INSTRUMENT) t.type.label else t.type.button,
                    color = if (on) color else c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** Small label above a field or section, styled by direction. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, color: Color = WatchTheme.colors.fg3) {
    val t = WatchTheme.values
    Text(t.label(text), style = t.type.label, color = color, modifier = modifier)
}

/**
 * The app's text field: label above, value below on an underline (Atelier, Instrument)
 * or in a soft filled box (Gallery). The underline turns to the accent while focused.
 */
@Composable
fun WatchTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    supporting: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    secret: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    textStyle: TextStyle? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    autoCorrect: Boolean = true,
    showLabel: Boolean = true,
    leading: ImageVector? = null,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val gallery = style.direction == Direction.GALLERY
    val lineColor = when {
        error != null -> c.danger
        focused -> c.accent
        else -> c.lineStrong
    }
    Column(modifier) {
        if (showLabel) {
            FieldLabel(label)
            Spacer(Modifier.height(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = (textStyle ?: t.type.body).copy(color = c.fg),
            cursorBrush = SolidColor(c.accent),
            interactionSource = interaction,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = if (keyboardType == KeyboardType.Text) capitalization else KeyboardCapitalization.None,
                autoCorrectEnabled = autoCorrect && keyboardType == KeyboardType.Text && !secret,
                keyboardType = keyboardType,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .then(
                            if (gallery) {
                                Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface)
                                    .border(BorderStroke(1.dp, if (focused || error != null) lineColor else c.lineStrong.copy(alpha = 0.7f)), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            } else {
                                Modifier.underline(lineColor).padding(vertical = 10.dp)
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leading != null) {
                        Icon(leading, null, tint = if (focused) c.accent else c.fg3, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = t.type.body, color = c.fg3)
                        inner()
                    }
                    if (suffix != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(suffix, style = t.type.specUnit, color = c.fg3)
                    }
                    if (trailing != null) trailing()
                }
            },
        )
        val note = error ?: supporting
        if (note != null) {
            Spacer(Modifier.height(4.dp))
            Text(note, style = t.type.specSource, color = if (error != null) c.danger else c.fg3)
        }
    }
}

fun Modifier.underline(color: Color, thickness: Dp = 1.dp): Modifier = drawBehind {
    val y = size.height - thickness.toPx() / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), thickness.toPx())
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = t.type.groupTitle, color = c.fg) },
        text = { Text(message, style = t.type.body, color = c.fg2) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, style = t.type.button, color = if (destructive) c.danger else c.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = t.type.button, color = c.fg2) }
        },
    )
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(io.github.tunlezah.watches.ui.theme.WatchIcons.Watch, null, tint = c.fg3, modifier = Modifier.size(40.dp))
        Text(t.title(title), style = t.type.groupTitle, color = c.fg, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Text(message, style = t.type.body, color = c.fg2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

val ScreenContentPadding = PaddingValues(bottom = 120.dp)
