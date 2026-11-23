package io.github.tunlezah.watches.data

import platform.UIKit.UIImage
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGBitmapContextCreate
import kotlinx.cinterop.convert
import io.github.tunlezah.watches.data.photos.PhotoFormat
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

// The simulator's files aren't the build machine's: there is nowhere to keep them for a later step.
actual fun keepForInspection(folder: String, name: String, bytes: ByteArray) = Unit

@OptIn(ExperimentalForeignApi::class)
actual fun decodePixels(file: Path): TestPixels {
    val image = UIImage.imageWithContentsOfFile(file.toString()) ?: error("Not a readable image: $file")
    val pixels = image.CGImage ?: error("No pixels in $file")
    val width = CGImageGetWidth(pixels).toInt()
    val height = CGImageGetHeight(pixels).toInt()
    // Drawn into plain memory, four bytes a pixel: red, green, blue and one unused.
    val bytes = ByteArray(width * height * 4)
    bytes.usePinned { pinned ->
        val space = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            pinned.addressOf(0), width.convert(), height.convert(), 8.convert(), (width * 4).convert(), space,
            CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
        )
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), pixels)
        CGContextRelease(context)
        CGColorSpaceRelease(space)
    }
    return TestPixels(width, height, IntArray(width * height) { i ->
        ((bytes[4 * i].toInt() and 0xFF) shl 16) or ((bytes[4 * i + 1].toInt() and 0xFF) shl 8) or (bytes[4 * i + 2].toInt() and 0xFF)
    })
}

actual val decodableFormats: Set<PhotoFormat> = setOf(PhotoFormat.JPEG, PhotoFormat.PNG, PhotoFormat.WEBP, PhotoFormat.HEIC)

actual val rendersBySharedGeometry: Boolean = false
