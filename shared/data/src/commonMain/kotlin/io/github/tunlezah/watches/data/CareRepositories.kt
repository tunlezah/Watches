package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.Ids
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.model.BoxItem
import io.github.tunlezah.watches.core.model.BoxItemState
import io.github.tunlezah.watches.core.model.BoxPapersItem
import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.ServiceEntry
import io.github.tunlezah.watches.core.model.WatchDocument
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.core.model.keyedOf
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.BoxPapersItemEntity
import io.github.tunlezah.watches.data.db.toEntity
import io.github.tunlezah.watches.data.db.toModel
import io.github.tunlezah.watches.data.files.DocumentFormats
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.ImageProcessor
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/** One watch's service and warranty inputs, as the Service tab and reminders need them. */
data class WatchCare(
    val id: String,
    val title: String,
    val reference: String?,
    val status: WatchStatus,
    val archived: Boolean,
    val acquiredOn: LocalDate?,
    val intervalMonths: Int?,
    val reminder: Boolean,
    val warrantyStart: LocalDate?,
    val warrantyMonths: Int?,
    val lastService: LocalDate?,
    val lastFullService: LocalDate?,
    /** Absolute path of the cover thumbnail, if any. */
    val thumb: String?,
) {
    /** Only watches you have on hand need servicing: not wishlist, incoming, sold or archived ones. */
    val needsCare: Boolean get() = !archived && (status == WatchStatus.OWNED || status == WatchStatus.IN_SERVICE)
}

/** A manual or quick start guide kept with another watch. */
data class GuideElsewhere(val document: WatchDocument, val watchTitle: String, val caliber: String?)

class ServiceRepository(
    private val db: AppDatabase,
    private val watches: WatchRepository,
    private val documents: DocumentRepository,
    private val files: FileStore,
    private val time: TimeSource = TimeSource.System,
) {
    private val dao = db.serviceDao()

    fun observe(watchId: String): Flow<List<ServiceEntry>> = dao.observeForWatch(watchId).map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: String): ServiceEntry? = dao.get(id)?.toModel()

    fun observeServicers(): Flow<List<String>> = dao.observeServicers()

    fun observeWithWarranty(): Flow<List<ServiceEntry>> = dao.observeWithWarranty().map { rows -> rows.map { it.toModel() } }

    fun observeCare(): Flow<List<WatchCare>> = dao.observeCare().map { rows ->
        rows.map { r ->
            WatchCare(
                id = r.id,
                title = listOfNotNull(r.brand, r.modelName ?: r.collection ?: r.reference).joinToString(" ").ifBlank { r.nickname ?: "Untitled watch" },
                reference = r.reference,
                status = keyedOf<WatchStatus>(r.status) ?: WatchStatus.OWNED,
                archived = r.archivedAt != null,
                acquiredOn = r.acquiredOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                intervalMonths = r.serviceIntervalMonths,
                reminder = r.serviceReminder,
                warrantyStart = r.warrantyStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                warrantyMonths = r.warrantyMonths,
                lastService = r.lastService?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                lastFullService = r.lastFullService?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                thumb = r.coverThumb?.let(files::absolute),
            )
        }
    }

    /** Creates or updates an entry. A new entry gets an id; timestamps are set here. */
    suspend fun save(entry: ServiceEntry): ServiceEntry {
        val now = time.now()
        val existing = entry.id.takeIf { it.isNotEmpty() }?.let { dao.get(it) }
        val saved = entry.copy(
            id = entry.id.ifEmpty { Ids.new() },
            createdAt = existing?.createdAt?.let { kotlin.time.Instant.fromEpochMilliseconds(it) } ?: now,
            updatedAt = now,
        )
        dao.upsert(saved.toEntity())
        watches.reindex(saved.watchId)
        return saved
    }

    /** Deletes an entry. Its attached documents go too when [withDocuments]; otherwise they stay with the watch. */
    suspend fun delete(id: String, withDocuments: Boolean) {
        val entry = dao.get(id) ?: return
        if (withDocuments) db.documentDao().forEntry(id).forEach { documents.delete(it.id) }
        dao.delete(id)
        watches.reindex(entry.watchId)
    }

    /** The watch's interval and reminder switch live on the watch itself, recorded as the owner's values. */
    suspend fun setPlan(watchId: String, intervalMonths: Int?, reminder: Boolean) {
        val w = watches.get(watchId) ?: return
        watches.update(w.copy(serviceIntervalMonths = intervalMonths?.takeIf { it > 0 }, serviceReminder = reminder))
    }
}

class BoxPapersRepository(private val db: AppDatabase, private val watches: WatchRepository) {
    private val dao = db.boxPapersDao()

    fun observe(watchId: String): Flow<List<BoxPapersItem>> = dao.observe(watchId).map { rows -> rows.mapNotNull { it.toModel() } }

    /** Sets one item; null clears it back to "not recorded". */
    suspend fun set(watchId: String, item: BoxItem, state: BoxItemState?) {
        if (state == null) {
            dao.delete(watchId, item.key)
            return
        }
        val note = dao.get(watchId).firstOrNull { it.item == item.key }?.note
        dao.upsert(listOf(BoxPapersItemEntity(watchId, item.key, state.key, null, note)))
    }

    suspend fun setNote(watchId: String, item: BoxItem, note: String?) {
        val row = dao.get(watchId).firstOrNull { it.item == item.key } ?: return
        dao.upsert(listOf(row.copy(note = note?.trim()?.ifEmpty { null })))
    }

    /** Marks every item not yet recorded as not applicable, so the checklist can be finished in one tap. */
    suspend fun markRestNotApplicable(watchId: String) {
        val recorded = dao.get(watchId).map { it.item }.toSet()
        dao.upsert(BoxItem.entries.filter { it.key !in recorded }.map { BoxPapersItemEntity(watchId, it.key, BoxItemState.NOT_APPLICABLE.key, null, null) })
    }

    suspend fun setWarranty(watchId: String, start: LocalDate?, months: Int?) {
        val w = watches.get(watchId) ?: return
        watches.update(w.copy(warrantyStart = start, warrantyMonths = months?.takeIf { it > 0 }))
    }
}

class DocumentRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val processor: ImageProcessor,
    private val watches: WatchRepository,
    private val time: TimeSource = TimeSource.System,
) {
    private val dao = db.documentDao()

    class UnsupportedDocument : Exception("Only PDFs and photos (JPEG, PNG, WebP, HEIC) can be kept as documents.")
    class TooLarge : Exception("That file is larger than ${MAX_BYTES / (1024 * 1024)} MB.")

    fun observe(watchId: String): Flow<List<WatchDocument>> = dao.observeForWatch(watchId).map { rows -> rows.map { it.toModel() } }

    fun observeOne(id: String): Flow<WatchDocument?> = dao.observe(id).map { it?.toModel() }

    suspend fun get(id: String): WatchDocument? = dao.get(id)?.toModel()

    suspend fun forEntry(entryId: String): List<WatchDocument> = dao.forEntry(entryId).map { it.toModel() }

    fun observeGuidesElsewhere(watchId: String): Flow<List<GuideElsewhere>> = dao.observeGuidesElsewhere(watchId).map { rows ->
        rows.map { r ->
            GuideElsewhere(
                document = r.document.toModel(),
                watchTitle = listOfNotNull(r.watchBrand, r.watchModel ?: r.watchCollection).joinToString(" ").ifBlank { "Another watch" },
                caliber = r.watchCaliber,
            )
        }
    }

    fun absolute(document: WatchDocument): String = files.absolute(document.file)

    /**
     * Keeps a PDF or photo with a watch (and optionally a service entry). The type comes from the
     * file's own bytes, not its name. Photos lose their GPS location, as watch photos do. HEIC is
     * stored as JPEG, which every viewer and app it's shared with can open.
     */
    suspend fun add(
        watchId: String,
        bytes: ByteArray,
        kind: DocumentKind,
        title: String,
        serviceEntryId: String? = null,
    ): WatchDocument {
        if (bytes.size > MAX_BYTES) throw TooLarge()
        val (detected, ext) = DocumentFormats.detect(bytes) ?: throw UnsupportedDocument()
        val heic = detected == DocumentFormats.HEIC
        val mime = if (heic) "image/jpeg" else detected
        val id = Ids.new()
        val path = "${FileStore.documentDir(watchId)}/$id.${if (heic) "jpg" else ext}"
        // Until the row is written the file is ours to clean up; after that the row owns it.
        try {
            if (heic) {
                val source = "${FileStore.documentDir(watchId)}/$id.heic"
                files.write(source, bytes)
                try {
                    processor.render(files.resolve(source), null, HEIC_EDGE, HEIC_QUALITY, files.resolve(path))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw UnsupportedDocument()
                } finally {
                    withContext(NonCancellable) { files.delete(source) }
                }
            } else {
                files.write(path, bytes)
                if (mime.startsWith("image/")) runCatching { processor.stripLocation(files.resolve(path)) }
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) { files.delete(path) }
            throw e
        }
        val doc = WatchDocument(
            id = id,
            watchId = watchId,
            serviceEntryId = serviceEntryId,
            kind = kind,
            title = title.trim().ifEmpty { kind.label },
            file = path,
            mimeType = mime,
            sizeBytes = files.size(path),
            addedAt = time.now(),
        )
        withContext(NonCancellable) {
            try {
                dao.upsert(doc.toEntity())
            } catch (e: Throwable) {
                files.delete(path)
                throw e
            }
            watches.reindex(watchId)
        }
        return doc
    }

    /** Copies a document (a manual, typically) to another watch. Each watch keeps its own file. */
    suspend fun copyTo(documentId: String, watchId: String): WatchDocument? {
        val source = dao.get(documentId)?.toModel() ?: return null
        val id = Ids.new()
        val path = "${FileStore.documentDir(watchId)}/$id.${source.file.substringAfterLast('.', "bin")}"
        try {
            files.copy(source.file, path)
        } catch (e: Throwable) {
            withContext(NonCancellable) { files.delete(path) }
            throw e
        }
        val copy = source.copy(id = id, watchId = watchId, serviceEntryId = null, file = path, addedAt = time.now())
        withContext(NonCancellable) {
            try {
                dao.upsert(copy.toEntity())
            } catch (e: Throwable) {
                files.delete(path)
                throw e
            }
            watches.reindex(watchId)
        }
        return copy
    }

    suspend fun update(document: WatchDocument) {
        dao.upsert(document.copy(title = document.title.trim().ifEmpty { document.kind.label }).toEntity())
        watches.reindex(document.watchId)
    }

    suspend fun delete(id: String) {
        val doc = dao.get(id) ?: return
        dao.delete(id)
        files.delete(doc.file)
        watches.reindex(doc.watchId)
    }

    companion object {
        const val MAX_BYTES = 50 * 1024 * 1024
        private const val HEIC_EDGE = 4096
        private const val HEIC_QUALITY = 92
    }
}
