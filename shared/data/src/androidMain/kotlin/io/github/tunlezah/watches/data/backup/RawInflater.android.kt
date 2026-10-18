package io.github.tunlezah.watches.data.backup

import java.util.zip.DataFormatException
import java.util.zip.Inflater

actual class RawInflater actual constructor() {
    private val inflater = Inflater(true)

    actual fun setInput(bytes: ByteArray, offset: Int, length: Int) = inflater.setInput(bytes, offset, length)

    actual fun inflate(out: ByteArray): Int = try {
        inflater.inflate(out)
    } catch (e: DataFormatException) {
        throw ZipFormatException("Compressed data is damaged")
    }

    actual val finished: Boolean get() = inflater.finished()
    actual val needsInput: Boolean get() = inflater.needsInput()
    actual val remaining: Int get() = inflater.remaining
    actual fun end() = inflater.end()
}
