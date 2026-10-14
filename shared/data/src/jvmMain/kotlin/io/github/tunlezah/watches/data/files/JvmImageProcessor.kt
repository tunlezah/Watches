package io.github.tunlezah.watches.data.files

import io.github.tunlezah.watches.core.model.NormalizedRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/** Desktop JVM implementation, used by tests. It ignores EXIF orientation. */
class JvmImageProcessor : ImageProcessor {

    override suspend fun size(file: Path): ImageSize = withContext(Dispatchers.IO) {
        val img = ImageIO.read(File(file.toString())) ?: error("Not a readable image")
        ImageSize(img.width, img.height)
    }

    override suspend fun stripLocation(file: Path) = Unit

    override suspend fun render(source: Path, crop: NormalizedRect?, maxEdge: Int, quality: Int, dest: Path): ImageSize =
        withContext(Dispatchers.Default) {
            val img = ImageIO.read(File(source.toString())) ?: error("Not a readable image")
            val c = crop ?: NormalizedRect.Full
            val x = (c.left * img.width).roundToInt()
            val y = (c.top * img.height).roundToInt()
            val w = (c.width * img.width).roundToInt().coerceIn(1, img.width - x)
            val h = (c.height * img.height).roundToInt().coerceIn(1, img.height - y)
            val scale = minOf(1.0, maxEdge.toDouble() / max(w, h))
            val outW = (w * scale).roundToInt().coerceAtLeast(1)
            val outH = (h * scale).roundToInt().coerceAtLeast(1)
            val out = BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB)
            out.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(img, 0, 0, outW, outH, x, y, x + w, y + h, null)
                dispose()
            }
            dest.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
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
            ImageSize(outW, outH)
        }
}
