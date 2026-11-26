package io.github.tunlezah.watches.data.files

import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.tunlezah.watches.data.testImageProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which image code these JVM tests ran on. On a Mac that was given the bridge (the macOS workflow
 * does that) every other test in this module has just run against Apple's codec through it, the
 * conformance suite included; this confirms that is really what happened. Anywhere else there is
 * no bridge, and the plain-Java codec stood in, with the system's HEIC and WebP decoders where the
 * machine has them (Linux CI installs them, as the server image does).
 */
class MacBridgeTest {

    @Test fun theBridgeIsUsedExactlyWhereOneWasGiven() {
        val formats = testImageProcessor().readableFormats
        if (MacBridge.path() == null) {
            assertFalse(MacBridge.isLoaded)
            assertNull(MacBridge.problem)
            assertNull(MacBridge.imageCodec())
            // Plain Java reads no HEIC or WebP itself. It takes them from the system's libraries where they're installed (the server).
            assertEquals(SystemImageLibraries.heic != null, PhotoFormat.HEIC in formats, "HEIC exactly where libheif loaded: $formats")
            assertEquals(SystemImageLibraries.webp != null, PhotoFormat.WEBP in formats, "WebP exactly where libwebp loaded: $formats")
            assertEquals(formats, MacBridge.imageProcessor().readableFormats, "the app gets the same image code as these tests")
            val with = if (SystemImageLibraries.decoders.isEmpty()) "" else " with the system's ${SystemImageLibraries.decoders.joinToString { it.format.name }} decoders"
            println("MacBridgeTest: no bridge on this machine; the tests ran on the plain-Java codec$with, which reads $formats")
            return
        }
        assertNull(MacBridge.problem, "the bridge at ${MacBridge.path()} should load")
        assertTrue(MacBridge.isLoaded)
        // What the Mac's own decoders read, and plain Java doesn't.
        for (format in listOf(PhotoFormat.JPEG, PhotoFormat.PNG, PhotoFormat.WEBP, PhotoFormat.HEIC, PhotoFormat.TIFF, PhotoFormat.GIF, PhotoFormat.BMP)) {
            assertTrue(format in formats, "the Mac reads $format: $formats")
        }
        assertEquals(formats, MacBridge.imageProcessor().readableFormats, "the app gets the same image code as these tests")
        println("MacBridgeTest: the tests ran on Apple's codec through ${MacBridge.path()}, which reads $formats")
    }
}
