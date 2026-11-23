package io.github.tunlezah.watches.data.photos

import io.github.tunlezah.watches.data.backup.Crc32

/** What [PhotoMetadata.removeLocation] did to a file. */
enum class LocationRemoval {
    /** There was no location. The file is unchanged. */
    NONE,

    /** A location was there and has been overwritten. The file is the same length and the picture untouched. */
    REMOVED,

    /**
     * The file's metadata couldn't be followed, so it may still hold a location. The file is
     * unchanged, and the caller must not keep it as it is.
     */
    UNREADABLE,
}

/**
 * Reads and cleans the metadata inside a photo file: where a location hides, and which way up the
 * picture is. It is shared code, so every platform removes exactly the same things in exactly the
 * same way, instead of each trusting its own system's library (which on Android can't rewrite a
 * HEIC at all). See docs/MACOS_PLAN.md, 5.2 and 8.2.2.
 *
 * A location is kept in two places: the GPS directory of the EXIF block, and `exif:GPS…` fields of
 * the XMP packet. Both are overwritten where they lie. Nothing is moved and the file's length
 * doesn't change, so the picture data and everything that points into it stay valid. The capture
 * date, the camera and the orientation are left alone.
 *
 * It handles the four formats that are stored (see [PhotoFormat]): JPEG, PNG, WebP and HEIC.
 */
object PhotoMetadata {

    /** The picture's turning from its EXIF block. Upright if there is none or it can't be read. */
    fun orientation(bytes: ByteArray): Orientation = guarded(Orientation.NORMAL) {
        val exif = blocks(bytes).orEmpty().firstOrNull { it.kind == Kind.EXIF } ?: return@guarded Orientation.NORMAL
        Orientation.fromExif(Tiff.open(bytes, exif)?.orientation())
    }

    /** True if the file holds a location, or its metadata can't be followed far enough to rule one out. */
    fun hasLocation(bytes: ByteArray): Boolean = guarded(true) { scan(bytes, erase = false) != LocationRemoval.NONE }

    /** Overwrites any location in [bytes], in place. */
    fun removeLocation(bytes: ByteArray): LocationRemoval {
        // Looked at first without touching anything, so a file that turns out unreadable is left exactly as it was.
        val found = guarded(LocationRemoval.UNREADABLE) { scan(bytes, erase = false) }
        if (found != LocationRemoval.REMOVED) return found
        return guarded(LocationRemoval.UNREADABLE) { scan(bytes, erase = true) }
    }

    /**
     * The byte ranges that hold metadata: everything [removeLocation] may write to lies inside
     * them. Null if the file's structure can't be followed.
     */
    internal fun metadataRanges(bytes: ByteArray): List<IntRange>? = guarded(null) {
        blocks(bytes)?.flatMap { block ->
            // A PNG chunk's checksum follows its data, and is rewritten with it.
            listOf(block.start until block.start + block.length) + listOfNotNull(block.pngChunk?.let { it.crcAt until it.crcAt + 4 })
        }
    }

    private fun scan(bytes: ByteArray, erase: Boolean): LocationRemoval {
        val blocks = blocks(bytes) ?: return LocationRemoval.UNREADABLE
        var found = false
        for (block in blocks) {
            val here = when (block.kind) {
                Kind.EXIF -> (Tiff.open(bytes, block) ?: return LocationRemoval.UNREADABLE).location(erase) ?: return LocationRemoval.UNREADABLE
                Kind.XMP -> Xmp.location(bytes, block.start, block.length, erase)
            }
            if (here && erase) block.pngChunk?.rewriteChecksum(bytes)
            found = found || here
        }
        return if (found) LocationRemoval.REMOVED else LocationRemoval.NONE
    }

    private fun blocks(bytes: ByteArray): List<Block>? = when (PhotoFormat.detect(bytes)) {
        PhotoFormat.JPEG -> Containers.jpeg(bytes)
        PhotoFormat.PNG -> Containers.png(bytes)
        PhotoFormat.WEBP -> Containers.webp(bytes)
        PhotoFormat.HEIC -> Containers.heic(bytes)
        else -> null
    }

    /** A damaged file can point anywhere. Every read is bounds-checked, and this catches whatever still slips through. */
    private inline fun <T> guarded(otherwise: T, block: () -> T): T = try {
        block()
    } catch (_: IndexOutOfBoundsException) {
        otherwise
    } catch (_: IllegalArgumentException) {
        otherwise
    } catch (_: ArithmeticException) {
        otherwise
    }
}

internal enum class Kind { EXIF, XMP }

/** A PNG chunk: its four-letter type starts at [typeAt], and its data is [length] bytes long. */
internal class PngChunk(private val typeAt: Int, private val length: Int) {
    val crcAt: Int get() = typeAt + 4 + length

    /** A PNG chunk carries a checksum of its type and data, which a reader may check. */
    fun rewriteChecksum(bytes: ByteArray) {
        val crc = Crc32().apply { update(bytes, typeAt, 4 + length) }.value
        for (i in 0 until 4) bytes[crcAt + i] = (crc shr (24 - 8 * i)).toByte()
    }
}

/** Where a block of metadata lies in the file. */
internal class Block(val kind: Kind, val start: Int, val length: Int, val pngChunk: PngChunk? = null)

private fun u8(b: ByteArray, at: Int): Int = b[at].toInt() and 0xFF
private fun u16be(b: ByteArray, at: Int): Int = (u8(b, at) shl 8) or u8(b, at + 1)
private fun u32be(b: ByteArray, at: Int): Long = (u16be(b, at).toLong() shl 16) or u16be(b, at + 2).toLong()
private fun u32le(b: ByteArray, at: Int): Long =
    u8(b, at).toLong() or (u8(b, at + 1).toLong() shl 8) or (u8(b, at + 2).toLong() shl 16) or (u8(b, at + 3).toLong() shl 24)

private fun startsWith(b: ByteArray, at: Int, limit: Int, text: ByteArray): Boolean {
    if (text.size > limit || at + text.size > b.size) return false
    for (i in text.indices) if (b[at + i] != text[i]) return false
    return true
}

private fun ascii(text: String): ByteArray = text.encodeToByteArray()

/** A length or offset from a file, as an Int inside the file or not at all. */
private fun inside(value: Long, size: Int): Int {
    require(value in 0..size.toLong()) { "Outside the file: $value" }
    return value.toInt()
}

/** Finds the EXIF and XMP blocks in each container. Each returns null when the file's structure can't be followed. */
private object Containers {
    private val EXIF_LABEL = byteArrayOf('E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0)
    private val XMP_LABEL = ascii("http://ns.adobe.com/xap/1.0/") + byteArrayOf(0)
    private val XMP_MORE_LABEL = ascii("http://ns.adobe.com/xmp/extension/") + byteArrayOf(0)

    /** A TIFF block, with or without the six-byte label some writers put in front of it. */
    private fun exif(b: ByteArray, at: Int, length: Int, chunk: PngChunk? = null): Block =
        if (startsWith(b, at, length, EXIF_LABEL)) Block(Kind.EXIF, at + 6, length - 6, chunk) else Block(Kind.EXIF, at, length, chunk)

    /** Segments, each a marker and a length, up to where the picture data starts. */
    fun jpeg(b: ByteArray): List<Block>? {
        val out = ArrayList<Block>()
        var p = 2
        while (true) {
            if (p + 4 > b.size || u8(b, p) != 0xFF) return null
            val marker = u8(b, p + 1)
            when {
                marker == 0xFF -> p++ // padding before a marker
                marker == 0xDA || marker == 0xD9 -> return out // the picture data, or the end
                marker in 0xD0..0xD7 || marker == 0x01 -> p += 2 // markers with no length
                else -> {
                    val length = u16be(b, p + 2)
                    if (length < 2 || p + 2 + length > b.size) return null
                    val data = p + 4
                    val size = length - 2
                    if (marker == 0xE1) {
                        when {
                            startsWith(b, data, size, EXIF_LABEL) -> out += Block(Kind.EXIF, data + 6, size - 6)
                            startsWith(b, data, size, XMP_LABEL) -> out += Block(Kind.XMP, data + XMP_LABEL.size, size - XMP_LABEL.size)
                            startsWith(b, data, size, XMP_MORE_LABEL) -> out += Block(Kind.XMP, data + XMP_MORE_LABEL.size, size - XMP_MORE_LABEL.size)
                        }
                    }
                    p += 2 + length
                }
            }
        }
    }

    /** Chunks: a length, a four-letter type, the data and a checksum. */
    fun png(b: ByteArray): List<Block>? {
        val out = ArrayList<Block>()
        var p = 8
        while (true) {
            if (p + 12 > b.size) return null
            val length = inside(u32be(b, p), b.size)
            val typeAt = p + 4
            val data = p + 8
            if (data + length + 4 > b.size) return null
            val type = b.decodeToString(typeAt, typeAt + 4)
            val chunk = PngChunk(typeAt, length)
            when (type) {
                "IEND" -> return out
                "eXIf" -> out += exif(b, data, length, chunk)
                "iTXt" -> {
                    // keyword, 0, compressed?, method, language, 0, translated keyword, 0, text
                    val keywordEnd = indexOfZero(b, data, data + length) ?: return null
                    if (b.decodeToString(data, keywordEnd) == "XML:com.adobe.xmp") {
                        if (keywordEnd + 2 >= data + length || u8(b, keywordEnd + 1) != 0) return null // compressed: can't be edited in place
                        val languageEnd = indexOfZero(b, keywordEnd + 3, data + length) ?: return null
                        val translatedEnd = indexOfZero(b, languageEnd + 1, data + length) ?: return null
                        out += Block(Kind.XMP, translatedEnd + 1, data + length - translatedEnd - 1, chunk)
                    }
                }
                // Older tools keep EXIF and XMP as text "profiles", usually compressed. They can't be edited in place.
                "tEXt", "zTXt" -> {
                    val keywordEnd = indexOfZero(b, data, data + length) ?: return null
                    val keyword = b.decodeToString(data, keywordEnd)
                    if (keyword.startsWith("Raw profile type") || keyword == "XML:com.adobe.xmp") return null
                }
            }
            p = data + length + 4
        }
    }

    private fun indexOfZero(b: ByteArray, from: Int, until: Int): Int? {
        for (i in from until until) if (b[i] == 0.toByte()) return i
        return null
    }

    /** A RIFF file: a four-letter name and a little-endian length for each chunk, padded to an even length. */
    fun webp(b: ByteArray): List<Block>? {
        if (b.size < 12) return null
        val out = ArrayList<Block>()
        var p = 12
        while (p + 8 <= b.size) {
            val name = b.decodeToString(p, p + 4)
            val length = inside(u32le(b, p + 4), b.size)
            val data = p + 8
            if (data + length > b.size) return null
            when (name) {
                "EXIF" -> out += exif(b, data, length)
                "XMP " -> out += Block(Kind.XMP, data, length)
            }
            p = data + length + (length and 1)
        }
        return out
    }

    private class Box(val type: String, val payload: Int, val end: Int)

    private fun box(b: ByteArray, at: Int, limit: Int): Box? {
        if (at + 8 > limit) return null
        val declared = u32be(b, at)
        val type = b.decodeToString(at + 4, at + 8)
        return when (declared) {
            0L -> Box(type, at + 8, limit) // runs to the end
            1L -> {
                if (at + 16 > limit || u32be(b, at + 8) != 0L) return null // a 64-bit length too large for a photo
                val end = at + inside(u32be(b, at + 12), limit - at)
                if (end < at + 16) null else Box(type, at + 16, end)
            }
            else -> {
                val end = at + inside(declared, limit - at)
                if (end < at + 8) null else Box(type, at + 8, end)
            }
        }
    }

    private class Item(val type: String, val contentType: String?)
    private class Place(val method: Int, val offset: Long, val length: Long, val extents: Int)

    /**
     * HEIC is a box file. Metadata are "items": a table (iinf) names each item's kind, and another
     * (iloc) says where its bytes are, either in the file or in a data box inside the table's own
     * box. An EXIF item starts with a four-byte offset to its TIFF block.
     */
    fun heic(b: ByteArray): List<Block>? {
        var meta: Box? = null
        var p = 0
        while (p < b.size) {
            val box = box(b, p, b.size) ?: return null
            if (box.type == "meta") {
                meta = box
                break
            }
            p = box.end
        }
        val table = meta ?: return null
        val items = HashMap<Long, Item>()
        val places = HashMap<Long, Place>()
        var dataAt: Int? = null
        p = table.payload + 4 // the box's version and flags
        while (p < table.end) {
            val box = box(b, p, table.end) ?: return null
            when (box.type) {
                "iinf" -> readItems(b, box, items) ?: return null
                "iloc" -> readPlaces(b, box, places) ?: return null
                "idat" -> dataAt = box.payload
            }
            p = box.end
        }
        val out = ArrayList<Block>()
        for ((id, item) in items) {
            val exif = item.type == "Exif"
            val xmp = item.type == "mime" && item.contentType == "application/rdf+xml"
            if (!exif && !xmp) continue
            val place = places[id] ?: return null
            // In pieces, or held inside another item: not something a phone writes, and not followed here.
            if (place.extents != 1 || place.method > 1) return null
            val at = inside(if (place.method == 1) (dataAt ?: return null) + place.offset else place.offset, b.size)
            val length = inside(place.length, b.size - at)
            if (xmp) {
                out += Block(Kind.XMP, at, length)
            } else {
                if (length < 4) return null
                val skip = inside(u32be(b, at), length - 4)
                out += exif(b, at + 4 + skip, length - 4 - skip)
            }
        }
        return out
    }

    private fun readItems(b: ByteArray, box: Box, into: MutableMap<Long, Item>): Unit? {
        val version = u8(b, box.payload)
        var p = box.payload + 4 + if (version == 0) 2 else 4 // past the count: the entries are boxes themselves
        while (p < box.end) {
            val entry = box(b, p, box.end) ?: return null
            if (entry.type == "infe") {
                val v = u8(b, entry.payload)
                if (v >= 2) {
                    var q = entry.payload + 4
                    val id = if (v == 2) u16be(b, q).toLong() else u32be(b, q)
                    q += if (v == 2) 2 else 4
                    q += 2 // protection
                    val type = b.decodeToString(q, q + 4)
                    q += 4
                    val nameEnd = indexOfZero(b, q, entry.end) ?: return null
                    val contentType = if (type == "mime") {
                        val end = indexOfZero(b, nameEnd + 1, entry.end) ?: return null
                        b.decodeToString(nameEnd + 1, end)
                    } else {
                        null
                    }
                    into[id] = Item(type, contentType)
                }
            }
            p = entry.end
        }
        return Unit
    }

    private fun readPlaces(b: ByteArray, box: Box, into: MutableMap<Long, Place>): Unit? {
        val version = u8(b, box.payload)
        var p = box.payload + 4
        val offsetSize = u8(b, p) shr 4
        val lengthSize = u8(b, p) and 0xF
        val baseSize = u8(b, p + 1) shr 4
        val indexSize = if (version >= 1) u8(b, p + 1) and 0xF else 0
        p += 2
        fun sized(size: Int): Long? {
            val value = when (size) {
                0 -> 0L
                4 -> u32be(b, p)
                8 -> if (u32be(b, p) != 0L) return null else u32be(b, p + 4)
                else -> return null
            }
            p += size
            return value
        }
        val count = if (version < 2) u16be(b, p).toLong().also { p += 2 } else u32be(b, p).also { p += 4 }
        repeat(inside(count, box.end - p)) {
            val id = if (version < 2) u16be(b, p).toLong().also { p += 2 } else u32be(b, p).also { p += 4 }
            val method = if (version >= 1) (u16be(b, p) and 0xF).also { p += 2 } else 0
            p += 2 // data reference
            val base = sized(baseSize) ?: return null
            val extents = u16be(b, p)
            p += 2
            var first: Pair<Long, Long>? = null
            repeat(extents) {
                if (indexSize > 0) sized(indexSize) ?: return null
                val offset = sized(offsetSize) ?: return null
                val length = sized(lengthSize) ?: return null
                if (first == null) first = offset to length
            }
            val (offset, length) = first ?: (0L to 0L)
            into[id] = Place(method, base + offset, length, extents)
        }
        return Unit
    }
}

/**
 * A TIFF block: the structure EXIF is stored in. A header says the byte order and where the main
 * directory is; a directory is a count and that many twelve-byte entries (tag, type, count, and
 * the value or the offset of the value). All offsets are from the start of the block.
 */
private class Tiff private constructor(private val b: ByteArray, private val start: Int, private val length: Int, private val little: Boolean) {

    private fun u16(at: Int): Int {
        require(at >= 0 && at + 2 <= length)
        return if (little) u8(b, start + at) or (u8(b, start + at + 1) shl 8) else u16be(b, start + at)
    }

    private fun u32(at: Int): Long {
        require(at >= 0 && at + 4 <= length)
        return if (little) u32le(b, start + at) else u32be(b, start + at)
    }

    private fun entries(directory: Int): List<Int> {
        val count = u16(directory)
        require(directory + 2 + 12 * count + 4 <= length)
        return List(count) { directory + 2 + 12 * it }
    }

    private fun mainDirectory(): Int = inside(u32(4), length)

    fun orientation(): Int? = entries(mainDirectory()).firstOrNull { u16(it) == ORIENTATION && u16(it + 2) == SHORT }?.let { u16(it + 8) }

    /**
     * True if the GPS directory holds anything beyond its version number, and with [erase] it is
     * then overwritten: every value zeroed, and the directory rewritten to hold the version alone,
     * which is what a file that never had a location looks like. Null if the directory can't be followed.
     */
    fun location(erase: Boolean): Boolean? {
        val pointer = entries(mainDirectory()).firstOrNull { u16(it) == GPS_POINTER } ?: return false
        val directory = inside(u32(pointer + 8), length)
        val entries = entries(directory)
        if (entries.none { u16(it) != GPS_VERSION }) return false
        // Values over four bytes are kept elsewhere in the block: the coordinates themselves. Every one
        // is checked to lie inside the block before anything is overwritten, so a block that can't be
        // followed is left exactly as it was.
        val values = entries.mapNotNull { entry ->
            val size = (TYPE_SIZES.getOrNull(u16(entry + 2)) ?: 0) * u32(entry + 4)
            if (size <= 4) return@mapNotNull null
            val at = inside(u32(entry + 8), length)
            if (size > length - at) return null
            at to size.toInt()
        }
        if (!erase) return true
        for ((at, size) in values) b.fill(0, start + at, start + at + size)
        b.fill(0, start + directory, start + directory + 2 + 12 * entries.size + 4)
        put16(directory, 1)
        put16(directory + 2, GPS_VERSION)
        put16(directory + 4, BYTE)
        put32(directory + 6, 4)
        b[start + directory + 10] = 2
        b[start + directory + 11] = 3
        return true
    }

    private fun put16(at: Int, value: Int) {
        val hi = (value shr 8).toByte()
        val lo = value.toByte()
        b[start + at] = if (little) lo else hi
        b[start + at + 1] = if (little) hi else lo
    }

    private fun put32(at: Int, value: Int) {
        if (little) {
            put16(at, value and 0xFFFF)
            put16(at + 2, value ushr 16)
        } else {
            put16(at, value ushr 16)
            put16(at + 2, value and 0xFFFF)
        }
    }

    companion object {
        private const val ORIENTATION = 0x0112
        private const val GPS_POINTER = 0x8825
        private const val GPS_VERSION = 0x0000
        private const val BYTE = 1
        private const val SHORT = 3

        /** Bytes per value, by TIFF type number: byte, text, short, long, rational, and their signed and float kin. */
        private val TYPE_SIZES = listOf(0L, 1L, 1L, 2L, 4L, 8L, 1L, 1L, 2L, 4L, 8L, 4L, 8L, 4L)

        /** Null if the block doesn't start as a TIFF block does. */
        fun open(b: ByteArray, block: Block): Tiff? {
            if (block.length < 8 || block.start < 0 || block.start + block.length > b.size) return null
            val little = when {
                u8(b, block.start) == 'I'.code && u8(b, block.start + 1) == 'I'.code -> true
                u8(b, block.start) == 'M'.code && u8(b, block.start + 1) == 'M'.code -> false
                else -> return null
            }
            val tiff = Tiff(b, block.start, block.length, little)
            return if (tiff.u16(2) == 42) tiff else null
        }
    }
}

/**
 * An XMP packet is text (XML). A location is in fields named `exif:GPS…`, written either as
 * attributes or as elements. Their values are overwritten with spaces, which keeps the packet's
 * length and leaves it well-formed.
 */
private object Xmp {
    private val FIELD = ascii("exif:GPS")
    private const val SPACE = ' '.code.toByte()

    fun location(b: ByteArray, start: Int, length: Int, erase: Boolean): Boolean {
        val end = minOf(start + length, b.size)
        var found = false
        var i = indexOf(b, FIELD, start, end)
        while (i >= 0) {
            var nameEnd = i + FIELD.size
            while (nameEnd < end && isNameByte(b[nameEnd])) nameEnd++
            val closing = i >= start + 2 && b[i - 1] == '/'.code.toByte() && b[i - 2] == '<'.code.toByte()
            var next = nameEnd
            if (!closing) {
                var p = nameEnd
                while (p < end && isSpace(b[p])) p++
                if (p < end && b[p] == '='.code.toByte()) {
                    // exif:GPSLatitude="51,28.639N"
                    p++
                    while (p < end && isSpace(b[p])) p++
                    if (p < end && (b[p] == '"'.code.toByte() || b[p] == '\''.code.toByte())) {
                        val quote = b[p]
                        var valueEnd = p + 1
                        while (valueEnd < end && b[valueEnd] != quote) valueEnd++
                        if (blank(b, p + 1, valueEnd, erase)) found = true
                        next = valueEnd
                    }
                } else if (i >= start + 1 && b[i - 1] == '<'.code.toByte()) {
                    // <exif:GPSAltitude>46/1</exif:GPSAltitude>
                    while (p < end && b[p] != '>'.code.toByte()) p++
                    var valueEnd = p + 1
                    while (valueEnd < end && b[valueEnd] != '<'.code.toByte()) valueEnd++
                    if (p < end && blank(b, p + 1, valueEnd, erase)) found = true
                    next = valueEnd
                }
            }
            i = indexOf(b, FIELD, maxOf(next, nameEnd), end)
        }
        return found
    }

    /** True if [from] until [until] held anything but spaces; with [erase] it holds only spaces afterwards. */
    private fun blank(b: ByteArray, from: Int, until: Int, erase: Boolean): Boolean {
        var any = false
        for (i in from until until) {
            if (!isSpace(b[i])) {
                any = true
                if (erase) b[i] = SPACE
            }
        }
        return any
    }

    private fun isSpace(c: Byte) = c == SPACE || c == '\n'.code.toByte() || c == '\r'.code.toByte() || c == '\t'.code.toByte()

    private fun isNameByte(c: Byte): Boolean {
        val v = c.toInt()
        return v in 'A'.code..'Z'.code || v in 'a'.code..'z'.code || v in '0'.code..'9'.code || v == '_'.code || v == '-'.code || v == '.'.code
    }

    private fun indexOf(b: ByteArray, text: ByteArray, from: Int, until: Int): Int {
        var i = maxOf(from, 0)
        while (i + text.size <= until) {
            var j = 0
            while (j < text.size && b[i + j] == text[j]) j++
            if (j == text.size) return i
            i++
        }
        return -1
    }
}
