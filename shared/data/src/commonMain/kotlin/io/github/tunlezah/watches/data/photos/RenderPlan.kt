package io.github.tunlezah.watches.data.photos

import io.github.tunlezah.watches.core.model.NormalizedRect
import kotlin.math.floor

/**
 * Exactly what to cut from a picture and how large to write it, in whole pixels.
 *
 * Every platform has its own decoder, but they all cut by this plan, so the same crop of the same
 * photo is the same pixels and the same output size on Android, iPhone and Mac. The arithmetic is
 * in integers, apart from one rounding of the crop's edges, so it can't differ between them.
 */
data class RenderPlan(
    /** The stored pixel grid, before the picture is turned upright. */
    val storedWidth: Int,
    val storedHeight: Int,
    val orientation: Orientation,
    /** The upright picture: what a person sees, and what a photo's recorded size and crop refer to. */
    val displayWidth: Int,
    val displayHeight: Int,
    /** The part to keep, in the upright picture. */
    val region: PixelRect,
    /** The same part in the stored grid, for decoders that cut before they turn. */
    val storedRegion: PixelRect,
    /** The size to write. Never larger than [region]: a picture is reduced to fit, never enlarged. */
    val outWidth: Int,
    val outHeight: Int,
) {
    /**
     * The largest power of two the picture can be subsampled by while decoding and still have at
     * least the output's pixels: 1 means decode at full size.
     */
    val sampleSize: Int
        get() {
            val have = maxOf(region.width, region.height)
            val need = maxOf(outWidth, outHeight)
            var sample = 1
            while (have / (sample * 2) >= need) sample *= 2
            return sample
        }

    companion object {
        /**
         * @param crop the part to keep, as fractions of the upright picture; null for all of it
         * @param maxEdge the longest the output's long edge may be
         */
        fun of(storedWidth: Int, storedHeight: Int, orientation: Orientation, crop: NormalizedRect?, maxEdge: Int): RenderPlan {
            require(storedWidth > 0 && storedHeight > 0) { "Not a picture: $storedWidth x $storedHeight" }
            require(maxEdge > 0) { "maxEdge must be positive" }
            val displayWidth = if (orientation.swapsAxes) storedHeight else storedWidth
            val displayHeight = if (orientation.swapsAxes) storedWidth else storedHeight

            val c = crop ?: NormalizedRect.Full
            // Each edge goes to the nearest pixel boundary. The region always keeps at least one pixel.
            val left = edge(c.left, displayWidth).coerceIn(0, displayWidth - 1)
            val top = edge(c.top, displayHeight).coerceIn(0, displayHeight - 1)
            val right = edge(c.right, displayWidth).coerceIn(left + 1, displayWidth)
            val bottom = edge(c.bottom, displayHeight).coerceIn(top + 1, displayHeight)
            val region = PixelRect(left, top, right, bottom)

            val long = maxOf(region.width, region.height)
            val (outWidth, outHeight) = if (long <= maxEdge) {
                region.width to region.height
            } else {
                scaled(region.width, long, maxEdge) to scaled(region.height, long, maxEdge)
            }
            return RenderPlan(
                storedWidth, storedHeight, orientation, displayWidth, displayHeight,
                region, orientation.toStored(region, displayWidth, displayHeight), outWidth, outHeight,
            )
        }

        private fun edge(fraction: Float, size: Int): Int = floor(fraction.toDouble() * size + 0.5).toInt()

        /** [side] reduced by maxEdge / long, to the nearest pixel and never below one. */
        private fun scaled(side: Int, long: Int, maxEdge: Int): Int =
            ((side.toLong() * maxEdge + long / 2) / long).toInt().coerceAtLeast(1)
    }
}
