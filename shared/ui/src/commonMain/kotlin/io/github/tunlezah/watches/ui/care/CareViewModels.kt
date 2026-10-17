package io.github.tunlezah.watches.ui.care

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tunlezah.watches.core.Ids
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.care.BoxPapersSummary
import io.github.tunlezah.watches.core.care.CarePlan
import io.github.tunlezah.watches.core.care.DueState
import io.github.tunlezah.watches.core.care.ServiceStatus
import io.github.tunlezah.watches.core.care.WarrantyState
import io.github.tunlezah.watches.core.care.WarrantyStatus
import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.model.BoxItem
import io.github.tunlezah.watches.core.model.BoxItemState
import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.ServiceEntry
import io.github.tunlezah.watches.core.model.ServiceWork
import io.github.tunlezah.watches.core.model.WatchDocument
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.data.BoxPapersRepository
import io.github.tunlezah.watches.data.DocumentRepository
import io.github.tunlezah.watches.data.GuideElsewhere
import io.github.tunlezah.watches.data.ServiceRepository
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.data.WatchCare
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.ui.platform.FileSharer
import io.github.tunlezah.watches.ui.platform.NotificationAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun TimeSource.today(): LocalDate = now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/** "Due 14/03/2027 · in 5 months" and friends, shared by the Service tab, history and detail screens. */
object CareText {
    fun due(status: ServiceStatus, prefs: DisplayPrefs): String? = when (status.state) {
        DueState.NO_INTERVAL -> null
        DueState.NO_START -> "Set the date acquired or log a full service to work out when it's due"
        else -> {
            val date = prefs.dateFormat.format(status.nextDue!!)
            val rel = CarePlan.relative(status.daysLeft ?: 0)
            if (status.state == DueState.OVERDUE) "Overdue since $date ($rel)" else "Due $date ($rel)"
        }
    }

    fun warranty(w: WarrantyStatus, prefs: DisplayPrefs): String = when (w.state) {
        WarrantyState.EXPIRED -> "Warranty ended ${prefs.dateFormat.format(w.until)}"
        else -> "Warranty until ${prefs.dateFormat.format(w.until)} (${CarePlan.relative(w.daysLeft)})"
    }

    fun boxPapers(s: BoxPapersSummary): String = when {
        s.fullSet -> "Full set"
        s.recorded == 0 -> "Not recorded yet"
        s.unrecorded == 0 -> "${s.present} of ${s.applicable} present"
        else -> listOfNotNull(
            "${s.present} present",
            s.missing.takeIf { it > 0 }?.let { "$it missing" },
            "${s.unrecorded} not recorded",
        ).joinToString(", ")
    }

    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> {
            val tenths = bytes * 10 / (1024 * 1024)
            "${tenths / 10}.${tenths % 10} MB"
        }
    }

    fun docType(d: WatchDocument): String = if (d.isPdf) "PDF" else "Image"
}

// Service tab

@Immutable
data class DueItem(
    val watchId: String,
    val title: String,
    val reference: String?,
    val thumb: String?,
    val headline: String,
    val detail: String?,
    val urgent: Boolean,
    val opensPapers: Boolean = false,
    /** Unique within a section: a watch can have several service warranties ending. */
    val key: String = watchId,
)

@Immutable
data class ServiceDueUiState(
    val loaded: Boolean = false,
    val overdue: List<DueItem> = emptyList(),
    val soon: List<DueItem> = emptyList(),
    val later: List<DueItem> = emptyList(),
    val inService: List<DueItem> = emptyList(),
    val noStart: List<DueItem> = emptyList(),
    val warranties: List<DueItem> = emptyList(),
    val noInterval: Int = 0,
    val onHand: Int = 0,
)

class ServiceDueViewModel(services: ServiceRepository, settings: SettingsRepository, private val time: TimeSource) : ViewModel() {

    val state: StateFlow<ServiceDueUiState> = combine(services.observeCare(), services.observeWithWarranty(), settings.settings) { care, serviceWarranties, s ->
        build(care, serviceWarranties, s.display, time.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceDueUiState())

    companion object {
        fun build(care: List<WatchCare>, serviceWarranties: List<ServiceEntry>, prefs: DisplayPrefs, today: LocalDate): ServiceDueUiState {
            val onHand = care.filter { it.needsCare }
            val withStatus = onHand.map { it to CarePlan.service(it.intervalMonths, it.acquiredOn, it.lastService, it.lastFullService, today) }
            fun item(w: WatchCare, status: ServiceStatus) = DueItem(
                watchId = w.id, title = w.title, reference = w.reference, thumb = w.thumb,
                headline = CareText.due(status, prefs) ?: "",
                detail = listOfNotNull(
                    status.basis?.let { "Counted from ${it.label}" },
                    status.lastService?.let { "last serviced ${prefs.dateFormat.format(it)}" },
                ).joinToString(", ").ifEmpty { null },
                urgent = status.state == DueState.OVERDUE,
            )
            val atWatchmaker = withStatus.filter { it.first.status == WatchStatus.IN_SERVICE }
            val rest = withStatus - atWatchmaker.toSet()
            val byWatch = care.associateBy { it.id }
            val warranties = buildList {
                for (w in onHand) {
                    val ws = CarePlan.warranty(w.warrantyStart, w.warrantyMonths, today) ?: continue
                    // Ending soon, or ended in the last few months: worth a look before it's forgotten.
                    if (ws.state == WarrantyState.ENDING_SOON || (ws.state == WarrantyState.EXPIRED && ws.daysLeft > -CarePlan.SOON_DAYS)) {
                        add(DueItem(w.id, w.title, w.reference, w.thumb, CareText.warranty(ws, prefs), "Watch warranty", ws.state == WarrantyState.EXPIRED, opensPapers = true, key = "watch-${w.id}"))
                    }
                }
                for (e in serviceWarranties) {
                    val w = byWatch[e.watchId]?.takeIf { it.needsCare } ?: continue
                    val until = e.warrantyUntil ?: continue
                    val ws = CarePlan.warranty(e.servicedOn, e.warrantyMonths, today) ?: continue
                    if (ws.state == WarrantyState.ENDING_SOON) {
                        add(DueItem(w.id, w.title, w.reference, w.thumb, "Service warranty until ${prefs.dateFormat.format(until)} (${CarePlan.relative(ws.daysLeft)})",
                            listOfNotNull("Service on ${prefs.dateFormat.format(e.servicedOn)}", e.servicedBy).joinToString(" by "), false, key = "entry-${e.id}"))
                    }
                }
            }
            fun List<Pair<WatchCare, ServiceStatus>>.items(state: DueState) =
                filter { it.second.state == state }.sortedBy { it.second.nextDue }.map { (w, s) -> item(w, s) }
            return ServiceDueUiState(
                loaded = true,
                overdue = rest.items(DueState.OVERDUE),
                soon = rest.items(DueState.DUE_SOON),
                later = rest.items(DueState.SCHEDULED),
                inService = atWatchmaker.map { (w, s) ->
                    item(w, s).copy(headline = "At the watchmaker", detail = s.lastService?.let { "Last serviced ${prefs.dateFormat.format(it)}" }, urgent = false)
                },
                noStart = rest.filter { it.second.state == DueState.NO_START }.map { (w, s) -> item(w, s) },
                warranties = warranties,
                noInterval = rest.count { it.second.state == DueState.NO_INTERVAL },
                onHand = onHand.size,
            )
        }
    }
}

// Service history for one watch

@Immutable
data class EntryUi(
    val id: String,
    val date: String,
    val work: String,
    val by: String?,
    val cost: String?,
    val warranty: String?,
    val notes: String?,
    val documents: List<WatchDocument>,
)

@Immutable
data class ServiceHistoryUiState(
    val loaded: Boolean = false,
    val title: String = "",
    val intervalMonths: Int? = null,
    val reminder: Boolean = false,
    val due: String? = null,
    val dueUrgent: Boolean = false,
    val lastService: String? = null,
    val entries: List<EntryUi> = emptyList(),
    val notificationsBlocked: Boolean = false,
)

class ServiceHistoryViewModel(
    private val watchId: String,
    private val services: ServiceRepository,
    documents: DocumentRepository,
    watches: WatchRepository,
    settings: SettingsRepository,
    private val notifications: NotificationAccess,
    private val time: TimeSource,
) : ViewModel() {

    private val blocked = MutableStateFlow(false)

    val state: StateFlow<ServiceHistoryUiState> = combine(
        watches.observeDetail(watchId), services.observe(watchId), documents.observe(watchId), settings.settings, blocked,
    ) { detail, entries, docs, s, notificationsBlocked ->
        val w = detail?.watch ?: return@combine ServiceHistoryUiState(loaded = true, title = "Watch deleted")
        val prefs = s.display
        val lastFull = entries.filter { it.isFullService }.maxOfOrNull { it.servicedOn }
        val status = CarePlan.service(w.serviceIntervalMonths, w.acquiredOn, entries.maxOfOrNull { it.servicedOn }, lastFull, time.today())
        ServiceHistoryUiState(
            loaded = true,
            title = w.title,
            intervalMonths = w.serviceIntervalMonths,
            reminder = w.serviceReminder,
            due = CareText.due(status, prefs),
            dueUrgent = status.state == DueState.OVERDUE,
            lastService = status.lastService?.let { prefs.dateFormat.format(it) },
            entries = entries.map { e ->
                EntryUi(
                    id = e.id,
                    date = prefs.dateFormat.format(e.servicedOn),
                    work = ServiceWork.entries.filter { it in e.work }.joinToString(", ") { it.label }.ifEmpty { "Service" },
                    by = e.servicedBy,
                    cost = e.cost?.format(),
                    warranty = e.warrantyUntil?.let { "Work guaranteed until ${prefs.dateFormat.format(it)}" },
                    notes = e.notes,
                    documents = docs.filter { it.serviceEntryId == e.id },
                )
            },
            notificationsBlocked = notificationsBlocked && w.serviceReminder,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceHistoryUiState())

    fun setInterval(months: Int?) = viewModelScope.launch {
        services.setPlan(watchId, months, state.value.reminder)
    }

    /** Turning reminders on asks for notification permission the first time. */
    fun setReminder(on: Boolean) = viewModelScope.launch {
        services.setPlan(watchId, state.value.intervalMonths, on)
        if (on) blocked.value = !notifications.request()
    }
}

// One service entry

@Immutable
data class PendingAttachment(val key: String, val title: String, val bytes: ByteArray, val kind: DocumentKind)

@Immutable
data class ServiceEditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val date: LocalDate? = null,
    val by: String = "",
    val work: Set<ServiceWork> = emptySet(),
    val cost: String = "",
    val currency: String = "AUD",
    val warrantyMonths: String = "",
    val notes: String = "",
    val attached: List<WatchDocument> = emptyList(),
    val pending: List<PendingAttachment> = emptyList(),
    /** Attached documents the owner removed; deleted on Save, kept on Discard. */
    val removed: List<WatchDocument> = emptyList(),
    val servicers: List<String> = emptyList(),
    val prefs: DisplayPrefs = DisplayPrefs(),
    val error: String? = null,
    val saving: Boolean = false,
    val dirty: Boolean = false,
) {
    val costError: String? get() = cost.takeIf { it.isNotBlank() && Money.parse(it, currency) == null }?.let { "Enter an amount, like 650" }
    val warrantyError: String? get() = warrantyMonths.takeIf { it.isNotBlank() && it.trim().toIntOrNull()?.takeIf { m -> m in 0..240 } == null }?.let { "Months, like 24" }
}

/** What the service entry form can do; the view model implements it, previews pass a stub. */
interface ServiceEditActions {
    fun edit(transform: (ServiceEditUiState) -> ServiceEditUiState)
    fun toggleWork(w: ServiceWork)
    fun attach(bytes: ByteArray, name: String)
    fun removePending(key: String)
    fun detach(doc: WatchDocument)
    fun save(onDone: () -> Unit)
    fun delete(withDocuments: Boolean, onDone: () -> Unit)
}

class ServiceEditViewModel(
    private val watchId: String,
    private val entryId: String?,
    private val services: ServiceRepository,
    private val documents: DocumentRepository,
    settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel(), ServiceEditActions {

    private val _state = MutableStateFlow(ServiceEditUiState(isNew = entryId == null))
    val state: StateFlow<ServiceEditUiState> = _state.asStateFlow()
    private var original: ServiceEntry? = null

    init {
        viewModelScope.launch {
            val prefs = settings.current().display
            val e = entryId?.let { services.get(it) }
            original = e
            _state.value = ServiceEditUiState(
                loaded = true,
                isNew = e == null,
                date = e?.servicedOn ?: time.today(),
                by = e?.servicedBy.orEmpty(),
                work = e?.work ?: setOf(ServiceWork.FULL_SERVICE),
                cost = e?.cost?.let { m -> m.format().filter { it.isDigit() || it == '.' } }.orEmpty(),
                currency = e?.cost?.currency ?: prefs.defaultCurrency,
                warrantyMonths = e?.warrantyMonths?.toString().orEmpty(),
                notes = e?.notes.orEmpty(),
                attached = e?.let { documents.forEntry(it.id) }.orEmpty(),
                prefs = prefs,
            )
            _state.update { it.copy(servicers = services.observeServicers().first()) }
        }
    }

    override fun edit(transform: (ServiceEditUiState) -> ServiceEditUiState) = _state.update { transform(it).copy(dirty = true, error = null) }

    override fun toggleWork(w: ServiceWork) = edit { it.copy(work = if (w in it.work) it.work - w else it.work + w) }

    /**
     * Attaching and removing files both wait until Save, like the rest of the form, so Discard
     * leaves the stored paperwork exactly as it was.
     */
    override fun attach(bytes: ByteArray, name: String) {
        val title = name.substringBeforeLast('.').ifBlank { "Invoice" }
        edit { it.copy(pending = it.pending + PendingAttachment(Ids.new(), title, bytes, DocumentKind.INVOICE)) }
    }

    override fun removePending(key: String) = edit { it.copy(pending = it.pending.filterNot { p -> p.key == key }) }

    override fun detach(doc: WatchDocument) = edit { it.copy(attached = it.attached - doc, removed = it.removed + doc) }

    override fun save(onDone: () -> Unit) {
        val s = _state.value
        val date = s.date ?: return _state.update { it.copy(error = "Choose the service date.") }
        if (s.costError != null || s.warrantyError != null) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = time.now()
            val saved = try {
                services.save(
                    ServiceEntry(
                        id = original?.id.orEmpty(),
                        watchId = watchId,
                        servicedOn = date,
                        servicedBy = s.by.trim().ifEmpty { null },
                        work = s.work,
                        cost = s.cost.takeIf { it.isNotBlank() }?.let { Money.parse(it, s.currency) },
                        warrantyMonths = s.warrantyMonths.trim().toIntOrNull()?.takeIf { it > 0 },
                        notes = s.notes.trim().ifEmpty { null },
                        createdAt = original?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = "Couldn't save this service.") }
                return@launch
            }
            // From here on the entry exists, so a second Save updates it rather than adding another.
            original = saved
            s.removed.forEach { documents.delete(it.id) }
            val added = mutableListOf<WatchDocument>()
            val failed = mutableListOf<PendingAttachment>()
            val messages = mutableListOf<String>()
            for (p in s.pending) {
                try {
                    added += documents.add(watchId, p.bytes, p.kind, p.title, serviceEntryId = saved.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += p
                    messages += "${p.title}: ${e.message ?: "couldn't be attached"}"
                }
            }
            // Files that couldn't be attached stay listed, so they can be removed or tried again.
            val failedKeys = failed.map { it.key }.toSet()
            val triedKeys = s.pending.map { it.key }.toSet()
            _state.update {
                it.copy(
                    isNew = false,
                    saving = false,
                    dirty = failed.isNotEmpty(),
                    attached = it.attached + added,
                    pending = it.pending.filter { p -> p.key in failedKeys || p.key !in triedKeys },
                    removed = it.removed - s.removed.toSet(),
                    error = messages.takeIf { m -> m.isNotEmpty() }?.joinToString("\n"),
                )
            }
            if (failed.isEmpty()) onDone()
        }
    }

    override fun delete(withDocuments: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            original?.let { services.delete(it.id, withDocuments) }
            onDone()
        }
    }
}

// Box and papers

@Immutable
data class BoxPapersUiState(
    val loaded: Boolean = false,
    val title: String = "",
    val states: Map<BoxItem, BoxItemState> = emptyMap(),
    val notes: Map<BoxItem, String> = emptyMap(),
    val summary: BoxPapersSummary = BoxPapersSummary(0, 0, 0, BoxItem.entries.size),
    val warrantyStart: LocalDate? = null,
    val warrantyMonths: Int? = null,
    val warranty: String? = null,
    val warrantyExpired: Boolean = false,
    val hasElectronicManual: Boolean = false,
    val prefs: DisplayPrefs = DisplayPrefs(),
)

interface BoxPapersActions {
    fun set(item: BoxItem, value: BoxItemState)
    fun setNote(item: BoxItem, note: String)
    fun markRestNotApplicable()
    fun setWarranty(start: LocalDate?, months: Int?)
}

class BoxPapersViewModel(
    private val watchId: String,
    private val papers: BoxPapersRepository,
    watches: WatchRepository,
    documents: DocumentRepository,
    settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel(), BoxPapersActions {

    val state: StateFlow<BoxPapersUiState> = combine(
        papers.observe(watchId), watches.observeDetail(watchId), documents.observe(watchId), settings.settings,
    ) { items, detail, docs, s ->
        val w = detail?.watch
        val ws = CarePlan.warranty(w?.warrantyStart, w?.warrantyMonths, time.today())
        BoxPapersUiState(
            loaded = true,
            title = w?.title.orEmpty(),
            states = items.associate { it.item to it.state },
            notes = items.mapNotNull { i -> i.note?.let { i.item to it } }.toMap(),
            summary = CarePlan.boxPapers(items),
            warrantyStart = w?.warrantyStart,
            warrantyMonths = w?.warrantyMonths,
            warranty = ws?.let { CareText.warranty(it, s.display) },
            warrantyExpired = ws?.state == WarrantyState.EXPIRED,
            hasElectronicManual = docs.any { it.kind == DocumentKind.MANUAL },
            prefs = s.display,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoxPapersUiState())

    /** Tapping the selected state again clears it back to "not recorded". */
    override fun set(item: BoxItem, value: BoxItemState) {
        viewModelScope.launch { papers.set(watchId, item, if (state.value.states[item] == value) null else value) }
    }

    override fun setNote(item: BoxItem, note: String) {
        viewModelScope.launch { papers.setNote(watchId, item, note) }
    }

    override fun markRestNotApplicable() {
        viewModelScope.launch { papers.markRestNotApplicable(watchId) }
    }

    override fun setWarranty(start: LocalDate?, months: Int?) {
        viewModelScope.launch { papers.setWarranty(watchId, start, months) }
    }
}

// Documents

@Immutable
data class DocumentGroup(val title: String, val documents: List<WatchDocument>)

@Immutable
data class DocumentsUiState(
    val loaded: Boolean = false,
    val title: String = "",
    val brand: String? = null,
    val groups: List<DocumentGroup> = emptyList(),
    val entryDates: Map<String, String> = emptyMap(),
    val guidesElsewhere: List<GuideElsewhere> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class DocumentsViewModel(
    private val watchId: String,
    private val documents: DocumentRepository,
    services: ServiceRepository,
    watches: WatchRepository,
    settings: SettingsRepository,
    private val sharer: FileSharer,
) : ViewModel() {

    private val transient = MutableStateFlow(false to (null as String?))

    val state: StateFlow<DocumentsUiState> = combine(
        documents.observe(watchId), watches.observeDetail(watchId), services.observe(watchId),
        documents.observeGuidesElsewhere(watchId), combine(settings.settings, transient) { s, t -> s to t },
    ) { docs, detail, entries, elsewhere, (s, t) ->
        val (busy, message) = t
        val brand = detail?.watch?.brand
        DocumentsUiState(
            loaded = true,
            title = detail?.watch?.title.orEmpty(),
            brand = brand,
            groups = listOf(
                DocumentGroup("Manual & quick start", docs.filter { it.kind.isGuide }),
                DocumentGroup("Purchase & warranty", docs.filter { !it.kind.isGuide && it.serviceEntryId == null && it.kind != DocumentKind.OTHER }),
                DocumentGroup("Service", docs.filter { !it.kind.isGuide && it.serviceEntryId != null }),
                DocumentGroup("Other", docs.filter { !it.kind.isGuide && it.serviceEntryId == null && it.kind == DocumentKind.OTHER }),
            ).filter { it.documents.isNotEmpty() },
            entryDates = entries.associate { it.id to s.display.dateFormat.format(it.servicedOn) },
            // Same brand first: an Omega manual is most likely what another Omega needs.
            guidesElsewhere = elsewhere.sortedByDescending { brand != null && it.watchTitle.startsWith(brand, ignoreCase = true) },
            busy = busy,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState())

    fun add(bytes: ByteArray, name: String, kind: DocumentKind) {
        transient.value = true to null
        viewModelScope.launch {
            val message = try {
                documents.add(watchId, bytes, kind, name.substringBeforeLast('.').ifBlank { kind.label })
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Couldn't add that file."
            }
            transient.value = false to message
        }
    }

    fun copyFrom(documentId: String) {
        transient.value = true to null
        viewModelScope.launch {
            val message = try {
                if (documents.copyTo(documentId, watchId) == null) "That document is no longer stored." else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Couldn't copy that document."
            }
            transient.value = false to message
        }
    }

    /** Title and kind are written together, so neither change can overwrite the other. */
    fun edit(doc: WatchDocument, title: String, kind: DocumentKind) = viewModelScope.launch { documents.update(doc.copy(title = title, kind = kind)) }

    fun delete(doc: WatchDocument) = viewModelScope.launch { documents.delete(doc.id) }

    fun share(doc: WatchDocument) = sharer.share(documents.absolute(doc), doc.mimeType, doc.title)

    fun dismissMessage() { transient.value = transient.value.first to null }

    fun tooLarge() { transient.value = false to "That file is larger than ${DocumentRepository.MAX_BYTES / (1024 * 1024)} MB." }
}

@Immutable
data class ViewerUiState(val loaded: Boolean = false, val document: WatchDocument? = null, val path: String? = null)

class DocumentViewerViewModel(documentId: String, private val documents: DocumentRepository, private val sharer: FileSharer) : ViewModel() {
    val state: StateFlow<ViewerUiState> = documents.observeOne(documentId)
        .combine(flowOf(Unit)) { doc, _ -> ViewerUiState(true, doc, doc?.let(documents::absolute)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerUiState())

    fun share() {
        val doc = state.value.document ?: return
        sharer.share(documents.absolute(doc), doc.mimeType, doc.title)
    }
}
