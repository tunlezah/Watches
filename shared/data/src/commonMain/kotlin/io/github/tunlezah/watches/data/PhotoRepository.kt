package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.Ids
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.core.model.Photo
import io.github.tunlezah.watches.core.model.PhotoKind
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.toEntity
import io.github.tunlezah.watches.data.db.toModel
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.ImageFormats
import io.github.tunlezah.watches.data.files.ImageProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Photos are stored three ways: the untouched original (location stripped), a cropped display
 * rendition and a small thumbnail. Crops are non-destructive: re-cropping renders from the original.
 */
class PhotoRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val processor: ImageProcessor,
    private val time: TimeSource = TimeSource.System,
) {
    private val dao = db.photoDao()
    private val watchDao = db.watchDao()

    // One import at a time keeps peak memory flat when many photos arrive together.
    private val importLock = Mutex()

    fun observe(watchId: String): Flow<List<Photo>> = dao.observeForWatch(watchId).map { list -> list.map { it.toModel() } }

    suspend fun forWatch(watchId: String): List<Photo> = dao.getForWatch(watchId).map { it.toModel() }

    suspend fun byId(id: String): Photo? = dao.get(id)?.toModel()

    suspend fun add(
        watchId: String,
        bytes: ByteArray,
        kind: PhotoKind = PhotoKind.USER,
        attribution: String? = null,
        attributionUrl: String? = null,
        id: String = Ids.new(),
    ): Photo = importLock.withLock {
        val dir = FileStore.photoDir(watchId)
        val original = "$dir/$id-original.${ImageFormats.extension(bytes)}"
        files.write(original, bytes)
        try {
            processor.stripLocation(files.resolve(original))
            val size = processor.size(files.resolve(original))
            val (display, thumb) = renderRenditions(dir, id, original, crop = null)
            val photo = Photo(
                id = id,
                watchId = watchId,
                position = dao.maxPosition(watchId) + 1,
                originalFile = original,
                displayFile = display,
                thumbFile = thumb,
                width = size.width,
                height = size.height,
                kind = kind,
                attribution = attribution,
                attributionUrl = attributionUrl,
                addedAt = time.now(),
            )
            dao.upsert(photo.toEntity())
            val watch = watchDao.getWatch(watchId)
            if (watch != null && watch.coverPhotoId == null && kind == PhotoKind.USER) {
                watchDao.setCover(watchId, id, time.now().toEpochMilliseconds())
            }
            photo
        } catch (e: Throwable) {
            files.delete(original)
            throw e
        }
    }

    suspend fun reorder(watchId: String, orderedIds: List<String>) {
        val known = dao.getForWatch(watchId).map { it.id }.toSet()
        dao.reorder(orderedIds.filter { it in known })
    }

    suspend fun setCover(watchId: String, photoId: String) {
        watchDao.setCover(watchId, photoId, time.now().toEpochMilliseconds())
    }

    /** Applies a crop (null = full frame) by re-rendering the display image and thumbnail from the original. */
    suspend fun crop(photoId: String, crop: NormalizedRect?) {
        val current = dao.get(photoId)?.toModel() ?: return
        val effective = crop?.takeUnless { it == NormalizedRect.Full }
        val dir = FileStore.photoDir(current.watchId)
        val (display, thumb) = renderRenditions(dir, current.id, current.originalFile, effective)
        dao.upsert(current.copy(displayFile = display, thumbFile = thumb, crop = effective).toEntity())
        files.delete(current.displayFile)
        files.delete(current.thumbFile)
    }

    suspend fun delete(photoId: String) {
        val photo = dao.get(photoId)?.toModel() ?: return
        dao.delete(photoId)
        val watch = watchDao.getWatch(photo.watchId)
        if (watch?.coverPhotoId == photoId) {
            val next = dao.getForWatch(photo.watchId).firstOrNull { it.kind == PhotoKind.USER.key }
            watchDao.setCover(photo.watchId, next?.id, time.now().toEpochMilliseconds())
        }
        dao.reorder(dao.getForWatch(photo.watchId).map { it.id })
        files.delete(photo.originalFile)
        files.delete(photo.displayFile)
        files.delete(photo.thumbFile)
    }

    /** Rendition names carry a random suffix so image caches never show a stale crop. */
    private suspend fun renderRenditions(dir: String, id: String, original: String, crop: NormalizedRect?): Pair<String, String> {
        val tag = Random.nextInt(0, 1 shl 30).toString(36)
        val display = "$dir/$id-display-$tag.jpg"
        val thumb = "$dir/$id-thumb-$tag.jpg"
        processor.render(files.resolve(original), crop, DISPLAY_EDGE, DISPLAY_QUALITY, files.resolve(display))
        processor.render(files.resolve(original), crop, THUMB_EDGE, THUMB_QUALITY, files.resolve(thumb))
        return display to thumb
    }

    companion object {
        const val DISPLAY_EDGE = 2560
        const val THUMB_EDGE = 640
        const val DISPLAY_QUALITY = 88
        const val THUMB_QUALITY = 82
    }
}
