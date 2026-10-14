package io.github.tunlezah.watches.ui.lookup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tunlezah.watches.core.Ids
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.lookup.Coverage
import io.github.tunlezah.watches.core.lookup.DiffRow
import io.github.tunlezah.watches.core.lookup.LookupDiff
import io.github.tunlezah.watches.core.lookup.RecordImage
import io.github.tunlezah.watches.core.lookup.SearchHit
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.core.model.OtherSpec
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.model.WatchSummary
import io.github.tunlezah.watches.core.settings.AppSettings
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.data.LookupRepository
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.search.Connectivity
import io.github.tunlezah.watches.search.LookupService
import io.github.tunlezah.watches.search.SourceInfo
import io.github.tunlezah.watches.search.SourceState
import io.github.tunlezah.watches.search.SourceStatus
import io.github.tunlezah.watches.search.http.LookupFailure
import io.github.tunlezah.watches.search.sources.WikidataSource
import io.github.tunlezah.watches.ui.photos.PhotoImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One source as the Lookup screen shows it. */
@Immutable
data class SourceUi(
    val info: SourceInfo,
    val enabled: Boolean,
    val state: SourceState?,
)

@Immutable
data class LookupUiState(
    val target: LookupTarget = LookupTarget.None,
    val targetTitle: String? = null,
    val query: String = "",
    val searched: String? = null,
    val sources: List<SourceUi> = emptyList(),
    val recent: List<String> = emptyList(),
    val tray: List<SourceRecord> = emptyList(),
    val offline: Boolean = false,
) {
    val searching: Boolean get() = sources.any { it.state == SourceState.Loading }
    val anyEnabled: Boolean get() = sources.any { it.enabled }
}

/** Short words for why a source couldn't answer, shown on its card. */
fun LookupFailure.describe(sourceName: String): String = when (this) {
    is LookupFailure.Offline -> "You're offline. Nothing was sent."
    is LookupFailure.TimedOut -> "$sourceName didn't answer within 10 seconds."
    is LookupFailure.RobotsDisallowed -> "$sourceName's robots.txt doesn't allow this page, so it wasn't requested."
    is LookupFailure.RobotsUnavailable -> "Couldn't read $sourceName's robots.txt, so nothing was requested."
    is LookupFailure.RateLimited -> "$sourceName asked the app to wait. Try again in ${retryAfterSeconds} s."
    is LookupFailure.Blocked -> "$sourceName blocked the request. The app won't disguise itself to get past it."
    is LookupFailure.Http -> "$sourceName answered with an error ($status)."
    is LookupFailure.TooLarge -> "$sourceName sent a page too large to read."
    is LookupFailure.Unreadable -> "Couldn't read $sourceName. Its pages may have changed."
    is LookupFailure.NotAllowed -> "That page isn't one the app may fetch."
}

class LookupViewModel(
    private val target: LookupTarget,
    initialQuery: String?,
    private val session: LookupSession,
    private val service: LookupService,
    private val lookups: LookupRepository,
    private val settings: SettingsRepository,
    private val watches: WatchRepository,
    private val drafts: LookupDrafts,
    private val images: RemoteImages,
    private val connectivity: Connectivity,
) : ViewModel() {

    private val targetTitle = MutableStateFlow<String?>(null)
    private val offline = MutableStateFlow(!connectivity.isOnline())

    init {
        session.begin(target, initialQuery)
        viewModelScope.launch {
            targetTitle.value = when (target) {
                LookupTarget.None -> null
                is LookupTarget.Watch -> watches.get(target.id)?.title
                is LookupTarget.Form -> drafts.form(target.key)?.title
            }
        }
    }

    val state: StateFlow<LookupUiState> = combine(
        session.state, lookups.observeRecent(), settings.settings, targetTitle, offline,
    ) { s, recent, prefs, title, off ->
        LookupUiState(
            target = target,
            targetTitle = title,
            query = s.query,
            searched = s.searched,
            sources = service.sources.filter { it.info.status == SourceStatus.READY }.map { src ->
                SourceUi(src.info, src.info.id !in prefs.disabledSources, s.sources[src.info.id])
            },
            recent = recent,
            tray = s.tray,
            offline = off,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LookupUiState(target = target))

    val sessionImages: RemoteImages get() = images

    fun setQuery(q: String) = session.setQuery(q)

    fun search(query: String = session.state.value.query) {
        val q = LookupService.cleanQuery(query)
        if (q.isEmpty()) return
        session.setQuery(q)
        offline.value = !connectivity.isOnline()
        viewModelScope.launch {
            images.clear()
            lookups.remember(q)
            val disabled = settings.current().disabledSources
            session.search(viewModelScope, service, q, service.searchable.map { it.info.id }.filter { it !in disabled }.toSet())
        }
    }

    fun retry() = session.state.value.searched?.let { search(it) }

    fun toggleSource(id: String) = viewModelScope.launch {
        settings.update { s -> s.copy(disabledSources = if (id in s.disabledSources) s.disabledSources - id else s.disabledSources + id) }
    }

    fun forgetRecent(q: String) = viewModelScope.launch { lookups.forget(q) }

    fun clearRecent() = viewModelScope.launch { lookups.clearRecent() }

    fun clearTray() = session.clearTray()

    /** Watches for the "Apply to a watch" picker. */
    val watchChoices: StateFlow<List<WatchSummary>> = watches.observeSummaries()
        .map { all -> all.filter { !it.archived }.sortedWith(compareBy({ it.brand?.lowercase() }, { it.modelName?.lowercase() })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Immutable
data class ResultUiState(
    val hit: SearchHit? = null,
    val sourceName: String = "",
    val record: SourceRecord? = null,
    val loading: Boolean = true,
    val failure: String? = null,
    val inTray: Boolean = false,
    val canSavePictures: Boolean = false,
    val pictureStatus: String? = null,
)

class ResultViewModel(
    private val hitId: String,
    private val session: LookupSession,
    private val service: LookupService,
    private val importer: PhotoImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.collect { s ->
                val record = _state.value.record
                _state.update {
                    it.copy(
                        inTray = record != null && session.inTray(record.source, record.key),
                        canSavePictures = s.target is LookupTarget.Watch,
                    )
                }
            }
        }
        load()
    }

    fun load() {
        val hit = session.hit(hitId)
        val cached = session.record(hitId)
        val name = (hit?.source ?: cached?.source)?.let { service.source(it)?.info?.name }.orEmpty()
        if (cached != null) {
            _state.update { it.copy(hit = hit, sourceName = name, record = cached, loading = false, failure = null, inTray = session.inTray(cached.source, cached.key)) }
            return
        }
        if (hit == null) {
            _state.update { it.copy(loading = false, failure = "This result is no longer available. Search again.") }
            return
        }
        _state.update { it.copy(hit = hit, sourceName = name, loading = true, failure = null) }
        viewModelScope.launch {
            try {
                val record = service.detail(hit)
                session.remember(record)
                _state.update { it.copy(record = record, loading = false, inTray = session.inTray(record.source, record.key)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LookupFailure) {
                _state.update { it.copy(loading = false, failure = e.describe(name)) }
            }
        }
    }

    fun toggleTray() {
        val record = _state.value.record ?: return
        session.toggleTray(record)
    }

    /** Copies a freely licensed picture into the watch's photos, with its credit. Wikimedia Commons only. */
    fun savePicture(image: RecordImage) {
        val target = session.state.value.target as? LookupTarget.Watch ?: return
        val page = image.pageUrl ?: return
        val wikidata = service.source(WikidataSource.ID) as? WikidataSource ?: return
        if (!image.savable) return
        if (!service.isOnline()) {
            _state.update { it.copy(pictureStatus = LookupFailure.Offline().describe("Wikimedia Commons")) }
            return
        }
        _state.update { it.copy(pictureStatus = "Saving picture…") }
        viewModelScope.launch {
            val status = try {
                val info = wikidata.commonsImage(page)
                val bytes = service.image(info.imageUrl, maxBytes = 12 * 1024 * 1024)
                importer.importReference(target.id, bytes, info.attribution, info.pageUrl).await()
                "Saved to this watch's photos, with its credit. It won't become the cover unless you choose it."
            } catch (e: CancellationException) {
                throw e
            } catch (e: LookupFailure) {
                e.describe("Wikimedia Commons")
            } catch (_: Exception) {
                "Couldn't save that picture."
            }
            _state.update { it.copy(pictureStatus = status) }
        }
    }
}

/** One record's extra specs and whether they'll be saved. */
@Immutable
data class ExtrasChoice(val source: String, val title: String, val specs: List<OtherSpec>, val keep: Boolean = true)

@Immutable
data class ApplyUiState(
    val loaded: Boolean = false,
    val target: LookupTarget = LookupTarget.None,
    val targetTitle: String = "",
    val rows: List<DiffRow> = emptyList(),
    val choices: Map<String, Int> = emptyMap(),
    val extras: List<ExtrasChoice> = emptyList(),
    val records: List<SourceRecord> = emptyList(),
    val applying: Boolean = false,
    val error: String? = null,
) {
    val changeCount: Int get() = choices.size + extras.count { it.keep && it.specs.isNotEmpty() }
}

class ApplyViewModel(
    private val target: LookupTarget,
    private val session: LookupSession,
    private val watches: WatchRepository,
    private val lookups: LookupRepository,
    private val drafts: LookupDrafts,
    private val time: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ApplyUiState(target = target))
    val state: StateFlow<ApplyUiState> = _state.asStateFlow()

    private var base: Watch? = null

    init {
        viewModelScope.launch {
            val records = session.state.value.tray
            val now = time.now()
            val (current, provenance, title) = when (target) {
                LookupTarget.None -> Triple(Watch(id = "", createdAt = now, updatedAt = now), emptyMap(), "New watch")
                is LookupTarget.Watch -> {
                    val detail = watches.observeDetail(target.id).first()
                    if (detail == null) {
                        _state.update { it.copy(loaded = true, error = "This watch no longer exists.") }
                        return@launch
                    }
                    Triple(detail.watch, detail.provenance, detail.watch.title)
                }
                is LookupTarget.Form -> {
                    val form = drafts.form(target.key)
                    if (form == null) {
                        _state.update { it.copy(loaded = true, error = "The form this lookup was for has closed.") }
                        return@launch
                    }
                    val (watch, prov) = form.current()
                    Triple(watch, prov, form.title)
                }
            }
            base = current
            val rows = LookupDiff.rows(current, provenance, records)
            _state.value = ApplyUiState(
                loaded = true,
                target = target,
                targetTitle = title,
                rows = rows,
                choices = rows.mapNotNull { r -> r.defaultChoice?.let { r.field.key to it } }.toMap(),
                extras = records.map { r -> ExtrasChoice(r.source, r.title, extrasOf(r)) }.filter { it.specs.isNotEmpty() },
                records = records,
            )
        }
    }

    /** Ticks [option] for [key], or unticks the row when [option] is null or already ticked. */
    fun choose(key: String, option: Int?) = _state.update { s ->
        val next = if (option == null || s.choices[key] == option) s.choices - key else s.choices + (key to option)
        s.copy(choices = next)
    }

    fun toggleExtras(source: String) = _state.update { s ->
        s.copy(extras = s.extras.map { if (it.source == source) it.copy(keep = !it.keep) else it })
    }

    /** Applies the ticked values. [onDone] gets the id of a saved watch, or a draft key for the edit form. */
    fun apply(onSaved: (String) -> Unit, onFormFilled: () -> Unit, onDraft: (String) -> Unit) {
        val s = _state.value
        val current = base ?: return
        if (s.applying) return
        _state.update { it.copy(applying = true) }
        val (applied, provenance) = LookupDiff.apply(current, s.rows, s.choices)
        val sourced = provenance.associateBy { it.fieldKey }
        val sourcedValues = sourced.keys.associateWith { key -> SpecFields.byKey(key)?.get(applied) }
        val extras = s.extras.filter { it.keep }.flatMap { it.specs }
        val records = s.records
        viewModelScope.launch {
            when (target) {
                is LookupTarget.Watch -> {
                    watches.update(applied, sourced, extras)
                    lookups.saveSnapshots(target.id, records)
                    session.clearTray()
                    onSaved(target.id)
                }
                is LookupTarget.Form -> {
                    drafts.form(target.key)?.apply(LookupDraft(applied, sourced, sourcedValues, extras, records))
                    session.clearTray()
                    onFormFilled()
                }
                LookupTarget.None -> {
                    // The tray stays: if the owner comes back from the form, the review is still there.
                    val key = drafts.put(LookupDraft(applied, sourced, sourcedValues, extras, records))
                    _state.update { it.copy(applying = false) }
                    onDraft(key)
                }
            }
        }
    }

    private fun extrasOf(record: SourceRecord): List<OtherSpec> = record.extras.map { e ->
        OtherSpec(Ids.new(), e.label, e.value, e.source ?: record.source, e.url ?: record.url, record.fetchedAt)
    }
}

/** Re-fetches the source pages a watch's specs came from, then opens the review screen. */
class RefreshSpecs(
    private val lookups: LookupRepository,
    private val service: LookupService,
    private val session: LookupSession,
) {
    /** Returns null on success (the tray holds fresh records), or a message. */
    suspend fun prepare(watchId: String, title: String): String? {
        val pages = lookups.snapshotSources(watchId)
        if (pages.isEmpty()) return "No specs on this watch came from a lookup yet."
        val hits = pages.mapNotNull { (source, url) -> service.source(source)?.hitFor(url, title) }
        session.begin(LookupTarget.Watch(watchId), null)
        val results = kotlinx.coroutines.coroutineScope {
            hits.map { hit -> async { runCatching { service.detail(hit) } } }.awaitAll()
        }
        val records = results.mapNotNull { it.getOrNull() }
        results.firstNotNullOfOrNull { it.exceptionOrNull() as? CancellationException }?.let { throw it }
        if (records.isEmpty()) {
            val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() as? LookupFailure }
            return failure?.describe("The source") ?: "Couldn't refresh from the sources."
        }
        records.forEach(session::remember)
        session.setTray(records)
        return null
    }
}

/** The settings section for lookup sources. */
@Immutable
data class SourcesUiState(
    val sources: List<SourceUi> = emptyList(),
    val watchBase: SourceInfo? = null,
    val watchBaseResult: String? = null,
    val testing: Boolean = false,
)

class SourcesViewModel(
    private val service: LookupService,
    private val settings: SettingsRepository,
    private val lookups: LookupRepository,
) : ViewModel() {
    private val probe = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    val state: StateFlow<SourcesUiState> = combine(settings.settings, probe) { prefs: AppSettings, (testing, result) ->
        SourcesUiState(
            sources = service.sources.filter { it.info.status == SourceStatus.READY }.map { SourceUi(it.info, it.info.id !in prefs.disabledSources, null) },
            watchBase = service.sources.firstOrNull { it.info.status == SourceStatus.UNVERIFIED }?.info,
            watchBaseResult = result,
            testing = testing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourcesUiState())

    fun toggle(id: String) = viewModelScope.launch {
        settings.update { s -> s.copy(disabledSources = if (id in s.disabledSources) s.disabledSources - id else s.disabledSources + id) }
    }

    fun clearRecent() = viewModelScope.launch { lookups.clearRecent() }

    fun testWatchBase() {
        val source = service.source(io.github.tunlezah.watches.search.sources.WatchBaseSource.ID)
            as? io.github.tunlezah.watches.search.sources.WatchBaseSource ?: return
        if (!service.isOnline()) {
            probe.value = false to LookupFailure.Offline().describe("WatchBase")
            return
        }
        probe.value = true to null
        viewModelScope.launch {
            val failure = source.testConnection()
            probe.value = false to (failure?.describe("WatchBase") ?: "WatchBase answered normally. Send the three saved pages so its parser can be built.")
        }
    }
}

val Coverage.short: String
    get() = when (this) {
        Coverage.WATCH -> "Watches"
        Coverage.MOVEMENT -> "Movements"
        Coverage.IDENTITY -> "Identity & history"
    }
