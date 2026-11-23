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
 * The plain-Java codec, on the JDK's own image readers. It runs the shared tests on any machine,
 * and it is what the desktop app has until the Mac's own codec arrives (docs/MACOS_PLAN.md, 8.2.5).
 * It reads the formats the JDK does (JPEG, PNG, GIF, BMP, TIFF, but not WebP or HEIC) and decodes
 * the whole picture at once, which is why the Mac doesn't take photos in yet.
 */
class JvmImageCodec : ImageCodec {

    override val formats: Set<PhotoFormat> by lazy {
        val names = ImageIO.getReaderFormatNames().map { it.lowercase() }.toSet()
        buildSet {
            if ("jpeg" in names || "jpg" in names) add(PhotoFormat.JPEG)
            if ("png" in names) add(PhotoFormat.PNG)
            if ("gif" in names) add(PhotoFormat.GIF)
            if ("bmp" in names) add(PhotoFormat.BMP)
            if ("tiff" in names || "tif" in names) add(PhotoFormat.TIFF)
        }
    }

    override suspend fun probe(file: Path): ImageInfo = withContext(Dispatchers.IO) {
        val stream = ImageIO.createImageInputStream(File(file.toString())) ?: error("Not a readable image")
        stream.use {
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: error("Not a readable image")
            try {
                reader.input = stream
                // The JDK's readers hand pixels over as stored, and the JDK has no metadata reader of its
                // own, so the turning and the location both come from the shared one.
                val bytes = File(file.toString()).readBytes()
                ImageInfo(reader.getWidth(0), reader.getHeight(0), PhotoMetadata.orientation(bytes), hasLocation = PhotoMetadata.hasLocation(bytes))
            } finally {
                reader.dispose()
            }
        }
    }

    override suspend fun render(source: Path, plan: RenderPlan, quality: Int, dest: Path) = withContext(Dispatchers.Default) {
        val image = ImageIO.read(File(source.toString())) ?: error("Not a readable image")
        val stored = plan.storedRegion
        val part = image.getSubimage(stored.left, stored.top, stored.width, stored.height)
        val out = BufferedImage(plan.outWidth, plan.outHeight, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            // Transparent areas come out black, as on every platform.
            color = Color.BLACK
            fillRect(0, 0, plan.outWidth, plan.outHeight)
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val place = AffineTransform.getScaleInstance(plan.outWidth.toDouble() / plan.region.width, plan.outHeight.toDouble() / plan.region.height)
            place.concatenate(upright(plan.orientation, stored.width.toDouble(), stored.height.toDouble()))
            drawImage(part, place, null)
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

/** The shared photo rules on the plain-Java codec: what the tests and, for now, the desktop app use. */
class JvmImageProcessor : CodecImageProcessor(JvmImageCodec())
