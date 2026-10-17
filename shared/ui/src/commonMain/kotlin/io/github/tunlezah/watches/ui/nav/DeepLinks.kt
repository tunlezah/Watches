package io.github.tunlezah.watches.ui.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Requests from outside the UI (a tapped notification) to open a screen. The shell consumes them. */
object DeepLinks {
    private val _serviceHistory = MutableStateFlow<String?>(null)
    val serviceHistory: StateFlow<String?> = _serviceHistory.asStateFlow()

    fun openServiceHistory(watchId: String) { _serviceHistory.value = watchId }

    fun consumed() { _serviceHistory.value = null }
}
