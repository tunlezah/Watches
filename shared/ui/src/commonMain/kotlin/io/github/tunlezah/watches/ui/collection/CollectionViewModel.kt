package io.github.tunlezah.watches.ui.collection

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tunlezah.watches.core.format.Numbers
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.core.model.WatchSummary
import io.github.tunlezah.watches.core.query.CollectionFilter
import io.github.tunlezah.watches.core.query.CollectionQuery
import io.github.tunlezah.watches.core.query.SortSpec
import io.github.tunlezah.watches.core.query.StatusTab
import io.github.tunlezah.watches.core.settings.CollectionLayout
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.ui.components.WatchLook
import io.github.tunlezah.watches.ui.diagnostics.PerfLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

@Immutable
data class CollectionItem(
    val id: String,
    val brand: String?,
    val title: String,
    val reference: String?,
    val meta: String,
    val status: WatchStatus,
    val archived: Boolean,
    /** Absolute paths, so the UI never touches the file store. */
    val thumb: String?,
    val look: WatchLook,
)

@Immutable
data class CollectionUiState(
    val loaded: Boolean = false,
    val items: List<CollectionItem> = emptyList(),
    val totalActive: Int = 0,
    val ownedCount: Int = 0,
    val query: String = "",
    val filter: CollectionFilter = CollectionFilter(),
    val sort: SortSpec = SortSpec(),
    val layout: CollectionLayout = CollectionLayout.GRID,
    val brands: List<String> = emptyList(),
    val complications: List<String> = emptyList(),
    val sizeBounds: ClosedFloatingPointRange<Double>? = null,
) {
    val isEmptyCollection: Boolean get() = loaded && totalActive == 0 && filter.tab != StatusTab.ARCHIVED && query.isBlank()
}

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModel(
    private val watches: WatchRepository,
    private val settings: SettingsRepository,
    private val files: FileStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(CollectionFilter())

    // Items are cached by summary so unchanged tiles keep their identity and skip recomposition.
    private val itemCache = HashMap<String, Pair<WatchSummary, CollectionItem>>()

    private val summaries = watches.observeSummaries()

    /** Search re-runs when the text or the data changes. Null means no search is active. */
    private val matches = combine(query, summaries) { q, _ -> q }
        .mapLatest { q ->
            val mark = TimeSource.Monotonic.markNow()
            val result = watches.search(q)
            if (result != null) PerfLog.search(mark.elapsedNow().inWholeMicroseconds / 1000.0)
            result
        }

    val state: StateFlow<CollectionUiState> = combine(summaries, filter, matches, settings.settings, query) { all, f, m, s, q ->
        val active = all.filter { !it.archived }
        val visible = CollectionQuery.apply(all, f, s.sort, m)
        CollectionUiState(
            loaded = true,
            items = visible.map(::itemFor),
            totalActive = active.size,
            ownedCount = active.count { it.status == WatchStatus.OWNED },
            query = q,
            filter = f,
            sort = s.sort,
            layout = s.layout,
            brands = active.mapNotNull { it.brand }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
            complications = Complication.sortKeys(active.flatMap { it.complications }.toSet()),
            sizeBounds = CollectionQuery.diameterBounds(active),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState())

    private fun itemFor(s: WatchSummary): CollectionItem {
        itemCache[s.id]?.let { (summary, item) -> if (summary == s) return item }
        val meta = listOfNotNull(
            s.diameterMm?.let { "${Numbers.decimal(it, 1)} mm" },
            s.movementType?.label?.replace(" winding", ""),
        ).joinToString(" · ")
        val item = CollectionItem(
            id = s.id,
            brand = s.brand,
            title = s.title,
            reference = s.reference,
            meta = meta,
            status = s.status,
            archived = s.archived,
            thumb = s.coverThumb?.let(files::absolute),
            look = WatchLook.guess(s.dialColour, s.caseMaterial, s.complications),
        )
        itemCache[s.id] = s to item
        return item
    }

    fun setQuery(text: String) = query.update { text }

    fun setTab(tab: StatusTab) = filter.update { it.copy(tab = tab) }

    fun setFilter(f: CollectionFilter) = filter.update { f.copy(tab = it.tab) }

    fun clearFilters() = filter.update { CollectionFilter(tab = it.tab) }

    fun setSort(sort: SortSpec) = viewModelScope.launch { settings.update { it.copy(sort = sort) } }

    fun toggleLayout() = viewModelScope.launch {
        settings.update { it.copy(layout = if (it.layout == CollectionLayout.GRID) CollectionLayout.LIST else CollectionLayout.GRID) }
    }
}
