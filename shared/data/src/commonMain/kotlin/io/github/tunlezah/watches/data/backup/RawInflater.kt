package io.github.tunlezah.watches.data.backup

/** Raw DEFLATE decompression (no zlib header), as ZIP entries use it. */
expect class RawInflater() {
    /** Hands over the next compressed bytes. Only call when [needsInput]. */
    fun setInput(bytes: ByteArray, offset: Int, length: Int)

    /** Decompresses into [out] and returns how many bytes were written. */
    fun inflate(out: ByteArray): Int

    /** The end of the compressed stream has been reached. */
    val finished: Boolean

    val needsInput: Boolean

    /** Input bytes handed over but not used yet. */
    val remaining: Int

    fun end()
}
