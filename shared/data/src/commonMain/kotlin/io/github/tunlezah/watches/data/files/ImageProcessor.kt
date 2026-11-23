package io.github.tunlezah.watches.data.files

import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.data.photos.PhotoFormat
import okio.Path

data class ImageSize(val width: Int, val height: Int)

/**
 * Platform image work. Every call decodes off the main thread and never holds a full-size
 * bitmap longer than it must. Sizes and crops are in display orientation (EXIF applied).
 */
interface ImageProcessor {

    /** Pixel size after orientation is applied. */
    suspend fun size(file: Path): ImageSize

    /** Removes GPS location metadata in place, where the format allows. Other metadata is kept. */
    suspend fun stripLocation(file: Path)

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
