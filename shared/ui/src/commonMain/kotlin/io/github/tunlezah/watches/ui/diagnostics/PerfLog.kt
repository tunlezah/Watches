package io.github.tunlezah.watches.ui.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Timings measured on the device, shown in Settings › Diagnostics against the targets. Never leaves the phone. */
data class PerfSnapshot(
    val coldStartMs: Long? = null,
    val lastSearchMs: Double? = null,
    val worstSearchMs: Double? = null,
    val lastDetailOpenMs: Long? = null,
    val worstDetailOpenMs: Long? = null,
)

object PerfLog {
    private val state = MutableStateFlow(PerfSnapshot())
    val snapshot: StateFlow<PerfSnapshot> = state.asStateFlow()

    fun coldStart(ms: Long) = state.update { if (it.coldStartMs == null) it.copy(coldStartMs = ms) else it }

    fun search(ms: Double) = state.update { it.copy(lastSearchMs = ms, worstSearchMs = maxOf(ms, it.worstSearchMs ?: 0.0)) }

    fun detailOpen(ms: Long) = state.update { it.copy(lastDetailOpenMs = ms, worstDetailOpenMs = maxOf(ms, it.worstDetailOpenMs ?: 0)) }

    /** Set when a watch is tapped; the detail screen reports the gap when its content first draws. */
    var detailRequestedAt: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null
}
