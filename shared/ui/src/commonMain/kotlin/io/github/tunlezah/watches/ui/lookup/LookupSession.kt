package io.github.tunlezah.watches.ui.lookup

import io.github.tunlezah.watches.core.lookup.SearchHit
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.core.model.OtherSpec
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.spec.SpecValue
import io.github.tunlezah.watches.search.LookupService
import io.github.tunlezah.watches.search.SourceState
import io.github.tunlezah.watches.search.http.LookupFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a lookup's results are for. */
sealed interface LookupTarget {
    /** Started from the Lookup tab: results become a new watch, or are applied to one picked later. */
    data object None : LookupTarget

    /** Started from a saved watch: results are applied to it after review. */
    data class Watch(val id: String) : LookupTarget

    /** Started from an open edit form: results fill the form, and nothing is saved until the form is. */
    data class Form(val key: String) : LookupTarget

    fun encode(): String = when (this) {
        None -> "none"
        is Watch -> "watch:$id"
        is Form -> "form:$key"
    }

    companion object {
        fun decode(s: String): LookupTarget = when {
            s.startsWith("watch:") -> Watch(s.removePrefix("watch:"))
            s.startsWith("form:") -> Form(s.removePrefix("form:"))
            else -> None
        }
    }
}

/**
 * The lookup in progress: the query, each source's results, fetched records and the records the
 * owner chose to use (the "tray"). Kept in memory only; a lookup is a short task the owner drives.
 */
class LookupSession {

    data class State(
        val target: LookupTarget = LookupTarget.None,
        val query: String = "",
        val searched: String? = null,
        val sources: Map<String, SourceState> = emptyMap(),
        val tray: List<SourceRecord> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val hits = mutableMapOf<String, SearchHit>()
    private val records = mutableMapOf<String, SourceRecord>()
    private var job: Job? = null

    fun hit(id: String): SearchHit? = hits[id]
    fun record(id: String): SourceRecord? = records[id]
    fun remember(record: SourceRecord) { records[idOf(record.source, record.key)] = record }

    /** Opens the lookup for [target]. A different target starts afresh, so choices never leak between watches. */
    fun begin(target: LookupTarget, query: String?) {
        val s = _state.value
        if (s.target != target) {
            job?.cancel()
            _state.value = State(target = target, query = query.orEmpty())
        } else if (!query.isNullOrBlank() && s.searched == null) {
            _state.update { it.copy(query = query) }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun search(scope: CoroutineScope, service: LookupService, query: String, enabled: Set<String>) {
        job?.cancel()
        hits.clear()
        _state.update { it.copy(searched = query, sources = emptyMap()) }
        job = scope.launch {
            service.search(query, enabled).collect { event ->
                (event.state as? SourceState.Results)?.hits?.forEach { hits[idOf(it.source, it.key)] = it }
                _state.update { it.copy(sources = it.sources + (event.source to event.state)) }
            }
        }
    }

    fun inTray(source: String, key: String) = _state.value.tray.any { it.source == source && it.key == key }

    /** One record per source: choosing a second Ranfft calibre replaces the first. */
    fun toggleTray(record: SourceRecord) = _state.update { s ->
        val present = s.tray.any { it.source == record.source && it.key == record.key }
        s.copy(tray = if (present) s.tray.filterNot { it.source == record.source && it.key == record.key } else s.tray.filterNot { it.source == record.source } + record)
    }

    fun setTray(records: List<SourceRecord>) = _state.update { it.copy(tray = records) }

    fun clearTray() = _state.update { it.copy(tray = emptyList()) }

    companion object {
        fun idOf(source: String, key: String) = "$source/$key"
        fun idOf(hit: SearchHit) = idOf(hit.source, hit.key)
    }
}

/** Where result pictures come from. Screens take this, so previews and tests can supply their own. */
fun interface ImageSource {
    suspend fun load(scope: CoroutineScope, url: String): ByteArray?
}

/**
 * Pictures for result cards, fetched only when a card is on screen and kept in memory for the
 * current search only (cleared when a new search starts). Each fetch goes through the polite client.
 */
class RemoteImages(private val service: LookupService) : ImageSource {
    private val lock = Mutex()
    private val cache = LinkedHashMap<String, ByteArray>()
    private val inFlight = mutableMapOf<String, Deferred<ByteArray?>>()

    override suspend fun load(scope: CoroutineScope, url: String): ByteArray? {
        val pending = lock.withLock {
            cache[url]?.let { return it }
            inFlight.getOrPut(url) {
                scope.async {
                    try {
                        service.image(url).also { bytes -> lock.withLock { put(url, bytes); inFlight.remove(url) } }
                    } catch (e: CancellationException) {
                        lock.withLock { inFlight.remove(url) }
                        throw e
                    } catch (_: LookupFailure) {
                        lock.withLock { inFlight.remove(url) }
                        null
                    }
                }
            }
        }
        return pending.await()
    }

    private fun put(url: String, bytes: ByteArray) {
        cache[url] = bytes
        while (cache.size > MAX) cache.remove(cache.keys.first())
    }

    suspend fun clear() = lock.withLock { cache.clear() }

    private companion object {
        const val MAX = 60
    }
}

/** Lookup results on their way into an edit form, with where each value came from. */
data class LookupDraft(
    val watch: Watch,
    val sourced: Map<String, Provenance>,
    val sourcedValues: Map<String, SpecValue?>,
    val extras: List<OtherSpec>,
    val records: List<SourceRecord>,
)

/** An open edit form that lookup results can be applied to. */
interface LookupForm {
    val title: String
    /** The form's values as they stand, and what's known about where each came from. */
    fun current(): Pair<Watch, Map<String, Provenance>>
    fun apply(draft: LookupDraft)
}

/** Hands lookup results to edit forms: drafts for new watches, and values for forms already open. */
class LookupDrafts {
    private val drafts = mutableMapOf<String, LookupDraft>()
    private val forms = mutableMapOf<String, LookupForm>()
    private var counter = 0

    fun put(draft: LookupDraft): String = "d${++counter}".also { drafts[it] = draft }
    fun take(key: String): LookupDraft? = drafts.remove(key)

    fun register(form: LookupForm): String = "f${++counter}".also { forms[it] = form }
    fun unregister(key: String) { forms.remove(key) }
    fun form(key: String): LookupForm? = forms[key]
}
