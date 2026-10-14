package io.github.tunlezah.watches.data.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import io.github.tunlezah.watches.core.model.NormalizedRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class AndroidImageProcessor : ImageProcessor {

    override suspend fun size(file: Path): ImageSize = withContext(Dispatchers.IO) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.toString(), opts)
        require(opts.outWidth > 0 && opts.outHeight > 0) { "Not a readable image" }
        if (swapsAxes(orientation(file))) ImageSize(opts.outHeight, opts.outWidth) else ImageSize(opts.outWidth, opts.outHeight)
    }

    override suspend fun stripLocation(file: Path) = withContext(Dispatchers.IO) {
        // ExifInterface can rewrite JPEG, PNG and WebP. Other formats keep their metadata.
        runCatching {
            val exif = ExifInterface(file.toString())
            if (GPS_TAGS.any { exif.getAttribute(it) != null }) {
                GPS_TAGS.forEach { exif.setAttribute(it, null) }
                exif.saveAttributes()
            }
        }
        Unit
    }

    override suspend fun render(source: Path, crop: NormalizedRect?, maxEdge: Int, quality: Int, dest: Path): ImageSize =
        withContext(Dispatchers.Default) {
            val path = source.toString()
            val orientation = orientation(source)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val rawW = bounds.outWidth
            val rawH = bounds.outHeight
            require(rawW > 0 && rawH > 0) { "Not a readable image" }

            val rawCrop = toRaw(crop ?: NormalizedRect.Full, orientation)
            val region = Rect(
                (rawCrop.left * rawW).roundToInt().coerceIn(0, rawW - 1),
                (rawCrop.top * rawH).roundToInt().coerceIn(0, rawH - 1),
                (rawCrop.right * rawW).roundToInt().coerceIn(1, rawW),
                (rawCrop.bottom * rawH).roundToInt().coerceIn(1, rawH),
            )
            val regionLong = max(region.width(), region.height())
            // Largest power-of-two subsample that still leaves at least maxEdge pixels.
            var sample = 1
            while (regionLong / (sample * 2) >= maxEdge) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = decodeRegion(path, region, opts)
                ?: BitmapFactory.decodeFile(path, opts)?.let { full ->
                    // Region decoding isn't available for every format: decode subsampled, then cut.
                    val r = Rect(region.left / sample, region.top / sample, region.right / sample, region.bottom / sample)
                    Bitmap.createBitmap(full, r.left, r.top, r.width().coerceAtMost(full.width - r.left), r.height().coerceAtMost(full.height - r.top))
                        .also { if (it !== full) full.recycle() }
                }
                ?: error("Could not decode image")

            val scale = minOf(1f, maxEdge.toFloat() / max(decoded.width, decoded.height))
            val matrix = orientationMatrix(orientation).apply { postScale(scale, scale) }
            val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (out !== decoded) decoded.recycle()

            dest.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            FileOutputStream(dest.toString()).use { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            val result = ImageSize(out.width, out.height)
            out.recycle()
            result
        }

    @Suppress("DEPRECATION")
    private fun decodeRegion(path: String, region: Rect, opts: BitmapFactory.Options): Bitmap? = runCatching {
        val decoder = BitmapRegionDecoder.newInstance(path, false) ?: return null
        try {
            decoder.decodeRegion(region, opts)
        } finally {
            decoder.recycle()
        }
    }.getOrNull()

    private fun orientation(file: Path): Int = runCatching {
        ExifInterface(file.toString()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun swapsAxes(orientation: Int) = orientation in 5..8

    private fun orientationMatrix(orientation: Int) = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setRotate(180f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }

    companion object {
        private val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR, ExifInterface.TAG_GPS_VERSION_ID,
        )

        /** Maps a crop in display orientation to the raw (stored) pixel grid. */
        fun toRaw(r: NormalizedRect, orientation: Int): NormalizedRect = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> NormalizedRect(1 - r.right, r.top, 1 - r.left, r.bottom)
            ExifInterface.ORIENTATION_ROTATE_180 -> NormalizedRect(1 - r.right, 1 - r.bottom, 1 - r.left, 1 - r.top)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> NormalizedRect(r.left, 1 - r.bottom, r.right, 1 - r.top)
            ExifInterface.ORIENTATION_TRANSPOSE -> NormalizedRect(r.top, r.left, r.bottom, r.right)
            ExifInterface.ORIENTATION_ROTATE_90 -> NormalizedRect(r.top, 1 - r.right, r.bottom, 1 - r.left)
            ExifInterface.ORIENTATION_TRANSVERSE -> NormalizedRect(1 - r.bottom, 1 - r.right, 1 - r.top, 1 - r.left)
            ExifInterface.ORIENTATION_ROTATE_270 -> NormalizedRect(1 - r.bottom, r.left, 1 - r.top, r.right)
            else -> r
        }
    }
}
