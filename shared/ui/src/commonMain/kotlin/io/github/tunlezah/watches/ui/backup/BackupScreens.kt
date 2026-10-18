package io.github.tunlezah.watches.ui.backup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.backhandler.BackHandler
import io.github.tunlezah.watches.core.care.CarePlan
import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.data.backup.ArchivePreview
import io.github.tunlezah.watches.data.backup.BackupProgress
import io.github.tunlezah.watches.data.backup.RestoreMode
import io.github.tunlezah.watches.data.backup.RestoreReport
import io.github.tunlezah.watches.ui.care.CareText
import io.github.tunlezah.watches.ui.components.Badge
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.Callout
import io.github.tunlezah.watches.ui.components.ConfirmDialog
import io.github.tunlezah.watches.ui.components.FigureRow
import io.github.tunlezah.watches.ui.components.IconBadge
import io.github.tunlezah.watches.ui.components.ListRow
import io.github.tunlezah.watches.ui.components.ProgressPanel
import io.github.tunlezah.watches.ui.components.RowDivider
import io.github.tunlezah.watches.ui.components.RowGroup
import io.github.tunlezah.watches.ui.components.ScreenHeader
import io.github.tunlezah.watches.ui.components.Tone
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.platform.KeepScreenOn
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class BackupActions(
    val onBack: () -> Unit,
    val onBackUp: () -> Unit,
    val onRestore: () -> Unit,
    val onExportCsv: () -> Unit,
    val onCancel: () -> Unit,
    val onDismiss: () -> Unit,
)

/** Settings › Backup & restore. */
@Composable
fun BackupScreen(state: BackupUiState, prefs: DisplayPrefs, today: LocalDate, actions: BackupActions) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val job = state.job
    val busy = job.running
    if (busy) KeepScreenOn()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(job::class) {
        when (job) {
            is BackupState.BackedUp, is BackupState.CsvExported -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            is BackupState.Failed -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            else -> Unit
        }
    }
    var confirmCancel by remember { mutableStateOf(false) }
    BackHandler(enabled = busy) { confirmCancel = true }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        ScreenHeader("Backup & restore", null, { if (busy) confirmCancel = true else actions.onBack() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = style.screenPadding).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StatusCard(state, prefs, today, actions, busy)

            AnimatedContent(job.resultKey(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "result") { _ ->
                when (job) {
                    is BackupState.BackedUp -> Callout(
                        "Backup saved",
                        listOfNotNull(
                            "${job.fileName}.zip · ${CareText.size(job.result.bytes)} · ${count(job.result.counts.watches, "watch", "watches")}",
                            job.result.missingFiles.takeIf { it > 0 }?.let { "$it ${if (it == 1) "file was" else "files were"} missing on this phone and left out." },
                        ).joinToString("\n"),
                        tone = Tone.Success,
                        actions = { WatchButton("Done", actions.onDismiss, kind = ButtonKind.Text) },
                    )
                    is BackupState.CsvExported -> Callout(
                        "Spreadsheet saved",
                        "${job.fileName}.csv · ${count(job.watches, "watch", "watches")}",
                        tone = Tone.Success,
                        actions = { WatchButton("Done", actions.onDismiss, kind = ButtonKind.Text) },
                    )
                    is BackupState.Failed -> Callout(
                        when (job.task) {
                            BackupTask.BACKUP -> "Backup didn't finish"
                            BackupTask.CSV -> "Export didn't finish"
                            BackupTask.READ, BackupTask.RESTORE -> "Couldn't restore that file"
                        },
                        job.message,
                        tone = Tone.Danger,
                        actions = { WatchButton("OK", actions.onDismiss, kind = ButtonKind.Text) },
                    )
                    else -> Spacer(Modifier.height(0.dp))
                }
            }

            RowGroup("Restore", footer = "Choose a backup made by Watches on this or another phone. You'll see what it holds, then merge it with this phone or replace what's here.") {
                ListRow(
                    "Restore from a backup", icon = WatchIcons.Download, detail = "A .zip file saved by Back up now",
                    enabled = !busy, onClick = actions.onRestore,
                )
            }

            RowGroup("Export", footer = "For Excel, Numbers or Google Sheets: one row per watch with every spec, service and warranty date. A spreadsheet can't be restored, so keep a backup too.") {
                ListRow(
                    "Export as spreadsheet", icon = WatchIcons.Table, detail = "CSV, one row per watch",
                    enabled = !busy, onClick = actions.onExportCsv,
                )
            }

            Text(
                "Backups aren't encrypted, so keep them somewhere private. Watches never uploads them, and Android's own cloud backup is switched off for this app: a backup is only where you put it.",
                style = t.type.bodySmall, color = c.fg3, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    if (confirmCancel) {
        val restoring = (job as? BackupState.Working)?.task == BackupTask.RESTORE
        ConfirmDialog(
            if (restoring) "Stop restoring?" else "Stop the backup?",
            if (restoring) "Nothing on this phone has changed yet." else "The unfinished file will be deleted.",
            "Stop",
            { confirmCancel = false; actions.onCancel() },
            { confirmCancel = false },
            destructive = true,
        )
    }
}

@Composable
private fun StatusCard(state: BackupUiState, prefs: DisplayPrefs, today: LocalDate, actions: BackupActions, busy: Boolean) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val last = state.lastBackup
    val age = last?.let { it.at.localDate().daysUntil(today) }
    val stale = last == null || (age ?: 0) > STALE_DAYS
    Column(
        Modifier.fillMaxWidth().clip(style.cardShape).background(c.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (stale) WatchIcons.Warning else WatchIcons.Shield, if (stale && state.contents?.watches != 0) Tone.Warning else Tone.Normal)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        last == null -> "Not backed up yet"
                        age == 0 -> "Backed up today"
                        else -> "Backed up ${CarePlan.relative(-(age ?: 0))}"
                    },
                    style = t.type.groupTitle, color = c.fg, modifier = Modifier.semantics { heading() },
                )
                Text(
                    last?.let { "${prefs.dateFormat.format(it.at.localDate())} at ${it.at.localTime()} · ${CareText.size(it.bytes)} · ${count(it.watches, "watch", "watches")}" }
                        ?: "Keep a copy of your collection somewhere other than this phone.",
                    style = t.type.bodySmall, color = c.fg2,
                )
            }
        }
        state.contents?.let { k ->
            FigureRow(
                listOf(
                    k.watches.toString() to "Watches",
                    k.photos.toString() to "Photos",
                    (k.documents + k.serviceEntries).toString() to "Records",
                    CareText.size(k.bytes) to "Size",
                ),
            )
        }
        val working = state.job as? BackupState.Working
        if (working != null) {
            ProgressPanel(
                title = working.title(),
                detail = working.progress?.takeIf { it.total > 0 && it.step != BackupProgress.Step.SAVING }
                    ?.let { "${CareText.size(it.done)} of ${CareText.size(it.total)} · keep Watches open" } ?: "Keep Watches open until this finishes.",
                fraction = working.progress?.takeIf { it.total > 0 && it.step != BackupProgress.Step.SAVING }?.fraction,
                action = if (working.cancellable) ({ WatchButton("Cancel", actions.onCancel, kind = ButtonKind.Text) }) else null,
            )
        } else {
            WatchButton("Back up now", actions.onBackUp, Modifier.fillMaxWidth(), kind = ButtonKind.Primary, icon = WatchIcons.Backup, enabled = !busy)
            Text(
                "One .zip file with every watch, photo, document and service record, and your settings. You choose where it goes: this phone, a USB drive or a cloud folder.",
                style = t.type.bodySmall, color = c.fg3,
            )
        }
    }
}

class RestoreActions(
    val onBack: () -> Unit,
    val onRestore: (RestoreMode) -> Unit,
    val onCancel: () -> Unit,
    val onDone: () -> Unit,
)

/** Reviewing a backup before restoring it, the restore itself, and its report. */
@Composable
fun RestoreScreen(job: BackupState, prefs: DisplayPrefs, actions: RestoreActions) {
    val c = WatchTheme.colors
    val style = WatchTheme.style
    val busy = job.running
    if (busy) KeepScreenOn()
    BackHandler(enabled = busy) { }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(job::class) {
        if (job is BackupState.Restored) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        val title = when (job) {
            is BackupState.Restored -> "Restore complete"
            is BackupState.Previewing -> "Restore backup"
            else -> "Restoring"
        }
        ScreenHeader(title, (job as? BackupState.Previewing)?.fileName, { if (!busy) (if (job is BackupState.Restored) actions.onDone else actions.onBack)() },
            backLabel = if (job is BackupState.Restored) "Done" else "Back")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (job) {
                is BackupState.Previewing -> Review(job.preview, prefs, actions)
                is BackupState.Working -> Column(Modifier.fillMaxSize().padding(horizontal = style.screenPadding), verticalArrangement = Arrangement.Center) {
                    ProgressPanel(
                        title = job.title(),
                        detail = job.progress?.takeIf { it.total > 0 && it.step != BackupProgress.Step.SAVING }?.let { "${CareText.size(it.done)} of ${CareText.size(it.total)}" }
                            ?: "Saving your collection. This takes a moment.",
                        fraction = job.progress?.takeIf { it.total > 0 && it.step != BackupProgress.Step.SAVING }?.fraction,
                        action = if (job.cancellable) ({ WatchButton("Cancel", actions.onCancel, kind = ButtonKind.Text) }) else null,
                    )
                }
                is BackupState.Restored -> Report(job.report, actions)
                is BackupState.Failed -> Column(Modifier.fillMaxSize().padding(horizontal = style.screenPadding), verticalArrangement = Arrangement.Center) {
                    Callout("Couldn't restore that file", job.message, tone = Tone.Danger,
                        actions = { WatchButton("OK", actions.onBack, kind = ButtonKind.Text) })
                }
                BackupState.Idle, is BackupState.BackedUp, is BackupState.CsvExported -> Unit
            }
        }
    }
}

@Composable
private fun Review(preview: ArchivePreview, prefs: DisplayPrefs, actions: RestoreActions) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val phoneEmpty = preview.watchesOnPhone == 0
    var mode by remember { mutableStateOf(if (phoneEmpty) RestoreMode.REPLACE else RestoreMode.MERGE) }
    var confirmReplace by remember { mutableStateOf(false) }
    val merge = preview.merge
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = style.screenPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(style.cardShape).background(c.surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(WatchIcons.Archive)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(preview.createdAt?.let { "Made ${prefs.dateFormat.format(it.localDate())} at ${it.localTime()}" } ?: "Watches backup",
                            style = t.type.groupTitle, color = c.fg)
                        Text(preview.manifest.app, style = t.type.bodySmall, color = c.fg3)
                    }
                }
                FigureRow(
                    listOf(
                        preview.counts.watches.toString() to "Watches",
                        preview.counts.photos.toString() to "Photos",
                        (preview.counts.documents + preview.counts.serviceEntries).toString() to "Records",
                        CareText.size(preview.mediaBytes) to "Size",
                    ),
                )
            }

            if (!phoneEmpty) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t.label("How to restore"), style = t.type.label, color = c.fg3, modifier = Modifier.padding(start = 16.dp).semantics { heading() })
                    ModeCard(
                        selected = mode == RestoreMode.MERGE,
                        title = "Merge with this phone",
                        badge = "Recommended",
                        lines = buildList {
                            add(mergeSummary(preview))
                            add("Nothing on this phone is deleted. Where a watch differs, the more recently edited version is kept.")
                        },
                        onSelect = { mode = RestoreMode.MERGE },
                    )
                    ModeCard(
                        selected = mode == RestoreMode.REPLACE,
                        title = "Replace this phone's collection",
                        danger = true,
                        lines = listOf(
                            "Removes the ${count(preview.watchesOnPhone, "watch", "watches")} on this phone and restores the ${count(preview.counts.watches, "watch", "watches")} in the backup, with its settings.",
                        ),
                        onSelect = { mode = RestoreMode.REPLACE },
                    )
                }
                if (mode == RestoreMode.MERGE && merge.conflicts > 0) {
                    RowGroup("${count(merge.conflicts, "watch differs", "watches differ")}") {
                        val rows = merge.updated.map { it to "Backup is newer · will be updated" } + merge.kept.map { it to "This phone's is newer · kept" }
                        rows.take(MAX_LISTED).forEachIndexed { i, (w, what) ->
                            if (i > 0) RowDivider(inset = false)
                            ListRow(w.title, detail = what, chevron = false)
                        }
                        if (rows.size > MAX_LISTED) ListRow("and ${rows.size - MAX_LISTED} more", chevron = false)
                    }
                }
            } else {
                Callout("This phone's collection is empty", "Everything in the backup will be restored, including its settings.")
            }

            if (preview.warnings.isNotEmpty()) {
                Callout("Some items can't be restored", preview.warnings.joinToString("\n"), tone = Tone.Warning)
            }
        }
        Column(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = style.screenPadding).padding(top = 12.dp, bottom = 16.dp).navigationBarsPadding()) {
            WatchButton(
                when {
                    phoneEmpty -> "Restore"
                    mode == RestoreMode.MERGE -> "Merge"
                    else -> "Replace everything"
                },
                { if (mode == RestoreMode.REPLACE && !phoneEmpty) confirmReplace = true else actions.onRestore(mode) },
                Modifier.fillMaxWidth(),
                kind = if (mode == RestoreMode.REPLACE && !phoneEmpty) ButtonKind.Danger else ButtonKind.Primary,
            )
        }
    }
    if (confirmReplace) {
        ConfirmDialog(
            "Replace ${count(preview.watchesOnPhone, "watch", "watches")}?",
            "Everything now on this phone is removed and replaced by the backup. This can't be undone.",
            "Replace",
            { confirmReplace = false; actions.onRestore(RestoreMode.REPLACE) },
            { confirmReplace = false },
            destructive = true,
        )
    }
}

@Composable
private fun ModeCard(selected: Boolean, title: String, lines: List<String>, onSelect: () -> Unit, badge: String? = null, danger: Boolean = false) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val edge = when {
        selected && danger -> c.danger
        selected -> c.accent
        else -> c.line
    }
    Row(
        Modifier.fillMaxWidth().clip(style.cardShape).background(c.surface)
            .border(if (selected) 2.dp else 1.dp, edge, style.cardShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(16.dp),
    ) {
        Box(Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape).border(2.dp, if (selected) edge else c.fg3, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(edge))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = t.type.button, color = if (danger && selected) c.danger else c.fg, modifier = Modifier.weight(1f, fill = false))
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Badge(badge)
                }
            }
            lines.forEach { Text(it, style = t.type.bodySmall, color = c.fg2) }
        }
    }
}

@Composable
private fun Report(report: RestoreReport, actions: RestoreActions) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = style.screenPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(WatchIcons.CheckCircle, size = 64)
                Text(
                    if (report.mode == RestoreMode.REPLACE) "Your collection was restored" else "The backup was merged",
                    style = t.type.groupTitle, color = c.fg,
                )
                Text(reportSummary(report), style = t.type.bodySmall, color = c.fg2)
            }
            RowGroup(null) {
                val lines = listOfNotNull(
                    report.watchesAdded.takeIf { it > 0 }?.let { count(it, "watch", "watches") + if (report.mode == RestoreMode.REPLACE) " restored" else " added" },
                    report.updated.size.takeIf { it > 0 }?.let { count(it, "watch", "watches") + " updated from the backup" },
                    report.kept.size.takeIf { it > 0 }?.let { count(it, "watch", "watches") + " kept as on this phone" },
                    report.watchesUnchanged.takeIf { it > 0 }?.let { count(it, "watch", "watches") + " already the same" },
                    report.photosAdded.takeIf { it > 0 }?.let { count(it, "photo", "photos") + " added" },
                    report.serviceEntriesAdded.takeIf { it > 0 }?.let { count(it, "service record", "service records") + " added" },
                    report.documentsAdded.takeIf { it > 0 }?.let { count(it, "document", "documents") + " added" },
                )
                lines.forEachIndexed { i, line ->
                    if (i > 0) RowDivider(inset = false)
                    ListRow(line, chevron = false)
                }
            }
            if (report.updated.isNotEmpty() || report.kept.isNotEmpty()) {
                RowGroup("Watches that differed") {
                    val rows = report.updated.map { it to "Updated from the backup" } + report.kept.map { it to "This phone's newer version kept" }
                    rows.forEachIndexed { i, (w, what) ->
                        if (i > 0) RowDivider(inset = false)
                        ListRow(w.title, detail = what, chevron = false)
                    }
                }
            }
            if (report.warnings.isNotEmpty()) Callout("Not restored", report.warnings.joinToString("\n"), tone = Tone.Warning)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = style.screenPadding).padding(top = 12.dp, bottom = 16.dp).navigationBarsPadding()) {
            WatchButton("Done", actions.onDone, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
        }
    }
}

private fun mergeSummary(p: ArchivePreview): String {
    val m = p.merge
    val parts = listOfNotNull(
        m.added.size.takeIf { it > 0 }?.let { "adds ${count(it, "watch", "watches")}" },
        m.updated.size.takeIf { it > 0 }?.let { "updates ${it}" },
        m.kept.size.takeIf { it > 0 }?.let { "keeps $it newer here" },
        (m.photosAdded + m.serviceEntriesAdded + m.documentsAdded).takeIf { it > 0 }?.let { "${count(it, "photo or record", "photos and records")} to add" },
    )
    return if (parts.isEmpty()) "This phone already has everything in the backup." else parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
}

private fun reportSummary(r: RestoreReport): String = when (r.mode) {
    RestoreMode.REPLACE -> listOfNotNull(
        count(r.watchesAdded, "watch", "watches") + " restored",
        r.watchesRemoved.takeIf { it > 0 }?.let { "${count(it, "watch", "watches")} that were on this phone removed" },
    ).joinToString(" · ")
    RestoreMode.MERGE -> "Nothing on this phone was deleted."
}

private fun BackupState.Working.title(): String = when (task) {
    BackupTask.BACKUP -> when (progress?.step) {
        BackupProgress.Step.CHECKING, null -> "Preparing backup…"
        else -> "Writing backup…"
    }
    BackupTask.CSV -> "Exporting spreadsheet…"
    BackupTask.READ -> "Reading backup…"
    BackupTask.RESTORE -> if (progress?.step == BackupProgress.Step.SAVING) "Saving your collection…" else "Restoring photos and documents…"
}

/** Once a restore starts saving it always finishes, so the collection is never half-restored. */
private val BackupState.Working.cancellable: Boolean get() = task != BackupTask.RESTORE || progress?.step != BackupProgress.Step.SAVING

private fun BackupState.resultKey(): String = when (this) {
    is BackupState.BackedUp -> "backed-up"
    is BackupState.CsvExported -> "csv"
    is BackupState.Failed -> "failed"
    else -> "none"
}

private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

private fun Instant.localDate(): LocalDate = toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun Instant.localTime(): String = toLocalDateTime(TimeZone.currentSystemDefault()).let {
    "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
}

private const val STALE_DAYS = 30
private const val MAX_LISTED = 8
