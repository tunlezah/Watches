package io.github.tunlezah.watches.ui.platform

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun openPdf(path: String): PdfDocument? = withContext(Dispatchers.IO) {
    val fd = runCatching { ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull() ?: return@withContext null
    runCatching { AndroidPdf(PdfRenderer(fd), fd) }.getOrElse { fd.close(); null }
}

/** PdfRenderer allows one open page at a time, so every page goes through one lock. */
private class AndroidPdf(private val renderer: PdfRenderer, private val fd: ParcelFileDescriptor) : PdfDocument {
    private val lock = Mutex()
    private var closed = false
    override val pageCount: Int = renderer.pageCount

    // The lock is taken and released on the IO thread, so releasing it never waits for the UI thread.
    override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed || index !in 0 until pageCount) return@withLock null
            runCatching {
                renderer.openPage(index).use { page ->
                    val w = widthPx.coerceIn(200, 2400)
                    val h = (w.toFloat() * page.height / page.width).toInt().coerceIn(1, 4000)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    override fun close() {
        // Waits, off the UI thread, for any page being drawn, so the renderer is never closed mid-render.
        closer.launch {
            lock.withLock {
                if (closed) return@withLock
                closed = true
                runCatching { renderer.close() }
                runCatching { fd.close() }
            }
        }
    }

    private companion object {
        val closer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
