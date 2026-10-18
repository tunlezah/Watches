package io.github.tunlezah.watches.data.backup

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.memset
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/** zlib, which ships with iOS, in raw mode (negative window bits). */
@OptIn(ExperimentalForeignApi::class)
actual class RawInflater actual constructor() {
    private val stream = nativeHeap.alloc<z_stream>()
    // zlib keeps a pointer to the input between calls, so it stays pinned until replaced.
    private var input: Pinned<ByteArray>? = null
    private var done = false
    private var ended = false

    init {
        memset(stream.ptr, 0, sizeOf<z_stream>().convert())
        val rc = inflateInit2_(stream.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().convert())
        if (rc != Z_OK) {
            nativeHeap.free(stream)
            throw ZipFormatException("Couldn't start decompression ($rc)")
        }
    }

    actual fun setInput(bytes: ByteArray, offset: Int, length: Int) {
        input?.unpin()
        val pinned = bytes.copyOfRange(offset, offset + length).pin()
        input = pinned
        stream.next_in = if (length > 0) pinned.addressOf(0).reinterpret() else null
        stream.avail_in = length.convert()
    }

    actual fun inflate(out: ByteArray): Int {
        if (out.isEmpty() || done) return 0
        return out.usePinned { pinned ->
            stream.next_out = pinned.addressOf(0).reinterpret()
            stream.avail_out = out.size.convert()
            when (val rc = platform.zlib.inflate(stream.ptr, Z_NO_FLUSH)) {
                Z_STREAM_END -> done = true
                Z_OK, Z_BUF_ERROR -> Unit
                else -> throw ZipFormatException("Compressed data is damaged ($rc)")
            }
            val written = out.size - stream.avail_out.toInt()
            stream.next_out = null
            written
        }
    }

    actual val finished: Boolean get() = done
    actual val needsInput: Boolean get() = stream.avail_in.toInt() == 0
    actual val remaining: Int get() = stream.avail_in.toInt()

    actual fun end() {
        if (ended) return
        ended = true
        inflateEnd(stream.ptr)
        input?.unpin()
        input = null
        nativeHeap.free(stream)
    }
}
