package io.github.tunlezah.watches.data.backup

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.data.PhotoRepository
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.files.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.HashingSink
import okio.Sink
import okio.Source
import okio.blackholeSink
import okio.buffer
import okio.use
import kotlin.random.Random
import kotlin.time.Instant

/** The app version and platform written into each backup's manifest. */
data class AppInfo(val version: String, val platform: String) {
    companion object {
        val Unknown = AppInfo("unknown", "unknown")
    }
}

/** What a backup made now would hold. */
data class BackupContents(val watches: Int, val photos: Int, val serviceEntries: Int, val documents: Int, val bytes: Long)

/** Progress through a long backup or restore step, in bytes. */
data class BackupProgress(val step: Step, val done: Long, val total: Long) {
    enum class Step { CHECKING, WRITING, READING, SAVING }

    val fraction: Float get() = if (total <= 0) 0f else (done.toDouble() / total).toFloat().coerceIn(0f, 1f)
}

data class ExportResult(val counts: ArchiveCounts, val bytes: Long, val missingFiles: Int)

/** A backup that has been read and checked, ready to restore. */
class ArchivePreview(
    val manifest: Manifest,
    val createdAt: Instant?,
    val counts: ArchiveCounts,
    /** Size of the photos, documents and snapshots in the backup. */
    val mediaBytes: Long,
    /** What a merge would do with this phone's collection as it is now. */
    val merge: MergePlan,
    /** Watches on this phone now, which Replace removes. */
    val watchesOnPhone: Int,
    val warnings: List<String>,
    internal val data: CollectionData,
    internal val prefix: String,
    internal val manifestHash: String,
)

enum class RestoreMode { REPLACE, MERGE }

data class RestoreReport(
    val mode: RestoreMode,
    val watchesAdded: Int,
    val updated: List<WatchName>,
    val kept: List<WatchName>,
    val watchesUnchanged: Int,
    val watchesRemoved: Int,
    val photosAdded: Int,
    val serviceEntriesAdded: Int,
    val documentsAdded: Int,
    val warnings: List<String>,
)

sealed class BackupException(message: String) : Exception(message) {
    class NotABackup : BackupException("This file isn't a Watches backup.")
    class TooNew : BackupException("This backup was made by a newer version of Watches. Update the app, then try again.")
    class Damaged(detail: String) : BackupException("This backup is damaged and can't be restored ($detail). Nothing on this phone was changed.")
    class Changed : BackupException("The backup file changed after it was checked. Choose it again.")
    class NotEnoughSpace(val needed: Long, val available: Long) : BackupException("Not enough free space on this phone to restore this backup.")
    class CollectionChanged : BackupException("Your collection changed while the backup was being made. Try again.")
}

/**
 * Backs the whole collection up to one archive, and restores it by replacing or merging.
 *
 * Restoring is careful about the collection already on the phone:
 * - the archive's own checks (manifest, every file's SHA-256) all pass before the database is touched;
 * - the database changes in one transaction;
 * - incoming files are written beside the existing ones, never over them, and only the files the
 *   restore itself stopped using are removed, once the new rows are committed. A journal lets an
 *   interrupted restore be tidied up at the next launch ([recoverInterruptedRestore]).
 */
class BackupRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val watches: WatchRepository,
    private val photos: PhotoRepository,
    private val app: AppInfo,
    private val time: TimeSource = TimeSource.System,
) {
    private val dao = db.backupDao()
    private val busy = Mutex()

    fun suggestedFileName(): String {
        val t = time.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "Watches backup ${t.year}-${two(t.month.ordinal + 1)}-${two(t.day)} ${two(t.hour)}${two(t.minute)}.zip"
    }

    suspend fun contents(): BackupContents = withContext(Dispatchers.IO) {
        val data = snapshot()
        BackupContents(data.watch.size, data.photo.size, data.serviceEntry.size, data.document.size, data.referencedFiles.sumOf { files.size(it) })
    }

    /**
     * Writes a backup of everything to [sink], which is closed afterwards. If this fails or is
     * cancelled the output is incomplete, and the caller should delete it.
     */
    suspend fun export(sink: Sink, onProgress: (BackupProgress) -> Unit = {}): ExportResult = withContext(Dispatchers.IO) {
        try {
            busy.withLock { writeArchive(sink, onProgress) }
        } catch (e: okio.FileNotFoundException) {
            // A photo or document was removed (or re-cropped) between the two passes.
            throw BackupException.CollectionChanged()
        }
    }

    private suspend fun writeArchive(sink: Sink, onProgress: (BackupProgress) -> Unit): ExportResult {
        val job = currentCoroutineContext()[Job]
        var zip: ZipWriter? = null
        try {
            val data = snapshot()
            val present = data.referencedFiles.filter { ArchivePaths.isSafe(it) && files.exists(it) }.sorted()
            val missing = data.referencedFiles.size - present.size
            val total = present.sumOf { files.size(it) }

            // First pass: sizes, CRCs and hashes, so the manifest can lead the archive and every
            // local header is complete.
            var checked = 0L
            val digests = present.map { path ->
                digest(path) { n ->
                    job?.ensureActive()
                    checked += n
                    onProgress(BackupProgress(BackupProgress.Step.CHECKING, checked, total))
                }.let { path to it }
            }
            val collection = ArchiveJson.encodeToString(CollectionData.serializer(), data).encodeToByteArray()
            val manifest = Manifest(
                format = ARCHIVE_FORMAT_ID,
                formatVersion = ARCHIVE_FORMAT_VERSION,
                schemaVersion = SCHEMA_VERSION,
                createdAt = time.now().toString(),
                app = "Watches ${app.version} (${app.platform})",
                counts = data.counts,
                files = listOf(ManifestFile(ARCHIVE_COLLECTION, collection.size.toLong(), sha256(collection))) +
                    digests.map { (path, d) -> ManifestFile(ARCHIVE_FILES_PREFIX + path, d.size, d.sha256) },
            )
            val now = time.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val writer = ZipWriter(sink, DosTime.of(now.year, now.month.ordinal + 1, now.day, now.hour, now.minute, now.second))
            zip = writer
            writer.add(ARCHIVE_MANIFEST, ArchiveJson.encodeToString(Manifest.serializer(), manifest).encodeToByteArray())
            writer.add(ARCHIVE_COLLECTION, collection)
            var written = 0L
            for ((path, d) in digests) {
                files.source(path).use { source ->
                    writer.add(ARCHIVE_FILES_PREFIX + path, d.crc, d.size, source) { n ->
                        job?.ensureActive()
                        written += n
                        onProgress(BackupProgress(BackupProgress.Step.WRITING, written, total))
                    }
                }
            }
            writer.finish()
            return ExportResult(data.counts, writer.size, missing)
        } finally {
            // The sink is ours to close either way, even when the first pass failed before any writing.
            zip?.close() ?: runCatching { sink.close() }
        }
    }

    /**
     * Reads a backup's manifest and collection, checks them, and works out what a merge would do.
     * Only the start of the archive is read, unless another tool reordered it. Nothing is written.
     */
    suspend fun inspect(open: () -> Source): ArchivePreview = withContext(Dispatchers.IO) {
        val (manifestBytes, manifest, prefix, collection) = readHead(open)
        val warnings = mutableListOf<String>()
        val data = sanitize(collection, manifest, warnings)
        val local = snapshot()
        ArchivePreview(
            manifest = manifest,
            createdAt = runCatching { Instant.parse(manifest.createdAt) }.getOrNull(),
            counts = data.counts,
            mediaBytes = manifest.files.filter { it.path.startsWith(ARCHIVE_FILES_PREFIX) }.sumOf { it.size },
            merge = MergePlanner.plan(local, data),
            watchesOnPhone = local.watch.size,
            warnings = warnings,
            data = data,
            prefix = prefix,
            manifestHash = sha256(manifestBytes),
        )
    }

    /**
     * Restores [preview] from the archive [open] returns (the same file, read again from the start).
     * Every file is checked against the manifest before the database changes.
     */
    suspend fun restore(
        open: () -> Source,
        preview: ArchivePreview,
        mode: RestoreMode,
        onProgress: (BackupProgress) -> Unit = {},
        /** Called once the restore has been saved, even if the caller is cancelled while it tidies up. */
        onCommitted: (RestoreReport) -> Unit = {},
    ): RestoreReport = withContext(Dispatchers.IO) {
        busy.withLock {
            val job = currentCoroutineContext()[Job]
            val warnings = preview.warnings.toMutableList()
            val incoming = preview.data
            val plan = if (mode == RestoreMode.MERGE) MergePlanner.plan(snapshot(), incoming) else null
            val rows = plan?.rows ?: incoming
            val needed = rows.referencedFiles
            val expected = preview.manifest.files.associateBy { it.path }
            val total = needed.sumOf { expected[ARCHIVE_FILES_PREFIX + it]?.size ?: 0L }
            val fresh = needed.filterNot { files.exists(it) }.sumOf { expected[ARCHIVE_FILES_PREFIX + it]?.size ?: 0L }
            files.availableBytes()?.let { free -> if (fresh + SPACE_MARGIN > free) throw BackupException.NotEnoughSpace(fresh + SPACE_MARGIN, free) }

            // Stream the archive, writing each needed file beside what's there and checking its hash.
            val placed = HashMap<String, String>()
            val written = mutableListOf<String>()
            var committed = false
            try {
                var done = 0L
                open().use { raw ->
                    val zip = ZipStreamReader(raw)
                    while (true) {
                        val entry = zip.next() ?: break
                        val name = entryName(entry.name, preview.prefix)
                        when {
                            name == ARCHIVE_MANIFEST -> {
                                if (sha256(zip.readBytes(entry, MAX_MANIFEST)) != preview.manifestHash) throw BackupException.Changed()
                            }
                            name == null || !name.startsWith(ARCHIVE_FILES_PREFIX) -> zip.skip(entry)
                            else -> {
                                val relative = name.removePrefix(ARCHIVE_FILES_PREFIX)
                                val want = expected[name]
                                if (relative !in needed || want == null || relative in placed) {
                                    zip.skip(entry)
                                    continue
                                }
                                if (files.exists(relative) && digest(relative).sha256 == want.sha256) {
                                    // Same content is already in place (restoring onto the same phone).
                                    zip.skip(entry)
                                    placed[relative] = relative
                                } else {
                                    val target = if (files.exists(relative)) uniqueVariant(relative) else relative
                                    journal(target)
                                    written += target
                                    val hashing = HashingSink.sha256(files.sink(target))
                                    try {
                                        zip.read(entry, hashing) { n ->
                                            job?.ensureActive()
                                            onProgress(BackupProgress(BackupProgress.Step.READING, done + n, total))
                                            done += n
                                        }
                                    } finally {
                                        hashing.close()
                                    }
                                    if (hashing.hash.hex() != want.sha256) throw BackupException.Damaged("$relative doesn't match its checksum")
                                    placed[relative] = target
                                    continue
                                }
                                done += want.size
                                onProgress(BackupProgress(BackupProgress.Step.READING, done, total))
                            }
                        }
                    }
                }
                val absent = needed - placed.keys
                if (absent.isNotEmpty()) throw BackupException.Damaged("${absent.size} files are missing")

                onProgress(BackupProgress(BackupProgress.Step.SAVING, 0, 1))
                withContext(NonCancellable) {
                    // The search index and photo renditions are redone after the commit. The marker
                    // gets that finished at the next launch if the app stops first.
                    files.write(AFTERCARE, ByteArray(0))
                    val outcome = write(rows.remap(placed), mode, plan, preview, warnings)
                    committed = true
                    // The restore stands from here. What follows is tidying, and it can't undo that.
                    runCatching {
                        // The journal now lists the files the restore stopped using, so an interruption
                        // still gets them removed at the next launch.
                        files.write(JOURNAL, outcome.released.joinToString("\n").encodeToByteArray())
                        outcome.released.forEach { files.delete(it) }
                        files.delete(JOURNAL)
                    }
                    aftercare()
                    onProgress(BackupProgress(BackupProgress.Step.SAVING, 1, 1))
                    onCommitted(outcome.report)
                    outcome.report
                }
            } catch (e: Throwable) {
                if (committed) throw e
                // Before the commit nothing refers to the new files, so they go.
                withContext(NonCancellable) {
                    written.forEach { files.delete(it) }
                    files.delete(JOURNAL)
                    files.delete(AFTERCARE)
                }
                throw if (e is ZipFormatException || e is okio.IOException) BackupException.Damaged(e.message ?: "unreadable") else e
            }
        }
    }

    /**
     * Finishes a restore the app stopped in the middle of: removes the files a journal lists that no
     * row uses (from before the commit, the incoming files; after it, the ones the restore replaced),
     * and redoes the search index and missing photo renditions if the restore didn't get to them.
     */
    suspend fun recoverInterruptedRestore() = withContext(Dispatchers.IO) {
        if (!files.exists(JOURNAL) && !files.exists(AFTERCARE)) return@withContext
        busy.withLock {
            if (files.exists(JOURNAL)) {
                val listed = files.read(JOURNAL).decodeToString().lines().filter { it.isNotBlank() }
                val used = dao.referencedFiles().toSet()
                listed.filter { it !in used && ArchivePaths.isSafe(it) }.forEach { files.delete(it) }
                files.delete(JOURNAL)
            }
            if (files.exists(AFTERCARE)) aftercare()
        }
    }

    /**
     * Rebuilds the search index, then the display image and thumbnail of any photo that came without
     * them. Each step is tried once; a photo that can't be rendered keeps showing its original.
     */
    private suspend fun aftercare() {
        runCatching { watches.rebuildSearchIndex() }
        runCatching {
            for (p in dao.photos()) {
                if (p.displayFile == p.originalFile || p.thumbFile == p.originalFile) runCatching { photos.rerender(p.id) }
            }
        }
        runCatching { files.delete(AFTERCARE) }
    }

    private class Outcome(val report: RestoreReport, val released: Set<String>)

    /**
     * Saves [rows] in one transaction. Returns the report, and the files that rows referred to
     * before and no longer do: only those may be deleted, so a photo being added or cropped
     * alongside the restore keeps its files.
     */
    private suspend fun write(rows: CollectionData, mode: RestoreMode, plan: MergePlan?, preview: ArchivePreview, warnings: MutableList<String>): Outcome {
        val released = db.useWriterConnection { connection ->
            connection.immediateTransaction {
                val before = dao.referencedFiles().toSet()
                when (mode) {
                    RestoreMode.REPLACE -> {
                        dao.deleteAllWatches()
                        dao.deleteAllSettings()
                        insert(rows)
                        dao.upsertSettings(rows.setting)
                    }
                    RestoreMode.MERGE -> {
                        // A watch the backup wins takes the backup's lists whole, so removed specs stay removed.
                        for ((id, decision) in plan!!.decisions) {
                            if (decision == WatchDecision.UPDATE) {
                                dao.deleteComplications(id)
                                dao.deleteProvenance(id)
                                dao.deleteOtherSpecs(id)
                                dao.deleteBoxPapers(id)
                            }
                        }
                        insert(rows)
                    }
                }
                dao.clearDanglingCovers()
                before - dao.referencedFiles().toSet()
            }
        }
        val merge = plan ?: preview.merge
        val report = when (mode) {
            RestoreMode.REPLACE -> RestoreReport(
                mode = mode,
                watchesAdded = rows.watch.size,
                updated = emptyList(),
                kept = emptyList(),
                watchesUnchanged = 0,
                watchesRemoved = preview.watchesOnPhone,
                photosAdded = rows.photo.size,
                serviceEntriesAdded = rows.serviceEntry.size,
                documentsAdded = rows.document.size,
                warnings = warnings,
            )
            RestoreMode.MERGE -> RestoreReport(
                mode = mode,
                watchesAdded = merge.added.size,
                updated = merge.updated,
                kept = merge.kept,
                watchesUnchanged = merge.unchanged,
                watchesRemoved = 0,
                photosAdded = merge.photosAdded,
                serviceEntriesAdded = merge.serviceEntriesAdded,
                documentsAdded = merge.documentsAdded,
                warnings = warnings,
            )
        }
        return Outcome(report, released)
    }

    /** Parents before children, so every foreign key is satisfied as rows go in. */
    private suspend fun insert(rows: CollectionData) {
        dao.upsertWatches(rows.watch)
        dao.upsertComplications(rows.watchComplication)
        dao.upsertProvenance(rows.fieldProvenance)
        dao.upsertOtherSpecs(rows.otherSpec)
        dao.upsertBoxPapers(rows.boxPapersItem)
        dao.upsertPhotos(rows.photo)
        dao.upsertServiceEntries(rows.serviceEntry)
        dao.upsertDocuments(rows.document)
        dao.upsertSnapshots(rows.sourceSnapshot)
    }

    private suspend fun snapshot(): CollectionData = CollectionData(
        watch = dao.watches(),
        watchComplication = dao.complications(),
        fieldProvenance = dao.provenance(),
        otherSpec = dao.otherSpecs(),
        photo = dao.photos(),
        serviceEntry = dao.serviceEntries(),
        boxPapersItem = dao.boxPapers(),
        document = dao.documents(),
        sourceSnapshot = dao.snapshots(),
        setting = dao.settings(),
    )

    private data class Head(val manifestBytes: ByteArray, val manifest: Manifest, val prefix: String, val collection: CollectionData)

    private fun readHead(open: () -> Source): Head = open().use { raw ->
        val zip = ZipStreamReader(raw)
        var manifestBytes: ByteArray? = null
        var prefix = ""
        val collections = HashMap<String, ByteArray>()
        try {
            while (true) {
                val entry = zip.next() ?: break
                val name = entryName(entry.name, null)
                when {
                    name == null -> zip.skip(entry)
                    manifestBytes == null && (name == ARCHIVE_MANIFEST || name.endsWith("/$ARCHIVE_MANIFEST")) -> {
                        manifestBytes = zip.readBytes(entry, MAX_MANIFEST)
                        prefix = name.removeSuffix(ARCHIVE_MANIFEST)
                    }
                    name == ARCHIVE_COLLECTION || name.endsWith("/$ARCHIVE_COLLECTION") -> collections[name] = zip.readBytes(entry, MAX_COLLECTION)
                    else -> zip.skip(entry)
                }
                if (manifestBytes != null && collections.containsKey(prefix + ARCHIVE_COLLECTION)) break
            }
        } catch (e: Exception) {
            if (e !is ZipFormatException && e !is okio.IOException) throw e
            if (manifestBytes == null) throw BackupException.NotABackup()
            throw BackupException.Damaged(e.message ?: "unreadable")
        }
        val mBytes = manifestBytes ?: throw BackupException.NotABackup()
        val manifest = runCatching { ArchiveJson.decodeFromString(Manifest.serializer(), mBytes.decodeToString()) }.getOrNull()
            ?.takeIf { it.format == ARCHIVE_FORMAT_ID } ?: throw BackupException.NotABackup()
        if (manifest.formatVersion > ARCHIVE_FORMAT_VERSION || manifest.schemaVersion > SCHEMA_VERSION) throw BackupException.TooNew()
        val cBytes = collections[prefix + ARCHIVE_COLLECTION] ?: throw BackupException.Damaged("collection.json is missing")
        val listed = manifest.files.firstOrNull { it.path == ARCHIVE_COLLECTION } ?: throw BackupException.Damaged("collection.json isn't listed")
        if (sha256(cBytes) != listed.sha256) throw BackupException.Damaged("collection.json doesn't match its checksum")
        val collection = try {
            ArchiveJson.decodeFromString(CollectionData.serializer(), cBytes.decodeToString())
        } catch (e: Exception) {
            throw BackupException.Damaged("collection.json can't be read")
        }
        Head(mBytes, manifest, prefix, collection)
    }

    /**
     * Drops rows a restore couldn't honour, with a warning for each kind: rows for unknown watches,
     * and rows whose file is missing from the archive or has an unsafe path. A photo whose
     * renditions are missing keeps its original; the renditions are rebuilt after the restore.
     */
    private fun sanitize(data: CollectionData, manifest: Manifest, warnings: MutableList<String>): CollectionData {
        val inArchive = manifest.files.map { it.path }.filter { it.startsWith(ARCHIVE_FILES_PREFIX) }.map { it.removePrefix(ARCHIVE_FILES_PREFIX) }.toSet()
        fun ok(path: String) = ArchivePaths.isSafe(path) && path in inArchive
        val watchIds = data.watch.map { it.id }.toSet()
        var orphans = 0
        fun <T> List<T>.forKnownWatch(watchId: (T) -> String) = filter { (watchId(it) in watchIds).also { known -> if (!known) orphans++ } }

        val photos = data.photo.forKnownWatch { it.watchId }
        val keptPhotos = photos.filter { ok(it.originalFile) }.map { p ->
            if (ok(p.displayFile) && ok(p.thumbFile)) p else p.copy(displayFile = p.originalFile, thumbFile = p.originalFile)
        }
        val entries = data.serviceEntry.forKnownWatch { it.watchId }
        val entryIds = entries.map { it.id }.toSet()
        val documents = data.document.forKnownWatch { it.watchId }
        val keptDocuments = documents.filter { ok(it.file) }.map { if (it.serviceEntryId == null || it.serviceEntryId in entryIds) it else it.copy(serviceEntryId = null) }
        val snapshots = data.sourceSnapshot.forKnownWatch { it.watchId }
        val keptSnapshots = snapshots.filter { ok(it.bodyFile) }

        val lostPhotos = photos.size - keptPhotos.size
        val lostDocuments = documents.size - keptDocuments.size
        if (lostPhotos > 0) warnings += "$lostPhotos ${plural(lostPhotos, "photo")} missing from the backup will be skipped."
        if (lostDocuments > 0) warnings += "$lostDocuments ${plural(lostDocuments, "document")} missing from the backup will be skipped."
        if (orphans > 0) warnings += "$orphans ${plural(orphans, "record")} for watches not in the backup will be skipped."
        return data.copy(
            watchComplication = data.watchComplication.forKnownWatch { it.watchId },
            fieldProvenance = data.fieldProvenance.forKnownWatch { it.watchId },
            otherSpec = data.otherSpec.forKnownWatch { it.watchId },
            boxPapersItem = data.boxPapersItem.forKnownWatch { it.watchId },
            photo = keptPhotos,
            serviceEntry = entries,
            document = keptDocuments,
            sourceSnapshot = keptSnapshots,
            setting = data.setting.filterNot { it.key.startsWith(DEVICE_SETTING) },
        )
    }

    private fun CollectionData.remap(placed: Map<String, String>): CollectionData {
        fun at(path: String) = placed[path] ?: path
        return copy(
            photo = photo.map { it.copy(originalFile = at(it.originalFile), displayFile = at(it.displayFile), thumbFile = at(it.thumbFile)) },
            document = document.map { it.copy(file = at(it.file)) },
            sourceSnapshot = sourceSnapshot.map { it.copy(bodyFile = at(it.bodyFile)) },
        )
    }

    private suspend fun uniqueVariant(relative: String): String {
        while (true) {
            val candidate = ArchivePaths.variant(relative, Random.nextInt(0, 36 * 36 * 36 * 36 * 36 * 36).toString(36).padStart(6, '0'))
            if (!files.exists(candidate)) return candidate
        }
    }

    private fun journal(relative: String) {
        files.fileSystem.createDirectories(files.root)
        files.fileSystem.appendingSink(files.resolve(JOURNAL)).buffer().use { it.writeUtf8(relative).writeUtf8("\n") }
    }

    private class Digest(val size: Long, val crc: Long, val sha256: String)

    private fun digest(relative: String, onBytes: (Long) -> Unit = {}): Digest {
        val crc = Crc32()
        val hashing = HashingSink.sha256(blackholeSink())
        val chunk = ByteArray(64 * 1024)
        val pass = Buffer()
        var size = 0L
        files.source(relative).buffer().use { src ->
            while (true) {
                val n = src.read(chunk)
                if (n == -1) break
                crc.update(chunk, 0, n)
                pass.write(chunk, 0, n)
                hashing.write(pass, n.toLong())
                size += n
                onBytes(n.toLong())
            }
        }
        return Digest(size, crc.value, hashing.hash.hex())
    }

    private companion object {
        /** The database schema version (AppDatabase). Bump with it. */
        const val SCHEMA_VERSION = 1
        const val JOURNAL = "restore-journal.txt"
        /** Present while a committed restore's search index and renditions still need redoing. */
        const val AFTERCARE = "restore-aftercare"
        /** Settings that belong to one phone (app lock, last backup) and never travel. */
        const val DEVICE_SETTING = "device."
        const val MAX_MANIFEST = 32L * 1024 * 1024
        const val MAX_COLLECTION = 512L * 1024 * 1024
        const val SPACE_MARGIN = 64L * 1024 * 1024

        fun sha256(bytes: ByteArray): String = bytes.toByteString().sha256().hex()

        fun plural(n: Int, word: String) = if (n == 1) word else "${word}s"

        /** The entry's name inside the backup, or null for entries to ignore (macOS metadata, other folders). */
        fun entryName(raw: String, prefix: String?): String? {
            val name = raw.removePrefix("./")
            if (name.startsWith("__MACOSX/") || name.contains("/._") || name.startsWith("._") || name.endsWith("/")) return null
            if (prefix == null) return name
            return if (name.startsWith(prefix)) name.removePrefix(prefix) else null
        }
    }
}
