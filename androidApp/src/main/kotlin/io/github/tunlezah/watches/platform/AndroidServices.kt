package io.github.tunlezah.watches.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.tunlezah.watches.MainActivity
import io.github.tunlezah.watches.R
import io.github.tunlezah.watches.ui.platform.FileSharer
import io.github.tunlezah.watches.ui.platform.NotificationAccess
import io.github.tunlezah.watches.ui.platform.ReminderScheduler
import io.github.tunlezah.watches.ui.platform.ServiceReminder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * One WorkManager job per watch, set for 9 am on its due date. Jobs survive restarts and reboots.
 * Dates already past aren't scheduled: the watch shows as overdue in the app instead, and a
 * reminder that already fired isn't repeated every time the app opens.
 */
class WorkManagerReminders(private val context: Context) : ReminderScheduler {
    override fun reschedule(reminders: List<ServiceReminder>) {
        val work = WorkManager.getInstance(context)
        work.cancelAllWorkByTag(TAG)
        val now = Clock.System.now()
        for (r in reminders) {
            val at = r.due.atTime(LocalTime(9, 0)).toInstant(TimeZone.currentSystemDefault())
            val delay = (at - now).inWholeMilliseconds
            if (delay <= 0) continue
            val request = OneTimeWorkRequestBuilder<ServiceReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_WATCH to r.watchId, KEY_TITLE to r.title))
                .addTag(TAG)
                .build()
            work.enqueueUniqueWork("service-${r.watchId}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    companion object {
        const val TAG = "service-reminder"
        const val KEY_WATCH = "watchId"
        const val KEY_TITLE = "title"
    }
}

class ServiceReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val watchId = inputData.getString(WorkManagerReminders.KEY_WATCH) ?: return Result.success()
        val title = inputData.getString(WorkManagerReminders.KEY_TITLE) ?: "A watch"
        Notifications.serviceDue(applicationContext, watchId, title)
        return Result.success()
    }
}

object Notifications {
    private const val CHANNEL = "service"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Service reminders", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "When a watch is due for a service" },
            )
        }
    }

    fun serviceDue(context: Context, watchId: String, title: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SERVICE_WATCH, watchId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, watchId.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_service)
            .setContentTitle("$title is due for a service")
            .setContentText("Its service interval ends today.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(watchId.hashCode(), notification) }
    }
}

/** Asks for POST_NOTIFICATIONS on Android 13+. The activity registers the launcher while it's alive. */
class AndroidNotificationAccess(private val context: Context) : NotificationAccess {
    var launcher: ActivityResultLauncher<String>? = null
    private var pending: CompletableDeferred<Boolean>? = null

    override fun granted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override suspend fun request(): Boolean {
        if (granted()) return true
        val ask = launcher ?: return false
        pending?.complete(false)
        val answer = CompletableDeferred<Boolean>().also { pending = it }
        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        return answer.await()
    }

    fun onResult(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }
}

/** Shares through a FileProvider limited to the documents folder (see res/xml/file_paths.xml). */
class AndroidFileSharer(private val context: Context) : FileSharer {
    override fun share(path: String, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(title, uri)
        val chooser = Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }
}

fun androidPlatformModule(context: Context): Module = module {
    single<ReminderScheduler> { WorkManagerReminders(context) }
    single { AndroidNotificationAccess(context) }
    single<NotificationAccess> { get<AndroidNotificationAccess>() }
    single<FileSharer> { AndroidFileSharer(context) }
}
