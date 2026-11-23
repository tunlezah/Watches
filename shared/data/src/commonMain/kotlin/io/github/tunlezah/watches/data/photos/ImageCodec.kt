package io.github.tunlezah.watches.data.photos

import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.data.files.ImageProcessor
import io.github.tunlezah.watches.data.files.ImageSize
import okio.FileSystem
import okio.Path

/** What a picture file holds, read from its header without decoding the picture. */
data class ImageInfo(
    /** The stored pixel grid, before the picture is turned upright. */
    val storedWidth: Int,
    val storedHeight: Int,
    /** How this platform's decoder hands the pixels over: [Orientation.NORMAL] if it has already turned them. */
    val orientation: Orientation,
    /** Whether this platform's own metadata reader finds a location in the file. */
    val hasLocation: Boolean = false,
)

/**
 * The part of photo handling that has to be a platform's own: reading a file's size and turning,
 * and decoding pixels. Everything else (which formats are kept, what a crop means in pixels, how
 * large a rendition is) is shared code, so the platforms can't drift apart. See
 * docs/MACOS_PLAN.md, section 5.
 *
 * A codec never holds a full-size bitmap longer than it must, and works off the main thread.
 */
interface ImageCodec {

    /**
     * The formats this platform's decoders read. No system reads them all: Android has no TIFF
     * decoder, plain Java none for WebP or HEIC, and AVIF depends on the system's version.
     */
    val formats: Set<PhotoFormat>

    /** Throws if [file] isn't a picture this platform can decode. */
    suspend fun probe(file: Path): ImageInfo

    /**
     * Cuts [plan]'s region from [source], turns it upright, scales it to exactly the plan's output
     * size and writes it to [dest] as a JPEG in sRGB with no metadata. [dest]'s folder exists.
     */
    suspend fun render(source: Path, plan: RenderPlan, quality: Int, dest: Path)
}

/** The shared [ImageProcessor]: the rules, on top of a platform's [ImageCodec]. */
open class CodecImageProcessor(
    private val codec: ImageCodec,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : ImageProcessor {

    override val readableFormats: Set<PhotoFormat> get() = codec.formats

    override suspend fun size(file: Path): ImageSize {
        val info = codec.probe(file)
        return if (info.orientation.swapsAxes) ImageSize(info.storedHeight, info.storedWidth) else ImageSize(info.storedWidth, info.storedHeight)
    }

    override suspend fun hasLocation(file: Path): Boolean = codec.probe(file).hasLocation

    override suspend fun render(source: Path, crop: NormalizedRect?, maxEdge: Int, quality: Int, dest: Path): ImageSize {
        val info = codec.probe(source)
        val plan = RenderPlan.of(info.storedWidth, info.storedHeight, info.orientation, crop, maxEdge)
        dest.parent?.let { fileSystem.createDirectories(it) }
        codec.render(source, plan, quality, dest)
        return ImageSize(plan.outWidth, plan.outHeight)
    }
}
