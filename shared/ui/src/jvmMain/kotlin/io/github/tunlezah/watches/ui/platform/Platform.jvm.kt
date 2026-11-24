package io.github.tunlezah.watches.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

actual fun encodeJpeg(bitmap: ImageBitmap, quality: Int): ByteArray =
    Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
        ?: error("JPEG encoding failed")

// macOS's Reduce Motion setting is read through the Mac bridge, which arrives with milestone mac-2.
actual fun systemAnimationsEnabled(): Boolean = true

actual fun processUptimeMillis(): Long? =
    ProcessHandle.current().info().startInstant().map { System.currentTimeMillis() - it.toEpochMilli() }.orElse(null)

/**
 * A desktop doesn't suspend a running app, but a Mac left alone goes to sleep. While this is shown,
 * macOS's own `caffeinate` holds idle sleep off. It is told to watch this process, so it can't outlive the app.
 */
@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        val hold = runCatching {
            if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) return@runCatching null
            ProcessBuilder("/usr/bin/caffeinate", "-i", "-w", ProcessHandle.current().pid().toString()).start()
        }.getOrNull()
        onDispose { hold?.destroy() }
    }
}

/** No PDF renderer yet (docs/MACOS_PLAN.md, 8.3.11): the viewer offers to open the file in another app instead. */
actual suspend fun openPdf(path: String): PdfDocument? = null
