package io.github.tunlezah.watches.data

import androidx.room.Room
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.configure
import io.github.tunlezah.watches.data.files.ImageProcessor
import io.github.tunlezah.watches.data.files.JvmImageProcessor
import io.github.tunlezah.watches.data.files.MacBridge
import io.github.tunlezah.watches.data.photos.CodecImageProcessor
import okio.Path
import okio.Path.Companion.toOkioPath
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO

actual fun newTestDir(): Path = Files.createTempDirectory("watches-test").toOkioPath()

actual fun openTestDatabase(file: Path): AppDatabase = Room.databaseBuilder<AppDatabase>(name = file.toString()).configure()

// On a Mac that was given the bridge, these tests run against Apple's codec through it, exactly as the
// Mac app does. A bridge that was named but can't be used fails them, rather than quietly falling back.
actual fun testImageProcessor(): ImageProcessor = MacBridge.imageCodec()?.let(::CodecImageProcessor) ?: JvmImageProcessor()

actual fun testJpeg(width: Int, height: Int, red: Int, green: Int, blue: Int): ByteArray {
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    img.createGraphics().apply { color = Color(red, green, blue); fillRect(0, 0, width, height); dispose() }
    return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
}

actual fun keepForInspection(folder: String, name: String, bytes: ByteArray) {
    java.io.File("build/outputs/$folder").apply { mkdirs() }.resolve(name).writeBytes(bytes)
}

actual fun decodePixels(file: Path): TestPixels {
    val image = ImageIO.read(file.toFile()) ?: error("Not a readable image: $file")
    return TestPixels(image.width, image.height, IntArray(image.width * image.height) { image.getRGB(it % image.width, it / image.width) })
}
