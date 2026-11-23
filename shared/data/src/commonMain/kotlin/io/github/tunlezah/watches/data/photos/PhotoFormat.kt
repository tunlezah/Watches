package io.github.tunlezah.watches.data.photos

/**
 * A picture's format, told from the first bytes of the file rather than its name.
 *
 * [stored] formats are kept as they arrive: every device the app runs on can show them. The others
 * are ones some systems open and others don't, so a photo in one of them is converted to a JPEG as
 * it comes in and never kept as an original (docs/MACOS_PLAN.md, section 5).
 */
enum class PhotoFormat(val extension: String, val mime: String, val stored: Boolean, private val alsoNamed: Set<String> = emptySet()) {
    JPEG("jpg", "image/jpeg", stored = true, alsoNamed = setOf("jpeg")),
    PNG("png", "image/png", stored = true),
    WEBP("webp", "image/webp", stored = true),
    HEIC("heic", "image/heic", stored = true, alsoNamed = setOf("heif")),
    AVIF("avif", "image/avif", stored = false),

    /** TIFF, and the camera raw formats built on it (DNG, and most makers' own). */
    TIFF("tiff", "image/tiff", stored = false, alsoNamed = setOf("tif", "dng")),
    GIF("gif", "image/gif", stored = false),
    BMP("bmp", "image/bmp", stored = false);

    /** Every extension a file in this format usually has, for a file picker to offer. What a file is, is still told from its bytes. */
    val extensions: Set<String> get() = setOf(extension) + alsoNamed

    companion object {
        /** How much of a file [detect] may need: a HEIC's brand list can run past the first few bytes. */
        const val HEAD_BYTES = 64

        /** The format [head] starts, or null for anything else. [head] is the start of the file; the whole file is fine too. */
        fun detect(head: ByteArray): PhotoFormat? {
            fun at(i: Int) = if (i < head.size) head[i].toInt() and 0xFF else -1
            fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
            return when {
                at(0) == 0xFF && at(1) == 0xD8 -> JPEG
                at(0) == 0x89 && ascii(1, "PNG") -> PNG
                ascii(0, "RIFF") && ascii(8, "WEBP") -> WEBP
                ascii(0, "GIF87a") || ascii(0, "GIF89a") -> GIF
                ascii(0, "II") && at(2) == 42 && at(3) == 0 -> TIFF
                ascii(0, "MM") && at(2) == 0 && at(3) == 42 -> TIFF
                // "BM", the file's size, then four reserved bytes that are always zero.
                ascii(0, "BM") && at(6) == 0 && at(7) == 0 && at(8) == 0 && at(9) == 0 && head.size >= 14 -> BMP
                ascii(4, "ftyp") -> brandedFormat(head)
                else -> null
            }
        }

        private val HEIC_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs")
        private val AVIF_BRANDS = setOf("avif", "avis")

        /**
         * HEIC and AVIF share one container, and say which they are in a list of four-letter brands:
         * the main one, then the ones the file is also compatible with. `mif1` and `msf1` are the
         * container's own brands, used by both, so the others decide; alone they mean HEIC, which is
         * what phones write.
         */
        private fun brandedFormat(head: ByteArray): PhotoFormat? {
            val declared = ((head[0].toLong() and 0xFF) shl 24) or ((head[1].toLong() and 0xFF) shl 16) or
                ((head[2].toLong() and 0xFF) shl 8) or (head[3].toLong() and 0xFF)
            val end = minOf(if (declared < 16) head.size.toLong() else declared, head.size.toLong()).toInt()
            val brands = buildList {
                var i = 8
                while (i + 4 <= end) {
                    // Bytes 12 to 15 are the brand's version number, not a brand.
                    if (i != 12) add(head.decodeToString(i, i + 4))
                    i += 4
                }
            }
            return when {
                brands.any { it in AVIF_BRANDS } -> AVIF
                brands.any { it in HEIC_BRANDS } -> HEIC
                brands.firstOrNull() in setOf("mif1", "msf1") -> HEIC
                else -> null
            }
        }
    }
}
