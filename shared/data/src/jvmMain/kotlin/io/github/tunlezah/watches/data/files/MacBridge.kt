package io.github.tunlezah.watches.data.files

import com.sun.jna.Library
import com.sun.jna.Native
import io.github.tunlezah.watches.data.photos.CodecImageProcessor
import io.github.tunlezah.watches.data.photos.ImageCodec
import io.github.tunlezah.watches.data.photos.ImageInfo
import io.github.tunlezah.watches.data.photos.Orientation
import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.tunlezah.watches.data.photos.RenderPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import java.io.File

/**
 * The Mac bridge, from the Java side (docs/MACOS_PLAN.md, 8.2.6).
 *
 * The desktop app runs on Java, which can't call the Mac's own frameworks. On a Mac it loads a small
 * native library instead (the macBridge module) and decodes photos with the very code the iPhone
 * runs. Where no library is named (Linux, where the tests and screenshots run) there is no bridge
 * and the plain-Java codec stands in.
 */
object MacBridge {

    /** Must equal the library's own number: its functions are called by position, so a mismatch isn't survivable. */
    const val VERSION = 1

    /** The system property that names the library file. The packaged app sets it at start-up; the Mac's tests are given it. */
    const val PROPERTY = "watches.bridge"

    /** The library file this run was told to use, if any. */
    fun path(): String? = System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() }

    private val loaded: Result<BridgeLibrary?> by lazy { runCatching { path()?.let(::load) } }

    /** True once the library named by [path] is loaded and answers. */
    val isLoaded: Boolean get() = loaded.getOrNull() != null

    /** Why the named library couldn't be used, if it couldn't. */
    val problem: Throwable? get() = loaded.exceptionOrNull()

    /**
     * Apple's codec through the bridge, or null where no library was named. Throws if one was named
     * and can't be used, so a Mac's tests can't pass on the plain-Java codec by accident.
     */
    fun imageCodec(): ImageCodec? = loaded.getOrThrow()?.let(::BridgeImageCodec)

    /** The image code this run should use: Apple's where the bridge loaded, plain Java's otherwise. Never throws. */
    fun imageProcessor(): ImageProcessor = loaded.getOrNull()?.let { CodecImageProcessor(BridgeImageCodec(it)) } ?: JvmImageProcessor()

    private fun load(path: String): BridgeLibrary {
        check(File(path).isFile) { "No Mac bridge at $path" }
        val library = Native.load(path, BridgeLibrary::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
        val version = library.watches_bridge_version()
        check(version == VERSION) { "The Mac bridge at $path is version $version; this build needs $VERSION" }
        return library
    }
}

/**
 * The library's functions, exactly as macBridge's Bridge.kt declares them. Each returns 0, or 1
 * with the reason written into `error` as UTF-8 text ending in a zero byte.
 */
@Suppress("FunctionName")
internal interface BridgeLibrary : Library {
    fun watches_bridge_version(): Int
    fun watches_photo_formats(): Int
    fun watches_photo_probe(path: String, out: IntArray, error: ByteArray, errorSize: Int): Int
    fun watches_photo_render(source: String, plan: IntArray, quality: Int, dest: String, error: ByteArray, errorSize: Int): Int
}

/** [ImageCodec] on the bridge: every call is Apple's codec, in the native library. */
internal class BridgeImageCodec(private val library: BridgeLibrary) : ImageCodec {

    // One bit for each format the Mac reads, in the order PhotoFormat lists them.
    override val formats: Set<PhotoFormat> by lazy {
        val bits = library.watches_photo_formats()
        PhotoFormat.entries.filter { bits and (1 shl it.ordinal) != 0 }.toSet()
    }

    override suspend fun probe(file: Path): ImageInfo = withContext(Dispatchers.IO) {
        val out = IntArray(4)
        call { error -> library.watches_photo_probe(file.toString(), out, error, error.size) }
        ImageInfo(out[0], out[1], Orientation.fromExif(out[2]), hasLocation = out[3] != 0)
    }

    override suspend fun render(source: Path, plan: RenderPlan, quality: Int, dest: Path) = withContext(Dispatchers.IO) {
        call { error -> library.watches_photo_render(source.toString(), plan.toInts(), quality, dest.toString(), error, error.size) }
    }

    private inline fun call(function: (ByteArray) -> Int) {
        val error = ByteArray(512)
        if (function(error) == 0) return
        val end = error.indexOf(0).let { if (it < 0) error.size else it }
        error(String(error, 0, end, Charsets.UTF_8).ifEmpty { "The Mac's image code failed" })
    }
}
