package io.github.tunlezah.watches.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.model.WatchDocument
import io.github.tunlezah.watches.ui.components.Badge
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.FieldLabel
import io.github.tunlezah.watches.ui.components.ScreenHeader
import io.github.tunlezah.watches.ui.components.SectionTitle
import io.github.tunlezah.watches.ui.components.SelectChip
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.components.WatchLook
import io.github.tunlezah.watches.ui.components.WatchPhoto
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

/** The Service tab: what's overdue, what's coming up, and warranties about to end, across the collection. */
@Composable
fun ServiceDueScreen(state: ServiceDueUiState, onOpen: (DueItem) -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(start = style.screenPadding, end = style.screenPadding, bottom = 140.dp),
    ) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Text(t.title("Service"), style = t.type.screenTitle, color = c.fg, modifier = Modifier.padding(top = 20.dp).semantics { heading() })
            if (state.loaded) {
                val parts = listOfNotNull(
                    state.overdue.size.takeIf { it > 0 }?.let { "$it overdue" },
                    state.soon.size.takeIf { it > 0 }?.let { "$it due within 3 months" },
                    state.inService.size.takeIf { it > 0 }?.let { "$it at the watchmaker" },
                )
                Text(parts.joinToString(" · ").ifEmpty { "Nothing due in the next 3 months." }, style = t.type.bodySmall, color = c.fg2, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (state.loaded && state.onHand == 0) {
            item { EmptyState("No watches on hand", "Owned watches and ones at the watchmaker appear here once they have a service interval.", Modifier.padding(top = 40.dp)) }
        }
        dueSection("Overdue", state.overdue, onOpen)
        dueSection("Due soon", state.soon, onOpen)
        dueSection("At the watchmaker", state.inService, onOpen)
        dueSection("Warranties ending", state.warranties, onOpen)
        dueSection("Later", state.later, onOpen)
        dueSection("Needs a start date", state.noStart, onOpen)
        if (state.noInterval > 0) {
            item {
                Text(
                    "${state.noInterval} ${if (state.noInterval == 1) "watch has" else "watches have"} no service interval. Set one on the watch's Service screen to see when it's due.",
                    style = t.type.bodySmall, color = c.fg3, modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dueSection(title: String, items: List<DueItem>, onOpen: (DueItem) -> Unit) {
    if (items.isEmpty()) return
    item(key = "h-$title") { SectionTitle(title) }
    items(items, key = { "$title-${it.watchId}-${it.headline}" }) { item -> DueRow(item) { onOpen(item) } }
}

@Composable
private fun DueRow(item: DueItem, onClick: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WatchPhoto(item.thumb, WatchLook.guess(null, null, emptySet()), contentDescription = item.title,
            modifier = Modifier.size(52.dp).clip(WatchTheme.style.tileShape), illustrationPadding = 6.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = t.type.tileTitle, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.headline, style = t.type.bodySmall, color = if (item.urgent) c.danger else c.fg, maxLines = 2)
            item.detail?.let { Text(it, style = t.type.specSource, color = c.fg3, maxLines = 2) }
        }
        Icon(WatchIcons.Forward, null, tint = c.fg3, modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(color = c.line)
}

private val intervalChoices = listOf(12, 24, 36, 48, 60, 84, 120)


@Composable
fun ServiceHistoryScreen(
    state: ServiceHistoryUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenDocument: (WatchDocument) -> Unit,
    onInterval: (Int?) -> Unit,
    onReminder: (Boolean) -> Unit,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(Modifier.fillMaxSize().background(c.bg)) {
        ScreenHeader("Service", state.title, onBack)
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = style.screenPadding, end = style.screenPadding, bottom = 120.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().clip(style.cardShape).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.due ?: "No service interval set", style = t.type.body, color = if (state.dueUrgent) c.danger else c.fg)
                        state.lastService?.let { Text("Last serviced $it", style = t.type.bodySmall, color = c.fg2) }
                        FieldLabel("Service interval")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip("None", state.intervalMonths == null, { onInterval(null) }, underline = false)
                            val choices = (intervalChoices + listOfNotNull(state.intervalMonths)).distinct().sorted()
                            for (m in choices) SelectChip(monthsLabel(m), state.intervalMonths == m, { onInterval(m) }, underline = false)
                        }
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Switch, enabled = state.intervalMonths != null) { onReminder(!state.reminder) }, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Remind me when it's due", style = t.type.body, color = if (state.intervalMonths != null) c.fg else c.fg3)
                                Text("A notification on this phone at 9 am on the due date.", style = t.type.bodySmall, color = c.fg3)
                            }
                            Switch(
                                checked = state.reminder, onCheckedChange = onReminder, enabled = state.intervalMonths != null,
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent, uncheckedTrackColor = c.surface2, uncheckedBorderColor = c.line),
                            )
                        }
                        if (state.notificationsBlocked) {
                            Text("Notifications are turned off for Watches. Allow them in the phone's settings to get the reminder.", style = t.type.bodySmall, color = c.danger)
                        }
                    }
                }
                item { SectionTitle("History") }
                if (state.loaded && state.entries.isEmpty()) {
                    item { Text("No services logged yet. Log the last one so the next is counted from it.", style = t.type.bodySmall, color = c.fg2) }
                }
                items(state.entries, key = { it.id }) { e -> EntryRow(e, { onOpenEntry(e.id) }, onOpenDocument) }
            }
            WatchButton("Log a service", onAdd, Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp), kind = ButtonKind.Primary, icon = WatchIcons.Plus)
        }
    }
}

@Composable
private fun EntryRow(e: EntryUi, onClick: () -> Unit, onOpenDocument: (WatchDocument) -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.date, style = t.type.specValue, color = c.fg, modifier = Modifier.weight(1f))
            e.cost?.let { Text(it, style = t.type.specValue, color = c.fg2) }
        }
        Text(e.work, style = t.type.body, color = c.fg)
        e.by?.let { Text(it, style = t.type.bodySmall, color = c.fg2) }
        e.warranty?.let { Text(it, style = t.type.bodySmall, color = c.fg2) }
        e.notes?.let { Text(it, style = t.type.bodySmall, color = c.fg3, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        if (e.documents.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in e.documents) SelectChip("${d.title} · ${CareText.docType(d)}", false, { onOpenDocument(d) }, underline = false)
            }
        }
    }
    HorizontalDivider(color = c.line)
}

/** Shown in place of a count when a list is still loading or empty, so badges never read "0 overdue". */
@Composable
internal fun DueBadge(urgent: Boolean, text: String) {
    val c = WatchTheme.colors
    if (urgent) Badge(text, color = c.danger, background = c.danger.copy(alpha = 0.14f)) else Badge(text)
}

/** Settings: whether reminders can be shown, and asking if not. Reminders are switched on per watch. */
@Composable
fun RemindersSettings(granted: Boolean, onAllow: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Switch a reminder on from a watch's Service screen. It arrives as a notification at 9 am on the day the service is due. Nothing is sent anywhere.",
            style = t.type.bodySmall, color = c.fg2,
        )
        if (granted) Text("Notifications are allowed.", style = t.type.body, color = c.fg)
        else WatchButton("Allow notifications", onAllow, kind = ButtonKind.Tonal)
    }
}
