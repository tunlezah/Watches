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
import io.github.tunlezah.watches.data.files.ImageProcessor
import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.tunlezah.watches.data.photos.PhotoIntake
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.Source
import kotlin.random.Random

/**
 * Photos are stored three ways: the untouched original (location stripped), a cropped display
 * rendition and a small thumbnail. Crops are non-destructive: re-cropping renders from the original.
 *
 * An original is always in a format every device can show, and never carries a location: the rules
 * are shared code, so they are the same on Android, iPhone and Mac (docs/MACOS_PLAN.md, section 5).
 */
class PhotoRepository(
    private val db: AppDatabase,
    private val files: FileStore,
    private val processor: ImageProcessor,
    private val time: TimeSource = TimeSource.System,
) {
    private val dao = db.photoDao()
    private val watchDao = db.watchDao()
    private val intake = PhotoIntake(files, processor)

    // One import at a time keeps peak memory flat when many photos arrive together.
    private val importLock = Mutex()

    fun observe(watchId: String): Flow<List<Photo>> = dao.observeForWatch(watchId).map { list -> list.map { it.toModel() } }

    suspend fun forWatch(watchId: String): List<Photo> = dao.getForWatch(watchId).map { it.toModel() }

    suspend fun byId(id: String): Photo? = dao.get(id)?.toModel()

    /** Adds a picture to a watch. [bytes] is the file as it arrived. */
    suspend fun add(
        watchId: String,
        bytes: ByteArray,
        kind: PhotoKind = PhotoKind.USER,
        attribution: String? = null,
        attributionUrl: String? = null,
        id: String = Ids.new(),
    ): Photo = add(watchId, Buffer().write(bytes), kind, attribution, attributionUrl, id)

    /**
     * Adds a picture to a watch from a stream, which is read to its end and closed. A picked file
     * comes in this way, so however large it is, it is never held in memory to get here.
     *
     * The original the app keeps is whatever [PhotoIntake] leaves. A picture in a stored format is
     * kept byte for byte once its location has been overwritten. Anything else is kept as a
     * full-size JPEG made from it: a format only some devices can show (TIFF, GIF, a camera's raw
     * file), a file whose metadata can't be followed far enough to be sure the location has gone,
     * or one in which this platform's own reader still finds a location afterwards.
     */
    suspend fun add(
        watchId: String,
        source: Source,
        kind: PhotoKind = PhotoKind.USER,
        attribution: String? = null,
        attributionUrl: String? = null,
        id: String = Ids.new(),
    ): Photo = importLock.withLock {
        val dir = FileStore.photoDir(watchId)
        val original = intake.store(source, dir, "$id-original", ORIGINALS).path
        try {
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
            // Runs even when the import was cancelled, so no orphan files are left behind.
            withContext(NonCancellable) { files.delete(original) }
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

    /**
     * The whole upright picture as a JPEG, for the Crop screen to show, or null if there is no such
     * photo. Throws if this device can't read the original.
     *
     * The Crop screen used to hand the original to the image loader, whose decoders differ from the
     * app's own: on an iPhone or a Mac they read no HEIC, and none of them obeys a HEIC's own crop
     * box. The picture a crop is drawn on is now made by the codec that will make the cut, so the
     * two can't disagree. An uncropped photo's display image is already that picture; otherwise one
     * is made in the cache folder, which no backup includes.
     */
    suspend fun cropPreview(photoId: String): String? {
        val photo = dao.get(photoId)?.toModel() ?: return null
        if (photo.crop == null && photo.displayFile != photo.originalFile && files.exists(photo.displayFile)) return photo.displayFile
        val preview = "$CROP_PREVIEWS/${photo.id}.jpg"
        try {
            processor.render(files.resolve(photo.originalFile), null, DISPLAY_EDGE, DISPLAY_QUALITY, files.resolve(preview))
        } catch (e: Throwable) {
            withContext(NonCancellable) { files.delete(preview) }
            throw e
        }
        return preview
    }

    /** Removes what [cropPreview] made. They are quick to make again. */
    suspend fun clearCropPreviews() = files.deleteTree(CROP_PREVIEWS)

    /** Applies a crop (null = full frame) by re-rendering the display image and thumbnail from the original. */
    suspend fun crop(photoId: String, crop: NormalizedRect?) {
        val current = dao.get(photoId)?.toModel() ?: return
        val effective = crop?.takeUnless { it == NormalizedRect.Full }
        val dir = FileStore.photoDir(current.watchId)
        val (display, thumb) = renderRenditions(dir, current.id, current.originalFile, effective)
        dao.upsert(current.copy(displayFile = display, thumbFile = thumb, crop = effective).toEntity())
        // A restored photo can stand in for its own renditions until they are rebuilt.
        if (current.displayFile != current.originalFile) files.delete(current.displayFile)
        if (current.thumbFile != current.originalFile) files.delete(current.thumbFile)
    }

    /** Re-renders a photo's display image and thumbnail from its original, for a restored photo that came without them. */
    suspend fun rerender(photoId: String) {
        val current = dao.get(photoId)?.toModel() ?: return
        val (display, thumb) = renderRenditions(FileStore.photoDir(current.watchId), current.id, current.originalFile, current.crop)
        dao.upsert(current.copy(displayFile = display, thumbFile = thumb).toEntity())
        if (current.displayFile != current.originalFile) files.delete(current.displayFile)
        if (current.thumbFile != current.originalFile) files.delete(current.thumbFile)
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
        try {
            processor.render(files.resolve(original), crop, DISPLAY_EDGE, DISPLAY_QUALITY, files.resolve(display))
            processor.render(files.resolve(original), crop, THUMB_EDGE, THUMB_QUALITY, files.resolve(thumb))
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                files.delete(display)
                files.delete(thumb)
            }
            throw e
        }
        return display to thumb
    }

    companion object {
        /** A watch's photo is kept in any stored format; anything else becomes a full-size JPEG. */
        private val ORIGINALS = PhotoIntake.Rule(
            kept = PhotoFormat.entries.filter { it.stored }.toSet(), jpegEdge = ORIGINAL_EDGE, jpegQuality = ORIGINAL_QUALITY, tryUnknown = true,
        )

        private const val CROP_PREVIEWS = "${FileStore.CACHE_DIR}/crop"

        const val DISPLAY_EDGE = 2560
        const val THUMB_EDGE = 640
        const val DISPLAY_QUALITY = 88
        const val THUMB_QUALITY = 82

        /** For an original that has to be re-made as a JPEG: every pixel a phone's camera gives, at high quality. */
        const val ORIGINAL_EDGE = 16_384
        const val ORIGINAL_QUALITY = 95
    }
}
