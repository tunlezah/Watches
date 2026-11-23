package io.github.tunlezah.watches.data.photos

import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.ImageProcessor
import io.github.tunlezah.watches.data.files.findsLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.Source
import okio.buffer
import okio.use

/**
 * The one way a picture gets into the app's files (docs/MACOS_PLAN.md, 5.2), whatever brought it:
 * the photo library, the camera, a file dropped on the Mac, a picture from Lookup or a
 * photographed document. Each goes through the same steps.
 *
 * 1. It is streamed into the file store, so a large file is never held in memory to get there.
 * 2. Its format is told from its bytes, not its name.
 * 3. If that is a format this kind of picture is kept in, its location is overwritten where it
 *    lies and the file is kept as it arrived. The platform's own reader is then asked whether it
 *    still finds a location.
 * 4. Otherwise, or if step 3 can't be sure the location has gone, the picture is re-made as a JPEG
 *    by this platform's codec. A JPEG made that way holds nothing but the picture.
 *
 * What is left is one file with no location in it, in a format every device the app runs on can
 * show. A file this device can't decode fails, and leaves nothing behind.
 */
class PhotoIntake(private val files: FileStore, private val processor: ImageProcessor) {

    /** How one kind of picture is kept: a watch's photo, or a photographed document. */
    class Rule(
        /** The formats kept as they arrive. Anything else is re-made as a JPEG. */
        val kept: Set<PhotoFormat>,
        /** The longest edge, and the quality, of a JPEG that is re-made. */
        val jpegEdge: Int,
        val jpegQuality: Int,
        /**
         * Whether a file in no format the app knows is still offered to this device's decoders,
         * which read more than the app can name. If not, it is refused.
         */
        val tryUnknown: Boolean,
    )

    /** What [store] left in the file store. */
    class Stored(
        val path: String,
        val format: PhotoFormat,
        /** True if this is a JPEG made from the picture, not the file that arrived. */
        val remade: Boolean,
    )

    /**
     * Reads [source] to its end, closes it, and leaves the picture at `[dir]/[name].<extension>`.
     * Throws if it isn't a picture this device can keep; nothing is left behind then.
     */
    suspend fun store(source: Source, dir: String, name: String, rule: Rule): Stored {
        val incoming = "$dir/$name-incoming"
        try {
            val format = PhotoFormat.detect(receive(source, incoming))
            if (format != null && format in rule.kept) {
                keep(incoming, "$dir/$name.${format.extension}")?.let { return Stored(it, format, remade = false) }
            }
            require(format != null || rule.tryUnknown) { "Not a picture" }
            val jpeg = "$dir/$name.jpg"
            try {
                processor.render(files.resolve(incoming), null, rule.jpegEdge, rule.jpegQuality, files.resolve(jpeg))
            } catch (e: Throwable) {
                withContext(NonCancellable) { files.delete(jpeg) }
                throw e
            }
            return Stored(jpeg, PhotoFormat.JPEG, remade = true)
        } finally {
            withContext(NonCancellable) { files.delete(incoming) }
        }
    }

    /** Streams [source] into the file store and returns the file's first bytes, enough to tell its format. */
    private suspend fun receive(source: Source, to: String): ByteArray = withContext(Dispatchers.IO) {
        source.buffer().use { input ->
            input.request(PhotoFormat.HEAD_BYTES.toLong())
            val head = input.peek().readByteArray(minOf(PhotoFormat.HEAD_BYTES.toLong(), input.buffer.size))
            files.sink(to).buffer().use { it.writeAll(input) }
            head
        }
    }

    /**
     * The file as it arrived, at [path], once its location has gone. Null, with nothing left at
     * [path], if that can't be had: its metadata can't be followed far enough to be sure, the
     * platform's own reader still finds a location afterwards, or it is too large to clean.
     */
    private suspend fun keep(incoming: String, path: String): String? {
        // The location is overwritten in the file's bytes, in memory. A picture too large for that is re-made.
        if (files.size(incoming) > CLEANED_IN_MEMORY) return null
        val bytes = files.read(incoming)
        if (PhotoMetadata.removeLocation(bytes) == LocationRemoval.UNREADABLE) return null
        try {
            files.write(path, bytes)
            // A file that isn't a picture at all says nothing here, and is found out by whoever measures it next.
            if (!processor.findsLocation(files.resolve(path))) return path
        } catch (e: Throwable) {
            withContext(NonCancellable) { files.delete(path) }
            throw e
        }
        withContext(NonCancellable) { files.delete(path) }
        return null
    }

    private companion object {
        /** A phone's largest photos are a tenth of this. */
        const val CLEANED_IN_MEMORY = 128L * 1024 * 1024
    }
}
