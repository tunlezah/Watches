package io.github.tunlezah.watches.data.backup

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.Sink
import okio.Source
import okio.buffer

/** The archive isn't a ZIP this app can read, or it is damaged. */
class ZipFormatException(message: String) : Exception(message)

/** CRC-32 as ZIP uses it (the ISO-HDLC polynomial). */
class Crc32 {
    private var crc = -1

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        var c = crc
        for (i in offset until offset + length) c = TABLE[(c xor bytes[i].toInt()) and 0xFF] xor (c ushr 8)
        crc = c
    }

    val value: Long get() = crc.inv().toLong() and 0xFFFFFFFFL

    private companion object {
        val TABLE = IntArray(256) { n ->
            var c = n
            repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
            c
        }
    }
}

/**
 * Writes a ZIP archive front to back. Entries are stored uncompressed: photos and PDFs are already
 * compressed, and a stored entry can be read by anything. Each entry's CRC and size are known
 * before it is written, so local headers are complete and the archive can be read as a stream.
 * ZIP64 records are added only where a size, offset or entry count needs them.
 */
class ZipWriter(sink: Sink, private val modified: DosTime = DosTime.EPOCH) {
    private val out: BufferedSink = sink.buffer()
    private var offset = 0L
    private val entries = mutableListOf<Written>()
    private var finished = false

    private class Written(val name: ByteArray, val crc: Long, val size: Long, val offset: Long)

    /** A small entry held in memory. */
    fun add(name: String, bytes: ByteArray) {
        val crc = Crc32().apply { update(bytes) }.value
        add(name, crc, bytes.size.toLong(), Buffer().write(bytes))
    }

    /**
     * Copies exactly [size] bytes from [data] as entry [name]. [crc] must be the CRC-32 of those
     * bytes. [onBytes] reports progress as data is copied.
     */
    fun add(name: String, crc: Long, size: Long, data: Source, onBytes: (Long) -> Unit = {}) {
        check(!finished) { "Archive already finished" }
        val nameBytes = name.encodeToByteArray()
        val zip64 = size >= MAX_32
        val headerOffset = offset
        out.writeIntLe(LOCAL_HEADER)
        out.writeShortLe(if (zip64) VERSION_ZIP64 else VERSION_STORED)
        out.writeShortLe(FLAG_UTF8)
        out.writeShortLe(METHOD_STORED)
        out.writeShortLe(modified.time)
        out.writeShortLe(modified.date)
        out.writeIntLe(crc.toInt())
        out.writeIntLe(if (zip64) -1 else size.toInt())
        out.writeIntLe(if (zip64) -1 else size.toInt())
        out.writeShortLe(nameBytes.size)
        out.writeShortLe(if (zip64) 20 else 0)
        out.write(nameBytes)
        if (zip64) {
            out.writeShortLe(ZIP64_EXTRA)
            out.writeShortLe(16)
            out.writeLongLe(size)
            out.writeLongLe(size)
        }
        offset += 30 + nameBytes.size + (if (zip64) 20 else 0)

        var left = size
        val chunk = Buffer()
        while (left > 0) {
            val n = data.read(chunk, minOf(left, CHUNK))
            if (n == -1L) throw ZipFormatException("$name ended ${left} bytes early")
            out.write(chunk, n)
            left -= n
            onBytes(n)
        }
        offset += size
        entries += Written(nameBytes, crc, size, headerOffset)
    }

    /** Writes the central directory and closes the archive. */
    fun finish() {
        if (finished) return
        finished = true
        val directoryStart = offset
        for (e in entries) {
            val bigSize = e.size >= MAX_32
            val bigOffset = e.offset >= MAX_32
            val extra = (if (bigSize) 16 else 0) + (if (bigOffset) 8 else 0)
            out.writeIntLe(CENTRAL_HEADER)
            out.writeShortLe(MADE_BY_UNIX or VERSION_ZIP64)
            out.writeShortLe(if (bigSize || bigOffset) VERSION_ZIP64 else VERSION_STORED)
            out.writeShortLe(FLAG_UTF8)
            out.writeShortLe(METHOD_STORED)
            out.writeShortLe(modified.time)
            out.writeShortLe(modified.date)
            out.writeIntLe(e.crc.toInt())
            out.writeIntLe(if (bigSize) -1 else e.size.toInt())
            out.writeIntLe(if (bigSize) -1 else e.size.toInt())
            out.writeShortLe(e.name.size)
            out.writeShortLe(if (extra > 0) extra + 4 else 0)
            out.writeShortLe(0) // comment
            out.writeShortLe(0) // disk
            out.writeShortLe(0) // internal attributes
            out.writeIntLe(UNIX_FILE_0644)
            out.writeIntLe(if (bigOffset) -1 else e.offset.toInt())
            out.write(e.name)
            if (extra > 0) {
                out.writeShortLe(ZIP64_EXTRA)
                out.writeShortLe(extra)
                if (bigSize) {
                    out.writeLongLe(e.size)
                    out.writeLongLe(e.size)
                }
                if (bigOffset) out.writeLongLe(e.offset)
            }
            offset += 46 + e.name.size + (if (extra > 0) extra + 4 else 0)
        }
        val directorySize = offset - directoryStart
        val zip64End = entries.size >= 0xFFFF || directoryStart >= MAX_32 || directorySize >= MAX_32
        if (zip64End) {
            val recordOffset = offset
            out.writeIntLe(ZIP64_END)
            out.writeLongLe(44)
            out.writeShortLe(MADE_BY_UNIX or VERSION_ZIP64)
            out.writeShortLe(VERSION_ZIP64)
            out.writeIntLe(0)
            out.writeIntLe(0)
            out.writeLongLe(entries.size.toLong())
            out.writeLongLe(entries.size.toLong())
            out.writeLongLe(directorySize)
            out.writeLongLe(directoryStart)
            out.writeIntLe(ZIP64_LOCATOR)
            out.writeIntLe(0)
            out.writeLongLe(recordOffset)
            out.writeIntLe(1)
        }
        out.writeIntLe(END)
        out.writeShortLe(0)
        out.writeShortLe(0)
        out.writeShortLe(if (zip64End) 0xFFFF else entries.size)
        out.writeShortLe(if (zip64End) 0xFFFF else entries.size)
        out.writeIntLe(if (zip64End) -1 else directorySize.toInt())
        out.writeIntLe(if (zip64End) -1 else directoryStart.toInt())
        out.writeShortLe(0)
        out.flush()
    }

    fun close() = out.close()

    /** Bytes written so far. */
    val size: Long get() = offset
}

/** An MS-DOS timestamp, as ZIP headers store it (local time, 2-second resolution). */
data class DosTime(val date: Int, val time: Int) {
    companion object {
        val EPOCH = DosTime((0 shl 9) or (1 shl 5) or 1, 0)

        fun of(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) = DosTime(
            date = ((year - 1980).coerceIn(0, 127) shl 9) or (month shl 5) or day,
            time = (hour shl 11) or (minute shl 5) or (second / 2),
        )
    }
}

/** One entry's header, as read from the archive. */
class ZipEntry internal constructor(
    val name: String,
    internal val method: Int,
    internal val crc: Long,
    internal val compressedSize: Long,
    /** Uncompressed size, or -1 when only a trailing data descriptor gives it. */
    val size: Long,
    internal val hasDescriptor: Boolean,
    internal val zip64: Boolean,
)

/**
 * Reads a ZIP archive front to back, entry by entry, without needing to seek. That works on any
 * stream a file picker hands over, including ones from cloud providers. Stored and deflated
 * entries are supported, with or without trailing data descriptors (as macOS's Archive Utility
 * writes them), as long as a stored entry's size is in its header.
 */
class ZipStreamReader(source: Source) {
    private val src: BufferedSource = source.buffer()
    private var pending: ZipEntry? = null

    /** The next entry, or null once the central directory is reached. Skips the previous entry's data if it wasn't read. */
    fun next(): ZipEntry? {
        pending?.let { skip(it) }
        if (src.exhausted()) return null
        val signature = src.readIntLe()
        if (signature == CENTRAL_HEADER || signature == END || signature == ZIP64_END) return null
        if (signature != LOCAL_HEADER) throw ZipFormatException("Not a ZIP archive")
        src.readShortLe() // version needed
        val flags = src.readShortLe().toInt() and 0xFFFF
        val method = src.readShortLe().toInt() and 0xFFFF
        src.readShortLe() // time
        src.readShortLe() // date
        val crc = src.readIntLe().toLong() and 0xFFFFFFFFL
        var compressed = src.readIntLe().toLong() and 0xFFFFFFFFL
        var size = src.readIntLe().toLong() and 0xFFFFFFFFL
        val nameLength = src.readShortLe().toInt() and 0xFFFF
        val extraLength = src.readShortLe().toInt() and 0xFFFF
        val name = if (flags and FLAG_UTF8 != 0) src.readUtf8(nameLength.toLong()) else src.readByteArray(nameLength.toLong()).decodeLatin1()
        var zip64 = false
        var extraLeft = extraLength
        while (extraLeft >= 4) {
            val id = src.readShortLe().toInt() and 0xFFFF
            val length = src.readShortLe().toInt() and 0xFFFF
            extraLeft -= 4
            if (id == ZIP64_EXTRA) {
                zip64 = true
                var used = 0
                if (size == MAX_32 && used + 8 <= length) { size = src.readLongLe(); used += 8 }
                if (compressed == MAX_32 && used + 8 <= length) { compressed = src.readLongLe(); used += 8 }
                src.skip((length - used).toLong())
            } else {
                src.skip(length.toLong())
            }
            extraLeft -= length
        }
        if (extraLeft > 0) src.skip(extraLeft.toLong())
        if (flags and FLAG_ENCRYPTED != 0) throw ZipFormatException("$name is encrypted")
        if (method != METHOD_STORED && method != METHOD_DEFLATED) throw ZipFormatException("$name uses an unsupported compression method")
        val descriptor = flags and FLAG_DESCRIPTOR != 0
        if (descriptor && method == METHOD_STORED && compressed == 0L) {
            throw ZipFormatException("$name is stored without its size")
        }
        // A stored entry's sizes must be in its header (checked above); only a deflated entry with a
        // descriptor leaves them to the descriptor.
        val known = !descriptor || method == METHOD_STORED
        val entry = ZipEntry(name, method, crc, if (known) compressed else -1, if (known) size else -1, descriptor, zip64)
        pending = entry
        return entry
    }

    /** Copies [entry]'s data to [sink], checking its CRC and size. [onBytes] gets progress in uncompressed bytes. */
    fun read(entry: ZipEntry, sink: Sink, onBytes: (Long) -> Unit = {}) {
        check(pending === entry) { "Entries must be read in order" }
        pending = null
        val crc = Crc32()
        var written = 0L
        val chunk = ByteArray(CHUNK.toInt())
        val out = Buffer()
        fun emit(bytes: ByteArray, length: Int) {
            crc.update(bytes, 0, length)
            out.write(bytes, 0, length)
            sink.write(out, out.size)
            written += length
            onBytes(length.toLong())
        }
        if (entry.method == METHOD_STORED) {
            var left = entry.compressedSize
            while (left > 0) {
                val n = src.read(chunk, 0, minOf(left, CHUNK).toInt())
                if (n == -1) throw ZipFormatException("${entry.name} is cut short")
                emit(chunk, n)
                left -= n
            }
        } else {
            inflate(entry, ::emit)
        }
        val (expectedCrc, expectedSize) = if (entry.hasDescriptor) readDescriptor(entry) else entry.crc to entry.size
        if (written != expectedSize) throw ZipFormatException("${entry.name} has the wrong size")
        if (crc.value != expectedCrc) throw ZipFormatException("${entry.name} is damaged (CRC mismatch)")
    }

    /** Reads a whole small entry into memory. */
    fun readBytes(entry: ZipEntry, limit: Long = 64L * 1024 * 1024): ByteArray {
        if (entry.size > limit) throw ZipFormatException("${entry.name} is too large")
        val buffer = Buffer()
        read(entry, buffer) { if (buffer.size > limit) throw ZipFormatException("${entry.name} is too large") }
        return buffer.readByteArray()
    }

    fun skip(entry: ZipEntry) {
        if (pending !== entry) return
        if (entry.method == METHOD_STORED && !entry.hasDescriptor) {
            pending = null
            src.skip(entry.compressedSize)
        } else {
            read(entry, okio.blackholeSink())
        }
    }

    private fun inflate(entry: ZipEntry, emit: (ByteArray, Int) -> Unit) {
        val inflater = RawInflater()
        val input = ByteArray(CHUNK.toInt())
        val output = ByteArray(CHUNK.toInt())
        val unknown = entry.hasDescriptor
        var left = entry.compressedSize
        // Input is peeked, not consumed, so whatever the inflater doesn't use stays in the stream:
        // with a data descriptor, nothing else says where the compressed data ends.
        var given = 0
        try {
            while (true) {
                if (inflater.needsInput) {
                    src.skip(given.toLong())
                    if (!unknown) left -= given
                    given = 0
                    if ((!unknown && left <= 0L) || !src.request(1)) throw ZipFormatException("${entry.name} is cut short")
                    val n = (if (unknown) minOf(src.buffer.size, CHUNK) else minOf(src.buffer.size, left, CHUNK)).toInt()
                    src.peek().readFully(input, n)
                    inflater.setInput(input, 0, n)
                    given = n
                }
                val produced = inflater.inflate(output)
                if (produced > 0) emit(output, produced)
                if (inflater.finished) {
                    val used = given - inflater.remaining
                    src.skip(used.toLong())
                    if (!unknown) left -= used
                    break
                }
                if (produced == 0 && !inflater.needsInput) throw ZipFormatException("${entry.name} is damaged")
            }
            if (!unknown && left > 0) src.skip(left)
        } finally {
            inflater.end()
        }
    }

    private fun readDescriptor(entry: ZipEntry): Pair<Long, Long> {
        var first = src.readIntLe()
        if (first == DESCRIPTOR) first = src.readIntLe()
        val crc = first.toLong() and 0xFFFFFFFFL
        val size = if (entry.zip64) {
            src.readLongLe()
            src.readLongLe()
        } else {
            src.readIntLe()
            src.readIntLe().toLong() and 0xFFFFFFFFL
        }
        return crc to size
    }

    fun close() = src.close()
}

private fun BufferedSource.readFully(into: ByteArray, length: Int) {
    var read = 0
    while (read < length) {
        val n = read(into, read, length - read)
        if (n == -1) throw ZipFormatException("Archive is cut short")
        read += n
    }
}

private fun ByteArray.decodeLatin1(): String = CharArray(size) { (this[it].toInt() and 0xFF).toChar() }.concatToString()

private const val LOCAL_HEADER = 0x04034b50
private const val CENTRAL_HEADER = 0x02014b50
private const val END = 0x06054b50
private const val ZIP64_END = 0x06064b50
private const val ZIP64_LOCATOR = 0x07064b50
private const val DESCRIPTOR = 0x08074b50
private const val ZIP64_EXTRA = 0x0001
private const val VERSION_STORED = 20
private const val VERSION_ZIP64 = 45
private const val MADE_BY_UNIX = 3 shl 8
private const val FLAG_ENCRYPTED = 0x0001
private const val FLAG_DESCRIPTOR = 0x0008
private const val FLAG_UTF8 = 0x0800
private const val METHOD_STORED = 0
private const val METHOD_DEFLATED = 8
private const val UNIX_FILE_0644 = (0x81A4 shl 16)
private const val MAX_32 = 0xFFFFFFFFL
private const val CHUNK = 64L * 1024
