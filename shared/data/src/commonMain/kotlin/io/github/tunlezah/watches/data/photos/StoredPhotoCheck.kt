package io.github.tunlezah.watches.data.photos

import io.github.tunlezah.watches.data.DocumentRepository
import io.github.tunlezah.watches.data.PhotoRepository
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.files.FileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

/**
 * Brings the photos already on this device up to the rules a new photo meets
 * (docs/MACOS_PLAN.md, 8.2.9).
 *
 * Until the shared photo component, each platform removed a photo's location with its own system
 * library, and none of them could for every format: Android left a HEIC's in, and a failure was
 * silent. A photo stored by an older version, or restored from a backup an older version made, may
 * therefore still carry one, and its recorded size may be that of the picture on its side. This
 * goes through every stored photo and photographed document once and puts that right.
 *
 * A pass is due once on each device, and again after every restore, which brings in files from
 * somewhere else. It works through the photos in a fixed order and notes the last one it finished,
 * so one that is stopped (the app is closed) picks up where it was. A photo it can't deal with is
 * left as it is and doesn't stop the rest.
 */
class StoredPhotoCheck(
    private val db: AppDatabase,
    private val files: FileStore,
    private val photos: PhotoRepository,
    private val documents: DocumentRepository,
) {
    private val running = Mutex()

    /** What a pass did: how many photos it looked at, how many it had to change, and how many it couldn't. */
    data class Report(val checked: Int, val changed: Int, val failed: Int)

    /** True until a pass has run to its end since the last [request]. */
    suspend fun isDue(): Boolean = progress() != DONE

    /** Asks for another pass. A restore does, for the files it has just brought in. */
    suspend fun request() = files.delete(MARKER)

    /** Runs a pass if one is due, from where the last one stopped. Null if none was due. */
    suspend fun runIfDue(): Report? = running.withLock {
        val progress = progress()
        if (progress == DONE) return null
        val dao = db.backupDao()
        val items = (dao.photos().map { PHOTO + it.id } + dao.documents().filter { it.mimeType.startsWith("image/") }.map { DOCUMENT + it.id }).sorted()
        var checked = 0
        var changed = 0
        var failed = 0
        for (item in items) {
            if (progress != null && item <= progress) continue
            try {
                val altered = if (item.startsWith(PHOTO)) photos.recheck(item.removePrefix(PHOTO)) else documents.recheck(item.removePrefix(DOCUMENT))
                if (altered) changed++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed++
            }
            checked++
            files.write(MARKER, "$VERSION $item".encodeToByteArray())
            yield()
        }
        files.write(MARKER, "$VERSION $DONE".encodeToByteArray())
        Report(checked, changed, failed)
    }

    /** The last item finished, [DONE], or null if no pass by these rules has started. */
    private suspend fun progress(): String? {
        if (!files.exists(MARKER)) return null
        val (version, at) = files.read(MARKER).decodeToString().split(' ', limit = 2).let { it.first() to it.getOrNull(1) }
        return at?.takeIf { version == VERSION.toString() }
    }

    private companion object {
        /** Where the pass has got to. In the cache folder: if that is ever emptied the pass runs again, which does no harm. */
        const val MARKER = "${FileStore.CACHE_DIR}/photo-check"

        /** Raised when the rules for a stored photo change, so every device checks again. */
        const val VERSION = 1
        const val DONE = "done"
        const val PHOTO = "p:"
        const val DOCUMENT = "d:"
    }
}
