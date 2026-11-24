package io.github.tunlezah.watches.data

import androidx.room.Room
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.configure
import io.github.tunlezah.watches.data.files.AppleImageProcessor
import io.github.tunlezah.watches.data.files.ImageProcessor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFURLCreateFromFileSystemRepresentation
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.Foundation.NSTemporaryDirectory
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import kotlin.uuid.Uuid

/*
 * The same helpers for the iPhone simulator and the Mac: Core Graphics and ImageIO are the same on
 * both, and nothing here needs UIKit.
 */

actual fun newTestDir(): Path {
    val dir = NSTemporaryDirectory().toPath() / "watches-test-${Uuid.random()}"
    FileSystem.SYSTEM.createDirectories(dir)
    return dir
}

actual fun openTestDatabase(file: Path): AppDatabase = Room.databaseBuilder<AppDatabase>(name = file.toString()).configure()

actual fun testImageProcessor(): ImageProcessor = AppleImageProcessor()

/** A canvas of [width] by [height] in sRGB, four bytes a pixel: red, green, blue and one unused. */
@OptIn(ExperimentalForeignApi::class)
private fun <T> withCanvas(width: Int, height: Int, memory: kotlinx.cinterop.CPointer<*>?, block: (platform.CoreGraphics.CGContextRef) -> T): T {
    val space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
    val context = CGBitmapContextCreate(
        memory, width.convert(), height.convert(), 8.convert(), (width * 4).convert(), space,
        CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
    ) ?: error("No canvas of $width x $height")
    CGColorSpaceRelease(space)
    try {
        return block(context)
    } finally {
        CGContextRelease(context)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun testJpeg(width: Int, height: Int, red: Int, green: Int, blue: Int): ByteArray {
    val image = withCanvas(width, height, null) { context ->
        CGContextSetRGBFillColor(context, red / 255.0, green / 255.0, blue / 255.0, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        CGBitmapContextCreateImage(context) ?: error("No image")
    }
    val data = CFDataCreateMutable(null, 0) ?: error("Out of memory")
    val type = CFStringCreateWithCString(null, "public.jpeg", kCFStringEncodingUTF8)
    try {
        val destination = CGImageDestinationCreateWithData(data, type, 1.convert(), null) ?: error("No JPEG writer")
        try {
            CGImageDestinationAddImage(destination, image, null)
            check(CGImageDestinationFinalize(destination)) { "JPEG encoding failed" }
        } finally {
            CFRelease(destination)
        }
        val length = CFDataGetLength(data).toInt()
        val bytes = CFDataGetBytePtr(data) ?: error("No JPEG data")
        return ByteArray(length) { bytes[it].toByte() }
    } finally {
        CFRelease(type)
        CFRelease(data)
        CGImageRelease(image)
    }
}

// The simulator's files aren't the build machine's: there is nowhere to keep them for a later step.
actual fun keepForInspection(folder: String, name: String, bytes: ByteArray) = Unit

@OptIn(ExperimentalForeignApi::class)
actual fun decodePixels(file: Path): TestPixels {
    val name = file.toString().encodeToByteArray()
    val url = memScoped {
        val buffer = allocArray<UByteVar>(name.size + 1)
        for (i in name.indices) buffer[i] = name[i].toUByte()
        CFURLCreateFromFileSystemRepresentation(null, buffer, name.size.convert(), false)
    } ?: error("Not a path: $file")
    val source = CGImageSourceCreateWithURL(url, null)
    CFRelease(url)
    source ?: error("Not a readable image: $file")
    val image = CGImageSourceCreateImageAtIndex(source, 0.convert(), null)
    CFRelease(source)
    image ?: error("Not a readable image: $file")
    try {
        val width = CGImageGetWidth(image).toInt()
        val height = CGImageGetHeight(image).toInt()
        val bytes = ByteArray(width * height * 4)
        bytes.usePinned { pinned ->
            withCanvas(width, height, pinned.addressOf(0)) { context ->
                CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image)
            }
        }
        return TestPixels(width, height, IntArray(width * height) { i ->
            ((bytes[4 * i].toInt() and 0xFF) shl 16) or ((bytes[4 * i + 1].toInt() and 0xFF) shl 8) or (bytes[4 * i + 2].toInt() and 0xFF)
        })
    } finally {
        CGImageRelease(image)
    }
}
