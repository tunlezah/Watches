package io.github.tunlezah.watches.core

import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.core.model.WatchSummary
import io.github.tunlezah.watches.core.query.CollectionFilter
import io.github.tunlezah.watches.core.query.CollectionQuery
import io.github.tunlezah.watches.core.query.SortOrder
import io.github.tunlezah.watches.core.query.SortSpec
import io.github.tunlezah.watches.core.query.StatusTab
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CollectionQueryTest {

    private fun w(
        id: String, brand: String?, model: String, size: Double?, status: WatchStatus = WatchStatus.OWNED,
        acquired: LocalDate? = null, created: Long = 0, archived: Boolean = false,
        type: MovementType? = null, complications: Set<String> = emptySet(),
    ) = WatchSummary(id, brand, model, null, null, null, status, type, size, acquired,
        Instant.fromEpochMilliseconds(created), archived, complications, null, null)

    private val all = listOf(
        w("a", "Omega", "Speedmaster", 42.0, acquired = LocalDate(2023, 3, 14), created = 3, type = MovementType.MANUAL, complications = setOf("chronograph")),
        w("b", "Tudor", "Black Bay 58", 39.0, acquired = LocalDate(2021, 6, 1), created = 1, type = MovementType.AUTOMATIC),
        w("c", "Rolex", "Explorer", 36.0, status = WatchStatus.WISHLIST, created = 2, type = MovementType.AUTOMATIC),
        w("d", "Seiko", "SPB143", 40.5, archived = true, created = 4),
        w("e", null, "Mystery", null, created = 5),
    )

    private fun ids(list: List<WatchSummary>) = list.map { it.id }

    @Test fun allTabHidesArchived() {
        assertEquals(listOf("a", "c", "b", "e"), ids(CollectionQuery.apply(all, CollectionFilter(), SortSpec(), null)))
    }

    @Test fun archivedTab() {
        assertEquals(listOf("d"), ids(CollectionQuery.apply(all, CollectionFilter(tab = StatusTab.ARCHIVED), SortSpec(), null)))
    }

    @Test fun statusTab() {
        assertEquals(listOf("c"), ids(CollectionQuery.apply(all, CollectionFilter(tab = StatusTab.WISHLIST), SortSpec(), null)))
    }

    @Test fun sortBySizeAndDate() {
        assertEquals(listOf("c", "b", "a", "e"), ids(CollectionQuery.apply(all, CollectionFilter(), SortSpec(SortOrder.CASE_SIZE), null)))
        assertEquals(listOf("a", "b", "e", "c"), ids(CollectionQuery.apply(all, CollectionFilter(), SortSpec(SortOrder.DATE_ACQUIRED), null)))
        assertEquals(listOf("e", "a", "c", "b"), ids(CollectionQuery.apply(all, CollectionFilter(), SortSpec(SortOrder.RECENTLY_ADDED), null)))
        assertEquals(listOf("e", "a", "b", "c"), ids(CollectionQuery.apply(all, CollectionFilter(), SortSpec(SortOrder.CASE_SIZE, reversed = true), null)))
    }

    @Test fun filtersCombine() {
        val f = CollectionFilter(movementTypes = setOf(MovementType.AUTOMATIC), minDiameterMm = 38.0)
        assertEquals(listOf("b"), ids(CollectionQuery.apply(all, f, SortSpec(), null)))
        assertEquals(listOf("a"), ids(CollectionQuery.apply(all, CollectionFilter(complications = setOf("chronograph")), SortSpec(), null)))
        assertEquals(listOf("b"), ids(CollectionQuery.apply(all, CollectionFilter(brands = setOf("Tudor")), SortSpec(), null)))
    }

    @Test fun searchMatchesIntersect() {
        assertEquals(listOf("a", "c"), ids(CollectionQuery.apply(all, CollectionFilter(), SortSpec(), setOf("a", "c", "d"))))
    }

    @Test fun ftsExpression() {
        assertEquals("omega* 310* 30*", CollectionQuery.ftsExpression("Omega 310.30"))
        assertEquals("genève*", CollectionQuery.ftsExpression("Genève"))
        assertEquals("or* not* near*", CollectionQuery.ftsExpression("OR \"NOT\" NEAR -"))
        assertNull(CollectionQuery.ftsExpression("  ... "))
    }
}
