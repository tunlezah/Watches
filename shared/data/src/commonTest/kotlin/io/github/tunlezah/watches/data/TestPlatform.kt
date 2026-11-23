package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.files.ImageProcessor
import okio.Path

/*
 * What each platform provides to the repository and backup tests, so the same tests run on the JVM
 * and on the iOS simulator against that platform's real database, files and image code.
 */

/** A new, empty folder for one test. */
expect fun newTestDir(): Path

/** The app's database (bundled SQLite, as in the app) in [file]. */
expect fun openTestDatabase(file: Path): AppDatabase

/** The platform's real image processing. */
expect fun testImageProcessor(): ImageProcessor

/** A solid-colour JPEG made by the platform's own encoder. */
expect fun testJpeg(width: Int, height: Int, red: Int = 30, green: Int = 30, blue: Int = 34): ByteArray

/**
 * Keeps a file a test produced, where the platform has somewhere for it. On the JVM that is
 * build/outputs/<folder>, which tools/photo_fixtures.py then checks with an unrelated reader.
 */
expect fun keepForInspection(folder: String, name: String, bytes: ByteArray)

/** A decoded picture for a test to look at. */
class TestPixels(val width: Int, val height: Int, private val rgb: IntArray) {
    /** Red, green and blue, 0 to 255, of the pixel at ([x], [y]). */
    fun at(x: Int, y: Int): Triple<Int, Int, Int> {
        val v = rgb[y * width + x]
        return Triple((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
    }
}

/**
 * Decodes a JPEG the app wrote, with the platform's plain decoder rather than the app's own code,
 * so a test can see what a rendition really shows.
 */
expect fun decodePixels(file: Path): TestPixels
