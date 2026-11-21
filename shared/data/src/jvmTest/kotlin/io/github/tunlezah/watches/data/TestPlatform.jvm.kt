package io.github.tunlezah.watches.data

import androidx.room.Room
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.configure
import io.github.tunlezah.watches.data.files.ImageProcessor
import io.github.tunlezah.watches.data.files.JvmImageProcessor
import okio.Path
import okio.Path.Companion.toOkioPath
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO

actual fun newTestDir(): Path = Files.createTempDirectory("watches-test").toOkioPath()

actual fun openTestDatabase(file: Path): AppDatabase = Room.databaseBuilder<AppDatabase>(name = file.toString()).configure()

actual fun testImageProcessor(): ImageProcessor = JvmImageProcessor()

actual fun testJpeg(width: Int, height: Int, red: Int, green: Int, blue: Int): ByteArray {
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    img.createGraphics().apply { color = Color(red, green, blue); fillRect(0, 0, width, height); dispose() }
    return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
}
