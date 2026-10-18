package io.github.tunlezah.watches.ui.lookup

import io.github.tunlezah.watches.ui.components.Masthead
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.tunlezah.watches.core.lookup.SearchHit
import io.github.tunlezah.watches.core.model.Sources
import io.github.tunlezah.watches.core.model.WatchSummary
import io.github.tunlezah.watches.search.SourceState
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.FieldLabel
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.SelectChip
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.components.underline
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

class LookupActions(
    val onBack: (() -> Unit)?,
    val onQuery: (String) -> Unit,
    val onSearch: (String?) -> Unit,
    val onForgetRecent: (String) -> Unit,
    val onClearRecent: () -> Unit,
    val onToggleSource: (String) -> Unit,
    val onOpen: (SearchHit) -> Unit,
    val onRetry: () -> Unit,
    val onReview: (LookupTarget) -> Unit,
    val onClearTray: () -> Unit,
)

@Composable
fun LookupScreen(state: LookupUiState, images: ImageSource, watchChoices: List<WatchSummary>, actions: LookupActions) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    var picking by remember { mutableStateOf(false) }
    val pad = style.screenPadding

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxWidth()) {
            val title = if (state.target == LookupTarget.None) "Lookup" else "Look up specs"
            val onBack = actions.onBack
            if (onBack == null) {
                // The Lookup tab: a masthead like the other tabs.
                Box(Modifier.padding(horizontal = pad).padding(bottom = 4.dp)) { Masthead(title, subtitle = state.targetTitle?.let { "For $it" }) }
            } else {
                Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(WatchIcons.Back, "Back", onBack)
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(t.title(title), style = t.type.screenTitle, color = c.fg, modifier = Modifier.semantics { heading() })
                        state.targetTitle?.let { Text("For $it", style = t.type.bodySmall, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            SearchField(state.query, actions.onQuery, { actions.onSearch(null) }, Modifier.padding(horizontal = pad, vertical = 8.dp))
            FlowRow(Modifier.padding(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in state.sources) {
                    SelectChip(Sources.shortName(s.info.id), s.enabled, { actions.onToggleSource(s.info.id) }, underline = false, role = Role.Checkbox)
                }
            }
            if (state.offline) {
                Notice("You're offline. Lookups need a connection, and nothing is sent while offline.", Modifier.padding(horizontal = pad, vertical = 8.dp))
            } else if (!state.anyEnabled) {
                Notice("Every source is switched off. Tap one above to search it.", Modifier.padding(horizontal = pad, vertical = 8.dp))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = pad, end = pad, top = 8.dp, bottom = 160.dp)) {
                if (state.searched == null) {
                    item {
                        Text(
                            "Search for a watch, reference or calibre. Each source is asked once, only when you press Search, " +
                                "and every request is listed in Settings › Request log.",
                            style = t.type.bodySmall, color = c.fg2, modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    if (state.recent.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                FieldLabel("Recent searches", Modifier.weight(1f))
                                WatchButton("Clear", actions.onClearRecent, kind = ButtonKind.Text)
                            }
                        }
                        items(state.recent, key = { "recent-$it" }) { q ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { actions.onSearch(q) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(WatchIcons.History, null, tint = c.fg3, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(q, style = t.type.body, color = c.fg, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconAction(WatchIcons.Close, "Remove $q from recent searches", { actions.onForgetRecent(q) }, tint = c.fg3)
                            }
                        }
                    }
                } else {
                    for (s in state.sources.filter { it.enabled }) {
                        item(key = "head-${s.info.id}") { SourceHeader(s, actions.onRetry) }
                        val hits = (s.state as? SourceState.Results)?.hits.orEmpty()
                        items(hits, key = { "${it.source}/${it.key}" }) { hit ->
                            val chosen = state.tray.any { it.source == hit.source && it.key == hit.key }
                            HitRow(hit, chosen, images) { actions.onOpen(hit) }
                        }
                    }
                }
            }
            if (state.tray.isNotEmpty()) {
                TrayBar(state, Modifier.align(Alignment.BottomCenter), onReview = {
                    if (state.target == LookupTarget.None) actions.onReview(LookupTarget.None) else actions.onReview(state.target)
                }, onPick = { picking = true }, onClear = actions.onClearTray)
            }
        }
    }

    if (picking) {
        WatchPicker(watchChoices, onPick = { picking = false; actions.onReview(LookupTarget.Watch(it)) }, onDismiss = { picking = false })
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val boxed = style.boxedFields
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = t.type.body.copy(color = c.fg),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Watch, reference or calibre" },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .then(
                        if (boxed) Modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).padding(start = 14.dp)
                        else Modifier.underline(c.lineStrong),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(WatchIcons.Search, null, tint = c.fg3, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Omega Speedmaster, 116610LN, ETA 2824-2", style = t.type.body, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                WatchButton("Search", onSearch, kind = ButtonKind.Text, enabled = value.isNotBlank())
            }
        },
    )
}

@Composable
private fun SourceHeader(s: SourceUi, onRetry: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.title(s.info.name), style = t.type.groupTitle, color = c.fg, modifier = Modifier.semantics { heading() })
                Text(s.info.coverage.short, style = t.type.specSource, color = c.fg3)
            }
            when (val st = s.state) {
                SourceState.Loading, null -> CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
                is SourceState.Results -> Text(if (st.hits.isEmpty()) "No matches" else "${st.hits.size} found", style = t.type.specSource, color = c.fg3)
                is SourceState.Failed -> WatchButton("Retry", onRetry, kind = ButtonKind.Text)
            }
        }
        (s.state as? SourceState.Failed)?.let {
            Text(it.failure.describe(s.info.name), style = t.type.bodySmall, color = c.danger, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, chosen: Boolean, images: ImageSource, onClick: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hit.thumbnailUrl?.let {
            RemoteImage(it, images, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(hit.title, style = t.type.tileTitle, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            hit.subtitle?.let { Text(it, style = t.type.tileMeta, color = c.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (chosen) {
            Icon(WatchIcons.Check, "Chosen", tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
        }
        Icon(WatchIcons.Forward, null, tint = c.fg3, modifier = Modifier.size(18.dp))
    }
}

/** A picture from a source site, fetched politely while it's on screen and held in memory for this search only. */
@Composable
fun RemoteImage(url: String, images: ImageSource, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, description: String? = null) {
    val c = WatchTheme.colors
    val bytes by produceState<ByteArray?>(null, url) { value = images.load(this, url) }
    Box(modifier.background(c.surface2), contentAlignment = Alignment.Center) {
        val b = bytes
        if (b == null) {
            // Pictures trickle in at one request per second per site; a quiet glyph holds the place.
            Icon(WatchIcons.Image, null, tint = c.fg3.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        } else {
            AsyncImage(b, contentDescription = description, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TrayBar(state: LookupUiState, modifier: Modifier, onReview: () -> Unit, onPick: () -> Unit, onClear: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).navigationBarsPadding()
            .clip(style.cardShape).background(c.surface).border(1.dp, c.line, style.cardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Using " + state.tray.joinToString(" + ") { "${Sources.shortName(it.source)}: ${it.title}" },
                style = t.type.bodySmall, color = c.fg, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            IconAction(WatchIcons.Close, "Clear chosen results", onClear, tint = c.fg3)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state.target) {
                LookupTarget.None -> {
                    WatchButton("Add to collection", onReview, Modifier.weight(1f), kind = ButtonKind.Primary)
                    WatchButton("Apply to…", onPick, Modifier.weight(1f))
                }
                is LookupTarget.Form -> WatchButton("Review and fill the form", onReview, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
                is LookupTarget.Watch -> WatchButton("Review changes", onReview, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
            }
        }
    }
}

@Composable
private fun WatchPicker(choices: List<WatchSummary>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    var filter by remember { mutableStateOf("") }
    val shown = remember(choices, filter) {
        val f = filter.trim().lowercase()
        if (f.isEmpty()) choices else choices.filter { w -> listOfNotNull(w.brand, w.modelName, w.collection, w.reference, w.nickname).any { f in it.lowercase() } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Apply to which watch?", style = t.type.groupTitle, color = c.fg) },
        text = {
            Column {
                io.github.tunlezah.watches.ui.components.WatchTextField("Find", filter, { filter = it }, placeholder = "Brand, model or reference")
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { w ->
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(w.id) }.padding(vertical = 10.dp)) {
                            Text(listOfNotNull(w.brand, w.modelName ?: w.collection).joinToString(" ").ifBlank { "Untitled watch" }, style = t.type.body, color = c.fg)
                            w.reference?.let { Text(it, style = t.type.specSource, color = c.fg3) }
                        }
                    }
                }
            }
        },
        confirmButton = { WatchButton("Cancel", onDismiss, kind = ButtonKind.Text) },
    )
}

@Composable
fun Notice(text: String, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.accentTint).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(WatchIcons.Info, null, tint = c.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = t.type.bodySmall, color = c.fg)
    }
}
