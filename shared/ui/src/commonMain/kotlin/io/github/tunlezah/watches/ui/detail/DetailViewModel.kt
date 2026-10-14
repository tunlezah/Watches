package io.github.tunlezah.watches.ui.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.format.Numbers
import io.github.tunlezah.watches.core.model.OtherSpec
import io.github.tunlezah.watches.core.model.Sources
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.spec.FormattedValue
import io.github.tunlezah.watches.core.spec.SpecGroupSheet
import io.github.tunlezah.watches.core.spec.SpecRow
import io.github.tunlezah.watches.core.spec.SpecSheet
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.ui.components.WatchLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import io.github.tunlezah.watches.ui.lookup.RefreshSpecs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@Immutable
/** [credit] is set for reference pictures saved from a lookup, which must keep their attribution. */
data class PhotoUi(val id: String, val display: String, val thumb: String, val credit: String? = null)

@Immutable
data class DetailStat(val label: String, val value: String, val emphasis: Boolean = false)

@Immutable
data class DetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val watch: Watch? = null,
    val brand: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val reference: String? = null,
    val statusLabel: String = "",
    val archived: Boolean = false,
    val photos: List<PhotoUi> = emptyList(),
    val look: WatchLook? = null,
    val stats: List<DetailStat> = emptyList(),
    val groups: List<SpecGroupSheet> = emptyList(),
    val other: SpecGroupSheet? = null,
    val footer: String = "",
    /** Some values came from a lookup, so "Refresh from sources" has pages to re-read. */
    val hasSources: Boolean = false,
    val notice: String? = null,
    val refreshing: Boolean = false,
)

class DetailViewModel(
    private val watchId: String,
    private val watches: WatchRepository,
    settings: SettingsRepository,
    private val files: FileStore,
    private val time: TimeSource,
    private val refresher: RefreshSpecs,
) : ViewModel() {

    private val transient = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    val state: StateFlow<DetailUiState> = combine(watches.observeDetail(watchId), settings.settings, transient) { detail, s, (refreshing, notice) ->
        if (detail == null) return@combine DetailUiState(loading = false, missing = true)
        val w = detail.watch
        val prefs = s.display
        val photos = detail.photos.sortedBy { it.position }.let { list ->
            val cover = detail.cover
            if (cover == null) list else listOf(cover) + list.filter { it.id != cover.id }
        }
        DetailUiState(
            loading = false,
            watch = w,
            brand = w.brand,
            title = w.modelName ?: w.collection ?: w.reference ?: w.nickname ?: "Untitled watch",
            subtitle = listOfNotNull(w.nickname?.let { "“$it”" }, w.collection?.takeIf { it != w.modelName && w.modelName?.contains(it) != true })
                .joinToString(" · ").ifEmpty { null },
            reference = w.reference,
            statusLabel = w.status.label,
            archived = w.isArchived,
            photos = photos.map { PhotoUi(it.id, files.absolute(it.displayFile), files.absolute(it.thumbFile), it.attribution) },
            look = WatchLook.guess(w.dialColour, w.caseMaterial, w.complications),
            stats = stats(w, prefs),
            groups = SpecSheet.build(w, detail.provenance, prefs),
            other = otherSheet(detail.otherSpecs),
            footer = footer(w, prefs),
            hasSources = detail.provenance.values.any { it.source != Sources.MANUAL } || detail.otherSpecs.any { it.source != null && it.source != Sources.MANUAL },
            notice = notice,
            refreshing = refreshing,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private fun today(): LocalDate = time.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun stats(w: Watch, prefs: DisplayPrefs): List<DetailStat> = buildList {
        add(DetailStat("Status", if (w.isArchived) "Archived" else w.status.label, emphasis = true))
        w.diameterMm?.let { add(DetailStat("Case", "${Numbers.decimal(it, 1)} mm")) }
        w.movementType?.let { add(DetailStat("Movement", it.label.replace(" winding", ""))) }
        val interval = w.serviceIntervalMonths
        val since = w.acquiredOn
        if (interval != null && since != null) {
            val due = since.plus(interval, DateTimeUnit.MONTH)
            add(DetailStat(if (due < today()) "Service overdue" else "Next service", prefs.dateFormat.formatMonth(due), emphasis = due < today()))
        }
        val wStart = w.warrantyStart
        val wMonths = w.warrantyMonths
        if (wStart != null && wMonths != null) {
            val end = wStart.plus(wMonths, DateTimeUnit.MONTH)
            add(DetailStat("Warranty", if (end < today()) "Expired" else "to ${prefs.dateFormat.formatMonth(end)}"))
        }
    }.take(4)

    private fun otherSheet(other: List<OtherSpec>): SpecGroupSheet? {
        if (other.isEmpty()) return null
        return SpecGroupSheet(
            io.github.tunlezah.watches.core.spec.SpecGroup.IDENTITY,
            other.map {
                SpecRow(
                    keys = listOf("other.${it.id}"),
                    label = it.label,
                    value = FormattedValue(it.value),
                    sourceLabel = it.source?.takeIf { s -> s != Sources.MANUAL }?.let(Sources::shortName),
                    sourceUrl = it.sourceUrl,
                    userEdited = false,
                )
            },
        )
    }

    private fun footer(w: Watch, prefs: DisplayPrefs): String {
        val tz = TimeZone.currentSystemDefault()
        val added = prefs.dateFormat.format(w.createdAt.toLocalDateTime(tz).date)
        val updated = prefs.dateFormat.format(w.updatedAt.toLocalDateTime(tz).date)
        return if (added == updated) "Added $added" else "Added $added · Updated $updated"
    }

    fun setArchived(archived: Boolean) = viewModelScope.launch { watches.setArchived(watchId, archived) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        watches.delete(watchId)
        onDone()
    }

    /** Re-reads the source pages behind this watch's specs, then [onReady] opens the review. */
    fun refresh(onReady: () -> Unit) {
        if (transient.value.first) return
        transient.value = true to "Checking the sources…"
        viewModelScope.launch {
            val title = state.value.watch?.title.orEmpty()
            val message = refresher.prepare(watchId, title)
            transient.value = false to message
            if (message == null) onReady()
        }
    }

    fun dismissNotice() { transient.value = transient.value.first to null }

    fun duplicate(onDone: (String) -> Unit) = viewModelScope.launch {
        watches.duplicate(watchId)?.let { onDone(it.id) }
    }
}
