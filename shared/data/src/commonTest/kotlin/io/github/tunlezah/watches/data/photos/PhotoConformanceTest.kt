package io.github.tunlezah.watches.data.photos

import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.data.TestPixels
import io.github.tunlezah.watches.data.decodableFormats
import io.github.tunlezah.watches.data.decodePixels
import io.github.tunlezah.watches.data.files.ImageSize
import io.github.tunlezah.watches.data.newTestDir
import io.github.tunlezah.watches.data.rendersBySharedGeometry
import io.github.tunlezah.watches.data.testImageProcessor
import io.github.tunlezah.watches.data.testJpeg
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What every platform's image code must do with the same photos: the conformance suite of
 * docs/MACOS_PLAN.md, 9.4. It is shared test code, so it runs against the plain-Java codec on
 * Linux, the iPhone's on the simulator, and Android's and the Mac's as those join. Each platform's
 * result is read back with that platform's plain decoder, not the app's own code.
 *
 * Every fixture shows one picture, 64 by 48: red top left, green top right, blue bottom left and
 * yellow bottom right.
 */
class PhotoConformanceTest {

    private val dir = newTestDir()
    private val processor = testImageProcessor()

    @AfterTest
    fun tearDown() = FileSystem.SYSTEM.deleteRecursively(dir)

    private val red = Triple(200, 30, 30)
    private val green = Triple(30, 160, 60)
    private val blue = Triple(40, 60, 200)
    private val yellow = Triple(220, 200, 40)
    private val black = Triple(0, 0, 0)

    private fun fixture(name: String): Path = (dir / name).also { path -> FileSystem.SYSTEM.write(path) { write(PhotoFixtures.bytes(name)) } }

    /** Decoders and JPEG writers differ a little, so colours are compared loosely. The flat corners make that safe. */
    private fun assertColour(expected: Triple<Int, Int, Int>, actual: Triple<Int, Int, Int>, what: String) {
        val close = abs(expected.first - actual.first) <= 40 && abs(expected.second - actual.second) <= 40 && abs(expected.third - actual.third) <= 40
        assertTrue(close, "$what: expected about $expected, got $actual")
    }

    private fun assertTheReferencePicture(pixels: TestPixels, what: String) {
        assertEquals(64 to 48, pixels.width to pixels.height, what)
        assertColour(red, pixels.at(8, 8), "$what, top left")
        assertColour(green, pixels.at(56, 8), "$what, top right")
        assertColour(blue, pixels.at(8, 40), "$what, bottom left")
        assertColour(yellow, pixels.at(56, 40), "$what, bottom right")
    }

    @Test fun everyTurningComesOutUpright() = runTest {
        for (n in 1..8) {
            val source = fixture("turned-$n.jpg")
            assertEquals(ImageSize(64, 48), processor.size(source), "turned-$n: its size is the upright picture's")
            val out = dir / "upright-$n.jpg"
            assertEquals(ImageSize(64, 48), processor.render(source, null, 2560, 90, out))
            assertTheReferencePicture(decodePixels(out), "turned-$n")
        }
    }

    @Test fun theSameCropCutsTheSameCornerWhateverTheTurning() = runTest {
        val corners = listOf(
            NormalizedRect(0f, 0f, 0.5f, 0.5f) to red, NormalizedRect(0.5f, 0f, 1f, 0.5f) to green,
            NormalizedRect(0f, 0.5f, 0.5f, 1f) to blue, NormalizedRect(0.5f, 0.5f, 1f, 1f) to yellow,
        )
        for (n in 1..8) {
            val source = fixture("turned-$n.jpg")
            for ((index, corner) in corners.withIndex()) {
                val (crop, colour) = corner
                val out = dir / "corner-$n-$index.jpg"
                assertEquals(ImageSize(32, 24), processor.render(source, crop, 2560, 90, out), "turned-$n")
                val pixels = decodePixels(out)
                assertEquals(32 to 24, pixels.width to pixels.height)
                // The whole cut is one colour: nothing of a neighbouring corner has crept in.
                for ((x, y) in listOf(4 to 4, 27 to 4, 4 to 19, 27 to 19, 16 to 12)) assertColour(colour, pixels.at(x, y), "turned-$n corner $index at $x,$y")
            }
        }
    }

    @Test fun renditionsAreTheSizeTheSharedGeometrySays() = runTest {
        val source = dir / "large.jpg"
        FileSystem.SYSTEM.write(source) { write(testJpeg(3000, 2000)) }
        // Crops whose edges fall on whole pixels, which every platform must get exactly right.
        val aligned = listOf(null, NormalizedRect(0.25f, 0.1f, 0.75f, 0.9f), NormalizedRect(0f, 0f, 0.5f, 1f))
        // Crops whose edges fall between pixels: the shared geometry says which way each goes.
        val between = listOf(NormalizedRect(0.123f, 0.456f, 0.789f, 0.987f), NormalizedRect(0.3333f, 0.1111f, 0.6667f, 0.5555f))
        for (crop in aligned + if (rendersBySharedGeometry) between else emptyList()) {
            for (edge in listOf(2560, 640, 100)) {
                val plan = RenderPlan.of(3000, 2000, Orientation.NORMAL, crop, edge)
                val out = dir / "sized.jpg"
                assertEquals(ImageSize(plan.outWidth, plan.outHeight), processor.render(source, crop, edge, 85, out), "$crop at $edge")
                assertEquals(ImageSize(plan.outWidth, plan.outHeight), processor.size(out), "$crop at $edge: the file itself")
            }
        }
    }

    @Test fun aSmallPictureIsNeverEnlarged() = runTest {
        val out = dir / "small.jpg"
        assertEquals(ImageSize(64, 48), processor.render(fixture("plain.jpg"), null, 4000, 90, out))
        assertEquals(ImageSize(16, 12), processor.render(fixture("plain.jpg"), NormalizedRect(0.25f, 0.25f, 0.5f, 0.5f), 4000, 90, out))
    }

    @Test fun everyStoredFormatShowsTheSamePicture() = runTest {
        val fixtures = listOf("gps-exif-ii.jpg" to PhotoFormat.JPEG, "gps.png" to PhotoFormat.PNG, "gps.webp" to PhotoFormat.WEBP, "gps.heic" to PhotoFormat.HEIC)
        for ((name, format) in fixtures) {
            if (format !in decodableFormats) continue
            val source = fixture(name)
            assertEquals(ImageSize(64, 48), processor.size(source), name)
            val out = dir / "$name.jpg"
            processor.render(source, null, 2560, 90, out)
            assertTheReferencePicture(decodePixels(out), name)
            assertEquals(PhotoFormat.JPEG, PhotoFormat.detect(FileSystem.SYSTEM.read(out) { readByteArray() }), "$name: a rendition is a JPEG")
        }
        assertTrue(PhotoFormat.JPEG in decodableFormats && PhotoFormat.PNG in decodableFormats)
    }

    @Test fun aHeicTurnedInItsOwnWayComesOutUprightOnce() = runTest {
        if (PhotoFormat.HEIC !in decodableFormats) return@runTest
        // A HEIC records its turning in the container. An iPhone repeats it in EXIF, and obeying both would turn the picture twice.
        for (name in listOf("turned-6.heic", "turned-6-iphone.heic")) {
            val source = fixture(name)
            assertEquals(ImageSize(64, 48), processor.size(source), name)
            val out = dir / "$name.jpg"
            assertEquals(ImageSize(64, 48), processor.render(source, null, 2560, 90, out), name)
            assertTheReferencePicture(decodePixels(out), name)
            val corner = dir / "$name-corner.jpg"
            processor.render(source, NormalizedRect(0.5f, 0f, 1f, 0.5f), 2560, 90, corner)
            assertColour(green, decodePixels(corner).at(16, 12), "$name, its top right corner")
        }
    }

    @Test fun aRenditionCarriesNoLocationWhateverTheOriginalHad() = runTest {
        // The original still has its location here: this is the rendition's own promise.
        val out = dir / "rendition.jpg"
        processor.render(fixture("gps-exif-mm.jpg"), null, 2560, 90, out)
        assertFalse(PhotoMetadata.hasLocation(FileSystem.SYSTEM.read(out) { readByteArray() }))
        // And it is upright as it stands, with nothing left to say it should be turned again.
        assertEquals(Orientation.NORMAL, PhotoMetadata.orientation(FileSystem.SYSTEM.read(out) { readByteArray() }))
        assertTheReferencePicture(decodePixels(out), "a rendition of a turned photo")
    }

    @Test fun transparentAreasComeOutBlack() = runTest {
        val out = dir / "opaque.jpg"
        processor.render(fixture("alpha.png"), null, 2560, 90, out)
        val pixels = decodePixels(out)
        assertColour(red, pixels.at(8, 8), "the opaque half, top")
        assertColour(blue, pixels.at(8, 40), "the opaque half, bottom")
        assertColour(black, pixels.at(56, 8), "the transparent half, top")
        assertColour(black, pixels.at(56, 40), "the transparent half, bottom")
    }

    @Test fun aFileThatIsNotAPictureFailsAndLeavesNothingBehind() = runTest {
        val broken = dir / "broken.jpg"
        // A JPEG's first bytes with nothing behind them.
        FileSystem.SYSTEM.write(broken) { write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(60)) }
        val out = dir / "never.jpg"
        assertFails { processor.size(broken) }
        assertFails { processor.render(broken, null, 640, 85, out) }
        assertFalse(FileSystem.SYSTEM.exists(out), "nothing was written")
    }
}
