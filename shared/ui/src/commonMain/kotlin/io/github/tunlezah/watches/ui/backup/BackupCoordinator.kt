package io.github.tunlezah.watches.ui.backup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.settings.LastBackup
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.data.backup.ArchivePreview
import io.github.tunlezah.watches.data.backup.BackupContents
import io.github.tunlezah.watches.data.backup.BackupException
import io.github.tunlezah.watches.data.backup.BackupProgress
import io.github.tunlezah.watches.data.backup.BackupRepository
import io.github.tunlezah.watches.data.backup.CsvExporter
import io.github.tunlezah.watches.data.backup.ExportResult
import io.github.tunlezah.watches.data.backup.RestoreMode
import io.github.tunlezah.watches.data.backup.RestoreReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Sink
import okio.Source

enum class BackupTask { BACKUP, CSV, READ, RESTORE }

sealed interface BackupState {
    data object Idle : BackupState
    data class Working(val task: BackupTask, val progress: BackupProgress? = null) : BackupState
    data class BackedUp(val fileName: String, val result: ExportResult) : BackupState
    data class CsvExported(val fileName: String, val watches: Int) : BackupState
    data class Previewing(val fileName: String, val preview: ArchivePreview) : BackupState
    data class Restored(val report: RestoreReport) : BackupState
    data class Failed(val task: BackupTask, val message: String) : BackupState

    val running: Boolean get() = this is Working
}

/**
 * Runs one backup, restore or export at a time. It belongs to the app rather than a screen, so
 * leaving the Backup screen (or the phone rotating) doesn't stop a backup halfway.
 */
class BackupCoordinator(
    private val backup: BackupRepository,
    private val csv: CsvExporter,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()
    private var job: Job? = null
    private var reopen: (() -> Source)? = null
    /** A result that stands even if the task is then cancelled or fails: a restore that has been saved. */
    private var settled: BackupState? = null

    fun backupFileName(): String = backup.suggestedFileName().removeSuffix(".zip")
    fun csvFileName(): String = csv.suggestedFileName().removeSuffix(".csv")

    /** Writes a backup to [open]'s sink. [discard] removes the half-written file if it fails or is cancelled. */
    fun backUp(fileName: String, open: () -> Sink, discard: suspend () -> Unit) = start(BackupTask.BACKUP, discard) {
        val result = backup.export(open(), progress(BackupTask.BACKUP))
        settings.update { it.copy(device = it.device.copy(lastBackup = LastBackup(time.now(), result.bytes, result.counts.watches))) }
        BackupState.BackedUp(fileName, result)
    }

    fun exportCsv(fileName: String, open: () -> Sink, discard: suspend () -> Unit) = start(BackupTask.CSV, discard) {
        BackupState.CsvExported(fileName, csv.export(open()))
    }

    /** Reads and checks a backup; [open] must give a fresh stream each time, as restoring reads it again. */
    fun inspect(fileName: String, open: () -> Source) = start(BackupTask.READ) {
        reopen = open
        BackupState.Previewing(fileName, backup.inspect(open))
    }

    fun restore(mode: RestoreMode) {
        val previewing = _state.value as? BackupState.Previewing ?: return
        val open = reopen ?: return
        start(BackupTask.RESTORE) {
            val report = backup.restore(open, previewing.preview, mode, progress(BackupTask.RESTORE)) { saved ->
                reopen = null
                settled = BackupState.Restored(saved)
                scope.launch { noteRestored(previewing.preview) }
            }
            BackupState.Restored(report)
        }
    }

    /** The backup just restored exists, so it counts as this phone's latest if it's newer. */
    private suspend fun noteRestored(preview: ArchivePreview) {
        val made = preview.createdAt ?: return
        settings.update { s ->
            val last = s.device.lastBackup
            if (last == null || last.at < made) s.copy(device = s.device.copy(lastBackup = LastBackup(made, preview.mediaBytes, preview.counts.watches))) else s
        }
    }

    /** Stops the running task. A restore that has started saving finishes regardless, so the collection is never half-restored. */
    fun cancel() {
        job?.cancel()
    }

    /** Back to the start, once a result has been seen. */
    fun dismiss() {
        if (!_state.value.running) {
            reopen = null
            _state.value = BackupState.Idle
        }
    }

    private fun start(task: BackupTask, discard: (suspend () -> Unit)? = null, work: suspend () -> BackupState) {
        if (_state.value.running) return
        settled = null
        _state.value = BackupState.Working(task)
        job = scope.launch {
            try {
                _state.value = work()
            } catch (e: CancellationException) {
                _state.value = settled ?: run {
                    withContext(NonCancellable) { discard?.let { runCatching { it() } } }
                    BackupState.Idle
                }
                throw e
            } catch (e: Exception) {
                _state.value = settled ?: run {
                    withContext(NonCancellable) { discard?.let { runCatching { it() } } }
                    BackupState.Failed(task, message(task, e))
                }
            }
        }
    }

    /** Progress, at most every 0.2%, so a large archive doesn't flood the UI. */
    private fun progress(task: BackupTask): (BackupProgress) -> Unit {
        var lastStep: BackupProgress.Step? = null
        var lastShown = -1
        return { p ->
            val permille = (p.fraction * 500).toInt()
            if (p.step != lastStep || permille != lastShown) {
                lastStep = p.step
                lastShown = permille
                _state.value = BackupState.Working(task, p)
            }
        }
    }

    private fun message(task: BackupTask, e: Exception): String = when (e) {
        is BackupException -> e.message ?: "Something went wrong."
        else -> when (task) {
            BackupTask.BACKUP -> "The backup couldn't be saved there. Check there's enough space, or choose another place."
            BackupTask.CSV -> "The spreadsheet couldn't be saved there. Choose another place."
            BackupTask.READ -> "That file couldn't be read. If it's in a cloud folder, make sure it has finished downloading."
            BackupTask.RESTORE -> "The restore couldn't finish. Nothing on this phone was changed."
        }
    }
}

@Immutable
data class BackupUiState(
    val job: BackupState = BackupState.Idle,
    val contents: BackupContents? = null,
    val lastBackup: LastBackup? = null,
)

/** The Backup & restore screen: what a backup would hold, the last one, and the running job. */
class BackupViewModel(
    private val coordinator: BackupCoordinator,
    private val backup: BackupRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val contents = MutableStateFlow<BackupContents?>(null)

    val state: StateFlow<BackupUiState> = combine(coordinator.state, contents, settings.settings) { job, c, s ->
        BackupUiState(job, c, s.device.lastBackup)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState(coordinator.state.value))

    init {
        refresh()
        // A restore changes what a backup would hold.
        viewModelScope.launch {
            coordinator.state.distinctUntilChangedBy { it::class }.collect { if (it is BackupState.Restored || it is BackupState.Idle) refresh() }
        }
    }

    private fun refresh() {
        viewModelScope.launch { contents.value = runCatching { backup.contents() }.getOrNull() }
    }

    val actions get() = coordinator
}
