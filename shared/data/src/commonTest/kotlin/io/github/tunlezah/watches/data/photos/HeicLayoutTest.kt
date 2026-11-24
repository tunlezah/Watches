package io.github.tunlezah.watches.data.photos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A HEIC says in its own container how its picture is cut and turned. The shared code reads that,
 * so no platform has to trust its system's reading of it (Android's ignores the cut).
 */
class HeicLayoutTest {

    @Test fun anOrdinaryHeicIsAllPictureAndTurnedAsItsContainerSays() {
        for (n in 1..8) {
            val layout = PhotoMetadata.heicLayout(PhotoFixtures.bytes("large-turned-$n.heic"))!!
            val orientation = Orientation.fromExif(n)
            // Each file's coded pixels are turned so that the upright picture is 128 by 96.
            val coded = if (orientation.swapsAxes) 96 to 128 else 128 to 96
            assertEquals(HeicLayout(coded.first, coded.second, crop = null, orientation = orientation), layout, "large-turned-$n.heic")
        }
    }

    @Test fun aPictureSmallerThanItsCodedSizeIsCutFromIt() {
        assertEquals(HeicLayout(64, 64, PixelRect(0, 0, 64, 48), Orientation.NORMAL), PhotoMetadata.heicLayout(PhotoFixtures.bytes("gps.heic")))
        // Cut first, then turned: the cut is in the coded picture.
        for (name in listOf("turned-6.heic", "turned-6-iphone.heic")) {
            assertEquals(HeicLayout(64, 64, PixelRect(0, 0, 48, 64), Orientation.ROTATE_90), PhotoMetadata.heicLayout(PhotoFixtures.bytes(name)), name)
        }
    }

    @Test fun theContainersTurningCountsAndExifsDoesNot() {
        // EXIF says upright in the first and a quarter turn in the second. The container says a quarter turn in both.
        assertEquals(Orientation.ROTATE_90, PhotoMetadata.orientation(PhotoFixtures.bytes("turned-6.heic")))
        assertEquals(Orientation.ROTATE_90, PhotoMetadata.orientation(PhotoFixtures.bytes("turned-6-iphone.heic")))
        for (n in 1..8) assertEquals(Orientation.fromExif(n), PhotoMetadata.orientation(PhotoFixtures.bytes("large-turned-$n.heic")), "large-turned-$n.heic")
        assertEquals(Orientation.NORMAL, PhotoMetadata.orientation(PhotoFixtures.bytes("gps.heic")))
    }

    @Test fun theStartOfTheFileIsEnough() {
        val whole = PhotoFixtures.bytes("turned-6.heic")
        val layout = PhotoMetadata.heicLayout(whole)!!
        // The tables come before the picture data, so a prefix that holds them reads the same.
        val tablesEnd = (0..whole.size - 4).first { whole.decodeToString(it, it + 4) == "mdat" } - 4
        assertEquals(layout, PhotoMetadata.heicLayout(whole.copyOf(tablesEnd)))
    }

    @Test fun anythingElseHasNoLayout() {
        assertNull(PhotoMetadata.heicLayout(PhotoFixtures.bytes("plain.jpg")))
        assertNull(PhotoMetadata.heicLayout(PhotoFixtures.bytes("gps.webp")))
        assertNull(PhotoMetadata.heicLayout(ByteArray(0)))
        // Cut off in the middle of its tables: not followed, and nothing guessed.
        val whole = PhotoFixtures.bytes("gps.heic")
        for (length in listOf(12, 40, 100, 200)) assertNull(PhotoMetadata.heicLayout(whole.copyOf(length)), "the first $length bytes")
        // A table that points outside the file.
        val damaged = whole.copyOf()
        val ipma = (0..damaged.size - 4).first { damaged.decodeToString(it, it + 4) == "ipma" }
        damaged[ipma + 8 + 3] = 0x7F
        assertNull(PhotoMetadata.heicLayout(damaged))
    }

    @Test fun turningsFoldIntoOneOfTheEight() {
        // Doing one and then another is the same as doing the one they fold into, for a pixel anywhere in the picture.
        val (w, h) = 5 to 3
        for (first in Orientation.entries) for (next in Orientation.entries) {
            val both = first.then(next)
            val (w1, h1) = if (first.swapsAxes) h to w else w to h
            for (x in 0 until w) for (y in 0 until h) {
                val (x1, y1) = first.toDisplay(x, y, w, h)
                assertEquals(next.toDisplay(x1, y1, w1, h1), both.toDisplay(x, y, w, h), "$first then $next at $x,$y")
            }
        }
    }
}
