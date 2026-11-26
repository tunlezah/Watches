package io.github.tunlezah.watches.data.files

import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.data.TestPixels
import io.github.tunlezah.watches.data.decodePixels
import io.github.tunlezah.watches.data.newTestDir
import io.github.tunlezah.watches.data.photos.PhotoFixtures
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the plain-Java codec does when a picture is far larger than its rendition, which the
 * conformance suite's small fixtures don't reach: on the server it makes every thumbnail of a
 * phone's photo. Its HEIC and WebP cases run where the system's libraries are installed.
 */
class JvmImageCodecTest {

    private val dir = newTestDir()
    private val processor = JvmImageProcessor()

    @AfterTest
    fun tearDown() = FileSystem.SYSTEM.deleteRecursively(dir)

    private val red = Triple(200, 30, 30)
    private val green = Triple(30, 160, 60)
    private val blue = Triple(40, 60, 200)
    private val yellow = Triple(220, 200, 40)

    private fun assertColour(expected: Triple<Int, Int, Int>, actual: Triple<Int, Int, Int>, what: String) {
        val close = abs(expected.first - actual.first) <= 40 && abs(expected.second - actual.second) <= 40 && abs(expected.third - actual.third) <= 40
        assertTrue(close, "$what: expected about $expected, got $actual")
    }

    /** The four corner colours, checked a quarter of the way in from each corner. */
    private fun assertQuarters(pixels: TestPixels, what: String) {
        val (w, h) = pixels.width to pixels.height
        assertColour(red, pixels.at(w / 4, h / 4), "$what, top left")
        assertColour(green, pixels.at(3 * w / 4, h / 4), "$what, top right")
        assertColour(blue, pixels.at(w / 4, 3 * h / 4), "$what, bottom left")
        assertColour(yellow, pixels.at(3 * w / 4, 3 * h / 4), "$what, bottom right")
    }

    private fun png(name: String, image: BufferedImage): Path = (dir / name).also { ImageIO.write(image, "png", it.toFile()) }

    private fun quarters(width: Int, height: Int): Path {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            for ((colour, x, y) in listOf(Triple(red, 0, 0), Triple(green, width / 2, 0), Triple(blue, 0, height / 2), Triple(yellow, width / 2, height / 2))) {
                color = Color(colour.first, colour.second, colour.third)
                fillRect(x, y, width / 2, height / 2)
            }
            dispose()
        }
        return png("quarters.png", image)
    }

    @Test fun aLargePictureShrunkALongWayKeepsItsLayout() = runTest {
        val source = quarters(4000, 3000)
        for (edge in listOf(2560, 640, 100, 40)) {
            val out = dir / "whole-$edge.jpg"
            val size = processor.render(source, null, edge, 90, out)
            val pixels = decodePixels(out)
            assertEquals(size.width to size.height, pixels.width to pixels.height, "at $edge")
            assertEquals(edge, maxOf(pixels.width, pixels.height), "at $edge")
            assertQuarters(pixels, "the whole picture at $edge")
        }
        // A crop of one quarter, far smaller than it: only that quarter's colour.
        val out = dir / "corner.jpg"
        processor.render(source, NormalizedRect(0.5f, 0f, 1f, 0.5f), 64, 90, out)
        val pixels = decodePixels(out)
        for ((x, y) in listOf(4 to 4, pixels.width - 5 to 4, 4 to pixels.height - 5, pixels.width - 5 to pixels.height - 5)) {
            assertColour(green, pixels.at(x, y), "the top right quarter at $x,$y")
        }
    }

    @Test fun fineDetailShrunkALongWayComesOutEvenNotPatterned() = runTest {
        // One-pixel black and white squares. Averaged properly they're an even grey; sampled with a
        // large step they'd come out as stripes and blotches of black and white.
        val size = 1600 to 1200
        val image = BufferedImage(size.first, size.second, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until size.second) for (x in 0 until size.first) image.setRGB(x, y, if ((x + y) % 2 == 0) 0xFFFFFF else 0)
        val out = dir / "even.jpg"
        processor.render(png("checks.png", image), null, 160, 95, out)
        val pixels = decodePixels(out)
        for (y in 2 until pixels.height - 2 step 7) for (x in 2 until pixels.width - 2 step 7) {
            val (r, g, b) = pixels.at(x, y)
            assertTrue(listOf(r, g, b).all { it in 100..156 }, "an even grey at $x,$y, not ($r, $g, $b)")
        }
    }

    @Test fun theSystemsHeicDecoderAveragesAnUprightPicture() = runTest {
        if (SystemImageLibraries.heic == null) return@runTest
        // 128 by 96 shrunk to 16 by 12: libheif turns each one upright, and the codec averages it down while copying it out.
        for (n in 1..8) {
            val name = "large-turned-$n.heic"
            val source = (dir / name).also { path -> FileSystem.SYSTEM.write(path) { write(PhotoFixtures.bytes(name)) } }
            val out = dir / "$name.jpg"
            assertEquals(ImageSize(16, 12), processor.render(source, null, 16, 90, out), name)
            assertQuarters(decodePixels(out), name)
        }
    }

    @Test fun theSystemsWebpDecoderAveragesToo() = runTest {
        if (SystemImageLibraries.webp == null) return@runTest
        val source = (dir / "gps.webp").also { path -> FileSystem.SYSTEM.write(path) { write(PhotoFixtures.bytes("gps.webp")) } }
        val out = dir / "webp.jpg"
        assertEquals(ImageSize(16, 12), processor.render(source, null, 16, 90, out))
        assertQuarters(decodePixels(out), "gps.webp")
    }
}
