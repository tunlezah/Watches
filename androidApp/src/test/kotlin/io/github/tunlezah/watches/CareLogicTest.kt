package io.github.tunlezah.watches

import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.model.ServiceEntry
import io.github.tunlezah.watches.core.model.ServiceWork
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.data.WatchCare
import io.github.tunlezah.watches.ui.care.ReminderSync
import io.github.tunlezah.watches.ui.care.ServiceDueViewModel
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/** The rules behind reminders and the Service tab, without any UI. */
class CareLogicTest {

    private val today = LocalDate(2026, 10, 17)

    private fun care(
        id: String,
        interval: Int? = 60,
        reminder: Boolean = true,
        status: WatchStatus = WatchStatus.OWNED,
        archived: Boolean = false,
        acquired: LocalDate? = LocalDate(2024, 1, 10),
        lastFull: LocalDate? = null,
        warrantyStart: LocalDate? = null,
        warrantyMonths: Int? = null,
    ) = WatchCare(id, "Watch $id", null, status, archived, acquired, interval, reminder, warrantyStart, warrantyMonths, lastFull, lastFull, null)

    @Test fun remindersOnlyForWatchesOnHandWithAFutureDueDate() {
        val list = listOf(
            care("future"),
            care("off", reminder = false),
            care("overdue", acquired = LocalDate(2020, 1, 1)),
            care("sold", status = WatchStatus.SOLD),
            care("wishlist", status = WatchStatus.WISHLIST),
            care("archived", archived = true),
            care("at-watchmaker", status = WatchStatus.IN_SERVICE),
            care("no-interval", interval = null),
            care("serviced", lastFull = LocalDate(2025, 6, 1)),
        )
        val reminders = ReminderSync.reminders(list, today).associateBy { it.watchId }
        assertEquals(setOf("future", "at-watchmaker", "serviced"), reminders.keys)
        assertEquals(LocalDate(2029, 1, 10), reminders.getValue("future").due)
        assertEquals(LocalDate(2030, 6, 1), reminders.getValue("serviced").due)
        assertEquals("Watch future", reminders.getValue("future").title)
    }

    @Test fun serviceTabGroupsByUrgency() {
        val t = Instant.fromEpochMilliseconds(0)
        val list = listOf(
            care("overdue", acquired = LocalDate(2020, 1, 1)),
            care("soon", acquired = LocalDate(2021, 12, 1)),
            care("later"),
            care("bench", status = WatchStatus.IN_SERVICE),
            care("nostart", acquired = null),
            care("none", interval = null),
            care("warranty", interval = null, warrantyStart = LocalDate(2024, 12, 1), warrantyMonths = 24),
            care("sold", status = WatchStatus.SOLD, acquired = LocalDate(2020, 1, 1)),
        )
        val serviceWarranty = ServiceEntry("e", "later", LocalDate(2024, 12, 20), "Watchmaker", setOf(ServiceWork.REGULATION), null, 24, null, t, t)
        val s = ServiceDueViewModel.build(list, listOf(serviceWarranty), DisplayPrefs(), today)
        assertEquals(listOf("overdue"), s.overdue.map { it.watchId })
        assertTrue(s.overdue.single().urgent)
        assertTrue(s.overdue.single().headline.startsWith("Overdue since 01/01/2025"))
        assertEquals(listOf("soon"), s.soon.map { it.watchId })
        assertEquals(listOf("later"), s.later.map { it.watchId })
        assertEquals(listOf("bench"), s.inService.map { it.watchId })
        assertEquals(listOf("nostart"), s.noStart.map { it.watchId })
        assertEquals(2, s.noInterval)
        assertEquals(setOf("warranty", "later"), s.warranties.map { it.watchId }.toSet())
        assertTrue(s.warranties.single { it.watchId == "warranty" }.opensPapers)
        assertEquals(7, s.onHand)
    }
}
