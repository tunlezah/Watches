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
import androidx.compose.foundation.ScrollState
import io.github.tunlezah.watches.ui.components.WatchLook
import io.github.tunlezah.watches.ui.components.skeleton
import io.github.tunlezah.watches.ui.components.pressScale
import io.github.tunlezah.watches.ui.components.LocalAnimationsEnabled
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.animateContentSize
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
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.SelectChip
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.components.WatchPhoto
import io.github.tunlezah.watches.ui.components.WatchTextField
import io.github.tunlezah.watches.ui.components.sharedWatchPhoto
import io.github.tunlezah.watches.ui.components.Masthead
import io.github.tunlezah.watches.ui.components.StatusMarker
import io.github.tunlezah.watches.ui.components.surface
import io.github.tunlezah.watches.ui.theme.TileNumbers
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
    onLookUp: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    val c = WatchTheme.colors
    val style = WatchTheme.style
    val animate = LocalAnimationsEnabled.current
    val gridState = rememberLazyGridState()
    Box(modifier.fillMaxSize().background(c.bg)) {
        val grid = state.layout == CollectionLayout.GRID
        LazyVerticalGrid(
            state = gridState,
            columns = if (grid) GridCells.Adaptive(156.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(start = style.screenPadding, end = style.screenPadding, bottom = 132.dp),
            horizontalArrangement = Arrangement.spacedBy(style.gridGutter),
            verticalArrangement = Arrangement.spacedBy(if (grid) style.gridRowGap else 0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            header(state, onQuery, onTab, onOpenFilters, onClearFilters, onToggleLayout)
            when {
                // Placeholders the shape of the real thing, so the first frame isn't empty.
                !state.loaded -> items(6, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
                    if (grid) TileSkeleton() else RowSkeleton()
                }
                state.isEmptyCollection -> fullWidth("empty") { FirstRun(onAdd, onLookUp, onRestore) }
                state.items.isEmpty() -> fullWidth("none") {
                    val archived = state.filter.tab == StatusTab.ARCHIVED && state.query.isBlank() && state.filter.activeCount == 0
                    EmptyState(
                        title = if (archived) "Nothing archived" else "No matches",
                        message = when {
                            archived -> "Watches you archive are kept here, out of the way."
                            state.query.isNotBlank() -> "Nothing in your collection matches “${state.query.trim()}”."
                            else -> "No watches match these filters."
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    ) {
                        when {
                            state.query.isNotBlank() -> WatchButton("Clear search", { onQuery("") }, kind = ButtonKind.Secondary)
                            state.filter.activeCount > 0 -> WatchButton("Clear filters", onClearFilters, kind = ButtonKind.Secondary)
                        }
                    }
                }
                grid -> itemsIndexed(state.items, key = { _, it -> it.id }, contentType = { _, _ -> "tile" }) { index, item ->
                    WatchTile(item, number = index + 1, onClick = { onOpen(item.id) }, modifier = if (animate) Modifier.animateItem() else Modifier)
                }
                else -> items(state.items, key = { it.id }, contentType = { "row" }) { item ->
                    Box(if (animate) Modifier.animateItem() else Modifier) { WatchListRow(item, onClick = { onOpen(item.id) }) }
                }
            }
        }
        if (state.loaded && !state.isEmptyCollection) {
            // Reads "Add watch" at the top of the list, then tucks down to the icon once you scroll.
            val expanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
            AddButton(onAdd, expanded, Modifier.align(Alignment.BottomEnd).padding(end = style.screenPadding, bottom = 108.dp))
        }
    }
}

@Composable
private fun FirstRun(onAdd: () -> Unit, onLookUp: () -> Unit, onRestore: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        WatchPhoto(
            path = null,
            look = WatchLook.guess("Black", "Steel", emptySet()),
            contentDescription = "",
            modifier = Modifier.fillMaxWidth().aspectRatio(1.25f).clip(style.photoShape),
        )
        Spacer(Modifier.height(24.dp))
        Text(t.title("Start your collection"), style = t.type.groupTitle, color = c.fg, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a watch by hand, or look up its reference to fill in the specs. Everything stays on this phone.",
            style = t.type.body, color = c.fg2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
        WatchButton("Add a watch", onAdd, Modifier.fillMaxWidth(), kind = ButtonKind.Primary, icon = WatchIcons.Plus)
        Spacer(Modifier.height(10.dp))
        WatchButton("Look up a reference", onLookUp, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, icon = WatchIcons.Lookup)
        Spacer(Modifier.height(6.dp))
        WatchButton("Restore from a backup", onRestore, kind = ButtonKind.Text)
    }
}

@Composable
private fun TileSkeleton() {
    val style = WatchTheme.style
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(style.tileAspect).skeleton(style.photoShape))
        Box(Modifier.fillMaxWidth(0.4f).height(10.dp).skeleton(RoundedCornerShape(2.dp)))
        Box(Modifier.fillMaxWidth(0.75f).height(16.dp).skeleton(RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun RowSkeleton() {
    val style = WatchTheme.style
    Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(60.dp).skeleton(style.photoShape))
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(80.dp).height(10.dp).skeleton(RoundedCornerShape(2.dp)))
            Box(Modifier.width(180.dp).height(14.dp).skeleton(RoundedCornerShape(2.dp)))
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
) {
    fullWidth("header") {
        val c = WatchTheme.colors
        val t = WatchTheme.values
        val style = WatchTheme.style
        // A first-run collection shows just the title; search, tabs and filters arrive with the first watch.
        val tools = state.loaded && !state.isEmptyCollection
        Column {
            Masthead(
                "Collection",
                subtitle = if (tools) countLine(state.totalActive, state.ownedCount, style.numberedGroups) else null,
                tools = { if (tools) HeaderTools(state, onOpenFilters, onToggleLayout) },
            )
            if (tools) {
                Spacer(Modifier.height(16.dp))
                WatchTextField(
                    label = "Search",
                    value = state.query,
                    onValueChange = onQuery,
                    placeholder = "Brand, model, reference, notes",
                    showLabel = false,
                    leading = WatchIcons.Search,
                    trailing = if (state.query.isNotEmpty()) ({ IconAction(WatchIcons.Close, "Clear search", { onQuery("") }) }) else null,
                )
                Spacer(Modifier.height(8.dp))
                val tabScroll = rememberScrollState()
                Row(
                    Modifier.fillMaxWidth().fadedEdges(c.bg, tabScroll).horizontalScroll(tabScroll),
                    horizontalArrangement = Arrangement.spacedBy(style.tabGap),
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
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeaderTools(state: CollectionUiState, onOpenFilters: () -> Unit, onToggleLayout: () -> Unit) {
    val c = WatchTheme.colors
    val style = WatchTheme.style
    val framed = style.framedIcons
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconAction(
            if (state.layout == CollectionLayout.GRID) WatchIcons.List else WatchIcons.Grid,
            if (state.layout == CollectionLayout.GRID) "Show as list" else "Show as grid",
            onToggleLayout, framed = framed,
        )
        if (framed) Spacer(Modifier.width(6.dp))
        Box {
            IconAction(WatchIcons.Filter, "Sort and filter", onOpenFilters, framed = framed)
            if (state.filter.activeCount > 0) {
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { StatusMarker(size = 8.dp) }
            }
        }
    }
}

/** "8 watches · 7 owned", or Instrument's zero-padded "008 WATCHES / 007 OWNED". */
private fun countLine(total: Int, owned: Int, numbered: Boolean): String =
    if (numbered) "${total.toString().padStart(3, '0')} WATCHES / ${owned.toString().padStart(3, '0')} OWNED"
    else "$total ${if (total == 1) "watch" else "watches"} · $owned owned"

/** Fades a horizontally scrolling row into the background on whichever side has more to scroll to. */
private fun Modifier.fadedEdges(background: Color, scroll: ScrollState): Modifier = drawWithContent {
    drawContent()
    val w = 24.dp.toPx().coerceAtMost(size.width / 4)
    if (scroll.value > 0) {
        drawRect(Brush.horizontalGradient(listOf(background, Color.Transparent), startX = 0f, endX = w), size = Size(w, size.height))
    }
    if (scroll.value < scroll.maxValue) {
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, background), startX = size.width - w, endX = size.width),
            topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
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
    val interaction = remember { MutableInteractionSource() }
    val container = modifier
        .fillMaxWidth()
        .pressScale(interaction)
        .then(if (style.tileCard) Modifier.surface(style.tileShape) else Modifier.clip(style.tileShape))
        .then(if (style.tileFrame) Modifier.border(1.dp, c.line, style.tileShape) else Modifier)
        .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClickLabel = "Open", onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = description }
        .then(if (style.tileCard) Modifier.padding(8.dp) else Modifier)

    Column(container) {
        Box {
            WatchPhoto(
                path = item.thumb,
                look = item.look,
                contentDescription = "",
                modifier = Modifier.fillMaxWidth().aspectRatio(style.tileAspect).clip(style.photoShape).sharedWatchPhoto(item.id),
            )
            if (style.tileNumbers == TileNumbers.INDEX) {
                Text(number.toString().padStart(2, '0'), style = t.type.tileNumber, color = c.fg3, modifier = Modifier.padding(8.dp))
            }
            if (status != null) {
                StatusPill(status, Modifier.align(if (style.tileNumbers == TileNumbers.INDEX) Alignment.TopEnd else Alignment.TopStart).padding(8.dp))
            }
        }
        if (style.tileFrame) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        val textPad = when {
            style.tileFrame -> Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
            style.tileCard -> Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 2.dp)
            else -> Modifier.padding(top = 12.dp)
        }
        Column(textPad, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!style.uppercaseLabels) {
                // A wall-card caption: the name, then maker and reference, then size and movement.
                Text(item.title, style = t.type.tileTitle, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.brand, item.reference).joinToString(" · "), style = t.type.tileBrand, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.meta.isNotEmpty()) Text(item.meta, style = t.type.tileMeta, color = c.fg3, maxLines = 1)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(item.brand?.uppercase().orEmpty(), style = t.type.tileBrand, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    // Atelier's catalogue number, opposite the maker.
                    if (style.tileNumbers == TileNumbers.CATALOGUE) Text("No. $number", style = t.type.tileNumber, color = c.fg3, maxLines = 1)
                }
                Text(item.title, style = t.type.tileTitle, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // Reference on one line, size and movement on the next: every tile reads the same way.
                item.reference?.let { Text(it, style = t.type.tileMeta, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (item.meta.isNotEmpty()) {
                    Text(t.title(item.meta), style = t.type.tileMeta, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            modifier = Modifier.size(60.dp).clip(if (style.tileCard) RoundedCornerShape(14.dp) else style.photoShape).sharedWatchPhoto(item.id),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            item.brand?.let { Text(t.label(it), style = t.type.tileBrand, color = c.fg3, maxLines = 1) }
            Text(item.title, style = t.type.tileTitle, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.reference, item.meta.ifEmpty { null }).joinToString(" · "), style = t.type.tileMeta, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            StatusPill(status)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
}

/** "Wishlist", "In service", "Archived": a small pill that stays legible over any photo. */
@Composable
private fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Row(
        modifier.clip(WatchTheme.style.statusShape).background(c.surface.copy(alpha = 0.88f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusMarker()
        Spacer(Modifier.width(6.dp))
        Text(t.label(status), style = t.type.label, color = c.fg, maxLines = 1)
    }
}

/** The add button: "Add watch" with its icon, shrinking to the icon alone on scroll, in the design's own shape and case. */
@Composable
private fun AddButton(onAdd: () -> Unit, expanded: Boolean, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val shape = WatchTheme.style.fabShape
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction)
            .height(56.dp)
            .clip(shape)
            .background(c.accent)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onAdd)
            .semantics { contentDescription = "Add watch" }
            .animateContentSize()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(WatchIcons.Plus, null, tint = c.onAccent, modifier = Modifier.size(24.dp))
        if (expanded) {
            Spacer(Modifier.width(10.dp))
            Text(t.control("Add watch"), style = t.type.button, color = c.onAccent, maxLines = 1)
        }
    }
}
