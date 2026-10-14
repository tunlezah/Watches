package io.github.tunlezah.watches.ui.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.lookup.DiffRow
import io.github.tunlezah.watches.core.model.Sources
import io.github.tunlezah.watches.core.spec.SpecFormatter
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.FieldLabel
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

@Composable
fun ApplyScreen(
    state: ApplyUiState,
    prefs: DisplayPrefs,
    onBack: () -> Unit,
    onChoose: (String, Int?) -> Unit,
    onToggleExtras: (String) -> Unit,
    onApply: () -> Unit,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val pad = style.screenPadding

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(WatchIcons.Back, "Back", onBack)
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(t.title("Review changes"), style = t.type.groupTitle, color = c.fg, modifier = Modifier.semantics { heading() })
                if (state.targetTitle.isNotEmpty()) Text(state.targetTitle, style = t.type.bodySmall, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (state.error != null) {
            EmptyState("Nothing to review", state.error) { WatchButton("Back", onBack, kind = ButtonKind.Primary) }
            return@Column
        }
        if (!state.loaded) return@Column
        val changeable = state.rows.filter { !it.matchesCurrent }
        val matching = state.rows.filter { it.matchesCurrent }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 140.dp)) {
                item {
                    Text(
                        "Ticked values are written, with their source. Values you typed or edited start unticked, " +
                            "so a lookup only replaces one if you tick it.",
                        style = t.type.bodySmall, color = c.fg2, modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (changeable.isEmpty() && state.extras.isEmpty()) {
                    item { Text("These sources match what's already there.", style = t.type.body, color = c.fg) }
                }
                items(changeable, key = { it.field.key }) { row ->
                    DiffRowView(row, state.choices[row.field.key], prefs) { onChoose(row.field.key, it) }
                    HorizontalDivider(color = c.line)
                }
                if (state.extras.isNotEmpty()) {
                    item { FieldLabel("Other specs", Modifier.padding(top = 20.dp, bottom = 6.dp)) }
                    items(state.extras, key = { "extras-${it.source}" }) { e ->
                        CheckRow(
                            checked = e.keep,
                            title = "Keep ${e.specs.size} more from ${Sources.displayName(e.source)}",
                            detail = e.specs.joinToString(" · ") { it.label },
                            onClick = { onToggleExtras(e.source) },
                        )
                    }
                }
                if (matching.isNotEmpty()) {
                    item {
                        Text(
                            "Already matching: " + matching.joinToString(", ") { it.field.label },
                            style = t.type.specSource, color = c.fg3, modifier = Modifier.padding(top = 18.dp),
                        )
                    }
                }
            }
            val label = when (state.target) {
                LookupTarget.None -> "Continue to the form"
                is LookupTarget.Form -> "Fill the form"
                is LookupTarget.Watch -> if (state.changeCount == 0) "Nothing to apply" else "Apply ${state.changeCount} ${if (state.changeCount == 1) "change" else "changes"}"
            }
            WatchButton(
                if (state.applying) "Saving…" else label,
                onApply,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                kind = ButtonKind.Primary,
                enabled = !state.applying && (state.target !is LookupTarget.Watch || state.changeCount > 0),
            )
        }
    }
}

@Composable
private fun DiffRowView(row: DiffRow, chosen: Int?, prefs: DisplayPrefs, onChoose: (Int?) -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.field.label, style = t.type.specLabel, color = c.fg, modifier = Modifier.weight(1f))
            if (row.protected) Tag("Your value")
        }
        Text(
            "Now: " + (row.current?.let { SpecFormatter.format(row.field, it, prefs).plain } ?: "—"),
            style = t.type.bodySmall, color = c.fg2,
        )
        row.options.forEachIndexed { i, option ->
            val sources = (listOf(option.source) + option.agreeing).joinToString(", ") { Sources.shortName(it) }
            val agree = if (option.agreeing.isEmpty()) sources else "$sources agree"
            CheckRow(
                checked = chosen == i,
                title = SpecFormatter.format(row.field, option.value, prefs).plain,
                detail = if (row.isCurrent(i)) "$agree · same as now" else agree,
                onClick = { onChoose(i) },
                radio = row.conflict,
            )
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, title: String, detail: String?, onClick: () -> Unit, radio: Boolean = false) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
            .clickable(role = if (radio) Role.RadioButton else Role.Checkbox, onClick = onClick)
            .semantics { stateDescription = if (checked) "Ticked" else "Not ticked" }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = if (radio) RoundedCornerShape(50) else RoundedCornerShape(5.dp)
        Box(
            Modifier.size(22.dp).clip(shape).background(if (checked) c.accent else c.bg).border(1.5.dp, if (checked) c.accent else c.fg3, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(WatchIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = t.type.specValue, color = c.fg, maxLines = 3, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = t.type.specSource, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Tag(text: String) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Text(text, style = t.type.specSource, color = c.accent, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accentTint).padding(horizontal = 8.dp, vertical = 2.dp))
}
