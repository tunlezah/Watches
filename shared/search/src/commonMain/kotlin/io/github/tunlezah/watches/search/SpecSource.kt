package io.github.tunlezah.watches.search

import io.github.tunlezah.watches.core.lookup.Coverage
import io.github.tunlezah.watches.core.lookup.SearchHit
import io.github.tunlezah.watches.core.lookup.SourceRecord

enum class SourceStatus {
    /** Parser tested against saved pages; searchable. */
    READY,

    /** Not usable yet: the pages couldn't be checked from the build server (see docs/SOURCES.md). */
    UNVERIFIED,
}

/** What Settings shows about a source and why it's allowed. */
data class SourceInfo(
    val id: String,
    val name: String,
    val homepage: String,
    val coverage: Coverage,
    val terms: String,
    val status: SourceStatus = SourceStatus.READY,
)

/**
 * One spec source. [search] and [detail] each send one request per host (plus a robots.txt fetch
 * at most once a day). Parsing lives in pure functions next to each adapter, tested against
 * saved pages in `src/commonTest/resources/fixtures`.
 */
interface SpecSource {
    val info: SourceInfo
    suspend fun search(query: String): List<SearchHit>
    suspend fun detail(hit: SearchHit): SourceRecord

    /** Rebuilds a hit from a page URL saved with a watch, for "Refresh from sources". */
    fun hitFor(url: String, title: String): SearchHit?
}
