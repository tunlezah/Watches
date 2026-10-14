package io.github.tunlezah.watches.ui.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Encodes a bitmap as JPEG. Used to turn generated sample illustrations into photo files. */
expect fun encodeJpeg(bitmap: ImageBitmap, quality: Int): ByteArray

/** False when the user has asked the system to remove or reduce animations. */
expect fun systemAnimationsEnabled(): Boolean

/** Milliseconds since this process started, when the platform can tell; used for the cold-start timing. */
expect fun processUptimeMillis(): Long?
