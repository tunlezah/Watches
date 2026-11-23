package io.github.tunlezah.watches.data.photos

/**
 * How a stored picture has to be turned to be upright: the eight cases of EXIF's orientation tag.
 * A phone held sideways still writes its sensor's rows in the same order, and records here how to
 * turn them. Everything the app keeps about a photo (its size, its crop) is in the upright picture,
 * the one a person sees.
 */
enum class Orientation(val exif: Int, val swapsAxes: Boolean) {
    NORMAL(1, false),
    FLIP_HORIZONTAL(2, false),
    ROTATE_180(3, false),
    FLIP_VERTICAL(4, false),

    /** Mirrored across the diagonal from top left to bottom right. */
    TRANSPOSE(5, true),

    /** A quarter turn clockwise makes it upright: the usual portrait photo. */
    ROTATE_90(6, true),

    /** Mirrored across the other diagonal. */
    TRANSVERSE(7, true),

    /** A quarter turn anticlockwise makes it upright. */
    ROTATE_270(8, true);

    companion object {
        /** The orientation for an EXIF value. Anything outside 1 to 8 means the tag is missing or broken, and reads as upright. */
        fun fromExif(value: Int?): Orientation = entries.firstOrNull { it.exif == value } ?: NORMAL
    }
}

/** A rectangle of whole pixels. [right] and [bottom] are just past the last column and row. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(right > left && bottom > top) { "Empty rect: $this" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Where a rectangle of the upright picture lies in the stored pixel grid. [displayWidth] and
 * [displayHeight] are the upright picture's size. The mapping is exact: no pixel is gained or lost.
 */
fun Orientation.toStored(r: PixelRect, displayWidth: Int, displayHeight: Int): PixelRect = when (this) {
    Orientation.NORMAL -> r
    Orientation.FLIP_HORIZONTAL -> PixelRect(displayWidth - r.right, r.top, displayWidth - r.left, r.bottom)
    Orientation.ROTATE_180 -> PixelRect(displayWidth - r.right, displayHeight - r.bottom, displayWidth - r.left, displayHeight - r.top)
    Orientation.FLIP_VERTICAL -> PixelRect(r.left, displayHeight - r.bottom, r.right, displayHeight - r.top)
    Orientation.TRANSPOSE -> PixelRect(r.top, r.left, r.bottom, r.right)
    Orientation.ROTATE_90 -> PixelRect(r.top, displayWidth - r.right, r.bottom, displayWidth - r.left)
    Orientation.TRANSVERSE -> PixelRect(displayHeight - r.bottom, displayWidth - r.right, displayHeight - r.top, displayWidth - r.left)
    Orientation.ROTATE_270 -> PixelRect(displayHeight - r.bottom, r.left, displayHeight - r.top, r.right)
}

/**
 * Where one stored pixel ends up in the upright picture: the reverse of [toStored], for a single
 * pixel. [storedWidth] and [storedHeight] are the stored grid's size. A codec that turns pixels
 * itself uses this; the tests use it to check [toStored].
 */
fun Orientation.toDisplay(x: Int, y: Int, storedWidth: Int, storedHeight: Int): Pair<Int, Int> = when (this) {
    Orientation.NORMAL -> x to y
    Orientation.FLIP_HORIZONTAL -> (storedWidth - 1 - x) to y
    Orientation.ROTATE_180 -> (storedWidth - 1 - x) to (storedHeight - 1 - y)
    Orientation.FLIP_VERTICAL -> x to (storedHeight - 1 - y)
    Orientation.TRANSPOSE -> y to x
    Orientation.ROTATE_90 -> (storedHeight - 1 - y) to x
    Orientation.TRANSVERSE -> (storedHeight - 1 - y) to (storedWidth - 1 - x)
    Orientation.ROTATE_270 -> y to (storedWidth - 1 - x)
}
