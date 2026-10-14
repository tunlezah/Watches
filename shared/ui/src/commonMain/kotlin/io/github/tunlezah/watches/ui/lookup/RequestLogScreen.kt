package io.github.tunlezah.watches.ui.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.format.Numbers
import io.github.tunlezah.watches.search.http.LoggedRequest
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun RequestLogScreen(entries: List<LoggedRequest>, onBack: () -> Unit, onClear: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(WatchIcons.Back, "Back", onBack)
            Text(t.title("Request log"), style = t.type.groupTitle, color = c.fg, modifier = Modifier.weight(1f).semantics { heading() })
            if (entries.isNotEmpty()) WatchButton("Clear", onClear, kind = ButtonKind.Text)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = style.screenPadding, end = style.screenPadding, bottom = 40.dp)) {
            item {
                Text(
                    "Every request the app has made since it opened, newest first, including ones it refused to send. " +
                        "The log is kept in memory only and is gone when the app closes.",
                    style = t.type.bodySmall, color = c.fg2, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (entries.isEmpty()) {
                item { Text("No requests yet.", style = t.type.body, color = c.fg, modifier = Modifier.padding(top = 12.dp)) }
            }
            items(entries) { e ->
                val time = e.at.toLocalDateTime(TimeZone.currentSystemDefault()).time
                val clock = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}:${time.second.toString().padStart(2, '0')}"
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(clock, style = t.type.specSource, color = c.fg3)
                        Text(
                            "  ${e.host}",
                            style = t.type.specLabel, color = c.fg, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(e.status?.toString(), if (e.sent) "${e.millis} ms" else null, e.bytes?.let { if (it < 1024) "$it B" else "${Numbers.whole(it.toLong() / 1024)} KB" }).joinToString(" · "),
                            style = t.type.specSource, color = c.fg3,
                        )
                    }
                    Text(e.url.substringAfter(e.host), style = t.type.bodySmall, color = c.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(e.outcome, style = t.type.specSource, color = if (e.sent && e.status in 200..399) c.fg3 else c.danger)
                }
                HorizontalDivider(color = c.line)
            }
        }
    }
}
