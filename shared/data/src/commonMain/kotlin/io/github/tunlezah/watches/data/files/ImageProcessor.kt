package io.github.tunlezah.watches.data.files

import io.github.tunlezah.watches.core.model.NormalizedRect
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
    /** Guesses a file extension from magic bytes. Unknown formats are kept as `.img`. */
    fun extension(bytes: ByteArray): String {
        fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
        return when {
            at(0) == 0xFF && at(1) == 0xD8 -> "jpg"
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "png"
            at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 && at(8) == 0x57 && at(9) == 0x45 -> "webp"
            bytes.size > 12 && bytes.decodeToString(4, 12).let { it.startsWith("ftyphei") || it.startsWith("ftypmif") || it.startsWith("ftyphe") } -> "heic"
            else -> "img"
        }
    }
}

object DocumentFormats {
    /** The formats a document may be, by magic bytes: PDF and the usual photo formats. Null for anything else. */
    fun detect(bytes: ByteArray): Pair<String, String>? {
        if (bytes.size >= 5 && bytes.decodeToString(0, 5) == "%PDF-") return "application/pdf" to "pdf"
        return when (ImageFormats.extension(bytes)) {
            "jpg" -> "image/jpeg" to "jpg"
            "png" -> "image/png" to "png"
            "webp" -> "image/webp" to "webp"
            "heic" -> "image/heic" to "heic"
            else -> null
        }
    }
}
