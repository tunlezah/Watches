package io.github.tunlezah.watches.search

import io.github.tunlezah.watches.core.lookup.SearchHit
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.search.http.LookupFailure
import io.github.tunlezah.watches.search.http.PoliteHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** Whether the device has a usable network right now. */
fun interface Connectivity {
    fun isOnline(): Boolean
}

sealed interface SourceState {
    data object Loading : SourceState
    data class Results(val hits: List<SearchHit>) : SourceState
    data class Failed(val failure: LookupFailure) : SourceState
}

data class SourceEvent(val source: String, val state: SourceState)

/**
 * Runs a lookup across the enabled sources. Results stream per source, so a slow or broken source
 * never holds up the others. Offline, nothing is sent and every source reports [LookupFailure.Offline].
 */
class LookupService(
    val sources: List<SpecSource>,
    private val http: PoliteHttpClient,
    private val connectivity: Connectivity,
) {
    val searchable: List<SpecSource> get() = sources.filter { it.info.status == SourceStatus.READY }

    fun source(id: String): SpecSource? = sources.firstOrNull { it.info.id == id }

    fun search(query: String, enabled: Set<String>): Flow<SourceEvent> = channelFlow {
        val q = cleanQuery(query)
        val chosen = searchable.filter { it.info.id in enabled }
        if (q.isEmpty() || chosen.isEmpty()) return@channelFlow
        if (!connectivity.isOnline()) {
            chosen.forEach { send(SourceEvent(it.info.id, SourceState.Failed(LookupFailure.Offline()))) }
            return@channelFlow
        }
        for (source in chosen) {
            launch {
                send(SourceEvent(source.info.id, SourceState.Loading))
                val state = try {
                    SourceState.Results(source.search(q))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SourceState.Failed(e.asFailure(source))
                }
                send(SourceEvent(source.info.id, state))
            }
        }
    }

    /** Fetches one result's full record. Throws [LookupFailure]. */
    suspend fun detail(hit: SearchHit): SourceRecord {
        val source = source(hit.source) ?: throw LookupFailure.NotAllowed(hit.url)
        if (!connectivity.isOnline()) throw LookupFailure.Offline()
        return try {
            source.detail(hit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.asFailure(source)
        }
    }

    /** One picture for a card on screen. Images on these sites are under their robots rules like any page. */
    suspend fun image(url: String, maxBytes: Int = 3 * 1024 * 1024): ByteArray {
        if (!connectivity.isOnline()) throw LookupFailure.Offline()
        return http.get(url, accept = "image/avif,image/webp,image/*;q=0.8", maxBytes = maxBytes).bytes
    }

    private fun Exception.asFailure(source: SpecSource): LookupFailure =
        this as? LookupFailure ?: LookupFailure.Unreadable("Couldn't read ${source.info.name}", this)

    companion object {
        fun cleanQuery(query: String): String = query.trim().replace(Regex("\\s+"), " ").take(100)
    }
}
