package io.github.tunlezah.watches.search.http

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/**
 * One request the app made, or refused to make. [status] is null when nothing came back.
 * [outcome] is a short human description such as "OK", "Refused: robots.txt" or "Timed out".
 */
data class LoggedRequest(
    val at: Instant,
    val url: String,
    val host: String,
    val status: Int?,
    val outcome: String,
    val millis: Long,
    val bytes: Int?,
    val sent: Boolean = true,
)

/** Every request since launch, newest first. Kept in memory only, so it's gone when the app closes. */
class RequestLog(private val capacity: Int = 300) {
    private val _entries = MutableStateFlow<List<LoggedRequest>>(emptyList())
    val entries: StateFlow<List<LoggedRequest>> = _entries.asStateFlow()

    fun add(entry: LoggedRequest) = _entries.update { (listOf(entry) + it).take(capacity) }

    fun clear() { _entries.value = emptyList() }
}
