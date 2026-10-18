package io.github.tunlezah.watches.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.model.BoxItem
import io.github.tunlezah.watches.core.model.BoxItemState
import io.github.tunlezah.watches.core.model.ServiceWork
import io.github.tunlezah.watches.core.model.WatchDocument
import androidx.compose.ui.backhandler.BackHandler
import io.github.tunlezah.watches.ui.components.Badge
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.ConfirmDialog
import io.github.tunlezah.watches.ui.components.DatePickerField
import io.github.tunlezah.watches.ui.components.FieldLabel
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.MoneyField
import io.github.tunlezah.watches.ui.components.ScreenHeader
import io.github.tunlezah.watches.ui.components.SegmentedControl
import io.github.tunlezah.watches.ui.components.SectionTitle
import io.github.tunlezah.watches.ui.components.SelectChip
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.components.WatchTextField
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.launch

/** The extensions the document pickers offer; the content is checked again when stored. */
val DocumentExtensions = setOf("pdf", "jpg", "jpeg", "png", "webp", "heic", "heif")

@Composable
fun ServiceEditScreen(
    state: ServiceEditUiState,
    vm: ServiceEditActions,
    onClose: () -> Unit,
    onOpenDocument: (WatchDocument) -> Unit,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val scope = rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val close = { if (state.dirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.dirty && !confirmDiscard) { confirmDiscard = true }

    val picker = rememberFilePickerLauncher(type = FileKitType.File(DocumentExtensions)) { file ->
        if (file != null) scope.launch {
            if (file.size() > io.github.tunlezah.watches.data.DocumentRepository.MAX_BYTES) vm.edit { it.copy(error = "That file is larger than 50 MB.") }
            else vm.attach(file.readBytes(), file.name)
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        ScreenHeader(if (state.isNew) "Log a service" else "Service", null, close, backLabel = "Close") {
            if (!state.isNew) IconAction(WatchIcons.Trash, "Delete this service", { confirmDelete = true })
            WatchButton(if (state.saving) "Saving…" else "Save", { vm.save(onClose) }, kind = ButtonKind.Primary, enabled = state.loaded && !state.saving)
            Spacer(Modifier.width(8.dp))
        }
        if (!state.loaded) return@Column
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = style.screenPadding).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DatePickerField("Date", state.date, state.prefs.dateFormat::format, { d -> vm.edit { it.copy(date = d) } }, clearable = false)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Work done")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (w in ServiceWork.entries) SelectChip(w.label, w in state.work, { vm.toggleWork(w) }, underline = false, role = Role.Checkbox)
                }
                Text("A full service restarts the interval; other work is logged without moving the due date.", style = t.type.bodySmall, color = c.fg3)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchTextField("Serviced by", state.by, { v -> vm.edit { it.copy(by = v) } }, placeholder = "Watchmaker or service centre")
                val suggestions = state.servicers.filter { it.contains(state.by.trim(), ignoreCase = true) && !it.equals(state.by.trim(), ignoreCase = true) }.take(4)
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in suggestions) SelectChip(s, false, { vm.edit { it.copy(by = s) } }, underline = false, role = Role.Button)
                    }
                }
            }
            MoneyField("Cost", state.cost, state.currency, { v -> vm.edit { it.copy(cost = v) } }, { cur -> vm.edit { it.copy(currency = cur) } }, state.costError)
            WatchTextField(
                "Warranty on the work", state.warrantyMonths, { v -> vm.edit { it.copy(warrantyMonths = v) } },
                keyboardType = KeyboardType.Number, suffix = "months", placeholder = "24", error = state.warrantyError,
            )
            WatchTextField("Notes", state.notes, { v -> vm.edit { it.copy(notes = v) } }, singleLine = false, minLines = 3, placeholder = "What was done, parts replaced, timing results")

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Invoice and paperwork")
                for (d in state.attached) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${d.title} · ${CareText.docType(d)} · ${CareText.size(d.sizeBytes)}", style = t.type.body, color = c.fg,
                            modifier = Modifier.weight(1f).clickable(role = Role.Button) { onOpenDocument(d) }.padding(vertical = 10.dp))
                        IconAction(WatchIcons.Close, "Remove ${d.title}", { vm.detach(d) }, tint = c.fg3)
                    }
                }
                for (p in state.pending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(p.title, style = t.type.body, color = c.fg)
                            Text("Attached when you save", style = t.type.specSource, color = c.fg3)
                        }
                        IconAction(WatchIcons.Close, "Remove ${p.title}", { vm.removePending(p.key) }, tint = c.fg3)
                    }
                }
                WatchButton("Attach a PDF or photo", { picker.launch() }, kind = ButtonKind.Tonal, icon = WatchIcons.Plus)
            }
            state.error?.let { Text(it, style = t.type.bodySmall, color = c.danger) }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog("Discard changes?", "This service hasn't been saved.", "Discard", { confirmDiscard = false; onClose() }, { confirmDiscard = false }, destructive = true)
    }
    if (confirmDelete) {
        val docs = state.attached.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            title = { Text("Delete this service?", style = t.type.groupTitle, color = c.fg) },
            text = {
                Text(
                    if (docs == 0) "It's removed from the history. This can't be undone."
                    else "It's removed from the history. Its $docs attached ${if (docs == 1) "document" else "documents"} can stay with the watch or go too.",
                    style = t.type.body, color = c.fg2,
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (docs > 0) WatchButton("Delete service, keep documents", { confirmDelete = false; vm.delete(false, onClose) }, kind = ButtonKind.Text)
                    WatchButton(if (docs > 0) "Delete both" else "Delete", { confirmDelete = false; vm.delete(true, onClose) }, kind = ButtonKind.Danger)
                }
            },
            dismissButton = { WatchButton("Cancel", { confirmDelete = false }, kind = ButtonKind.Text) },
        )
    }
}

@Composable
fun BoxPapersScreen(state: BoxPapersUiState, vm: BoxPapersActions, onBack: () -> Unit, onManual: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    var editingNote by remember { mutableStateOf<BoxItem?>(null) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        ScreenHeader("Box & papers", state.title, onBack)
        if (!state.loaded) return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = style.screenPadding).padding(bottom = 40.dp)) {
            Row(Modifier.fillMaxWidth().clip(style.cardShape).background(c.surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(CareText.boxPapers(state.summary), style = t.type.body, color = c.fg)
                    Text("Full set means every item is recorded and none is missing.", style = t.type.bodySmall, color = c.fg3)
                }
                if (state.summary.fullSet) Badge("Full set")
            }
            SectionTitle("Checklist") {
                if (state.summary.unrecorded > 0 && state.summary.recorded > 0) {
                    WatchButton("Rest not applicable", { vm.markRestNotApplicable() }, kind = ButtonKind.Text)
                }
            }
            for (item in BoxItem.entries) {
                val current = state.states[item]
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.label, style = t.type.body, color = c.fg, modifier = Modifier.weight(1f))
                        if (current != null) IconAction(WatchIcons.Edit, "Note for ${item.label}", { editingNote = item }, tint = c.fg3)
                    }
                    SegmentedControl(
                        BoxItemState.entries.map { it to if (it == BoxItemState.NOT_APPLICABLE) "N/A" else it.label },
                        current,
                        { st -> vm.set(item, st) },
                        spoken = { st, label -> "${item.label}: ${if (st == BoxItemState.NOT_APPLICABLE) "not applicable" else label}" },
                        tint = { st -> if (st == BoxItemState.MISSING) WatchTheme.colors.danger else WatchTheme.colors.accent },
                    )
                    state.notes[item]?.let { Text(it, style = t.type.bodySmall, color = c.fg2) }
                    if (item == BoxItem.MANUAL) {
                        Text(
                            if (state.hasElectronicManual) "An electronic manual is stored with this watch." else "Keep an electronic copy too: add the PDF in Documents.",
                            style = t.type.bodySmall, color = c.accent, modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onManual).padding(vertical = 14.dp),
                        )
                    }
                }
                HorizontalDivider(color = c.line)
            }

            SectionTitle("Warranty")
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DatePickerField("Starts", state.warrantyStart, state.prefs.dateFormat::format, { d -> vm.setWarranty(d, state.warrantyMonths) })
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("Length")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip("None", state.warrantyMonths == null, { vm.setWarranty(state.warrantyStart, null) }, underline = false, role = Role.RadioButton)
                        for (m in (listOf(12, 24, 36, 60, 96) + listOfNotNull(state.warrantyMonths)).distinct().sorted()) {
                            SelectChip(monthsLabel(m), state.warrantyMonths == m, { vm.setWarranty(state.warrantyStart, m) }, underline = false, role = Role.RadioButton)
                        }
                    }
                }
                state.warranty?.let { Text(it, style = t.type.body, color = if (state.warrantyExpired) c.danger else c.fg) }
                    ?: Text("Set a start date and length to see when the warranty ends.", style = t.type.bodySmall, color = c.fg3)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editingNote?.let { item ->
        var text by remember(item) { mutableStateOf(state.notes[item].orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingNote = null },
            containerColor = c.surface,
            title = { Text(item.label, style = t.type.groupTitle, color = c.fg) },
            text = { WatchTextField("Note", text, { text = it }, singleLine = false, minLines = 2, placeholder = "Condition, serial on the card, where it's kept") },
            confirmButton = { WatchButton("Save", { vm.setNote(item, text); editingNote = null }, kind = ButtonKind.Primary) },
            dismissButton = { WatchButton("Cancel", { editingNote = null }, kind = ButtonKind.Text) },
        )
    }
}

internal fun monthsLabel(months: Int) = when {
    months == 12 -> "1 year"
    months % 12 == 0 -> "${months / 12} years"
    else -> "$months months"
}
