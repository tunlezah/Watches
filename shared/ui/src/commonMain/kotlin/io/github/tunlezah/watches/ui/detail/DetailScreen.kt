package io.github.tunlezah.watches.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.WatchDocument
import io.github.tunlezah.watches.ui.care.CareText
import io.github.tunlezah.watches.ui.components.NavCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.core.spec.SpecRow
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.ConfirmDialog
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.SpecGroupView
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.components.WatchLook
import io.github.tunlezah.watches.ui.components.WatchPhoto
import io.github.tunlezah.watches.ui.components.sharedWatchPhoto
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import kotlinx.coroutines.launch

data class DetailActions(
    val onBack: () -> Unit,
    val onEdit: () -> Unit,
    val onPhotos: () -> Unit,
    val onViewPhoto: (Int) -> Unit,
    val onDuplicate: () -> Unit,
    val onArchive: (Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onLookUp: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onDismissNotice: () -> Unit = {},
    val onService: () -> Unit = {},
    val onBoxPapers: () -> Unit = {},
    val onDocuments: () -> Unit = {},
    val onOpenDocument: (WatchDocument) -> Unit = {},
    val onAddGuide: (DocumentKind) -> Unit = {},
)

@Composable
fun DetailScreen(state: DetailUiState, watchId: String, actions: DetailActions, showBack: Boolean = true, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val haptics = LocalHapticFeedback.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var revealed by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (state.missing) {
        Box(modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
            EmptyState("Not found", "This watch has been deleted.") { WatchButton("Back", actions.onBack) }
        }
        return
    }

    val copy: (SpecRow) -> Unit = { row ->
        clipboard.setText(AnnotatedString(row.value.plain))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { snackbar.showSnackbar("Copied ${row.label.lowercase()}") }
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item("hero") { Hero(state, watchId, actions.onViewPhoto) }
            item("title") { TitleBlock(state) }
            if (state.stats.isNotEmpty()) item("stats") { StatsStrip(state) }
            item("actions") {
                Row(Modifier.padding(horizontal = style.screenPadding).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WatchButton("Edit", actions.onEdit, Modifier.weight(1f), icon = WatchIcons.Edit)
                    WatchButton("Photos", actions.onPhotos, Modifier.weight(1f), icon = WatchIcons.Camera,
                        kind = if (style.direction == Direction.INSTRUMENT) ButtonKind.Primary else ButtonKind.Secondary)
                    WatchButton("Look up", actions.onLookUp, Modifier.weight(1f), icon = WatchIcons.Lookup)
                }
            }
            state.notice?.let { notice ->
                item("notice") {
                    Box(Modifier.padding(horizontal = style.screenPadding).padding(top = 14.dp).clickable(enabled = !state.refreshing, onClick = actions.onDismissNotice)) {
                        io.github.tunlezah.watches.ui.lookup.Notice(notice)
                    }
                }
            }
            state.care?.let { care ->
                item("care") { CareSection(care, actions, Modifier.padding(horizontal = style.screenPadding).padding(top = 18.dp)) }
            }
            itemsIndexed(state.groups, key = { _, g -> g.group.name }) { index, group ->
                SpecGroupView(
                    sheet = group,
                    number = index + 1,
                    revealedSecrets = revealed,
                    onReveal = { row -> revealed = revealed + row.keys.first() },
                    onCopy = copy,
                    modifier = Modifier.padding(horizontal = style.screenPadding).padding(top = if (style.groupCards) 12.dp else 30.dp),
                )
            }
            state.other?.let { other ->
                item("other") {
                    Column(Modifier.padding(horizontal = style.screenPadding).padding(top = if (style.groupCards) 12.dp else 30.dp)) {
                        SpecGroupView(other, state.groups.size + 1, revealed, {}, copy, title = "Other specs")
                    }
                }
            }
            item("footer") {
                Text(
                    state.footer,
                    style = t.type.specSource,
                    color = c.fg3,
                    modifier = Modifier.padding(horizontal = style.screenPadding).padding(top = 24.dp, bottom = 48.dp).navigationBarsPadding(),
                )
            }
        }

        // Top bar over the hero.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) OverlayButton(WatchIcons.Back, "Back", actions.onBack)
            Spacer(Modifier.weight(1f))
            var menu by remember { mutableStateOf(false) }
            Box {
                OverlayButton(WatchIcons.More, "More actions") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surface) {
                    MenuRow("Edit", WatchIcons.Edit) { menu = false; actions.onEdit() }
                    MenuRow("Manage photos", WatchIcons.Camera) { menu = false; actions.onPhotos() }
                    MenuRow("Look up specs", WatchIcons.Lookup) { menu = false; actions.onLookUp() }
                    if (state.hasSources) MenuRow("Refresh from sources", WatchIcons.Refresh) { menu = false; actions.onRefresh() }
                    MenuRow("Duplicate", WatchIcons.Copy) { menu = false; actions.onDuplicate() }
                    MenuRow(if (state.archived) "Unarchive" else "Archive", if (state.archived) WatchIcons.Restore else WatchIcons.Archive) {
                        menu = false
                        actions.onArchive(!state.archived)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    MenuRow("Delete", WatchIcons.Trash, danger = true) { menu = false; confirmDelete = true }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this watch?",
            message = "${state.brand?.let { "$it " } ?: ""}${state.title} and all its photos and notes will be removed from this phone. This can't be undone. Archive it instead to keep its history.",
            confirm = "Delete",
            destructive = true,
            onConfirm = { confirmDelete = false; actions.onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun MenuRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    DropdownMenuItem(
        text = { Text(text, style = t.type.body, color = if (danger) c.danger else c.fg) },
        leadingIcon = { Icon(icon, null, tint = if (danger) c.danger else c.fg2, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun OverlayButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = WatchTheme.colors
    val style = WatchTheme.style
    IconAction(
        icon, label, onClick, tint = c.fg,
        filled = if (style.direction == Direction.GALLERY) c.surface.copy(alpha = 0.7f) else null,
        framed = style.direction == Direction.INSTRUMENT,
    )
}

@Composable
private fun Hero(state: DetailUiState, watchId: String, onView: (Int) -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = style.heroInset).padding(top = style.heroInset)) {
        val height = (maxWidth * 1.05f).coerceAtMost(520.dp)
        val pages = state.photos.size.coerceAtLeast(1)
        val pager = rememberPagerState { pages }
        Box(Modifier.fillMaxWidth().height(height).clip(style.heroShape)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { state.photos.getOrNull(it)?.id ?: "none" }) { page ->
                val photo = state.photos.getOrNull(page)
                val look = state.look ?: WatchLook.guess(null, null, emptySet())
                WatchPhoto(
                    path = photo?.display,
                    look = look,
                    contentDescription = if (photo != null) "Photo ${page + 1} of ${state.photos.size}" else "No photo yet",
                    illustrationPadding = 56.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (page == 0) Modifier.sharedWatchPhoto(watchId) else Modifier)
                        .clickable(enabled = photo != null, role = Role.Image, onClickLabel = "View full screen") { onView(page) },
                )
            }
            state.photos.getOrNull(pager.currentPage)?.credit?.let { credit ->
                Text(
                    credit, style = t.type.specSource, color = c.fg2, maxLines = 2,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(6.dp))
                        .background(c.bg.copy(alpha = 0.75f)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (state.photos.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (style.direction) {
                        Direction.INSTRUMENT -> Text(
                            "${(pager.currentPage + 1).toString().padStart(2, '0')} / ${state.photos.size.toString().padStart(2, '0')}",
                            style = t.type.label, color = c.fg2,
                        )
                        else -> repeat(state.photos.size) { i ->
                            val active = i == pager.currentPage
                            Box(
                                Modifier
                                    .size(width = if (active) 18.dp else 6.dp, height = if (style.direction == Direction.GALLERY) 6.dp else 2.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (active) c.accent else c.fg3.copy(alpha = 0.6f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleBlock(state: DetailUiState) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(Modifier.padding(horizontal = style.screenPadding).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.brand?.let {
            Text(
                if (style.direction == Direction.GALLERY) it else it.uppercase(),
                style = if (style.direction == Direction.GALLERY) t.type.button else t.type.label,
                color = c.accent,
            )
        }
        Text(t.title(state.title), style = t.type.heroTitle, color = c.fg, modifier = Modifier.semantics { heading() })
        state.subtitle?.let { Text(it, style = t.type.heroSubtitle, color = c.fg2) }
        val status = if (state.archived) "Archived" else state.statusLabel
        if (style.direction == Direction.INSTRUMENT) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.reference?.let { ref ->
                    Text("REF $ref", style = t.type.reference, color = c.fg2, modifier = Modifier.border(1.dp, c.line).padding(horizontal = 7.dp, vertical = 4.dp))
                }
                Text(status.uppercase(), style = t.type.reference, color = c.accent, modifier = Modifier.background(c.surface2).padding(horizontal = 7.dp, vertical = 5.dp))
            }
        } else if (style.direction == Direction.ATELIER) {
            Text(listOfNotNull(state.reference?.let { "Ref. $it" }, status).joinToString(" · "), style = t.type.reference, color = c.fg2, modifier = Modifier.padding(top = 2.dp))
        } else {
            state.reference?.let { Text("Ref. $it", style = t.type.reference, color = c.fg2, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

@Composable
private fun StatsStrip(state: DetailUiState) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    // Gallery shows status as a card; the strip directions carry it in the title block and keep three figures.
    val items = if (style.direction == Direction.GALLERY) state.stats else state.stats.filterNot { it.label == "Status" }.take(3)
    if (items.isEmpty()) return
    when (style.direction) {
        Direction.GALLERY -> Row(
            Modifier.padding(horizontal = style.screenPadding).padding(top = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (s in items) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (s.emphasis) c.accentTint else c.surface).padding(12.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "${s.label}: ${s.value}" },
                ) {
                    Text(s.value, style = t.type.button, color = if (s.emphasis) c.accent else c.fg, maxLines = 1)
                    Text(s.label, style = t.type.specSource, color = c.fg3, maxLines = 1)
                }
            }
        }
        else -> Row(
            Modifier
                .padding(horizontal = style.screenPadding).padding(top = 20.dp).fillMaxWidth()
                .then(if (style.direction == Direction.INSTRUMENT) Modifier.border(1.dp, c.line) else Modifier.topBottomRules(c.line)),
        ) {
            items.forEachIndexed { i, s ->
                if (i > 0) Box(Modifier.width(1.dp).height(56.dp).background(c.line))
                Column(
                    Modifier.weight(1f).padding(horizontal = if (style.direction == Direction.INSTRUMENT || i > 0) 10.dp else 0.dp, vertical = 12.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "${s.label}: ${s.value}" },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(t.label(s.label), style = t.type.label, color = c.fg3, maxLines = 2)
                    Text(s.value, style = t.type.stat.copy(fontSize = t.type.specValue.fontSize), color = if (s.emphasis) c.accent else c.fg, maxLines = 1)
                }
            }
        }
    }
}

private fun Modifier.topBottomRules(color: Color) = drawBehind {
    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
}

/** Manuals and quick start guides first (one tap to open), then service, box & papers and documents. */
@Composable
private fun CareSection(care: DetailCare, actions: DetailActions, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().clip(style.cardShape).background(c.surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(t.label("Manual & quick start"), style = t.type.label, color = c.fg3, modifier = Modifier.padding(vertical = 4.dp))
            for (g in care.guides) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Open") { actions.onOpenDocument(g) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(WatchIcons.Info, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.title, style = t.type.body, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${g.kind.label} · ${CareText.docType(g)} · ${CareText.size(g.sizeBytes)}", style = t.type.specSource, color = c.fg3)
                    }
                    Icon(WatchIcons.Forward, null, tint = c.fg3, modifier = Modifier.size(18.dp))
                }
            }
            if (!care.hasManual || !care.hasQuickStart) {
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!care.hasManual) WatchButton("Add manual", { actions.onAddGuide(DocumentKind.MANUAL) }, kind = ButtonKind.Tonal, icon = WatchIcons.Plus)
                    if (!care.hasQuickStart) WatchButton("Add quick start", { actions.onAddGuide(DocumentKind.QUICK_START) }, kind = ButtonKind.Tonal, icon = WatchIcons.Plus)
                }
            }
        }
        NavCard(WatchIcons.Service, "Service", care.service, actions.onService, emphasis = care.serviceUrgent, badge = care.serviceBadge)
        NavCard(
            WatchIcons.Archive, "Box & papers",
            listOfNotNull(care.papers, care.warranty).joinToString(" · "),
            actions.onBoxPapers, emphasis = care.warrantyEnded && !care.fullSet, badge = if (care.fullSet) "Full set" else null,
        )
        NavCard(WatchIcons.Copy, "Documents", care.documents, actions.onDocuments)
    }
}
