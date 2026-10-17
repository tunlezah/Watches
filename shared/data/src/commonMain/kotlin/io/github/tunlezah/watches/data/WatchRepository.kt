package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.Ids
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.ServiceWork
import io.github.tunlezah.watches.core.model.keyedOf
import io.github.tunlezah.watches.core.model.OtherSpec
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Sources
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.model.WatchDetail
import io.github.tunlezah.watches.core.model.WatchSummary
import io.github.tunlezah.watches.core.query.CollectionQuery
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecFormatter
import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.SearchIndex
import io.github.tunlezah.watches.data.db.WatchComplicationEntity
import io.github.tunlezah.watches.data.db.toEntity
import io.github.tunlezah.watches.data.db.toModel
import io.github.tunlezah.watches.data.files.FileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class WatchRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val time: TimeSource = TimeSource.System,
) {
    private val dao = db.watchDao()
    private val photoDao = db.photoDao()

    fun observeSummaries(): Flow<List<WatchSummary>> =
        dao.observeSummaries().map { rows -> rows.map { it.toModel() } }

    fun observeDetail(id: String): Flow<WatchDetail?> = combine(
        dao.observeWatch(id),
        dao.observeComplications(id),
        photoDao.observeForWatch(id),
        dao.observeProvenance(id),
        dao.observeOtherSpecs(id),
    ) { watch, complications, photos, provenance, other ->
        watch?.let {
            WatchDetail(
                watch = it.toModel(complications),
                photos = photos.map { p -> p.toModel() },
                provenance = provenance.associate { p -> p.fieldKey to p.toModel() },
                otherSpecs = other.map { o -> o.toModel() },
            )
        }
    }

    fun observeBrands(): Flow<List<String>> = dao.observeBrands()

    suspend fun get(id: String): Watch? = dao.getWatch(id)?.toModel(dao.getComplications(id))

    suspend fun count(): Int = dao.count()

    /** IDs matching free text across every field, notes included. Null when the text has no searchable words. */
    suspend fun search(text: String): Set<String>? {
        val expression = CollectionQuery.ftsExpression(text) ?: return null
        return db.useReaderConnection { connection ->
            connection.usePrepared(SearchIndex.MATCH_SQL) { statement ->
                statement.bindText(1, expression)
                buildSet { while (statement.step()) add(statement.getText(0)) }
            }
        }
    }

    /**
     * Creates a watch. Values in [sourced] came from a lookup and keep that source; every other
     * value is recorded as manual and user-entered.
     */
    suspend fun create(
        watch: Watch,
        id: String = Ids.new(),
        sourced: Map<String, Provenance> = emptyMap(),
        otherSpecs: List<OtherSpec> = emptyList(),
    ): Watch {
        val now = time.now()
        val created = watch.copy(id = id, createdAt = now, updatedAt = now)
        val provenance = SpecFields.all
            .filter { it.get(created) != null && it.key != SpecFields.Status.key }
            .map { sourced[it.key]?.copy(userEdited = false) ?: Provenance(it.key, Sources.MANUAL, userEdited = true) }
        write(created, provenance, otherSpecs.mapIndexed { i, o -> o.copy(position = i) })
        return created
    }

    /**
     * Saves manual edits. Changed fields are flagged user-edited; a field that came from a source
     * keeps its source details, so a later lookup can show where the original came from without
     * silently overwriting the edit.
     */
    suspend fun update(
        edited: Watch,
        sourced: Map<String, Provenance> = emptyMap(),
        otherSpecs: List<OtherSpec> = emptyList(),
    ): Watch {
        val before = get(edited.id) ?: return create(edited, edited.id, sourced, otherSpecs)
        val changed = SpecFields.changedKeys(before, edited) - SpecFields.Status.key
        val existing = dao.getProvenance(edited.id).associateBy { it.fieldKey }
        val provenance = changed.map { key ->
            val old = existing[key]
            when {
                // Applied from a lookup (and the owner ticked it if it replaced their own value).
                key in sourced -> sourced.getValue(key).copy(userEdited = false)
                old != null -> Provenance(key, old.source, old.sourceUrl, old.fetchedAt?.let { kotlin.time.Instant.fromEpochMilliseconds(it) }, userEdited = true)
                else -> Provenance(key, Sources.MANUAL, userEdited = true)
            }
        }
        val saved = edited.copy(createdAt = before.createdAt, updatedAt = time.now())
        val other = if (otherSpecs.isEmpty()) null else mergeOtherSpecs(dao.getOtherSpecs(edited.id).map { it.toModel() }, otherSpecs)
        write(saved, provenance, other)
        return saved
    }

    /** Copies the watch's specs into a new entry. Photos and anything only the owner knows stay behind. */
    suspend fun duplicate(id: String): Watch? {
        val source = get(id) ?: return null
        val now = time.now()
        val copy = source.copy(
            id = Ids.new(),
            createdAt = now,
            updatedAt = now,
            archivedAt = null,
            coverPhotoId = null,
            nickname = null,
            limitedNumber = null,
            acquiredOn = null,
            acquiredFrom = null,
            pricePaid = null,
            condition = null,
            serialNumber = null,
            privateNotes = null,
            story = null,
            warrantyStart = null,
        )
        val provenance = dao.getProvenance(id)
            .map { it.toModel() }
            .filter { p -> SpecFields.byKey(p.fieldKey)?.let { it.get(copy) != null } == true }
        val otherSpecs = dao.getOtherSpecs(id).map { it.toModel().copy(id = Ids.new()) }
        write(copy, provenance, otherSpecs)
        // Manuals and quick start guides belong to the model, so the copy gets its own copies of them.
        val docs = db.documentDao()
        for (doc in docs.forWatch(id).filter { it.serviceEntryId == null && keyedOf<DocumentKind>(it.kind)?.isGuide == true }) {
            val newId = Ids.new()
            val path = "${FileStore.documentDir(copy.id)}/$newId.${doc.file.substringAfterLast('.', "bin")}"
            files.copy(doc.file, path)
            docs.upsert(doc.copy(id = newId, watchId = copy.id, file = path, addedAt = now.toEpochMilliseconds()))
        }
        reindex(copy.id)
        return copy
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        val now = time.now().toEpochMilliseconds()
        dao.setArchived(id, if (archived) now else null, now)
    }

    /** Deletes the watch, every row that hangs off it, and its photo and document files. */
    suspend fun delete(id: String) {
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                usePrepared(SearchIndex.DELETE_SQL) { it.bindText(1, id); it.step() }
                dao.deleteWatch(id)
            }
        }
        files.deleteTree(FileStore.photoDir(id))
        files.deleteTree(FileStore.documentDir(id))
        files.deleteTree(FileStore.snapshotDir(id))
    }

    suspend fun sampleIds(): List<String> = dao.sampleIds()

    /** Saves a lookup's extra specs, replacing earlier ones from the same sources. */
    suspend fun saveOtherSpecs(watchId: String, specs: List<OtherSpec>) {
        val watch = get(watchId) ?: return
        write(watch, emptyList(), mergeOtherSpecs(dao.getOtherSpecs(watchId).map { it.toModel() }, specs))
    }

    /** Rebuilds the search index if it is out of step with the watch table (it is a cache). */
    suspend fun ensureSearchIndex() {
        val indexed = db.useReaderConnection { c ->
            c.usePrepared("SELECT COUNT(*) FROM ${SearchIndex.TABLE}") { if (it.step()) it.getLong(0) else 0L }
        }
        if (indexed != dao.count().toLong()) rebuildSearchIndex()
    }

    suspend fun rebuildSearchIndex() {
        val ids = db.useReaderConnection { c ->
            c.usePrepared("SELECT id FROM watch") { st -> buildList { while (st.step()) add(st.getText(0)) } }
        }
        val bodies = ids.mapNotNull { id -> get(id)?.let { id to searchBody(it, dao.getOtherSpecs(id).map { o -> o.toModel() }, careText(id)) } }
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                execSQL("DELETE FROM ${SearchIndex.TABLE}")
                for ((id, body) in bodies) {
                    usePrepared(SearchIndex.INSERT_SQL) { it.bindText(1, id); it.bindText(2, body); it.step() }
                }
            }
        }
    }

    /** Rewrites one watch's search entry, after its service history or documents change. */
    suspend fun reindex(id: String) {
        val watch = get(id) ?: return
        val body = searchBody(watch, dao.getOtherSpecs(id).map { it.toModel() }, careText(id))
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                usePrepared(SearchIndex.DELETE_SQL) { it.bindText(1, id); it.step() }
                usePrepared(SearchIndex.INSERT_SQL) { it.bindText(1, id); it.bindText(2, body); it.step() }
            }
        }
    }

    /** Searchable words from service history and documents: who serviced it, notes, document titles. */
    private suspend fun careText(id: String): List<String> = buildList {
        for (e in db.serviceDao().forWatch(id)) {
            e.servicedBy?.let(::add)
            e.notes?.let(::add)
            e.work.split(',').mapNotNull { keyedOf<ServiceWork>(it) }.forEach { add(it.label) }
        }
        for (d in db.documentDao().forWatch(id)) {
            add(d.title)
            keyedOf<DocumentKind>(d.kind)?.let { add(it.label) }
        }
    }

    private suspend fun write(watch: Watch, provenance: List<Provenance>, otherSpecs: List<OtherSpec>?) {
        val other = otherSpecs ?: dao.getOtherSpecs(watch.id).map { it.toModel() }
        val body = searchBody(watch, other, careText(watch.id))
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                dao.upsertWatch(watch.toEntity())
                dao.clearComplications(watch.id)
                dao.insertComplications(watch.complications.map { WatchComplicationEntity(watch.id, it) })
                dao.upsertProvenance(provenance.map { it.toEntity(watch.id) })
                if (otherSpecs != null) {
                    dao.clearOtherSpecs(watch.id)
                    dao.insertOtherSpecs(otherSpecs.map { it.toEntity(watch.id) })
                }
                usePrepared(SearchIndex.DELETE_SQL) { it.bindText(1, watch.id); it.step() }
                usePrepared(SearchIndex.INSERT_SQL) { it.bindText(1, watch.id); it.bindText(2, body); it.step() }
            }
        }
    }

    companion object {
        private val prefs = DisplayPrefs()

        /**
         * Extra specs from a fresh lookup replace the ones the same source gave before; specs from
         * other sources, and ones the owner added, stay.
         */
        fun mergeOtherSpecs(existing: List<OtherSpec>, incoming: List<OtherSpec>): List<OtherSpec> {
            val replaced = incoming.mapNotNull { it.source }.toSet()
            return (existing.filter { it.source == null || it.source !in replaced } + incoming)
                .mapIndexed { i, o -> o.copy(position = i) }
        }

        /** Every searchable word for a watch: all fields as shown, complications, notes and extra specs. */
        fun searchBody(watch: Watch, otherSpecs: List<OtherSpec>, care: List<String> = emptyList()): String = buildString {
            for (field in SpecFields.all) {
                val value = field.get(watch) ?: continue
                append(SpecFormatter.format(field, value, prefs).value).append(' ')
            }
            // References are often typed without their punctuation: "31030425001001".
            watch.reference?.filter { it.isLetterOrDigit() }?.let { append(it).append(' ') }
            watch.complications.forEach { append(Complication.labelFor(it)).append(' ') }
            watch.status.let { append(it.label).append(' ') }
            otherSpecs.forEach { append(it.label).append(' ').append(it.value).append(' ') }
            care.forEach { append(it).append(' ') }
        }.trim()
    }
}
