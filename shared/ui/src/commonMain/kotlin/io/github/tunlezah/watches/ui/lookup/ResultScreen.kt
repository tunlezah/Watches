package io.github.tunlezah.watches.ui.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.tunlezah.watches.ui.components.BottomActionBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.core.model.Sources
import io.github.tunlezah.watches.core.spec.FormattedValue
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecFormatter
import io.github.tunlezah.watches.core.spec.SpecGroup
import io.github.tunlezah.watches.core.spec.SpecGroupSheet
import io.github.tunlezah.watches.core.spec.SpecRow
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.SpecGroupView
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

/** The spec sheet a record would give, grouped like the watch's own, plus its extra specs. */
fun recordSheets(record: SourceRecord, prefs: DisplayPrefs): List<SpecGroupSheet> {
    val groups = SpecGroup.entries.mapNotNull { group ->
        val rows = SpecFields.inGroup(group).mapNotNull { f ->
            record.values[f.key]?.let { v -> SpecRow(listOf(f.key), f.label, SpecFormatter.format(f, v, prefs), null, null, userEdited = false) }
        }
        rows.takeIf { it.isNotEmpty() }?.let { SpecGroupSheet(group, it) }
    }
    val extras = record.extras.filter { it.label != "About" }.mapIndexed { i, e ->
        SpecRow(listOf("extra.$i"), e.label, FormattedValue(e.value), e.source?.let(Sources::shortName), e.url, userEdited = false, longText = e.value.length > 60)
    }
    return groups + listOfNotNull(extras.takeIf { it.isNotEmpty() }?.let { SpecGroupSheet(SpecGroup.IDENTITY, it) })
}

@Composable
fun ResultScreen(
    state: ResultUiState,
    images: ImageSource,
    prefs: DisplayPrefs,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggle: () -> Unit,
    onSavePicture: (io.github.tunlezah.watches.core.lookup.RecordImage) -> Unit,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val uri = LocalUriHandler.current
    val pageUrl = state.record?.url ?: state.hit?.url

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(WatchIcons.Back, "Back to results", onBack)
            Text(state.sourceName, style = t.type.label, color = c.fg2, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pageUrl != null) IconAction(WatchIcons.External, "Open the page in your browser", { uri.openUri(pageUrl) })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val record = state.record
            when {
                state.loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = c.accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Fetching from ${state.sourceName}…", style = t.type.bodySmall, color = c.fg2)
                }
                record == null -> EmptyState("Couldn't load this result", state.failure ?: "Something went wrong.") {
                    if (state.hit != null) WatchButton("Try again", onRetry, kind = ButtonKind.Primary)
                }
                else -> {
                    val sheets = remember(record, prefs) { recordSheets(record, prefs) }
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = style.screenPadding).padding(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        record.images.firstOrNull()?.let { image ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RemoteImage(image.url, images, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(style.cardShape), description = "Picture of ${record.title}")
                                Text(
                                    if (image.savable) "Picture: ${image.credit ?: "Wikimedia Commons"}. Freely licensed; its credit is kept if you save it."
                                    else "Picture: ${image.credit ?: state.sourceName}. Shown from the site, never saved.",
                                    style = t.type.specSource, color = c.fg3,
                                )
                                if (image.savable && state.canSavePictures) {
                                    WatchButton("Save picture to this watch", { onSavePicture(image) }, kind = ButtonKind.Tonal, icon = WatchIcons.Image)
                                }
                                state.pictureStatus?.let { Text(it, style = t.type.bodySmall, color = c.fg2) }
                            }
                        }
                        Column {
                            Text(record.title, style = t.type.heroTitle, color = c.fg, modifier = Modifier.semantics { heading() })
                            state.hit?.subtitle?.let { Text(it, style = t.type.bodySmall, color = c.fg2) }
                        }
                        record.summary?.let { summary ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(summary, style = t.type.body, color = c.fg2, maxLines = 12, overflow = TextOverflow.Ellipsis)
                                record.summaryCredit?.let { Text(it, style = t.type.specSource, color = c.fg3) }
                            }
                        }
                        if (sheets.isEmpty()) {
                            Text("This page had no specs the app could read.", style = t.type.bodySmall, color = c.fg2)
                        }
                        val extrasTitle = "More from ${state.sourceName}"
                        sheets.forEachIndexed { i, sheet ->
                            val isExtras = sheet.rows.firstOrNull()?.keys?.first()?.startsWith("extra.") == true
                            SpecGroupView(sheet, i + 1, emptySet(), {}, {}, title = if (isExtras) extrasTitle else sheet.group.label)
                        }
                    }
                    BottomActionBar {
                        WatchButton(
                            if (state.inTray) "Chosen · tap to remove" else "Use these specs",
                            onToggle,
                            Modifier.fillMaxWidth(),
                            kind = if (state.inTray) ButtonKind.Secondary else ButtonKind.Primary,
                            icon = if (state.inTray) WatchIcons.Check else null,
                        )
                    }
                }
            }
        }
    }
}
