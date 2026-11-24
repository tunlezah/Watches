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
 * no bridge, and the plain-Java codec stood in.
 */
class MacBridgeTest {

    @Test fun theBridgeIsUsedExactlyWhereOneWasGiven() {
        val formats = testImageProcessor().readableFormats
        if (MacBridge.path() == null) {
            assertFalse(MacBridge.isLoaded)
            assertNull(MacBridge.problem)
            assertNull(MacBridge.imageCodec())
            assertFalse(PhotoFormat.HEIC in formats, "plain Java reads no HEIC: $formats")
            println("MacBridgeTest: no bridge on this machine; the tests ran on the plain-Java codec, which reads $formats")
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
