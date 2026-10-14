package io.github.tunlezah.watches.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tunlezah.watches.core.settings.AppSettings
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.ui.diagnostics.PerfLog
import io.github.tunlezah.watches.ui.diagnostics.PerfSnapshot
import io.github.tunlezah.watches.ui.diagnostics.SampleGenerator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class DiagnosticsUiState(
    val total: Int = 0,
    val samples: Int = 0,
    val busy: Boolean = false,
    val progress: String? = null,
    val perf: PerfSnapshot = PerfSnapshot(),
)

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(transform) }
}

class DiagnosticsViewModel(
    watches: WatchRepository,
    private val generator: SampleGenerator,
) : ViewModel() {
    val state: StateFlow<DiagnosticsUiState> = combine(
        watches.observeSummaries(), generator.running, generator.status, PerfLog.snapshot,
    ) { all, busy, status, perf ->
        DiagnosticsUiState(all.size, all.count { it.id.startsWith(io.github.tunlezah.watches.core.Ids.SAMPLE_PREFIX) }, busy, status, perf)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsUiState())

    fun generate(count: Int) = generator.generate(count)

    fun removeSamples() = generator.removeAll()

    fun rebuildIndex() = generator.rebuildIndex()
}
