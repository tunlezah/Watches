package io.github.tunlezah.watches.data.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import io.github.tunlezah.watches.data.photos.CodecImageProcessor
import io.github.tunlezah.watches.data.photos.HeicLayout
import io.github.tunlezah.watches.data.photos.ImageCodec
import io.github.tunlezah.watches.data.photos.ImageInfo
import io.github.tunlezah.watches.data.photos.Orientation
import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.tunlezah.watches.data.photos.PhotoMetadata
import io.github.tunlezah.watches.data.photos.RenderPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Android's codec, on the system's decoders. What to cut and how large to write come from the
 * shared [RenderPlan]; this only reads the file's size and turning, decodes the region, and writes
 * the JPEG. It decodes just the region it needs, subsampled, so a small crop of a large photo never
 * loads the whole picture.
 */
class AndroidImageCodec : ImageCodec {

    // Android has no TIFF decoder, and read AVIF from version 12.
    override val formats: Set<PhotoFormat> = buildSet {
        addAll(listOf(PhotoFormat.JPEG, PhotoFormat.PNG, PhotoFormat.WEBP, PhotoFormat.HEIC, PhotoFormat.GIF, PhotoFormat.BMP))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(PhotoFormat.AVIF)
    }

    /** A file's picture as the app sees it, and where that lies in what the decoder hands over. */
    private class Frame(val info: ImageInfo, val left: Int, val top: Int)

    private fun frame(path: String): Frame {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not a readable image" }
        // BitmapFactory hands pixels over as stored, so the turning is read separately.
        val exif = runCatching { ExifInterface(path) }.getOrNull()
        val located = exif?.latLong != null
        val layout = heicLayout(path)
        if (layout == null) {
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL
            return Frame(ImageInfo(bounds.outWidth, bounds.outHeight, Orientation.fromExif(orientation), located), 0, 0)
        }
        // A HEIC says how it is turned in its container, and EXIF may repeat it, leave it out or disagree.
        // The container is the one that counts, so it is read by the shared code and not through EXIF.
        // It can also say that only part of the coded picture is the picture, which Android's decoder
        // ignores: where the decoder has handed over the whole coded picture, the part is cut here.
        val crop = layout.crop?.takeIf { bounds.outWidth == layout.codedWidth && bounds.outHeight == layout.codedHeight }
        return Frame(ImageInfo(crop?.width ?: bounds.outWidth, crop?.height ?: bounds.outHeight, layout.orientation, located), crop?.left ?: 0, crop?.top ?: 0)
    }

    private fun heicLayout(path: String): HeicLayout? = runCatching {
        if (PhotoFormat.detect(start(path, PhotoFormat.HEAD_BYTES)) != PhotoFormat.HEIC) return null
        PhotoMetadata.heicLayout(start(path, PhotoMetadata.HEIC_HEAD_BYTES))
    }.getOrNull()

    /** The first [limit] bytes of a file, or all of it if it is shorter. */
    private fun start(path: String, limit: Int): ByteArray = File(path).inputStream().use { stream ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val read = stream.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }

    override suspend fun probe(file: Path): ImageInfo = withContext(Dispatchers.IO) { frame(file.toString()).info }

    override suspend fun render(source: Path, plan: RenderPlan, quality: Int, dest: Path) = withContext(Dispatchers.Default) {
        val path = source.toString()
        val frame = frame(path)
        val stored = plan.storedRegion
        val region = Rect(frame.left + stored.left, frame.top + stored.top, frame.left + stored.right, frame.top + stored.bottom)
        val options = BitmapFactory.Options().apply {
            inSampleSize = plan.sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            // A wide-gamut photo (an iPhone's are Display P3) is converted as it's decoded, so a rendition is always sRGB.
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val decoded = decodeRegion(path, region, options)
            ?: BitmapFactory.decodeFile(path, options)?.let { full ->
                // Region decoding isn't available for every format: decode subsampled, then cut.
                val sample = options.inSampleSize
                val left = (region.left / sample).coerceIn(0, full.width - 1)
                val top = (region.top / sample).coerceIn(0, full.height - 1)
                val width = (region.width() / sample).coerceIn(1, full.width - left)
                val height = (region.height() / sample).coerceIn(1, full.height - top)
                Bitmap.createBitmap(full, left, top, width, height).also { if (it !== full) full.recycle() }
            }
            ?: error("Could not decode image")

        // Drawn into a bitmap of exactly the planned size, turned upright on the way. Black shows wherever the picture is transparent.
        val out = Bitmap.createBitmap(plan.outWidth, plan.outHeight, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.BLACK)
            drawBitmap(decoded, upright(plan.orientation, decoded.width.toFloat(), decoded.height.toFloat(), plan.outWidth.toFloat(), plan.outHeight.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        decoded.recycle()
        try {
            FileOutputStream(dest.toString()).use { check(out.compress(Bitmap.CompressFormat.JPEG, quality, it)) { "JPEG encoding failed" } }
        } finally {
            out.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeRegion(path: String, region: Rect, options: BitmapFactory.Options): Bitmap? = runCatching {
        val decoder = BitmapRegionDecoder.newInstance(path, false) ?: return null
        try {
            decoder.decodeRegion(region, options)
        } finally {
            decoder.recycle()
        }
    }.getOrNull()

    /**
     * Takes the decoded part, [w] by [h] and as stored, onto the output, [ow] by [oh] and upright.
     * Three corners are enough to fix it: the stored part's top left, top right and bottom left, and
     * where each belongs in the upright picture.
     */
    private fun upright(orientation: Orientation, w: Float, h: Float, ow: Float, oh: Float): Matrix {
        val to = when (orientation) {
            Orientation.NORMAL -> floatArrayOf(0f, 0f, ow, 0f, 0f, oh)
            Orientation.FLIP_HORIZONTAL -> floatArrayOf(ow, 0f, 0f, 0f, ow, oh)
            Orientation.ROTATE_180 -> floatArrayOf(ow, oh, 0f, oh, ow, 0f)
            Orientation.FLIP_VERTICAL -> floatArrayOf(0f, oh, ow, oh, 0f, 0f)
            Orientation.TRANSPOSE -> floatArrayOf(0f, 0f, 0f, oh, ow, 0f)
            Orientation.ROTATE_90 -> floatArrayOf(ow, 0f, ow, oh, 0f, 0f)
            Orientation.TRANSVERSE -> floatArrayOf(ow, oh, ow, 0f, 0f, oh)
            Orientation.ROTATE_270 -> floatArrayOf(0f, oh, 0f, 0f, ow, oh)
        }
        return Matrix().apply { setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, 0f, h), 0, to, 0, 3) }
    }
}

/** The shared photo rules on Android's codec. */
class AndroidImageProcessor : CodecImageProcessor(AndroidImageCodec())
