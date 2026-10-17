package io.github.tunlezah.watches.ui.platform

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.datetime.LocalDate

/** One service reminder: a local notification on [due] at 9 am. */
data class ServiceReminder(val watchId: String, val title: String, val due: LocalDate)

/** Schedules local notifications only; nothing leaves the device. */
interface ReminderScheduler {
    /** Replaces every scheduled service reminder with [reminders]. */
    suspend fun reschedule(reminders: List<ServiceReminder>)
}

/** Whether the app may post notifications, and asking for it. */
interface NotificationAccess {
    suspend fun granted(): Boolean
    suspend fun request(): Boolean
}

/** Hands a file to the system share sheet. */
fun interface FileSharer {
    fun share(path: String, mimeType: String, title: String)
}

/** An open PDF. Pages render one at a time; close it when the viewer leaves. */
interface PdfDocument {
    val pageCount: Int

    /** Page [index] (0-based) rendered [widthPx] wide on white, or null if the page can't be drawn. */
    suspend fun render(index: Int, widthPx: Int): ImageBitmap?

    fun close()
}

/** Opens a PDF from an absolute path, or null if it isn't a readable PDF. */
expect suspend fun openPdf(path: String): PdfDocument?
