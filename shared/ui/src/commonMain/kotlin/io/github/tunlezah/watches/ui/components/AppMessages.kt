package io.github.tunlezah.watches.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import io.github.tunlezah.watches.data.WatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One snackbar for the whole app, shown above the tab bar. A message outlives the screen that
 * raised it, which matters when an action also leaves the screen (deleting a watch goes back).
 */
class AppMessages {
    val host = SnackbarHostState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun show(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message)
        }
    }

    /** A message with Undo. [onDone] runs if it times out or another message replaces it. */
    fun showWithUndo(message: String, onUndo: () -> Unit, onDone: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            when (host.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long)) {
                SnackbarResult.ActionPerformed -> onUndo()
                SnackbarResult.Dismissed -> onDone()
            }
        }
    }
}

/**
 * Archive and delete, both undoable. A deleted watch disappears at once but is only really
 * removed when the Undo offer ends; if the app is closed before then, nothing was lost.
 */
class UndoableActions(private val watches: WatchRepository, private val messages: AppMessages) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _hidden = MutableStateFlow<Set<String>>(emptySet())

    /** Watches deleted but still within their Undo window: lists leave them out. */
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    fun delete(watchId: String, title: String) {
        _hidden.update { it + watchId }
        messages.showWithUndo(
            "Deleted $title",
            onUndo = { _hidden.update { it - watchId } },
            onDone = {
                scope.launch {
                    watches.delete(watchId)
                    _hidden.update { it - watchId }
                }
            },
        )
    }

    fun setArchived(watchId: String, archived: Boolean) {
        scope.launch { watches.setArchived(watchId, archived) }
        messages.showWithUndo(
            if (archived) "Archived" else "Back in your collection",
            onUndo = { scope.launch { watches.setArchived(watchId, !archived) } },
        )
    }
}
