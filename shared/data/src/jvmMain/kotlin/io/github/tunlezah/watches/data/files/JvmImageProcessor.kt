package io.github.tunlezah.watches.data.files

import io.github.tunlezah.watches.data.photos.CodecImageProcessor
import io.github.tunlezah.watches.data.photos.ImageCodec
import io.github.tunlezah.watches.data.photos.ImageInfo
import io.github.tunlezah.watches.data.photos.Orientation
import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.tunlezah.watches.data.photos.PhotoMetadata
import io.github.tunlezah.watches.data.photos.RenderPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * The plain-Java codec, on the JDK's own image readers. It runs the shared tests on any machine.
 * It reads the formats the JDK does (JPEG, PNG, GIF, BMP, TIFF), plus any [decoders] for the
 * formats the JDK can't: on Linux, HEIC and WebP from the system's libheif and libwebp where they
 * are installed ([SystemImageLibraries]). With those, it is the server's codec
 * (docs/SELF_HOSTING_PLAN.md, srv-2). A Mac uses Apple's codec through the bridge instead.
 *
 * The JDK's readers decode a whole picture at once, which a server's memory allows. The system
 * decoders average the picture down while they copy it out, when it is far larger than needed.
 */
class JvmImageCodec(private val decoders: List<PictureDecoder> = SystemImageLibraries.decoders) : ImageCodec {

    override val formats: Set<PhotoFormat> by lazy {
        val names = ImageIO.getReaderFormatNames().map { it.lowercase() }.toSet()
        buildSet {
            if ("jpeg" in names || "jpg" in names) add(PhotoFormat.JPEG)
            if ("png" in names) add(PhotoFormat.PNG)
            if ("gif" in names) add(PhotoFormat.GIF)
            if ("bmp" in names) add(PhotoFormat.BMP)
            if ("tiff" in names || "tif" in names) add(PhotoFormat.TIFF)
            decoders.forEach { add(it.format) }
        }
    }

    /** The decoder for the format [head] starts, when one of [decoders] reads it; null for the JDK's own. */
    private fun decoderFor(head: ByteArray): PictureDecoder? = PhotoFormat.detect(head)?.let { format -> decoders.firstOrNull { it.format == format } }

    override suspend fun probe(file: Path): ImageInfo = withContext(Dispatchers.IO) {
        val source = File(file.toString())
        // The JDK has no metadata reader of its own, so the turning and the location come from the shared one.
        val bytes = source.readBytes()
        decoderFor(bytes)?.let { decoder ->
            val (width, height) = decoder.size(source)
            val orientation = if (decoder.turnsItself) Orientation.NORMAL else PhotoMetadata.orientation(bytes)
            return@withContext ImageInfo(width, height, orientation, hasLocation = PhotoMetadata.hasLocation(bytes))
        }
        val stream = ImageIO.createImageInputStream(source) ?: error("Not a readable image")
        stream.use {
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: error("Not a readable image")
            try {
                reader.input = stream
                // The JDK's readers hand pixels over as stored.
                ImageInfo(reader.getWidth(0), reader.getHeight(0), PhotoMetadata.orientation(bytes), hasLocation = PhotoMetadata.hasLocation(bytes))
            } finally {
                reader.dispose()
            }
        }
    }

    override suspend fun render(source: Path, plan: RenderPlan, quality: Int, dest: Path) = withContext(Dispatchers.Default) {
        val file = File(source.toString())
        val stored = plan.storedRegion
        // The size the stored part ends up, before it is turned upright.
        val (endWidth, endHeight) = if (plan.orientation.swapsAxes) plan.outHeight to plan.outWidth else plan.outWidth to plan.outHeight
        val head = file.inputStream().use { it.readNBytes(PhotoFormat.HEAD_BYTES) }
        val part = decoderFor(head)
            // Averaged down while copied out, to no smaller than needed, so a large photo never sits in memory at full size.
            ?.decode(file, stored, step = maxOf(1, minOf(stored.width / endWidth, stored.height / endHeight)))
            ?: (ImageIO.read(file) ?: error("Not a readable image")).getSubimage(stored.left, stored.top, stored.width, stored.height)
        val reduced = halveTowards(part, endWidth, endHeight)
        val out = BufferedImage(plan.outWidth, plan.outHeight, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            // Transparent areas come out black, as on every platform.
            color = Color.BLACK
            fillRect(0, 0, plan.outWidth, plan.outHeight)
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val (uprightWidth, uprightHeight) = if (plan.orientation.swapsAxes) reduced.height to reduced.width else reduced.width to reduced.height
            val place = AffineTransform.getScaleInstance(plan.outWidth.toDouble() / uprightWidth, plan.outHeight.toDouble() / uprightHeight)
            place.concatenate(upright(plan.orientation, reduced.width.toDouble(), reduced.height.toDouble()))
            drawImage(reduced, place, null)
            dispose()
        }
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality / 100f
        }
        ImageIO.createImageOutputStream(File(dest.toString())).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(out, null, null), params)
            writer.dispose()
        }
    }

    /**
     * Halves [image] for as long as it stays at least twice [width] by [height]. Each halving
     * averages two by two pixels, so a photo shrunk a long way, such as to a thumbnail, keeps its
     * fine detail smooth where one large bilinear step would skip most of the pixels.
     */
    private fun halveTowards(image: BufferedImage, width: Int, height: Int): BufferedImage {
        var current = image
        while (current.width >= 2 * width && current.height >= 2 * height) {
            val type = if (current.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
            val half = BufferedImage(current.width / 2, current.height / 2, type)
            half.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(current, 0, 0, half.width, half.height, null)
                dispose()
            }
            current = half
        }
        return current
    }

    /** Takes a point of the stored part, [w] wide and [h] high, to where it belongs in the upright part. */
    private fun upright(orientation: Orientation, w: Double, h: Double): AffineTransform = when (orientation) {
        Orientation.NORMAL -> AffineTransform()
        Orientation.FLIP_HORIZONTAL -> AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0)
        Orientation.ROTATE_180 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h)
        Orientation.FLIP_VERTICAL -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h)
        Orientation.TRANSPOSE -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
        Orientation.ROTATE_90 -> AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0)
        Orientation.TRANSVERSE -> AffineTransform(0.0, -1.0, -1.0, 0.0, h, w)
        Orientation.ROTATE_270 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w)
    }
}

/**
 * The shared photo rules on the plain-Java codec: what the tests use, and the desktop app wherever
 * there is no Mac bridge. With the system's HEIC decoder (the server image), that app takes photos in.
 */
class JvmImageProcessor : CodecImageProcessor(JvmImageCodec())
