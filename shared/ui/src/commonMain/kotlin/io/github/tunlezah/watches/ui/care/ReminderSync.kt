package io.github.tunlezah.watches.ui.care

import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.care.CarePlan
import io.github.tunlezah.watches.data.ServiceRepository
import io.github.tunlezah.watches.data.WatchCare
import io.github.tunlezah.watches.ui.platform.ReminderScheduler
import io.github.tunlezah.watches.ui.platform.ServiceReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Keeps the platform's scheduled notifications in step with the collection: whenever a watch's
 * interval, reminder switch, status or service history changes, the reminders are rescheduled.
 */
class ReminderSync(
    private val services: ServiceRepository,
    private val scheduler: ReminderScheduler,
    private val time: TimeSource,
) {
    private var job: Job? = null

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            services.observeCare()
                .map { reminders(it, time.now().toLocalDateTime(TimeZone.currentSystemDefault()).date) }
                .distinctUntilChanged()
                .debounce(500)
                .collect { runCatching { scheduler.reschedule(it) } }
        }
    }

    companion object {
        /** Watches on hand with reminders on and a due date today or later. */
        fun reminders(care: List<WatchCare>, today: LocalDate): List<ServiceReminder> = care
            .filter { it.needsCare && it.reminder }
            .mapNotNull { w ->
                val due = CarePlan.service(w.intervalMonths, w.acquiredOn, w.lastService, w.lastFullService, today).nextDue
                due?.takeIf { it >= today }?.let { ServiceReminder(w.id, w.title, it) }
            }
            .sortedBy { it.watchId }
    }
}
