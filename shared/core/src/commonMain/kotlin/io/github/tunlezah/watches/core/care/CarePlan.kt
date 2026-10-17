package io.github.tunlezah.watches.core.care

import io.github.tunlezah.watches.core.model.BoxItem
import io.github.tunlezah.watches.core.model.BoxItemState
import io.github.tunlezah.watches.core.model.BoxPapersItem
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

enum class DueState {
    /** Past its due date. */
    OVERDUE,

    /** Due within [CarePlan.SOON_DAYS]. */
    DUE_SOON,
    SCHEDULED,

    /** No interval set, so nothing is due. */
    NO_INTERVAL,

    /** An interval is set but there's nothing to count from: no full service logged and no date acquired. */
    NO_START,
}

/** What the next service is counted from. */
enum class DueBasis(val label: String) {
    FULL_SERVICE("the last full service"),
    ACQUIRED("the date acquired"),
}

data class ServiceStatus(
    /** The most recent entry of any kind (a battery change counts). */
    val lastService: LocalDate?,
    val nextDue: LocalDate?,
    val basis: DueBasis?,
    val state: DueState,
    /** Days until due; negative when overdue. Null when nothing is due. */
    val daysLeft: Int?,
)

enum class WarrantyState { ACTIVE, ENDING_SOON, EXPIRED }

data class WarrantyStatus(val until: LocalDate, val state: WarrantyState, val daysLeft: Int)

data class BoxPapersSummary(val present: Int, val missing: Int, val notApplicable: Int, val unrecorded: Int) {
    val applicable: Int get() = present + missing
    val recorded: Int get() = present + missing + notApplicable

    /** Every item recorded, at least one present, none missing. */
    val fullSet: Boolean get() = unrecorded == 0 && missing == 0 && present > 0
}

/**
 * The service and warranty rules, in one place so the watch, the Service tab and reminders agree.
 *
 * The next service is due one interval after the last **full** service: a battery change or a
 * regulation doesn't restart the clock. With no full service logged, it counts from the date acquired.
 */
object CarePlan {
    const val SOON_DAYS = 90

    fun service(intervalMonths: Int?, acquiredOn: LocalDate?, lastService: LocalDate?, lastFullService: LocalDate?, today: LocalDate): ServiceStatus {
        val interval = intervalMonths?.takeIf { it > 0 } ?: return ServiceStatus(lastService, null, null, DueState.NO_INTERVAL, null)
        val (start, basis) = when {
            lastFullService != null -> lastFullService to DueBasis.FULL_SERVICE
            acquiredOn != null -> acquiredOn to DueBasis.ACQUIRED
            else -> return ServiceStatus(lastService, null, null, DueState.NO_START, null)
        }
        val due = start.plus(interval, DateTimeUnit.MONTH)
        val days = today.daysUntil(due)
        val state = when {
            days < 0 -> DueState.OVERDUE
            days <= SOON_DAYS -> DueState.DUE_SOON
            else -> DueState.SCHEDULED
        }
        return ServiceStatus(lastService, due, basis, state, days)
    }

    fun warranty(start: LocalDate?, months: Int?, today: LocalDate): WarrantyStatus? {
        val s = start ?: return null
        val m = months?.takeIf { it > 0 } ?: return null
        val until = s.plus(m, DateTimeUnit.MONTH)
        val days = today.daysUntil(until)
        val state = when {
            days < 0 -> WarrantyState.EXPIRED
            days <= SOON_DAYS -> WarrantyState.ENDING_SOON
            else -> WarrantyState.ACTIVE
        }
        return WarrantyStatus(until, state, days)
    }

    fun boxPapers(items: Collection<BoxPapersItem>): BoxPapersSummary {
        val byItem = items.associateBy { it.item }
        val states = BoxItem.entries.map { byItem[it]?.state }
        return BoxPapersSummary(
            present = states.count { it == BoxItemState.PRESENT },
            missing = states.count { it == BoxItemState.MISSING },
            notApplicable = states.count { it == BoxItemState.NOT_APPLICABLE },
            unrecorded = states.count { it == null },
        )
    }

    /** "in 3 months", "today", "2 weeks ago": short, for lists. */
    fun relative(days: Int): String {
        val past = days < 0
        val n = kotlin.math.abs(days)
        val text = when {
            n == 0 -> return "today"
            n == 1 -> "1 day"
            n < 14 -> "$n days"
            n < 61 -> "${n / 7} weeks"
            n < 730 -> "${n / 30} months"
            else -> "${n / 365} years"
        }
        return if (past) "$text ago" else "in $text"
    }
}
