package io.github.tunlezah.watches.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.model.Currencies
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The header every pushed screen shares: back, title and subtitle, then optional actions. */
@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: () -> Unit, backLabel: String = "Back", actions: @Composable RowScope.() -> Unit = {}) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(WatchIcons.Back, backLabel, onBack)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(t.title(title), style = t.type.headerTitle, color = c.fg, modifier = Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = t.type.bodySmall, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        actions()
    }
}

/** A section heading inside a scrolling screen. Atelier sets an accent dash above it. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp)) {
        if (WatchTheme.style.accentDash) {
            AccentDash()
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.title(text), style = t.type.groupTitle, color = c.fg, modifier = Modifier.weight(1f).semantics { heading() })
            trailing?.invoke()
        }
    }
}

/** A tappable row with an icon, a title, a line of detail and a chevron. */
@Composable
fun NavCard(icon: ImageVector, title: String, detail: String?, onClick: () -> Unit, modifier: Modifier = Modifier, emphasis: Boolean = false, badge: String? = null) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).surface(style.cardShape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (emphasis) c.danger else c.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = t.type.body, color = c.fg, modifier = Modifier.weight(1f, fill = false))
                badge?.let {
                    Spacer(Modifier.width(8.dp))
                    Badge(it)
                }
            }
            detail?.let { Text(it, style = t.type.bodySmall, color = if (emphasis) c.danger else c.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Icon(WatchIcons.Forward, null, tint = c.fg3, modifier = Modifier.size(18.dp))
    }
}

/** A small rounded label: "Full set", "Overdue". */
@Composable
fun Badge(text: String, color: Color = WatchTheme.colors.accent, background: Color = WatchTheme.colors.accentTint) {
    val t = WatchTheme.values
    Text(t.label(text), style = t.type.specSource, color = color, maxLines = 1,
        modifier = Modifier.clip(WatchTheme.style.statusShape).background(background).padding(horizontal = 8.dp, vertical = 2.dp))
}

/** A date chosen with the system date picker. [onPick] gets null when cleared. */
@Composable
fun DatePickerField(label: String, value: LocalDate?, format: (LocalDate) -> String, onPick: (LocalDate?) -> Unit, clearable: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip(value?.let(format) ?: "Choose date", value != null, { open = true }, underline = false, role = Role.Button)
            if (value != null && clearable) IconAction(WatchIcons.Close, "Clear ${label.lowercase()}", { onPick(null) })
        }
    }
    if (open) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = value?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    picker.selectedDateMillis?.let { onPick(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                    open = false
                }) { Text("OK", style = t.type.button, color = c.accent) }
            },
            dismissButton = { TextButton({ open = false }) { Text("Cancel", style = t.type.button, color = c.fg2) } },
        ) { DatePicker(picker) }
    }
}

/** An amount with its currency, AUD by default. */
@Composable
fun MoneyField(label: String, text: String, currency: String, onText: (String) -> Unit, onCurrency: (String) -> Unit, error: String? = null) {
    var menu by remember { mutableStateOf(false) }
    val t = WatchTheme.values
    val c = WatchTheme.colors
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            Column {
                FieldLabel("Currency")
                Spacer(Modifier.height(6.dp))
                SelectChip(currency, true, { menu = true }, underline = false, role = Role.Button)
            }
            DropdownMenu(menu, { menu = false }, containerColor = c.surface) {
                Currencies.common.forEach { code ->
                    DropdownMenuItem(text = { Text("$code  ${Currencies.symbol(code).trim()}", style = t.type.body, color = c.fg) }, onClick = { onCurrency(code); menu = false })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            WatchTextField(label = label, value = text, onValueChange = onText, keyboardType = KeyboardType.Decimal, error = error, placeholder = "0")
        }
    }
}
