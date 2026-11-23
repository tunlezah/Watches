@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package io.github.tunlezah.watches.bridge

import io.github.tunlezah.watches.data.files.AppleImageCodec
import io.github.tunlezah.watches.data.photos.RenderPlan
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import kotlin.experimental.ExperimentalNativeApi

/*
 * The library's whole surface: plain C functions, called from Java through JNA
 * (shared/data's MacBridge.kt, which declares the same names and arguments).
 *
 * Every function returns 0 when it worked. Otherwise it returns 1 and writes what went wrong, as
 * UTF-8 text ending in a zero byte, into the caller's `error` buffer of `errorSize` bytes. Nothing
 * Kotlin throws may leave a function: an exception that crossed into C would end the whole app.
 *
 * Text arrives as UTF-8. Numbers go in and out as arrays of 32-bit integers that the caller owns.
 */

/** Raised whenever a function's arguments or meaning change. The Java side refuses a library with a different number. */
private const val VERSION = 1

private val codec = AppleImageCodec()

@CName("watches_bridge_version")
fun bridgeVersion(): Int = VERSION

/** The formats ImageIO reads on this Mac: one bit for each, in the order PhotoFormat lists them. */
@CName("watches_photo_formats")
fun photoFormats(): Int = codec.formats.fold(0) { bits, format -> bits or (1 shl format.ordinal) }

/** Fills [out] with a picture's stored width and height, its EXIF orientation (1 to 8), and 1 if ImageIO finds a location in it. */
@CName("watches_photo_probe")
fun photoProbe(path: String, out: CPointer<IntVar>, error: CPointer<ByteVar>?, errorSize: Int): Int = answer(error, errorSize) {
    val info = runBlocking { codec.probe(path.toPath()) }
    out[0] = info.storedWidth
    out[1] = info.storedHeight
    out[2] = info.orientation.exif
    out[3] = if (info.hasLocation) 1 else 0
}

/** Writes [plan]'s cut of [source] to [dest] as a JPEG. [plan] is [RenderPlan.toInts]. */
@CName("watches_photo_render")
fun photoRender(source: String, plan: CPointer<IntVar>, quality: Int, dest: String, error: CPointer<ByteVar>?, errorSize: Int): Int = answer(error, errorSize) {
    val numbers = IntArray(RenderPlan.INTS) { plan[it] }
    runBlocking { codec.render(source.toPath(), RenderPlan.fromInts(numbers), quality, dest.toPath()) }
}

private inline fun answer(error: CPointer<ByteVar>?, errorSize: Int, block: () -> Unit): Int = try {
    block()
    0
} catch (e: Throwable) {
    if (error != null && errorSize > 0) {
        val text = (e.message ?: e.toString()).encodeToByteArray()
        val length = minOf(text.size, errorSize - 1)
        for (i in 0 until length) error[i] = text[i]
        error[length] = 0
    }
    1
}
