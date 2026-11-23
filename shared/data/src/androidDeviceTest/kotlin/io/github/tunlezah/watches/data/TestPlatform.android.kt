package io.github.tunlezah.watches.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.configure
import io.github.tunlezah.watches.data.files.AndroidImageProcessor
import io.github.tunlezah.watches.data.files.ImageProcessor
import io.github.tunlezah.watches.data.photos.PhotoFormat
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

actual fun newTestDir(): Path = File(context.cacheDir, "watches-test-${UUID.randomUUID()}").apply { mkdirs() }.toOkioPath()

actual fun openTestDatabase(file: Path): AppDatabase = Room.databaseBuilder<AppDatabase>(context = context, name = file.toString()).configure()

actual fun testImageProcessor(): ImageProcessor = AndroidImageProcessor()

actual fun testJpeg(width: Int, height: Int, red: Int, green: Int, blue: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(red, green, blue)) }
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray().also { bitmap.recycle() }
}

// The emulator's files aren't the build machine's: there is nowhere to keep them for a later step.
actual fun keepForInspection(folder: String, name: String, bytes: ByteArray) = Unit

actual fun decodePixels(file: Path): TestPixels {
    val bitmap = BitmapFactory.decodeFile(file.toString()) ?: error("Not a readable image: $file")
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return TestPixels(bitmap.width, bitmap.height, pixels).also { bitmap.recycle() }
}

actual val decodableFormats: Set<PhotoFormat> = setOf(PhotoFormat.JPEG, PhotoFormat.PNG, PhotoFormat.WEBP, PhotoFormat.HEIC)

actual val rendersBySharedGeometry: Boolean = true
