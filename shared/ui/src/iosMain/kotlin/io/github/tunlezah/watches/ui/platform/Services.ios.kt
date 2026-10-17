package io.github.tunlezah.watches.ui.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import io.github.tunlezah.watches.ui.nav.DeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSDateComponents
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalForeignApi::class)
actual suspend fun openPdf(path: String): PdfDocument? = withContext(Dispatchers.Default) {
    PDFDocument(uRL = NSURL.fileURLWithPath(path))?.let(::IosPdf)
}

/** PDFKit isn't safe to use from several threads at once, so pages render one at a time. */
@OptIn(ExperimentalForeignApi::class)
private class IosPdf(private val doc: PDFDocument) : PdfDocument {
    private val lock = Mutex()
    override val pageCount: Int = doc.pageCount.toInt()

    override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        lock.withLock { draw(index, widthPx) }
    }

    private fun draw(index: Int, widthPx: Int): ImageBitmap? {
        if (index !in 0 until pageCount) return null
        val page = doc.pageAtIndex(index.toULong()) ?: return null
        val (w, h) = page.boundsForBox(kPDFDisplayBoxMediaBox).useContents { size.width to size.height }
        if (w <= 0.0 || h <= 0.0) return null
        // Points, not pixels: UIKit renders at screen scale, so this is already sharp.
        val width = widthPx.coerceIn(200, 2400) / 2.0
        val image = page.thumbnailOfSize(CGSizeMake(width, width * h / w), kPDFDisplayBoxMediaBox)
        val png = UIImagePNGRepresentation(image) ?: return null
        return runCatching { Image.makeFromEncoded(png.toByteArray()).toComposeImageBitmap() }.getOrNull()
    }

    override fun close() = Unit
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

/** Pending local notifications, one per watch, at 9 am on the due date. */
class IosReminders : ReminderScheduler {
    override suspend fun reschedule(reminders: List<ServiceReminder>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.getPendingNotificationRequestsWithCompletionHandler { pending ->
            val ours = pending.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }.filter { it.startsWith(PREFIX) }
            center.removePendingNotificationRequestsWithIdentifiers(ours)
            for (r in reminders) {
                val content = UNMutableNotificationContent()
                content.setTitle("${r.title} is due for a service")
                content.setBody("Its service interval ends today. Open Watches to log the service or snooze by changing the interval.")
                content.setSound(UNNotificationSound.defaultSound())
                val date = NSDateComponents()
                date.setYear(r.due.year.toLong())
                date.setMonth(r.due.month.ordinal.toLong() + 1)
                date.setDay(r.due.day.toLong())
                date.setHour(9)
                val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(date, repeats = false)
                center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(PREFIX + r.watchId, content, trigger), withCompletionHandler = null)
            }
        }
    }

    companion object {
        const val PREFIX = "service-"
    }
}

/**
 * Shows reminders while the app is open, and opens the watch's service history when one is tapped.
 * Set from the app delegate at launch (see `onAppLaunched`), so a tap that launches the app is caught too.
 */
private class ReminderTaps : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val id = didReceiveNotificationResponse.notification.request.identifier
        if (id.startsWith(IosReminders.PREFIX)) DeepLinks.openServiceHistory(id.removePrefix(IosReminders.PREFIX))
        withCompletionHandler()
    }
}

// The notification centre holds its delegate weakly, so it's kept here for the app's lifetime.
private var reminderTaps: ReminderTaps? = null

internal fun installReminderTaps() {
    val taps = reminderTaps ?: ReminderTaps().also { reminderTaps = it }
    UNUserNotificationCenter.currentNotificationCenter().delegate = taps
}

class IosNotificationAccess : NotificationAccess {
    override suspend fun granted(): Boolean = suspendCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            cont.resume(status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional)
        }
    }

    override suspend fun request(): Boolean = suspendCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            cont.resume(granted)
        }
    }
}

class IosFileSharer : FileSharer {
    override fun share(path: String, mimeType: String, title: String) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        var top: UIViewController = root
        while (true) top = top.presentedViewController ?: break
        val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
        sheet.popoverPresentationController?.sourceView = top.view
        top.presentViewController(sheet, animated = true, completion = null)
    }
}

fun iosPlatformModule(): Module = module {
    single<ReminderScheduler> { IosReminders() }
    single<NotificationAccess> { IosNotificationAccess() }
    single<FileSharer> { IosFileSharer() }
}
