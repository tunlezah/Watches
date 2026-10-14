package io.github.tunlezah.watches.core.query

import io.github.tunlezah.watches.core.model.Keyed
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.core.model.WatchSummary

enum class SortOrder(override val key: String, override val label: String) : Keyed {
    BRAND("brand", "Brand"),
    DATE_ACQUIRED("date_acquired", "Date acquired"),
    CASE_SIZE("case_size", "Case size"),
    RECENTLY_ADDED("recently_added", "Recently added"),
}

/** Which slice of the collection is showing. Archived watches only appear in [ARCHIVED]. */
enum class StatusTab(override val key: String, override val label: String, val status: WatchStatus?) : Keyed {
    ALL("all", "All", null),
    OWNED("owned", "Owned", WatchStatus.OWNED),
    WISHLIST("wishlist", "Wishlist", WatchStatus.WISHLIST),
    INCOMING("incoming", "Incoming", WatchStatus.INCOMING),
    IN_SERVICE("in_service", "In service", WatchStatus.IN_SERVICE),
    SOLD("sold", "Sold", WatchStatus.SOLD),
    ARCHIVED("archived", "Archived", null),
}

data class CollectionFilter(
    val tab: StatusTab = StatusTab.ALL,
    val brands: Set<String> = emptySet(),
    val movementTypes: Set<MovementType> = emptySet(),
    val complications: Set<String> = emptySet(),
    val minDiameterMm: Double? = null,
    val maxDiameterMm: Double? = null,
) {
    /** Filters beyond the status tab, for the "Filters (n)" badge. */
    val activeCount: Int
        get() = listOf(brands.isNotEmpty(), movementTypes.isNotEmpty(), complications.isNotEmpty(),
            minDiameterMm != null || maxDiameterMm != null).count { it }
}

data class SortSpec(val order: SortOrder = SortOrder.BRAND, val reversed: Boolean = false)

object CollectionQuery {

    /**
     * Applies tab, filters, text-search matches (null = no search running) and sort.
     * Plain in-memory work: 500 summaries filter and sort in well under a millisecond.
     */
    fun apply(
        all: List<WatchSummary>,
        filter: CollectionFilter,
        sort: SortSpec,
        searchMatches: Set<String>?,
    ): List<WatchSummary> {
        // "Omega" and "OMEGA" are one brand chip, so they match as one too.
        val brands = filter.brands.mapTo(mutableSetOf()) { it.lowercase() }
        val filtered = all.filter { w ->
            val tabOk = when (filter.tab) {
                StatusTab.ARCHIVED -> w.archived
                StatusTab.ALL -> !w.archived
                else -> !w.archived && w.status == filter.tab.status
            }
            tabOk &&
                (searchMatches == null || w.id in searchMatches) &&
                (brands.isEmpty() || (w.brand ?: "").lowercase() in brands) &&
                (filter.movementTypes.isEmpty() || w.movementType in filter.movementTypes) &&
                (filter.complications.isEmpty() || w.complications.containsAll(filter.complications)) &&
                (filter.minDiameterMm == null || (w.diameterMm != null && w.diameterMm >= filter.minDiameterMm - 0.001)) &&
                (filter.maxDiameterMm == null || (w.diameterMm != null && w.diameterMm <= filter.maxDiameterMm + 0.001))
        }
        val sorted = when (sort.order) {
            SortOrder.BRAND -> filtered.sortedWith(
                compareBy<WatchSummary, String?>(nullsLast()) { it.brand?.lowercase() }
                    .thenBy(nullsLast()) { it.title.lowercase() }
                    .thenBy(nullsLast()) { it.reference },
            )
            // Newest first is the natural reading for dates; missing dates go last.
            SortOrder.DATE_ACQUIRED -> filtered.sortedWith(
                compareByDescending<WatchSummary, kotlinx.datetime.LocalDate?>(nullsFirst()) { it.acquiredOn }.thenByDescending { it.createdAt },
            )
            SortOrder.CASE_SIZE -> filtered.sortedWith(compareBy<WatchSummary, Double?>(nullsLast()) { it.diameterMm }.thenBy { it.title.lowercase() })
            SortOrder.RECENTLY_ADDED -> filtered.sortedByDescending { it.createdAt }
        }
        return if (sort.reversed) sorted.asReversed().toList() else sorted
    }

    /** The diameter bounds present in the collection, for the size slider. */
    fun diameterBounds(all: List<WatchSummary>): ClosedFloatingPointRange<Double>? {
        val sizes = all.mapNotNull { it.diameterMm }
        if (sizes.isEmpty()) return null
        return sizes.min()..sizes.max()
    }

    /**
     * Builds an FTS4 MATCH expression from free text: every word must prefix-match (`omega* 310*`).
     * Words are reduced to letters and digits and lower-cased, so no FTS operator (AND, OR, NOT,
     * NEAR are upper-case only) or special character can reach the query.
     */
    fun ftsExpression(text: String): String? {
        val words = text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        return words.joinToString(" ") { "$it*" }
    }
}
