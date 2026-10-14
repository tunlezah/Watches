package io.github.tunlezah.watches.ui.platform

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.os.Process
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

actual fun encodeJpeg(bitmap: ImageBitmap, quality: Int): ByteArray =
    ByteArrayOutputStream().use { out ->
        bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

actual fun systemAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

actual fun processUptimeMillis(): Long? = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
