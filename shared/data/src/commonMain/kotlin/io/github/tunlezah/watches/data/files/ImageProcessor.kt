package io.github.tunlezah.watches.data.files

import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.data.photos.PhotoFormat
import kotlinx.coroutines.CancellationException
import okio.Path

data class ImageSize(val width: Int, val height: Int)

/**
 * Image work. Every call decodes off the main thread and never holds a full-size bitmap longer
 * than it must. Sizes and crops are in display orientation: the upright picture a person sees.
 *
 * Removing a photo's location isn't here: that is shared code that works on the file's bytes
 * before it is stored ([io.github.tunlezah.watches.data.photos.PhotoMetadata]).
 */
interface ImageProcessor {

    /**
     * The formats this device can take a photo in from. The four that are stored as they arrive
     * (see [PhotoFormat]) are read everywhere the app runs; which of the others can be converted
     * depends on the system.
     */
    val readableFormats: Set<PhotoFormat>

    /** Pixel size after orientation is applied. */
    suspend fun size(file: Path): ImageSize

    /**
     * True if this platform's own metadata reader finds a location in [file]. The shared cleaner has
     * already run by the time this is asked: it is a second opinion from unrelated code, and a photo
     * that fails it isn't kept as it is.
     */
    suspend fun hasLocation(file: Path): Boolean

    /**
     * Decodes [source], applies orientation and [crop], downsamples so the long edge is at most
     * [maxEdge] pixels, and writes a JPEG to [dest]. Returns the written size.
     */
    suspend fun render(source: Path, crop: NormalizedRect?, maxEdge: Int, quality: Int, dest: Path): ImageSize
}

object ImageFormats {
    /**
     * The extension an original is kept under: its format's, for the formats every device can show
     * (see [PhotoFormat]), and `.img` for anything else.
     */
    fun extension(bytes: ByteArray): String = PhotoFormat.detect(bytes)?.takeIf { it.stored }?.extension ?: "img"
}

object DocumentFormats {
    const val HEIC = "image/heic"

    /** The formats a document may be, by magic bytes: PDF and the usual photo formats. Null for anything else. */
    fun detect(bytes: ByteArray): Pair<String, String>? {
        if (isPdf(bytes)) return "application/pdf" to "pdf"
        return when (ImageFormats.extension(bytes)) {
            "jpg" -> "image/jpeg" to "jpg"
            "png" -> "image/png" to "png"
            "webp" -> "image/webp" to "webp"
            "heic" -> HEIC to "heic"
            else -> null
        }
    }

    /** Readers accept the `%PDF-` header anywhere in the first kilobyte, and some scanners put junk before it. */
    private fun isPdf(bytes: ByteArray): Boolean {
        val end = minOf(bytes.size, 1024) - 5
        for (i in 0..end) {
            if (bytes[i] == '%'.code.toByte() && bytes[i + 1] == 'P'.code.toByte() && bytes[i + 2] == 'D'.code.toByte() &&
                bytes[i + 3] == 'F'.code.toByte() && bytes[i + 4] == '-'.code.toByte()
            ) return true
        }
        return false
    }
}

/** [ImageProcessor.hasLocation], for a file that may not be a picture at all: one this platform can't read says nothing either way. */
suspend fun ImageProcessor.findsLocation(file: Path): Boolean = try {
    hasLocation(file)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    false
}
