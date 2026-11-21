package io.github.tunlezah.watches.data

import androidx.room.Room
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.configure
import io.github.tunlezah.watches.data.files.ImageProcessor
import io.github.tunlezah.watches.data.files.IosImageProcessor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSTemporaryDirectory
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.uuid.Uuid

actual fun newTestDir(): Path {
    val dir = NSTemporaryDirectory().toPath() / "watches-test-${Uuid.random()}"
    FileSystem.SYSTEM.createDirectories(dir)
    return dir
}

actual fun openTestDatabase(file: Path): AppDatabase = Room.databaseBuilder<AppDatabase>(name = file.toString()).configure()

actual fun testImageProcessor(): ImageProcessor = IosImageProcessor()

@OptIn(ExperimentalForeignApi::class)
actual fun testJpeg(width: Int, height: Int, red: Int, green: Int, blue: Int): ByteArray {
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
        setScale(1.0)
        setOpaque(true)
    }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width.toDouble(), height.toDouble()), format = format)
    val image = renderer.imageWithActions { context ->
        UIColor(red = red / 255.0, green = green / 255.0, blue = blue / 255.0, alpha = 1.0).setFill()
        context?.fillRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
    }
    val data = UIImageJPEGRepresentation(image, 0.9) ?: error("JPEG encoding failed")
    val out = ByteArray(data.length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return out
}
