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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
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
import io.github.tunlezah.watches.core.spec.SpecRow
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.ConfirmDialog
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.SpecGroupView
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.components.WatchLook
import io.github.tunlezah.watches.ui.components.WatchPhoto
import io.github.tunlezah.watches.ui.components.registrationMarks
import io.github.tunlezah.watches.ui.components.sharedWatchPhoto
import io.github.tunlezah.watches.ui.components.surface
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
    val onCopied: (label: String) -> Unit = {},
)

@Composable
fun DetailScreen(state: DetailUiState, watchId: String, actions: DetailActions, showBack: Boolean = true, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val haptics = LocalHapticFeedback.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var revealed by remember { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var heroHeight by remember { mutableIntStateOf(0) }
    // The header gains a background and the watch's name once the hero has scrolled away under it.
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > heroHeight - with(density) { 72.dp.roundToPx() } }
    }
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, label = "bar")

    if (state.missing) {
        Box(modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
            EmptyState("Not found", "This watch has been deleted.") { WatchButton("Back", actions.onBack) }
        }
        return
    }

    val copy: (SpecRow) -> Unit = { row ->
        clipboard.setText(AnnotatedString(row.value.plain))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        actions.onCopied(row.label)
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item("hero") { Box(Modifier.onSizeChanged { heroHeight = it.height }) { Hero(state, watchId, actions.onViewPhoto, actions.onPhotos) } }
            item("title") { TitleBlock(state) }
            if (state.stats.isNotEmpty()) item("stats") { StatsStrip(state) }
            item("actions") {
                WatchButton(
                    "Look up specs", actions.onLookUp,
                    Modifier.padding(horizontal = style.screenPadding).padding(top = 18.dp).fillMaxWidth(),
                    kind = ButtonKind.Tonal, icon = WatchIcons.Lookup,
                )
            }
            state.notice?.let { notice ->
                item("notice") {
                    Box(Modifier.padding(horizontal = style.screenPadding).padding(top = 14.dp).clickable(enabled = !state.refreshing, onClickLabel = "Dismiss", onClick = actions.onDismissNotice)) {
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

        // Top bar: over the hero at first, then a solid header with the watch's name once scrolled.
        Column(Modifier.fillMaxWidth().background(c.bg.copy(alpha = barAlpha))) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = if (style.heroInset > 0.dp) style.heroInset + 4.dp else 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBack) OverlayButton(WatchIcons.Back, "Back", actions.onBack, solid = collapsed)
                Text(
                    t.title(state.title), style = t.type.headerTitle, color = c.fg.copy(alpha = barAlpha), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { if (!collapsed) invisibleToUser() },
                )
                OverlayButton(WatchIcons.Edit, "Edit", actions.onEdit, solid = collapsed)
                var menu by remember { mutableStateOf(false) }
                Box {
                    OverlayButton(WatchIcons.More, "More actions", { menu = true }, solid = collapsed)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surface) {
                        MenuRow("Manage photos", WatchIcons.Camera) { menu = false; actions.onPhotos() }
                        if (state.hasSources) MenuRow("Refresh from sources", WatchIcons.Refresh) { menu = false; actions.onRefresh() }
                        MenuRow("Duplicate", WatchIcons.Copy) { menu = false; actions.onDuplicate() }
                        MenuRow(if (state.archived) "Unarchive" else "Archive", if (state.archived) WatchIcons.Restore else WatchIcons.Archive) {
                            menu = false
                            actions.onArchive(!state.archived)
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                        MenuRow("Delete", WatchIcons.Trash, danger = true) {
                            menu = false
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            actions.onDelete()
                        }
                    }
                }
            }
            if (barAlpha > 0f) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = barAlpha)))
        }
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

/** A header button that floats over the photo (with a soft backing so it reads on any picture), then sits flat in the solid header. */
@Composable
private fun OverlayButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, solid: Boolean = false) {
    val c = WatchTheme.colors
    val style = WatchTheme.style
    IconAction(
        icon, label, onClick, tint = c.fg,
        filled = if (!solid && !style.framedIcons) c.surface.copy(alpha = 0.72f) else null,
        framed = style.framedIcons && !solid,
    )
}

@Composable
private fun Hero(state: DetailUiState, watchId: String, onView: (Int) -> Unit, onPhotos: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = style.heroInset).padding(top = style.heroInset)) {
        val height = (maxWidth * 1.05f).coerceAtMost(520.dp)
        val pages = state.photos.size.coerceAtLeast(1)
        val pager = rememberPagerState { pages }
        Box(
            Modifier.fillMaxWidth().height(height).clip(style.heroShape)
                .then(if (style.heroMarks) Modifier.registrationMarks(c.fg3) else Modifier),
        ) {
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
            // A soft shade at the top keeps the header buttons readable over bright photos.
            if (state.photos.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.45f), Color.Transparent))))
            }
            // Photos: one tap to add the first, or to manage them.
            if (state.photos.isEmpty()) {
                WatchButton("Add photo", onPhotos, Modifier.align(Alignment.BottomEnd).padding(12.dp), kind = ButtonKind.Tonal, icon = WatchIcons.Camera)
            } else {
                IconAction(
                    WatchIcons.Camera, "Manage photos", onPhotos, tint = c.fg,
                    filled = c.surface.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
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
                    if (style.numberedGroups) {
                        Text(
                            "${(pager.currentPage + 1).toString().padStart(2, '0')} / ${state.photos.size.toString().padStart(2, '0')}",
                            style = t.type.label, color = c.fg2,
                        )
                    } else {
                        // Atelier's hairline dashes, Gallery's soft pills.
                        repeat(state.photos.size) { i ->
                            val active = i == pager.currentPage
                            val width by animateDpAsState(if (active) 18.dp else 6.dp, label = "dot")
                            Box(
                                Modifier
                                    .size(width = width, height = if (style.accentDash) 2.dp else 6.dp)
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
        state.brand?.let { Text(t.label(it), style = if (style.uppercaseLabels) t.type.label else t.type.button, color = c.accent) }
        Text(t.title(state.title), style = t.type.heroTitle, color = c.fg, modifier = Modifier.semantics { heading() })
        state.subtitle?.let { Text(it, style = t.type.heroSubtitle, color = c.fg2) }
        val status = if (state.archived) "Archived" else state.statusLabel
        if (style.numberedGroups) {
            // Instrument: the reference in a frame, the status as a lit tag.
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.reference?.let { ref ->
                    Text("REF $ref", style = t.type.reference, color = c.fg2, modifier = Modifier.border(1.dp, c.line).padding(horizontal = 7.dp, vertical = 4.dp))
                }
                Text(status.uppercase(), style = t.type.reference, color = c.accent, modifier = Modifier.background(c.surface2).padding(horizontal = 7.dp, vertical = 5.dp))
            }
        } else if (style.groupCards) {
            // Gallery shows the status as a card in the stats row.
            state.reference?.let { Text("Ref. $it", style = t.type.reference, color = c.fg2, modifier = Modifier.padding(top = 2.dp)) }
        } else {
            Text(listOfNotNull(state.reference?.let { "Ref. $it" }, status).joinToString(" · "), style = t.type.reference, color = c.fg2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun StatsStrip(state: DetailUiState) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    // Gallery shows status as a card; the strip directions carry it in the title block and keep three figures.
    val items = if (style.groupCards) state.stats else state.stats.filterNot { it.label == "Status" }.take(3)
    if (items.isEmpty()) return
    // With large text, figures go two to a row instead of squeezing four (or three) across.
    val perRow = if (LocalDensity.current.fontScale > 1.3f) 2 else items.size
    if (style.groupCards) {
        Column(
            Modifier.padding(horizontal = style.screenPadding).padding(top = 16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (row in items.chunked(perRow)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in row) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight().surface(RoundedCornerShape(18.dp), if (s.emphasis) c.accentTint else c.surface).padding(12.dp)
                                .semantics(mergeDescendants = true) { contentDescription = "${s.label}: ${s.value}" },
                        ) {
                            Text(s.value, style = t.type.button, color = if (s.emphasis) c.accent else c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.label, style = t.type.specSource, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    } else {
        Column(
            Modifier
                .padding(horizontal = style.screenPadding).padding(top = 20.dp).fillMaxWidth()
                .then(if (style.tileFrame) Modifier.border(1.dp, c.line) else Modifier.topBottomRules(c.line)),
        ) {
            items.chunked(perRow).forEachIndexed { r, row ->
                if (r > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEachIndexed { i, s ->
                        if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(c.line))
                        Column(
                            Modifier.weight(1f).padding(horizontal = if (style.tileFrame || i > 0) 10.dp else 0.dp, vertical = 12.dp)
                                .semantics(mergeDescendants = true) { contentDescription = "${s.label}: ${s.value}" },
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(t.label(s.label), style = t.type.label, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(s.value, style = t.type.stat.copy(fontSize = t.type.specValue.fontSize), color = if (s.emphasis) c.accent else c.fg,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
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
        Column(Modifier.fillMaxWidth().surface(style.cardShape).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(t.label("Manual & quick start"), style = t.type.label, color = c.fg3, modifier = Modifier.padding(vertical = 4.dp))
            for (g in care.guides) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Open") { actions.onOpenDocument(g) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(WatchIcons.Document, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.title, style = t.type.body, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${g.kind.label} · ${CareText.docType(g)} · ${CareText.size(g.sizeBytes)}", style = t.type.specSource, color = c.fg3)
                    }
                    Icon(WatchIcons.Forward, null, tint = c.fg3, modifier = Modifier.size(18.dp))
                }
            }
            if (!care.hasManual || !care.hasQuickStart) {
                FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!care.hasManual) WatchButton("Add manual", { actions.onAddGuide(DocumentKind.MANUAL) }, kind = ButtonKind.Tonal, icon = WatchIcons.Plus)
                    if (!care.hasQuickStart) WatchButton("Add quick start", { actions.onAddGuide(DocumentKind.QUICK_START) }, kind = ButtonKind.Tonal, icon = WatchIcons.Plus)
                }
            }
        }
        NavCard(WatchIcons.Service, "Service", care.service, actions.onService, emphasis = care.serviceUrgent, badge = care.serviceBadge)
        NavCard(
            WatchIcons.Box, "Box & papers",
            listOfNotNull(care.papers, care.warranty).joinToString(" · "),
            actions.onBoxPapers, emphasis = care.warrantyEnded && !care.fullSet, badge = if (care.fullSet) "Full set" else null,
        )
        NavCard(WatchIcons.Document, "Documents", care.documents, actions.onDocuments)
    }
}
