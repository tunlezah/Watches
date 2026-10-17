package io.github.tunlezah.watches.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.WatchDocument
import io.github.tunlezah.watches.data.DocumentRepository
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.ConfirmDialog
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.FieldLabel
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.ScreenHeader
import io.github.tunlezah.watches.ui.components.SectionTitle
import io.github.tunlezah.watches.ui.components.SelectChip
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.components.WatchTextField
import io.github.tunlezah.watches.ui.photos.ZoomableImage
import io.github.tunlezah.watches.ui.platform.PdfDocument
import io.github.tunlezah.watches.ui.platform.openPdf
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitOpenCameraSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentActions(
    val onBack: () -> Unit,
    val onOpen: (WatchDocument) -> Unit,
    val onAdd: (bytes: ByteArray, name: String, kind: DocumentKind) -> Unit,
    val onCopy: (documentId: String) -> Unit,
    val onEdit: (WatchDocument, title: String, kind: DocumentKind) -> Unit,
    val onShare: (WatchDocument) -> Unit,
    val onDelete: (WatchDocument) -> Unit,
    val onDismissMessage: () -> Unit,
    val onTooLarge: () -> Unit,
)

/** Every document kept with a watch, grouped, with manuals and quick start guides first. */
@Composable
fun DocumentsScreen(state: DocumentsUiState, startAdding: DocumentKind?, actions: DocumentActions) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    // Opened from "Add manual" on a watch: go straight to adding one, once.
    var adding by rememberSaveable { mutableStateOf(startAdding?.key) }
    var renaming by remember { mutableStateOf<WatchDocument?>(null) }
    var deleting by remember { mutableStateOf<WatchDocument?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        ScreenHeader("Documents", state.title, actions.onBack)
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = style.screenPadding, end = style.screenPadding, bottom = 120.dp)) {
                if (state.loaded && state.groups.isEmpty()) {
                    item {
                        EmptyState(
                            "No documents yet",
                            "Keep the manual, a quick start guide, receipts, warranty cards and certificates with the watch. PDFs and photos both work.",
                            Modifier.padding(top = 32.dp),
                        )
                    }
                }
                for (group in state.groups) {
                    item(key = "h-${group.title}") { SectionTitle(group.title) }
                    items(group.documents, key = { it.id }) { d ->
                        DocumentRow(d, d.serviceEntryId?.let { state.entryDates[it] }, actions, onRename = { renaming = d }, onDelete = { deleting = d })
                    }
                }
                state.message?.let { m ->
                    item { Text(m, style = t.type.bodySmall, color = c.danger, modifier = Modifier.padding(top = 16.dp).clickable(onClick = actions.onDismissMessage)) }
                }
            }
            WatchButton(
                if (state.busy) "Adding…" else "Add a document", { adding = DocumentKind.MANUAL.key },
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                kind = ButtonKind.Primary, icon = WatchIcons.Plus, enabled = !state.busy,
            )
        }
    }

    adding?.let { key ->
        AddDocumentDialog(
            initial = DocumentKind.entries.firstOrNull { it.key == key } ?: DocumentKind.MANUAL,
            elsewhere = state.guidesElsewhere,
            onDismiss = { adding = null },
            onFile = { bytes, name, kind -> adding = null; actions.onAdd(bytes, name, kind) },
            onCopy = { id -> adding = null; actions.onCopy(id) },
            onTooLarge = { adding = null; actions.onTooLarge() },
        )
    }
    renaming?.let { doc ->
        var title by remember(doc.id) { mutableStateOf(doc.title) }
        var kind by remember(doc.id) { mutableStateOf(doc.kind) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = c.surface,
            title = { Text("Edit document", style = t.type.groupTitle, color = c.fg) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    WatchTextField("Title", title, { title = it })
                    FieldLabel("Kind")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (k in DocumentKind.entries) SelectChip(k.label, kind == k, { kind = k }, underline = false)
                    }
                }
            },
            confirmButton = {
                WatchButton("Save", {
                    if (title != doc.title || kind != doc.kind) actions.onEdit(doc, title, kind)
                    renaming = null
                }, kind = ButtonKind.Primary)
            },
            dismissButton = { WatchButton("Cancel", { renaming = null }, kind = ButtonKind.Text) },
        )
    }
    deleting?.let { doc ->
        ConfirmDialog("Delete “${doc.title}”?", "The file is removed from this phone. This can't be undone.", "Delete",
            { actions.onDelete(doc); deleting = null }, { deleting = null }, destructive = true)
    }
}

@Composable
private fun DocumentRow(d: WatchDocument, serviceDate: String?, actions: DocumentActions, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { actions.onOpen(d) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(WatchTheme.style.tileShape).background(c.surface2), contentAlignment = Alignment.Center) {
            Text(if (d.isPdf) "PDF" else "IMG", style = t.type.specSource, color = c.accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(d.title, style = t.type.body, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(d.kind.label, CareText.size(d.sizeBytes), serviceDate?.let { "service $it" }).joinToString(" · "),
                style = t.type.specSource, color = c.fg3,
            )
        }
        Box {
            IconAction(WatchIcons.More, "More for ${d.title}", { menu = true })
            DropdownMenu(menu, { menu = false }, containerColor = c.surface) {
                DropdownMenuItem(text = { Text("Share", style = t.type.body, color = c.fg) }, onClick = { menu = false; actions.onShare(d) })
                DropdownMenuItem(text = { Text("Rename or change kind", style = t.type.body, color = c.fg) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete", style = t.type.body, color = c.danger) }, onClick = { menu = false; onDelete() })
            }
        }
    }
    HorizontalDivider(color = c.line)
}

@Composable
private fun AddDocumentDialog(
    initial: DocumentKind,
    elsewhere: List<io.github.tunlezah.watches.data.GuideElsewhere>,
    onDismiss: () -> Unit,
    onFile: (ByteArray, String, DocumentKind) -> Unit,
    onCopy: (String) -> Unit,
    onTooLarge: () -> Unit,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(initial) }
    val take: (PlatformFile?) -> Unit = { file ->
        if (file != null) scope.launch {
            if (file.size() > DocumentRepository.MAX_BYTES) onTooLarge() else onFile(file.readBytes(), file.name, kind)
        }
    }
    val picker = rememberFilePickerLauncher(type = FileKitType.File(DocumentExtensions)) { take(it) }
    val camera = rememberCameraPickerLauncher(FileKitOpenCameraSettings.createDefault()) { take(it) }
    val offered = elsewhere.filter { it.document.kind == kind }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Add a document", style = t.type.groupTitle, color = c.fg) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FieldLabel("Kind")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in DocumentKind.entries) SelectChip(k.label, kind == k, { kind = k }, underline = false)
                }
                WatchButton("Choose a PDF or photo", { picker.launch() }, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
                WatchButton("Photograph a paper copy", { camera.launch() }, Modifier.fillMaxWidth(), icon = WatchIcons.Camera)
                if (kind.isGuide && offered.isNotEmpty()) {
                    FieldLabel("Or use one from another watch")
                    for (g in offered.take(8)) {
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onCopy(g.document.id) }.padding(vertical = 8.dp)) {
                            Text(g.document.title, style = t.type.body, color = c.fg)
                            Text(listOfNotNull(g.watchTitle, g.caliber?.let { "calibre $it" }, CareText.size(g.document.sizeBytes)).joinToString(" · "),
                                style = t.type.specSource, color = c.fg3)
                        }
                    }
                }
            }
        },
        confirmButton = { WatchButton("Cancel", onDismiss, kind = ButtonKind.Text) },
    )
}

/** Shows a stored PDF page by page, or an image, with zoom and Share. */
@Composable
fun DocumentViewerScreen(state: ViewerUiState, onBack: () -> Unit, onShare: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val doc = state.document
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ScreenHeader(doc?.title ?: "Document", doc?.let { "${it.kind.label} · ${CareText.size(it.sizeBytes)}" }, onBack) {
            if (doc != null) IconAction(WatchIcons.External, "Share", onShare)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.loaded -> Unit
                doc == null || state.path == null -> EmptyState("Document not found", "It may have been deleted.", Modifier.align(Alignment.Center))
                doc.isPdf -> PdfPages(state.path, doc.title, onShare)
                else -> ZoomableImage(state.path, doc.title)
            }
        }
    }
}

@Composable
private fun PdfPages(path: String, title: String, onShare: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    // Opening can't be cancelled halfway, so a PDF opened just as the viewer leaves is still closed.
    val pdf by produceState<Result<PdfDocument>?>(null, path) {
        val doc = withContext(NonCancellable) { openPdf(path) }
        if (doc == null) {
            value = Result.failure(IllegalStateException())
            return@produceState
        }
        try {
            value = Result.success(doc)
            awaitCancellation()
        } finally {
            doc.close()
        }
    }
    val open = pdf?.getOrNull()
    when {
        pdf == null -> CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(40.dp))
        open == null -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This PDF can't be shown here.", style = t.type.body, color = Color.White)
            Spacer(Modifier.size(12.dp))
            WatchButton("Open with another app", onShare, kind = ButtonKind.Primary)
        }
        else -> {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val zoom = rememberTransformableState { z, pan, _ ->
                scale = (scale * z).coerceIn(1f, 4f)
                offset = if (scale == 1f) Offset.Zero else offset + pan
            }
            BoxWithConstraints(Modifier.fillMaxSize().transformable(zoom, canPan = { scale > 1f })) {
                // Rendered at 1.5x the screen width so a zoomed page stays legible.
                val widthPx = with(LocalDensity.current) { (maxWidth.toPx() * 1.5f).toInt() }
                LazyColumn(
                    Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(open.pageCount) { index -> PdfPage(open, index, widthPx, "Page ${index + 1} of ${open.pageCount} of $title") }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(pdf: PdfDocument, index: Int, widthPx: Int, description: String) {
    val bitmap by produceState<ImageBitmap?>(null, pdf, index, widthPx) { value = pdf.render(index, widthPx) }
    val b = bitmap
    if (b == null) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Color.White.copy(alpha = 0.9f)).semantics { contentDescription = description })
    } else {
        androidx.compose.foundation.Image(
            b, contentDescription = description, contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height),
        )
    }
}
