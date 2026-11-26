package io.github.tunlezah.watches.data.files

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.tunlezah.watches.data.photos.PixelRect
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File

/**
 * A decoder for a format the JDK can't read, which the plain-Java codec ([JvmImageCodec]) uses for
 * that format.
 */
interface PictureDecoder {
    val format: PhotoFormat

    /**
     * True if the pictures it hands over are already upright (a HEIC's own turning applied), so
     * EXIF's word doesn't count. False if they come as stored, and EXIF says how to turn them.
     */
    val turnsItself: Boolean

    /** The picture's width and height as [decode] hands it over. Throws if [file] can't be read. */
    fun size(file: File): Pair<Int, Int>

    /**
     * [region] of the picture, as [size] describes it, with each [step] by [step] block of pixels
     * averaged into one. Opaque: transparent areas are laid over black.
     */
    fun decode(file: File, region: PixelRect, step: Int): BufferedImage
}

/**
 * HEIC and WebP on Linux, from the system's own libheif and libwebp (docs/SELF_HOSTING_PLAN.md,
 * srv-2). The JDK reads neither. The server image carries both, so the server takes photos in as
 * the phones do. Anywhere without them the plain-Java codec reads only what the JDK does. Like the
 * Mac bridge, they're reached through JNA.
 */
object SystemImageLibraries {

    /**
     * `required` (the server image and Linux CI set it): a library that doesn't load fails the
     * self-test and the tests, rather than the codec quietly reading less. `off`: they aren't
     * used, to check the plain-Java fallback on a machine that has them. Unset: used where present.
     */
    const val ENV = "WATCHES_SYSTEM_IMAGE_LIBS"

    val required: Boolean get() = System.getenv(ENV) == "required"

    private val heicResult: Result<PictureDecoder> by lazy { runCatching { allowed(); HeicDecoder.load() } }
    private val webpResult: Result<PictureDecoder> by lazy { runCatching { allowed(); WebpDecoder.load() } }

    /** libheif's HEIC decoder, or null where libheif or its HEVC plugin isn't installed. */
    val heic: PictureDecoder? get() = heicResult.getOrNull()

    /** libwebp's decoder, or null where it isn't installed. */
    val webp: PictureDecoder? get() = webpResult.getOrNull()

    /** The decoders that loaded. */
    val decoders: List<PictureDecoder> get() = listOfNotNull(heic, webp)

    /** Why each library that didn't load couldn't be used. */
    val problems: List<Throwable> get() = listOfNotNull(heicResult.exceptionOrNull(), webpResult.exceptionOrNull())

    private fun allowed() {
        check(System.getenv(ENV) != "off") { "$ENV is off" }
        // A Mac has its own image code through the bridge, and nothing here is tried elsewhere.
        check(System.getProperty("os.name").orEmpty().startsWith("Linux")) { "The system's image libraries are used on Linux only" }
    }
}

/** Loads the first of [names] that the system has. A versioned name comes first: the plain one exists only with a library's development files. */
private inline fun <reified T : Library> loadLibrary(vararg names: String): T {
    var failure: Throwable? = null
    for (name in names) {
        try {
            return Native.load(name, T::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
        } catch (e: UnsatisfiedLinkError) {
            failure = e
        }
    }
    throw failure ?: UnsatisfiedLinkError(names.joinToString())
}

/**
 * Copies [region] of an interleaved 8-bit RGB or RGBA picture into an opaque image, averaging each
 * [step] by [step] block into one pixel. [readRow] fills its array with row `y` of the region: its
 * pixels from `region.left` to `region.right`.
 */
private fun averageRegion(region: PixelRect, channels: Int, step: Int, readRow: (y: Int, into: ByteArray) -> Unit): BufferedImage {
    require(step >= 1) { "Step $step" }
    val outWidth = (region.width + step - 1) / step
    val outHeight = (region.height + step - 1) / step
    val out = BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB)
    val pixels = (out.raster.dataBuffer as DataBufferInt).data
    val row = ByteArray(region.width * channels)
    val red = LongArray(outWidth)
    val green = LongArray(outWidth)
    val blue = LongArray(outWidth)
    val count = IntArray(outWidth)
    for (oy in 0 until outHeight) {
        red.fill(0); green.fill(0); blue.fill(0); count.fill(0)
        val top = region.top + oy * step
        for (y in top until minOf(top + step, region.bottom)) {
            readRow(y, row)
            for (x in 0 until region.width) {
                val i = x * channels
                val alpha = if (channels == 4) row[i + 3].toInt() and 0xFF else 255
                val o = x / step
                // Laid over black: each colour counts for as much as it is opaque.
                red[o] += (row[i].toInt() and 0xFF) * alpha / 255
                green[o] += (row[i + 1].toInt() and 0xFF) * alpha / 255
                blue[o] += (row[i + 2].toInt() and 0xFF) * alpha / 255
                count[o]++
            }
        }
        for (o in 0 until outWidth) {
            val n = count[o]
            pixels[oy * outWidth + o] = ((red[o] / n).toInt() shl 16) or ((green[o] / n).toInt() shl 8) or (blue[o] / n).toInt()
        }
    }
    return out
}

private fun PixelRect.requireInside(width: Int, height: Int) =
    require(left >= 0 && top >= 0 && right <= width && bottom <= height) { "$this isn't inside a picture $width by $height" }

/**
 * libheif's C interface, as `libheif/heif.h` (1.17) declares it. Only what the decoder needs.
 * The enum values are the header's.
 */
@Suppress("FunctionName")
private interface LibHeif : Library {
    fun heif_init(params: Pointer?): HeifError.ByValue
    fun heif_have_decoder_for_format(format: Int): Int
    fun heif_context_alloc(): Pointer?
    fun heif_context_free(context: Pointer)
    fun heif_context_read_from_file(context: Pointer, filename: String, options: Pointer?): HeifError.ByValue
    fun heif_context_get_primary_image_handle(context: Pointer, handle: PointerByReference): HeifError.ByValue
    fun heif_image_handle_get_width(handle: Pointer): Int
    fun heif_image_handle_get_height(handle: Pointer): Int
    fun heif_image_handle_has_alpha_channel(handle: Pointer): Int
    fun heif_image_handle_release(handle: Pointer)
    fun heif_decode_image(handle: Pointer, image: PointerByReference, colorspace: Int, chroma: Int, options: Pointer?): HeifError.ByValue
    fun heif_image_get_width(image: Pointer, channel: Int): Int
    fun heif_image_get_height(image: Pointer, channel: Int): Int
    fun heif_image_get_bits_per_pixel_range(image: Pointer, channel: Int): Int
    fun heif_image_get_plane_readonly(image: Pointer, channel: Int, stride: IntByReference): Pointer?
    fun heif_image_release(image: Pointer)

    companion object {
        const val COMPRESSION_HEVC = 1
        const val COLORSPACE_RGB = 1
        const val CHROMA_INTERLEAVED_RGB = 10
        const val CHROMA_INTERLEAVED_RGBA = 11
        const val CHANNEL_INTERLEAVED = 10
    }
}

/** `struct heif_error`, which libheif returns by value: a code (0 is success), a subcode and a message that is never null. */
@Structure.FieldOrder("code", "subcode", "message")
internal open class HeifError : Structure() {
    @JvmField var code: Int = 0

    @JvmField var subcode: Int = 0

    @JvmField var message: String? = null

    class ByValue : HeifError(), Structure.ByValue
}

private fun HeifError.orThrow(what: String) = check(code == 0) { "$what: ${message ?: "libheif error $code.$subcode"}" }

/**
 * A HEIC through libheif. libheif applies the container's own cut and turning (`clap`, `irot`,
 * `imir`) as it decodes and ignores EXIF's, which is what the shared rules say a HEIC means. So the
 * size it reports is the upright picture's, and the pictures it hands over need no more turning.
 */
private class HeicDecoder private constructor(private val lib: LibHeif) : PictureDecoder {

    override val format = PhotoFormat.HEIC
    override val turnsItself = true

    override fun size(file: File): Pair<Int, Int> = withPrimaryImage(file) { handle ->
        lib.heif_image_handle_get_width(handle) to lib.heif_image_handle_get_height(handle)
    }

    override fun decode(file: File, region: PixelRect, step: Int): BufferedImage = withPrimaryImage(file) { handle ->
        val alpha = lib.heif_image_handle_has_alpha_channel(handle) != 0
        val decoded = PointerByReference()
        lib.heif_decode_image(
            handle, decoded, LibHeif.COLORSPACE_RGB,
            if (alpha) LibHeif.CHROMA_INTERLEAVED_RGBA else LibHeif.CHROMA_INTERLEAVED_RGB, null,
        ).orThrow("Not a readable HEIC")
        val image = decoded.value
        try {
            val channel = LibHeif.CHANNEL_INTERLEAVED
            check(lib.heif_image_get_bits_per_pixel_range(image, channel) == 8) { "Not an 8-bit picture" }
            region.requireInside(lib.heif_image_get_width(image, channel), lib.heif_image_get_height(image, channel))
            val stride = IntByReference()
            val plane = lib.heif_image_get_plane_readonly(image, channel, stride) ?: error("libheif gave no pixels")
            val channels = if (alpha) 4 else 3
            averageRegion(region, channels, step) { y, row -> plane.read(y.toLong() * stride.value + region.left.toLong() * channels, row, 0, row.size) }
        } finally {
            lib.heif_image_release(image)
        }
    }

    private inline fun <T> withPrimaryImage(file: File, block: (Pointer) -> T): T {
        val context = lib.heif_context_alloc() ?: error("libheif couldn't start")
        try {
            lib.heif_context_read_from_file(context, file.path, null).orThrow("Not a readable HEIC")
            val handle = PointerByReference()
            lib.heif_context_get_primary_image_handle(context, handle).orThrow("Not a readable HEIC")
            try {
                return block(handle.value)
            } finally {
                lib.heif_image_handle_release(handle.value)
            }
        } finally {
            lib.heif_context_free(context)
        }
    }

    companion object {
        fun load(): HeicDecoder {
            val lib = loadLibrary<LibHeif>("libheif.so.1", "heif")
            // Loads the decoder plugins, which is where HEVC comes from on Debian and Ubuntu.
            lib.heif_init(null).orThrow("libheif didn't start")
            check(lib.heif_have_decoder_for_format(LibHeif.COMPRESSION_HEVC) != 0) { "libheif has no HEVC decoder: install libheif-plugin-libde265" }
            return HeicDecoder(lib)
        }
    }
}

/** libwebp's simple decoding interface, as `webp/decode.h` declares it. `size_t` is 64 bits on the Linux this runs on. */
@Suppress("FunctionName")
private interface LibWebp : Library {
    fun WebPGetInfo(data: ByteArray, size: Long, width: IntByReference, height: IntByReference): Int
    fun WebPDecodeRGBA(data: ByteArray, size: Long, width: IntByReference, height: IntByReference): Pointer?
    fun WebPFree(pointer: Pointer)
}

/** A WebP through libwebp, which hands its pixels over as stored: EXIF, if any, says how to turn them, as for a JPEG. */
private class WebpDecoder private constructor(private val lib: LibWebp) : PictureDecoder {

    override val format = PhotoFormat.WEBP
    override val turnsItself = false

    override fun size(file: File): Pair<Int, Int> {
        val bytes = file.readBytes()
        val width = IntByReference()
        val height = IntByReference()
        check(lib.WebPGetInfo(bytes, bytes.size.toLong(), width, height) != 0) { "Not a readable WebP" }
        return width.value to height.value
    }

    override fun decode(file: File, region: PixelRect, step: Int): BufferedImage {
        val bytes = file.readBytes()
        val width = IntByReference()
        val height = IntByReference()
        val pixels = lib.WebPDecodeRGBA(bytes, bytes.size.toLong(), width, height) ?: error("Not a readable WebP")
        try {
            region.requireInside(width.value, height.value)
            val stride = width.value.toLong() * 4
            return averageRegion(region, 4, step) { y, row -> pixels.read(y * stride + region.left * 4L, row, 0, row.size) }
        } finally {
            lib.WebPFree(pixels)
        }
    }

    companion object {
        fun load(): WebpDecoder = WebpDecoder(loadLibrary<LibWebp>("libwebp.so.7", "webp"))
    }
}
