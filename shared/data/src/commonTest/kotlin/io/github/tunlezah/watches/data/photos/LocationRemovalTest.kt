package io.github.tunlezah.watches.data.photos

import io.github.tunlezah.watches.data.backup.Crc32
import io.github.tunlezah.watches.data.keepForInspection
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A photo's location is removed by shared code, the same way on every platform, without touching
 * the picture. The fixtures are small real files made by tools/photo_fixtures.py, which also
 * checks this test's output with an unrelated reader.
 */
class LocationRemovalTest {

    private val withLocation = listOf("gps-exif-ii.jpg", "gps-exif-mm.jpg", "gps-xmp.jpg", "gps.png", "gps.webp", "gps.heic")
    private val without = listOf("no-gps.jpg", "plain.jpg", "alpha.png") + (1..8).map { "turned-$it.jpg" }

    /** Formats that aren't stored as they are: the app keeps a JPEG made from them (RepositoryTest). */
    private val converted = listOf("other.bmp", "other.gif", "other.tiff")

    private fun indexOf(bytes: ByteArray, text: ByteArray, from: Int = 0): Int {
        for (i in from..bytes.size - text.size) if (text.indices.all { bytes[i + it] == text[it] }) return i
        return -1
    }

    private fun contains(bytes: ByteArray, text: String) = indexOf(bytes, text.encodeToByteArray()) >= 0

    @Test fun everyFixtureIsThere() {
        assertEquals((withLocation + without + converted).sorted(), PhotoFixtures.names.sorted())
    }

    @Test fun aLocationIsFoundInEveryStoredFormat() {
        for (name in withLocation) assertTrue(PhotoMetadata.hasLocation(PhotoFixtures.bytes(name)), name)
        for (name in without) assertFalse(PhotoMetadata.hasLocation(PhotoFixtures.bytes(name)), name)
    }

    @Test fun removingItChangesOnlyMetadataAndNeverTheLength() {
        for (name in withLocation) {
            val original = PhotoFixtures.bytes(name)
            val cleaned = PhotoFixtures.bytes(name)
            assertEquals(LocationRemoval.REMOVED, PhotoMetadata.removeLocation(cleaned), name)
            assertEquals(original.size, cleaned.size, name)
            assertFalse(PhotoMetadata.hasLocation(cleaned), name)

            // Every byte that changed lies inside the file's metadata: the picture is untouched.
            val metadata = PhotoMetadata.metadataRanges(original)!!
            val changed = original.indices.filter { original[it] != cleaned[it] }
            assertTrue(changed.isNotEmpty(), name)
            assertTrue(changed.all { i -> metadata.any { i in it } }, "$name: changed outside its metadata")

            // A second pass finds nothing left to do.
            val again = cleaned.copyOf()
            assertEquals(LocationRemoval.NONE, PhotoMetadata.removeLocation(again), name)
            assertContentEquals(cleaned, again, name)

            keepForInspection("cleaned-photos", name, cleaned)
        }
    }

    @Test fun theCoordinatesThemselvesAreGoneFromTheFile() {
        // 51 degrees 28 minutes 38.34 seconds, as EXIF stores it: three pairs of whole numbers.
        fun rational(n: Int, d: Int, little: Boolean): ByteArray {
            fun int(v: Int) = ByteArray(4) { i -> (v ushr (if (little) 8 * i else 24 - 8 * i)).toByte() }
            return int(n) + int(d)
        }
        for ((name, little) in listOf("gps-exif-ii.jpg" to true, "gps-exif-mm.jpg" to false, "gps.png" to false, "gps.webp" to true, "gps.heic" to false)) {
            val latitude = rational(51, 1, little) + rational(28, 1, little) + rational(3834, 100, little)
            val bytes = PhotoFixtures.bytes(name)
            assertTrue(indexOf(bytes, latitude) >= 0, "$name should hold the latitude to begin with")
            PhotoMetadata.removeLocation(bytes)
            assertEquals(-1, indexOf(bytes, latitude), name)
        }
        for (name in listOf("gps-xmp.jpg", "gps.png", "gps.webp", "gps.heic")) {
            val bytes = PhotoFixtures.bytes(name)
            assertTrue(contains(bytes, "51,28.639N") && contains(bytes, "0,0.0923W") && contains(bytes, ">46/1<"), name)
            PhotoMetadata.removeLocation(bytes)
            assertFalse(contains(bytes, "51,28.639N") || contains(bytes, "0,0.0923W") || contains(bytes, ">46/1<"), name)
            // The packet is still text of the same shape, with its other fields.
            assertTrue(contains(bytes, "exif:GPSLatitude=\"          \"") && contains(bytes, "xmp:CreateDate=\"2024-05-17T10:11:12\""), name)
        }
    }

    @Test fun theDateAndTheTurningAreKept() {
        for (name in withLocation) {
            val bytes = PhotoFixtures.bytes(name)
            val turning = PhotoMetadata.orientation(bytes)
            PhotoMetadata.removeLocation(bytes)
            assertEquals(turning, PhotoMetadata.orientation(bytes), name)
            assertTrue(contains(bytes, "2024:05:17 10:11:12") || contains(bytes, "2024-05-17T10:11:12"), "$name: the capture date")
        }
        assertEquals(Orientation.ROTATE_90, PhotoMetadata.orientation(PhotoFixtures.bytes("gps-exif-mm.jpg")))
    }

    @Test fun aPhotoWithoutALocationIsLeftExactlyAsItWas() {
        for (name in without) {
            val bytes = PhotoFixtures.bytes(name)
            assertEquals(LocationRemoval.NONE, PhotoMetadata.removeLocation(bytes), name)
            assertContentEquals(PhotoFixtures.bytes(name), bytes, name)
        }
    }

    @Test fun theTurningIsReadFromExifInEitherByteOrder() {
        for (n in 1..8) assertEquals(Orientation.fromExif(n), PhotoMetadata.orientation(PhotoFixtures.bytes("turned-$n.jpg")), "turned-$n")
        assertEquals(Orientation.NORMAL, PhotoMetadata.orientation(PhotoFixtures.bytes("plain.jpg")))
        assertEquals(Orientation.NORMAL, PhotoMetadata.orientation(PhotoFixtures.bytes("gps.png")))
        assertEquals(Orientation.NORMAL, PhotoMetadata.orientation(ByteArray(0)))
    }

    @Test fun aPngChunkGetsItsChecksumBack() {
        val bytes = PhotoFixtures.bytes("gps.png")
        PhotoMetadata.removeLocation(bytes)
        var p = 8
        var checked = 0
        while (p + 12 <= bytes.size) {
            val length = ((bytes[p].toInt() and 0xFF) shl 24) or ((bytes[p + 1].toInt() and 0xFF) shl 16) or ((bytes[p + 2].toInt() and 0xFF) shl 8) or (bytes[p + 3].toInt() and 0xFF)
            val crcAt = p + 8 + length
            val stored = (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (bytes[crcAt + i].toLong() and 0xFF) }
            assertEquals(Crc32().apply { update(bytes, p + 4, 4 + length) }.value, stored, bytes.decodeToString(p + 4, p + 8))
            checked++
            p = crcAt + 4
        }
        assertTrue(checked >= 5, "every chunk of the file was checked")
    }

    @Test fun aFileThatCantBeFollowedIsReportedAndNotTouched() {
        fun unreadable(what: String, bytes: ByteArray) {
            val before = bytes.copyOf()
            assertEquals(LocationRemoval.UNREADABLE, PhotoMetadata.removeLocation(bytes), what)
            assertContentEquals(before, bytes, "$what: left as it was")
            assertTrue(PhotoMetadata.hasLocation(bytes), "$what: a location can't be ruled out")
        }
        // Cut off in the middle of its EXIF block.
        unreadable("a truncated JPEG", PhotoFixtures.bytes("gps-exif-ii.jpg").copyOf(60))

        // The pointer to the GPS directory leads outside the block.
        val astray = PhotoFixtures.bytes("gps-exif-ii.jpg")
        val pointer = indexOf(astray, byteArrayOf(0x25, 0x88.toByte(), 0x04, 0x00, 0x01, 0x00, 0x00, 0x00))
        assertTrue(pointer > 0)
        for (i in 8..10) astray[pointer + i] = 0x7F
        unreadable("a GPS pointer outside the block", astray)

        // A coordinate's value is said to lie past the end of the block.
        val overrun = PhotoFixtures.bytes("gps-exif-mm.jpg")
        val latitude = indexOf(overrun, byteArrayOf(0x00, 0x02, 0x00, 0x05, 0x00, 0x00, 0x00, 0x03))
        assertTrue(latitude > 0)
        overrun[latitude + 10] = 0x7F
        unreadable("a coordinate outside the block", overrun)

        // XMP stored compressed can't be edited where it lies.
        val compressed = PhotoFixtures.bytes("gps.png")
        val keyword = indexOf(compressed, "XML:com.adobe.xmp".encodeToByteArray())
        compressed[keyword + "XML:com.adobe.xmp".length + 1] = 1
        unreadable("a PNG with compressed XMP", compressed)

        // The table that says where a HEIC's metadata lies uses a field size nothing writes.
        val table = PhotoFixtures.bytes("gps.heic")
        val iloc = indexOf(table, "iloc".encodeToByteArray())
        table[iloc + 8] = 0x33
        unreadable("a HEIC whose item table can't be followed", table)

        // A format that isn't stored at all.
        unreadable("a GIF", "GIF89a".encodeToByteArray() + ByteArray(32))
        unreadable("an empty file", ByteArray(0))
    }
}
