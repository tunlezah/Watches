package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.data.backup.RestoreMode
import io.github.tunlezah.watches.data.photos.PhotoFixtures
import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.tunlezah.watches.data.photos.PhotoMetadata
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A backup made on a Mac, restored here: on the JVM, on an Android emulator, on the iPhone
 * simulator and on a Mac, whichever this test is running on (docs/MACOS_PLAN.md, 9.5).
 *
 * The archive is `fixtures/archives/mac.zip`. ReferenceArchiveMaker made it on the macOS runner,
 * with the Mac's own image code, through the bridge. It holds one watch with four photos (a HEIC
 * turned in its container, a cropped JPEG that arrived with a location, a PNG that is half
 * transparent, and a TIFF, which is kept as a JPEG) and two documents (a PDF, and a receipt
 * photographed as a HEIC, which is kept as a JPEG). Every picture is the photo fixtures' one: red
 * top left, green top right, blue bottom left, yellow bottom right.
 */
class ReferenceArchiveTest {

    private val phones = TestPhones()

    @AfterTest
    fun tearDown() = phones.close()

    private val red = Triple(200, 30, 30)
    private val green = Triple(30, 160, 60)
    private val blue = Triple(40, 60, 200)
    private val yellow = Triple(220, 200, 40)
    private val black = Triple(0, 0, 0)

    private fun near(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>) =
        abs(a.first - b.first) <= 40 && abs(a.second - b.second) <= 40 && abs(a.third - b.third) <= 40

    private fun assertColour(expected: Triple<Int, Int, Int>, actual: Triple<Int, Int, Int>, what: String) =
        assertTrue(near(expected, actual), "$what: expected about $expected, got $actual")

    /** The fixtures' picture, at any size: a colour in the middle of each quarter. */
    private fun assertThePicture(pixels: TestPixels, what: String, topRight: Triple<Int, Int, Int> = green, bottomRight: Triple<Int, Int, Int> = yellow) {
        val (w, h) = pixels.width to pixels.height
        assertColour(red, pixels.at(w / 4, h / 4), "$what, top left")
        assertColour(topRight, pixels.at(3 * w / 4, h / 4), "$what, top right")
        assertColour(blue, pixels.at(w / 4, 3 * h / 4), "$what, bottom left")
        assertColour(bottomRight, pixels.at(3 * w / 4, 3 * h / 4), "$what, bottom right")
    }

    @Test fun aBackupMadeOnAMacRestoresHereAndShowsTheSamePictures() = runBlocking<Unit> {
        val phone = phones.new()
        val archive = phones.scratchDir() / "mac.zip"
        FileSystem.SYSTEM.write(archive) { write(ArchiveFixtures.bytes("mac.zip")) }

        val preview = phone.backup.inspect { FileSystem.SYSTEM.source(archive) }
        assertTrue(preview.warnings.isEmpty(), preview.warnings.toString())
        assertTrue("macOS" in preview.manifest.app, preview.manifest.app)
        assertEquals(listOf(1, 4, 2), listOf(preview.counts.watches, preview.counts.photos, preview.counts.documents))
        // Every file is checked against the checksum the Mac wrote as it is restored.
        phone.backup.restore({ FileSystem.SYSTEM.source(archive) }, preview, RestoreMode.REPLACE)

        val files = phone.files
        val watch = phone.everything().watch.single()
        val (heic, cropped, alpha, tiff) = phone.photos.forWatch(watch.id)

        // The rows say what the Mac found: sizes are the upright picture's, and the crop is as it was drawn.
        assertEquals(128 to 96, heic.width to heic.height)
        assertEquals(64 to 48, cropped.width to cropped.height)
        assertEquals(NormalizedRect(0.5f, 0.5f, 1f, 1f), cropped.crop)
        assertEquals(64 to 48, alpha.width to alpha.height)
        assertEquals(64 to 48, tiff.width to tiff.height)

        // Originals: kept as they arrived where the format is stored, with no location in any of them.
        assertTrue(files.read(heic.originalFile).contentEquals(PhotoFixtures.bytes("large-turned-6.heic")), "the HEIC is the file that was added, byte for byte")
        assertEquals(PhotoFixtures.bytes("gps-exif-mm.jpg").size, files.read(cropped.originalFile).size, "the JPEG is the file that was added, but for its location")
        assertTrue(files.read(alpha.originalFile).contentEquals(PhotoFixtures.bytes("alpha.png")))
        assertEquals(PhotoFormat.JPEG, PhotoFormat.detect(files.read(tiff.originalFile)), "the TIFF was kept as a JPEG")
        for (photo in listOf(heic, cropped, alpha, tiff)) {
            for (file in listOf(photo.originalFile, photo.displayFile, photo.thumbFile)) assertFalse(PhotoMetadata.hasLocation(files.read(file)), file)
        }

        // The Mac's renditions, read with this platform's plain decoder. A device with no HEIC decoder
        // still shows the HEIC photo, because its renditions came with it.
        val heicShown = decodePixels(files.resolve(heic.displayFile))
        assertEquals(128 to 96, heicShown.width to heicShown.height)
        assertThePicture(heicShown, "the HEIC, as the Mac rendered it")
        val croppedShown = decodePixels(files.resolve(cropped.displayFile))
        assertEquals(32 to 24, croppedShown.width to croppedShown.height)
        for ((x, y) in listOf(4 to 4, 27 to 4, 4 to 19, 27 to 19, 16 to 12)) assertColour(yellow, croppedShown.at(x, y), "the crop is the bottom right quarter, at $x,$y")
        assertThePicture(decodePixels(files.resolve(alpha.displayFile)), "the half-transparent PNG", topRight = black, bottomRight = black)
        assertThePicture(decodePixels(files.resolve(tiff.displayFile)), "the TIFF")
        assertThePicture(decodePixels(files.resolve(heic.thumbFile)), "the HEIC's thumbnail")

        // The documents.
        val (manual, receipt) = phone.everything().document.sortedBy { it.mimeType }
        assertEquals("application/pdf", manual.mimeType)
        assertTrue(files.read(manual.file).contentEquals(phones.pdf))
        assertEquals("image/jpeg", receipt.mimeType)
        assertFalse(PhotoMetadata.hasLocation(files.read(receipt.file)))
        val receiptShown = decodePixels(files.resolve(receipt.file))
        assertEquals(64 to 48, receiptShown.width to receiptShown.height)
        assertThePicture(receiptShown, "the receipt")

        // A restore asks for the photos it brought to be gone through. These came from the current rules,
        // so there is nothing to change, whatever this device can decode.
        val pass = phone.storedPhotos.runIfDue()!!
        assertEquals(5 to 0, pass.checked to pass.failed)
        assertEquals(0, pass.changed, "nothing a Mac stores needs putting right here")

        // Parity. Rendered again here, from the same original with the same crop, each photo comes out
        // the same size to the pixel, and the same picture, as the Mac made it.
        for ((name, photo) in listOf("the HEIC" to heic, "the cropped JPEG" to cropped, "the PNG" to alpha, "the TIFF" to tiff)) {
            if (PhotoFormat.detect(files.read(photo.originalFile)) !in phone.processor.readableFormats) continue
            // The Mac's two renditions are read first: rendering again replaces them both.
            val theirs = Rendition.entries.associateWith { decodePixels(files.resolve(it.of(photo))) }
            phone.photos.rerender(photo.id)
            val again = phone.photos.byId(photo.id)!!
            for (rendition in Rendition.entries) {
                val made = rendition.name.lowercase()
                val mac = theirs.getValue(rendition)
                val mine = decodePixels(files.resolve(rendition.of(again)))
                assertEquals(mac.width to mac.height, mine.width to mine.height, "$name: its $made image is the size the Mac made")
                // The middle of each cell of a four by four grid: clear of the edges between the colours.
                for (cx in 0 until 4) for (cy in 0 until 4) {
                    val x = (2 * cx + 1) * mine.width / 8
                    val y = (2 * cy + 1) * mine.height / 8
                    assertColour(mac.at(x, y), mine.at(x, y), "$name at $x,$y: the Mac's $made image and this device's")
                }
            }
        }
    }

    private enum class Rendition {
        DISPLAY, THUMB;

        fun of(photo: io.github.tunlezah.watches.core.model.Photo) = if (this == DISPLAY) photo.displayFile else photo.thumbFile
    }
}
