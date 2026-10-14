package io.github.tunlezah.watches.ui.lookup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

/** Source switches, the WatchBase connection test and the request log, inside Settings. */
@Composable
fun LookupSettingsSection(
    state: SourcesUiState,
    onToggle: (String) -> Unit,
    onTestWatchBase: () -> Unit,
    onRequestLog: () -> Unit,
    onClearRecent: () -> Unit,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (s in state.sources) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Switch) { onToggle(s.info.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.info.name, style = t.type.body, color = c.fg)
                    Text(s.info.coverage.short, style = t.type.specSource, color = c.accent)
                    Text(s.info.terms, style = t.type.bodySmall, color = c.fg3)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = s.enabled,
                    onCheckedChange = { onToggle(s.info.id) },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent, uncheckedTrackColor = c.surface2, uncheckedBorderColor = c.line),
                )
            }
        }
        state.watchBase?.let { wb ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${wb.name} · not searchable yet", style = t.type.body, color = c.fg)
                Text(wb.terms, style = t.type.bodySmall, color = c.fg3)
                Text(
                    "Test whether the app gets through from this phone. It sends one request to watchbase.com, identified as this app.",
                    style = t.type.bodySmall, color = c.fg2,
                )
                WatchButton(if (state.testing) "Testing…" else "Test connection", onTestWatchBase, kind = ButtonKind.Tonal, enabled = !state.testing)
                state.watchBaseResult?.let { Text(it, style = t.type.bodySmall, color = c.fg) }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onRequestLog).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Request log", style = t.type.body, color = c.fg)
                Text("Every request since the app opened", style = t.type.bodySmall, color = c.fg3)
            }
            Icon(WatchIcons.Forward, null, tint = c.fg3, modifier = Modifier.size(20.dp))
        }
        WatchButton("Clear recent searches", onClearRecent, kind = ButtonKind.Text)
    }
}
