package io.github.tunlezah.watches.ui.backup

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.source
import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout

/*
 * FileKit hands out kotlinx-io streams for the files the system pickers return; the backup code
 * uses okio. These adapters copy between the two in 64 KB chunks, so memory stays flat however
 * large the archive is.
 */

/** Streams into a file the system "save as" dialog returned. */
fun PlatformFile.okioSink(): Sink = KxSinkAdapter(sink())

/** Streams out of a file the system picker returned. */
fun PlatformFile.okioSource(): Source = KxSourceAdapter(source())

private class KxSinkAdapter(private val raw: kotlinx.io.RawSink) : Sink {
    private val chunk = ByteArray(CHUNK)
    private val staging = kotlinx.io.Buffer()

    override fun write(source: Buffer, byteCount: Long) {
        var left = byteCount
        while (left > 0) {
            val n = source.read(chunk, 0, minOf(left, CHUNK.toLong()).toInt())
            if (n <= 0) break
            staging.write(chunk, 0, n)
            raw.write(staging, staging.size)
            left -= n
        }
    }

    override fun flush() = raw.flush()
    override fun timeout() = Timeout.NONE
    override fun close() = raw.close()
}

private class KxSourceAdapter(private val raw: kotlinx.io.RawSource) : Source {
    private val chunk = ByteArray(CHUNK)
    private val staging = kotlinx.io.Buffer()

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (staging.size == 0L) {
            val got = raw.readAtMostTo(staging, CHUNK.toLong())
            if (got == -1L) return -1
        }
        val n = staging.readAtMostTo(chunk, 0, minOf(byteCount, staging.size, CHUNK.toLong()).toInt())
        sink.write(chunk, 0, n)
        return n.toLong()
    }

    override fun timeout() = Timeout.NONE
    override fun close() = raw.close()
}

private const val CHUNK = 64 * 1024
