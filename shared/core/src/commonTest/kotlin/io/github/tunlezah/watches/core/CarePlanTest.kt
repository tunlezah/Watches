package io.github.tunlezah.watches.core

import io.github.tunlezah.watches.core.care.CarePlan
import io.github.tunlezah.watches.core.care.DueBasis
import io.github.tunlezah.watches.core.care.DueState
import io.github.tunlezah.watches.core.care.WarrantyState
import io.github.tunlezah.watches.core.model.BoxItem
import io.github.tunlezah.watches.core.model.BoxItemState
import io.github.tunlezah.watches.core.model.BoxPapersItem
import io.github.tunlezah.watches.core.model.ServiceEntry
import io.github.tunlezah.watches.core.model.ServiceWork
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CarePlanTest {

    private val today = LocalDate(2026, 10, 17)

    @Test fun dueCountsFromTheLastFullService() {
        val s = CarePlan.service(60, acquiredOn = LocalDate(2018, 1, 1), lastService = LocalDate(2025, 6, 1), lastFullService = LocalDate(2022, 3, 14), today = today)
        assertEquals(LocalDate(2027, 3, 14), s.nextDue)
        assertEquals(DueBasis.FULL_SERVICE, s.basis)
        assertEquals(DueState.SCHEDULED, s.state)
        // A battery change in 2025 shows as the last service but doesn't restart the clock.
        assertEquals(LocalDate(2025, 6, 1), s.lastService)
    }

    @Test fun withoutAFullServiceItCountsFromAcquisition() {
        val s = CarePlan.service(48, acquiredOn = LocalDate(2022, 9, 1), lastService = null, lastFullService = null, today = today)
        assertEquals(LocalDate(2026, 9, 1), s.nextDue)
        assertEquals(DueBasis.ACQUIRED, s.basis)
        assertEquals(DueState.OVERDUE, s.state)
        assertEquals(-46, s.daysLeft)
    }

    @Test fun soonAndMissingInputs() {
        assertEquals(DueState.DUE_SOON, CarePlan.service(12, LocalDate(2025, 12, 1), null, null, today).state)
        assertEquals(DueState.NO_INTERVAL, CarePlan.service(null, LocalDate(2025, 1, 1), null, null, today).state)
        assertEquals(DueState.NO_START, CarePlan.service(60, null, LocalDate(2025, 1, 1), null, today).state)
        assertNull(CarePlan.service(0, LocalDate(2025, 1, 1), null, null, today).nextDue)
    }

    @Test fun warranty() {
        assertEquals(WarrantyState.ACTIVE, CarePlan.warranty(LocalDate(2025, 1, 1), 60, today)!!.state)
        assertEquals(LocalDate(2030, 1, 1), CarePlan.warranty(LocalDate(2025, 1, 1), 60, today)!!.until)
        assertEquals(WarrantyState.ENDING_SOON, CarePlan.warranty(LocalDate(2024, 12, 1), 24, today)!!.state)
        assertEquals(WarrantyState.EXPIRED, CarePlan.warranty(LocalDate(2020, 1, 1), 24, today)!!.state)
        assertNull(CarePlan.warranty(null, 24, today))
        assertNull(CarePlan.warranty(LocalDate(2020, 1, 1), null, today))
    }

    @Test fun fullSetNeedsEveryItemRecordedAndNoneMissing() {
        val all = BoxItem.entries.map { BoxPapersItem(it, if (it == BoxItem.OTHER || it == BoxItem.EXTRA_LINKS) BoxItemState.NOT_APPLICABLE else BoxItemState.PRESENT) }
        assertTrue(CarePlan.boxPapers(all).fullSet)
        assertEquals(6, CarePlan.boxPapers(all).applicable)
        assertFalse(CarePlan.boxPapers(all.dropLast(1)).fullSet)
        val missing = all.map { if (it.item == BoxItem.HANG_TAGS) it.copy(state = BoxItemState.MISSING) else it }
        assertFalse(CarePlan.boxPapers(missing).fullSet)
        assertEquals(5, CarePlan.boxPapers(missing).present)
        val noneHere = BoxItem.entries.map { BoxPapersItem(it, BoxItemState.NOT_APPLICABLE) }
        assertFalse(CarePlan.boxPapers(noneHere).fullSet)
    }

    @Test fun serviceWarrantyAndFullServiceFlag() {
        val t = Instant.fromEpochMilliseconds(0)
        val e = ServiceEntry("s", "w", LocalDate(2025, 3, 1), work = setOf(ServiceWork.FULL_SERVICE), warrantyMonths = 24, createdAt = t, updatedAt = t)
        assertTrue(e.isFullService)
        assertEquals(LocalDate(2027, 3, 1), e.warrantyUntil)
        assertNull(e.copy(warrantyMonths = 0).warrantyUntil)
    }

    @Test fun relativeWording() {
        assertEquals("today", CarePlan.relative(0))
        assertEquals("in 5 days", CarePlan.relative(5))
        assertEquals("3 weeks ago", CarePlan.relative(-21))
        assertEquals("in 4 months", CarePlan.relative(125))
        assertEquals("2 years ago", CarePlan.relative(-800))
    }
}
