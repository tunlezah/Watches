package io.github.tunlezah.watches.ui.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

actual fun encodeJpeg(bitmap: ImageBitmap, quality: Int): ByteArray =
    Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
        ?: error("JPEG encoding failed")

actual fun systemAnimationsEnabled(): Boolean = !UIAccessibilityIsReduceMotionEnabled()

actual fun processUptimeMillis(): Long? = null
