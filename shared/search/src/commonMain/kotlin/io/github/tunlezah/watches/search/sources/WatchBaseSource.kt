package io.github.tunlezah.watches.search.sources

import io.github.tunlezah.watches.core.lookup.Coverage
import io.github.tunlezah.watches.core.lookup.SearchHit
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.search.SourceInfo
import io.github.tunlezah.watches.search.SourceStatus
import io.github.tunlezah.watches.search.SpecSource
import io.github.tunlezah.watches.search.http.LookupFailure
import io.github.tunlezah.watches.search.http.PoliteHttpClient

/**
 * WatchBase. Every page except robots.txt returned a bot block to the build server, so its page
 * structure couldn't be checked and there is no parser yet (docs/SOURCES.md). Until pages saved
 * from a normal browser arrive as fixtures, it offers only a connection test: one request to the
 * home page, honestly identified, to learn whether the app gets through from the owner's phone.
 */
class WatchBaseSource(private val http: PoliteHttpClient) : SpecSource {

    override val info = SourceInfo(
        id = ID,
        name = "WatchBase",
        homepage = "https://watchbase.com/",
        coverage = Coverage.WATCH,
        terms = "Personal, non-commercial use. Not searchable yet: its pages couldn't be checked from the build server.",
        status = SourceStatus.UNVERIFIED,
    )

    override suspend fun search(query: String): List<SearchHit> = throw LookupFailure.Unreadable("WatchBase isn't set up yet")

    override suspend fun detail(hit: SearchHit): SourceRecord = throw LookupFailure.Unreadable("WatchBase isn't set up yet")

    override fun hitFor(url: String, title: String): SearchHit? = null

    /** Null when the home page came back normally; otherwise why it didn't. */
    suspend fun testConnection(): LookupFailure? = try {
        http.get("https://watchbase.com/")
        null
    } catch (e: LookupFailure) {
        e
    }

    companion object {
        const val ID = "watchbase"
    }
}
