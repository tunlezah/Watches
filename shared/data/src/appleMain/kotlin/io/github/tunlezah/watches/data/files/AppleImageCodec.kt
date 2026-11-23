package io.github.tunlezah.watches.data.files

import io.github.tunlezah.watches.data.photos.CodecImageProcessor
import io.github.tunlezah.watches.data.photos.ImageCodec
import io.github.tunlezah.watches.data.photos.ImageInfo
import io.github.tunlezah.watches.data.photos.Orientation
import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.tunlezah.watches.data.photos.RenderPlan
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.Path
import platform.CoreFoundation.CFDictionaryContainsKey
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFURLCreateFromFileSystemRepresentation
import platform.CoreFoundation.CFURLRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberDoubleType
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.CoreGraphics.kCGInterpolationHigh
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithURL
import platform.ImageIO.CGImageDestinationFinalize
import platform.Foundation.CFBridgingRelease
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCopyTypeIdentifiers
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImageDestinationLossyCompressionQuality
import platform.ImageIO.kCGImagePropertyGPSDictionary
import platform.ImageIO.kCGImagePropertyGPSLatitude
import platform.ImageIO.kCGImagePropertyGPSLongitude
import platform.ImageIO.kCGImagePropertyOrientation
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize

/**
 * Apple's codec, on ImageIO, for the iPhone and the Mac alike: the two share this one file
 * (docs/MACOS_PLAN.md, 8.2.5). What to cut and how large to write come from the shared [RenderPlan].
 *
 * ImageIO reads a file's size and turning from its header without decoding it, and decodes
 * straight to a reduced size, already upright. So a photo is never held at full size unless the
 * crop really needs every pixel: a 48-megapixel photo's thumbnail costs a few megabytes, where
 * decoding it whole costs about 190.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class AppleImageCodec : ImageCodec {

    // Asked of ImageIO itself rather than assumed: AVIF and WebP arrived in different system versions.
    override val formats: Set<PhotoFormat> by lazy {
        val known = (CFBridgingRelease(CGImageSourceCopyTypeIdentifiers()) as? List<*>).orEmpty().toSet()
        TYPE_NAMES.filterValues { it in known }.keys
    }

    override suspend fun probe(file: Path): ImageInfo = withContext(Dispatchers.IO) {
        withSource(file) { source ->
            val properties = CGImageSourceCopyPropertiesAtIndex(source, 0.convert(), null) ?: error("Not a readable image")
            try {
                val width = int(properties, kCGImagePropertyPixelWidth) ?: error("Not a readable image")
                val height = int(properties, kCGImagePropertyPixelHeight) ?: error("Not a readable image")
                require(width > 0 && height > 0) { "Not a readable image" }
                // ImageIO reports one turning for every format, a HEIC's own rotation included, and applies that same one below.
                val gps: CFDictionaryRef? = CFDictionaryGetValue(properties, kCGImagePropertyGPSDictionary)?.reinterpret()
                val located = gps != null && (CFDictionaryContainsKey(gps, kCGImagePropertyGPSLatitude) || CFDictionaryContainsKey(gps, kCGImagePropertyGPSLongitude))
                ImageInfo(width, height, Orientation.fromExif(int(properties, kCGImagePropertyOrientation)), hasLocation = located)
            } finally {
                CFRelease(properties)
            }
        }
    }

    override suspend fun render(source: Path, plan: RenderPlan, quality: Int, dest: Path) = withContext(Dispatchers.Default) {
        withSource(source) { image ->
            // The smallest decode at which the region still has every pixel the output needs.
            val displayLong = maxOf(plan.displayWidth, plan.displayHeight).toLong()
            val regionLong = maxOf(plan.region.width, plan.region.height).toLong()
            val outLong = maxOf(plan.outWidth, plan.outHeight).toLong()
            val decodeLong = minOf(displayLong, (outLong * displayLong + regionLong - 1) / regionLong).toInt().coerceAtLeast(1)
            val upright = decodeUpright(image, decodeLong) ?: error("Could not decode image")
            try {
                val drawn = draw(upright, plan) ?: error("Could not draw image")
                try {
                    write(drawn, quality, dest)
                } finally {
                    CGImageRelease(drawn)
                }
            } finally {
                CGImageRelease(upright)
            }
        }
    }

    /** The whole picture, turned upright by ImageIO and no longer than [longEdge] on its long side. Never enlarged. */
    private fun decodeUpright(source: CGImageSourceRef, longEdge: Int): CGImageRef? {
        val options = dictionary()
        try {
            CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
            CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
            CFDictionarySetValue(options, kCGImageSourceShouldCacheImmediately, kCFBooleanTrue)
            setNumber(options, kCGImageSourceThumbnailMaxPixelSize, longEdge)
            return CGImageSourceCreateThumbnailAtIndex(source, 0.convert(), options)
        } finally {
            CFRelease(options)
        }
    }

    /**
     * The plan's region of [upright], at exactly the plan's output size, in sRGB. The canvas starts
     * black, which is what shows wherever the picture is transparent.
     */
    private fun draw(upright: CGImageRef, plan: RenderPlan): CGImageRef? {
        val space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB) ?: return null
        val context = CGBitmapContextCreate(
            null, plan.outWidth.convert(), plan.outHeight.convert(), 8.convert(), 0.convert(), space,
            CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
        )
        CGColorSpaceRelease(space)
        context ?: return null
        try {
            val w = plan.outWidth.toDouble()
            val h = plan.outHeight.toDouble()
            CGContextSetRGBFillColor(context, 0.0, 0.0, 0.0, 1.0)
            CGContextFillRect(context, CGRectMake(0.0, 0.0, w, h))
            CGContextSetInterpolationQuality(context, kCGInterpolationHigh)
            // The whole picture is placed so that the region lands on the canvas. Scale: output pixels per
            // picture pixel. Core Graphics counts y from the bottom, so the region's bottom edge sets the offset.
            val fx = w / plan.region.width
            val fy = h / plan.region.height
            CGContextDrawImage(
                context,
                CGRectMake(-plan.region.left * fx, -(plan.displayHeight - plan.region.bottom) * fy, plan.displayWidth * fx, plan.displayHeight * fy),
                upright,
            )
            return CGBitmapContextCreateImage(context)
        } finally {
            CGContextRelease(context)
        }
    }

    /** A JPEG with nothing in it but the picture: no location, no turning, sRGB. */
    private fun write(image: CGImageRef, quality: Int, dest: Path) {
        val url = fileUrl(dest) ?: error("Could not write $dest")
        val type = CFStringCreateWithCString(null, "public.jpeg", kCFStringEncodingUTF8)
        val properties = dictionary()
        try {
            val destination = CGImageDestinationCreateWithURL(url, type, 1.convert(), null) ?: error("Could not write $dest")
            try {
                memScoped {
                    val value = alloc<DoubleVar>().apply { this.value = quality.coerceIn(1, 100) / 100.0 }
                    val number = CFNumberCreate(null, kCFNumberDoubleType, value.ptr)
                    CFDictionarySetValue(properties, kCGImageDestinationLossyCompressionQuality, number)
                    CFRelease(number)
                }
                CGImageDestinationAddImage(destination, image, properties)
                check(CGImageDestinationFinalize(destination)) { "Could not write $dest" }
            } finally {
                CFRelease(destination)
            }
        } finally {
            CFRelease(properties)
            CFRelease(type)
            CFRelease(url)
        }
    }

    /**
     * Opens [file] for [block]. In a pool of its own, so whatever the frameworks set aside while
     * decoding is freed when the photo is done with, on whichever thread this runs: a background
     * thread has no pool of its own, and an import handles many photos in a row.
     */
    private fun <T> withSource(file: Path, block: (CGImageSourceRef) -> T): T = autoreleasepool {
        val url = fileUrl(file) ?: error("Not a readable image")
        try {
            val source = CGImageSourceCreateWithURL(url, null) ?: error("Not a readable image")
            try {
                block(source)
            } finally {
                CFRelease(source)
            }
        } finally {
            CFRelease(url)
        }
    }

    private fun fileUrl(path: Path): CFURLRef? {
        val bytes = path.toString().encodeToByteArray()
        return memScoped {
            val buffer = allocArray<UByteVar>(bytes.size + 1)
            for (i in bytes.indices) buffer[i] = bytes[i].toUByte()
            CFURLCreateFromFileSystemRepresentation(null, buffer, bytes.size.convert(), false)
        }
    }

    private fun dictionary(): CFMutableDictionaryRef =
        CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr) ?: error("Out of memory")

    private fun setNumber(into: CFMutableDictionaryRef, key: CFStringRef?, number: Int) = memScoped {
        val value = alloc<IntVar>().apply { this.value = number }
        val boxed = CFNumberCreate(null, kCFNumberIntType, value.ptr)
        CFDictionarySetValue(into, key, boxed)
        CFRelease(boxed)
    }

    private fun int(from: CFDictionaryRef, key: CFStringRef?): Int? = memScoped {
        val number = CFDictionaryGetValue(from, key) ?: return null
        val out = alloc<IntVar>()
        if (CFNumberGetValue(number.reinterpret(), kCFNumberIntType, out.ptr)) out.value else null
    }
}

/** Apple's names for the formats the app knows. */
private val TYPE_NAMES = mapOf(
    PhotoFormat.JPEG to "public.jpeg", PhotoFormat.PNG to "public.png", PhotoFormat.WEBP to "org.webmproject.webp",
    PhotoFormat.HEIC to "public.heic", PhotoFormat.AVIF to "public.avif", PhotoFormat.TIFF to "public.tiff",
    PhotoFormat.GIF to "com.compuserve.gif", PhotoFormat.BMP to "com.microsoft.bmp",
)

/** The shared photo rules on Apple's codec. */
class AppleImageProcessor : CodecImageProcessor(AppleImageCodec())
