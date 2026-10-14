package io.github.tunlezah.watches.data.files

import io.github.tunlezah.watches.core.model.NormalizedRect
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.ImageIO.CGImageDestinationCopyImageSource
import platform.ImageIO.CGImageDestinationCreateWithURL
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.CGImageSourceGetType
import platform.ImageIO.kCGImageMetadataShouldExcludeGPS
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosImageProcessor : ImageProcessor {

    override suspend fun size(file: Path): ImageSize = withContext(Dispatchers.IO) {
        val image = UIImage.imageWithContentsOfFile(file.toString()) ?: error("Not a readable image")
        // UIImage.size already applies the EXIF orientation.
        image.size.useContents { ImageSize((width * image.scale).roundToInt(), (height * image.scale).roundToInt()) }
    }

    override suspend fun stripLocation(file: Path) = withContext(Dispatchers.IO) {
        val path = file.toString()
        val tmp = "$path.strip"
        val srcUrl = CFBridgingRetain(NSURL.fileURLWithPath(path)) as CFURLRef?
        val dstUrl = CFBridgingRetain(NSURL.fileURLWithPath(tmp)) as CFURLRef?
        try {
            val source = CGImageSourceCreateWithURL(srcUrl, null) ?: return@withContext
            try {
                val type = CGImageSourceGetType(source) ?: return@withContext
                val dest = CGImageDestinationCreateWithURL(dstUrl, type, 1u, null) ?: return@withContext
                try {
                    val options = CFDictionaryCreateMutable(null, 1, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                    CFDictionarySetValue(options, kCGImageMetadataShouldExcludeGPS, kCFBooleanTrue)
                    val ok = CGImageDestinationCopyImageSource(dest, source, options, null)
                    CFRelease(options)
                    if (ok) {
                        val fm = NSFileManager.defaultManager
                        fm.removeItemAtPath(path, null)
                        fm.moveItemAtPath(tmp, path, null)
                    }
                } finally {
                    CFRelease(dest)
                }
            } finally {
                CFRelease(source)
            }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(tmp, null)
            CFBridgingRelease(srcUrl)
            CFBridgingRelease(dstUrl)
        }
        Unit
    }

    override suspend fun render(source: Path, crop: NormalizedRect?, maxEdge: Int, quality: Int, dest: Path): ImageSize =
        withContext(Dispatchers.Default) {
            val image = UIImage.imageWithContentsOfFile(source.toString()) ?: error("Not a readable image")
            val (w, h) = image.size.useContents { width * image.scale to height * image.scale }
            val c = crop ?: NormalizedRect.Full
            val cw = c.width * w
            val ch = c.height * h
            val scale = minOf(1.0, maxEdge / max(cw, ch))
            val outW = (cw * scale).roundToInt().coerceAtLeast(1)
            val outH = (ch * scale).roundToInt().coerceAtLeast(1)
            val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
                setScale(1.0)
                setOpaque(true)
            }
            val renderer = UIGraphicsImageRenderer(size = CGSizeMake(outW.toDouble(), outH.toDouble()), format = format)
            val out = renderer.imageWithActions { _ ->
                // drawInRect honours the image's orientation.
                image.drawInRect(CGRectMake(-c.left * w * scale, -c.top * h * scale, w * scale, h * scale))
            }
            val data = UIImageJPEGRepresentation(out, quality / 100.0) ?: error("JPEG encoding failed")
            dest.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            check(data.writeToFile(dest.toString(), atomically = true)) { "Could not write $dest" }
            ImageSize(outW, outH)
        }
}
