package io.github.tunlezah.watches.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.core.query.StatusTab
import io.github.tunlezah.watches.core.settings.CollectionLayout
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.SelectChip
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.components.WatchPhoto
import io.github.tunlezah.watches.ui.components.WatchTextField
import io.github.tunlezah.watches.ui.components.sharedWatchPhoto
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

@Composable
fun CollectionScreen(
    state: CollectionUiState,
    onQuery: (String) -> Unit,
    onTab: (StatusTab) -> Unit,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onToggleLayout: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = WatchTheme.colors
    val style = WatchTheme.style
    val gridState = rememberLazyGridState()
    Box(modifier.fillMaxSize().background(c.bg)) {
        val grid = state.layout == CollectionLayout.GRID
        LazyVerticalGrid(
            state = gridState,
            columns = if (grid) GridCells.Adaptive(156.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(start = style.screenPadding, end = style.screenPadding, bottom = 132.dp),
            horizontalArrangement = Arrangement.spacedBy(if (style.direction == Direction.ATELIER) 14.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (grid && style.direction == Direction.ATELIER) 24.dp else if (grid) 12.dp else 0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            header(state, onQuery, onTab, onOpenFilters, onClearFilters, onToggleLayout, onAdd)
            when {
                !state.loaded -> Unit
                state.isEmptyCollection -> fullWidth("empty") {
                    EmptyState(
                        title = "Your collection is empty",
                        message = "Add your first watch: specs, photos, the story behind it. Everything stays on this phone.",
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    ) { WatchButton("Add a watch", onAdd, kind = ButtonKind.Primary, icon = WatchIcons.Plus) }
                }
                state.items.isEmpty() -> fullWidth("none") {
                    EmptyState(
                        title = if (state.filter.tab == StatusTab.ARCHIVED) "Nothing archived" else "No matches",
                        message = if (state.query.isNotBlank()) "Nothing in your collection matches “${state.query.trim()}”." else "No watches match these filters.",
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
                grid -> itemsIndexed(state.items, key = { _, it -> it.id }, contentType = { _, _ -> "tile" }) { index, item ->
                    WatchTile(item, number = index + 1, onClick = { onOpen(item.id) })
                }
                else -> items(state.items, key = { it.id }, contentType = { "row" }) { item ->
                    WatchListRow(item, onClick = { onOpen(item.id) })
                }
            }
        }
        if (style.direction != Direction.GALLERY && !state.isEmptyCollection) {
            AddButton(onAdd, Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 108.dp))
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }
}

private fun LazyGridScope.header(
    state: CollectionUiState,
    onQuery: (String) -> Unit,
    onTab: (StatusTab) -> Unit,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onToggleLayout: () -> Unit,
    onAdd: () -> Unit,
) {
    fullWidth("header") {
        val c = WatchTheme.colors
        val t = WatchTheme.values
        val style = WatchTheme.style
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(t.title("Collection"), style = t.type.screenTitle, color = c.fg, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(4.dp))
                    val count = if (style.direction == Direction.INSTRUMENT) {
                        "${state.totalActive.toString().padStart(3, '0')} WATCHES / ${state.ownedCount.toString().padStart(3, '0')} OWNED"
                    } else {
                        "${state.totalActive} ${if (state.totalActive == 1) "watch" else "watches"} · ${state.ownedCount} owned"
                    }
                    Text(count, style = if (style.direction == Direction.INSTRUMENT) t.type.label else t.type.bodySmall, color = c.fg3)
                }
                val framed = style.direction == Direction.INSTRUMENT
                IconAction(
                    if (state.layout == CollectionLayout.GRID) WatchIcons.List else WatchIcons.Grid,
                    if (state.layout == CollectionLayout.GRID) "Show as list" else "Show as grid",
                    onToggleLayout, framed = framed,
                )
                if (framed) Spacer(Modifier.width(6.dp))
                Box {
                    IconAction(WatchIcons.Sliders, "Sort and filter", onOpenFilters, framed = style.direction == Direction.INSTRUMENT)
                    if (state.filter.activeCount > 0) {
                        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(c.accent))
                    }
                }
                if (style.direction == Direction.GALLERY) {
                    IconAction(WatchIcons.Plus, "Add watch", onAdd, tint = c.onAccent, filled = c.accent)
                }
            }
            Spacer(Modifier.height(14.dp))
            WatchTextField(
                label = "Search",
                value = state.query,
                onValueChange = onQuery,
                placeholder = "Brand, model, reference, notes",
                trailing = if (state.query.isNotEmpty()) ({ IconAction(WatchIcons.Close, "Clear search", { onQuery("") }) }) else null,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(if (style.direction == Direction.ATELIER) 18.dp else 8.dp),
            ) {
                for (tab in StatusTab.entries) {
                    SelectChip(tab.label, state.filter.tab == tab, { onTab(tab) })
                }
            }
            if (state.filter.activeCount > 0) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.filter.activeCount} ${if (state.filter.activeCount == 1) "filter" else "filters"} on · ${state.items.size} shown",
                        style = t.type.bodySmall, color = c.fg2, modifier = Modifier.weight(1f))
                    WatchButton("Clear", onClearFilters, kind = ButtonKind.Text)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun statusLabel(item: CollectionItem): String? = when {
    item.archived -> "Archived"
    item.status == WatchStatus.OWNED -> null
    else -> item.status.label
}

private fun tileDescription(item: CollectionItem) = listOfNotNull(item.brand, item.title, item.reference, item.meta.ifEmpty { null }, statusLabel(item))
    .joinToString(", ")

@Composable
fun WatchTile(item: CollectionItem, number: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val status = statusLabel(item)
    val description = tileDescription(item)
    val container = modifier
        .fillMaxWidth()
        .clip(style.tileShape)
        .then(if (style.tileCard) Modifier.background(c.surface) else Modifier)
        .then(if (style.tileFrame) Modifier.border(1.dp, c.line, style.tileShape) else Modifier)
        .clickable(role = Role.Button, onClickLabel = "Open", onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = description }
        .then(if (style.tileCard) Modifier.padding(8.dp) else Modifier)

    Column(container) {
        Box {
            WatchPhoto(
                path = item.thumb,
                look = item.look,
                contentDescription = "",
                modifier = Modifier.fillMaxWidth().aspectRatio(if (style.direction == Direction.INSTRUMENT) 1f else 0.86f)
                    .clip(style.photoShape).sharedWatchPhoto(item.id),
            )
            if (style.direction == Direction.INSTRUMENT) {
                Text(number.toString().padStart(2, '0'), style = t.type.label, color = c.fg3, modifier = Modifier.padding(8.dp))
            }
            if (status != null) {
                val label = if (style.uppercaseLabels) status.uppercase() else status
                Text(
                    label,
                    style = t.type.label,
                    color = c.accent,
                    modifier = Modifier
                        .align(if (style.direction == Direction.INSTRUMENT) Alignment.TopEnd else Alignment.TopStart)
                        .padding(8.dp)
                        .then(if (style.direction == Direction.GALLERY) Modifier.clip(RoundedCornerShape(10.dp)).background(c.accentTint).padding(horizontal = 8.dp, vertical = 3.dp) else Modifier),
                )
            }
        }
        val textPad = when (style.direction) {
            Direction.ATELIER -> Modifier.padding(top = 10.dp)
            Direction.INSTRUMENT -> Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
            Direction.GALLERY -> Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 2.dp)
        }
        if (style.direction == Direction.INSTRUMENT) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Column(textPad, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (style.direction) {
                Direction.GALLERY -> {
                    Text(item.title, style = t.type.tileTitle, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(item.brand, item.reference).joinToString(" · "), style = t.type.tileBrand, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.meta.isNotEmpty()) Text(item.meta, style = t.type.tileMeta, color = c.fg3, maxLines = 1)
                }
                else -> {
                    item.brand?.let { Text(it.uppercase(), style = t.type.tileBrand, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(item.title, style = t.type.tileTitle, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = if (style.direction == Direction.INSTRUMENT) {
                        listOfNotNull(item.reference, item.meta.uppercase().ifEmpty { null }).joinToString("\n")
                    } else {
                        listOfNotNull(item.reference, item.meta.ifEmpty { null }).joinToString(" · ")
                    }
                    if (meta.isNotEmpty()) Text(meta, style = t.type.tileMeta, color = c.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun WatchListRow(item: CollectionItem, onClick: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val status = statusLabel(item)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClickLabel = "Open", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = tileDescription(item) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WatchPhoto(
            path = item.thumb,
            look = item.look,
            contentDescription = "",
            illustrationPadding = 4.dp,
            modifier = Modifier.size(60.dp).clip(if (style.direction == Direction.GALLERY) RoundedCornerShape(14.dp) else style.photoShape).sharedWatchPhoto(item.id),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            item.brand?.let { Text(t.label(it), style = t.type.tileBrand, color = c.fg3, maxLines = 1) }
            Text(item.title, style = t.type.tileTitle, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.reference, item.meta.ifEmpty { null }).joinToString(" · "), style = t.type.tileMeta, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            Text(t.label(status), style = t.type.label, color = c.accent)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
}

@Composable
private fun AddButton(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val style = WatchTheme.style
    val shape = if (style.direction == Direction.INSTRUMENT) RoundedCornerShape(4.dp) else RoundedCornerShape(28.dp)
    Box(
        modifier
            .size(56.dp)
            .clip(shape)
            .background(c.accent)
            .clickable(role = Role.Button, onClickLabel = "Add watch", onClick = onAdd)
            .semantics { contentDescription = "Add watch" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(WatchIcons.Plus, null, tint = c.onAccent, modifier = Modifier.size(26.dp))
    }
}
