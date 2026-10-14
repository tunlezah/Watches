package io.github.tunlezah.watches.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.format.Numbers
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onBack: () -> Unit,
    onGenerate: (Int) -> Unit,
    onRemove: () -> Unit,
    onRebuild: () -> Unit,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(WatchIcons.Back, "Back", onBack)
            Text(t.title("Diagnostics"), style = t.type.groupTitle, color = c.fg, modifier = Modifier.semantics { heading() })
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = style.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(t.title("Timings on this phone"), style = t.type.groupTitle, color = c.fg, modifier = Modifier.padding(top = 8.dp))
            Text("Measured in this session on this device. Targets are from the brief, for a release build on the Motorola Edge 60 Fusion.",
                style = t.type.bodySmall, color = c.fg2)
            val p = state.perf
            Metric("Cold start to collection", p.coldStartMs?.let { "${it} ms" }, "under 1,000 ms", p.coldStartMs?.let { it < 1000 })
            Metric("Search update (last)", p.lastSearchMs?.let { "${Numbers.decimal(it, 1)} ms" }, "under 100 ms", p.lastSearchMs?.let { it < 100 })
            Metric("Search update (slowest)", p.worstSearchMs?.let { "${Numbers.decimal(it, 1)} ms" }, "under 100 ms", p.worstSearchMs?.let { it < 100 })
            Metric("Open watch detail (last)", p.lastDetailOpenMs?.let { "$it ms" }, "under 150 ms", p.lastDetailOpenMs?.let { it < 150 })
            Metric("Open watch detail (slowest)", p.worstDetailOpenMs?.let { "$it ms" }, "under 150 ms", p.worstDetailOpenMs?.let { it < 150 })
            Text("Grid smoothness: scroll quickly through 500 watches. To measure dropped frames, turn on Developer options › Profile HWUI rendering, or run the macrobenchmark in the README.",
                style = t.type.bodySmall, color = c.fg2)

            Text(t.title("Sample collection"), style = t.type.groupTitle, color = c.fg, modifier = Modifier.padding(top = 12.dp))
            Text("${state.total} watches in total, ${state.samples} of them samples. Samples are realistic watches with a generated photo each, made on the phone. Removing them deletes only what this tool added.",
                style = t.type.bodySmall, color = c.fg2)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.line)
            state.progress?.let { Text(it, style = t.type.bodySmall, color = c.fg) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WatchButton("Add 500 samples", { onGenerate(500) }, Modifier.weight(1f), kind = ButtonKind.Primary, enabled = !state.busy)
                WatchButton("Add 50", { onGenerate(50) }, Modifier.weight(0.6f), enabled = !state.busy)
            }
            WatchButton("Remove sample watches", onRemove, Modifier.fillMaxWidth(), kind = ButtonKind.Danger, enabled = !state.busy && state.samples > 0)
            WatchButton("Rebuild search index", onRebuild, Modifier.fillMaxWidth(), enabled = !state.busy)
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun Metric(label: String, value: String?, target: String, ok: Boolean?) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = t.type.specLabel, color = c.fg2)
                Text("Target $target", style = t.type.specSource, color = c.fg3)
            }
            Text(value ?: "not measured yet", style = t.type.specValue, color = when (ok) { true -> c.fg; false -> c.danger; null -> c.fg3 })
        }
        HorizontalDivider(color = c.line)
    }
}
