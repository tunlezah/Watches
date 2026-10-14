package io.github.tunlezah.watches.search

import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.search.http.LookupFailure
import io.github.tunlezah.watches.search.http.PoliteHttpClient
import io.github.tunlezah.watches.search.http.RequestLog
import io.github.tunlezah.watches.search.http.platformHttpEngine
import io.github.tunlezah.watches.search.sources.GrailSource
import io.github.tunlezah.watches.search.sources.RanfftSource
import io.github.tunlezah.watches.search.sources.WikidataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs each adapter against the live site: one search and one detail page per source, through the
 * same polite client the app uses. Skipped unless WATCHES_LIVE_TESTS=1, so CI never sends requests.
 *
 *     WATCHES_LIVE_TESTS=1 ./gradlew :shared:search:jvmTest --tests '*LiveSourcesTest*'
 */
class LiveSourcesTest {

    private fun live(block: suspend (PoliteHttpClient) -> Unit) {
        assumeTrue("Set WATCHES_LIVE_TESTS=1 to run", System.getenv("WATCHES_LIVE_TESTS") == "1")
        val log = RequestLog()
        val http = PoliteHttpClient(platformHttpEngine(), log)
        try {
            runBlocking { block(http) }
        } finally {
            log.entries.value.reversed().forEach { println("${it.status ?: "-"} ${it.millis} ms ${it.outcome}  ${it.url}") }
            http.close()
        }
    }

    @Test fun ranfft() = live { http ->
        val source = RanfftSource(http)
        val hits = source.search("ETA 2824-2")
        println(hits)
        assertTrue(hits.isNotEmpty())
        val record = source.detail(hits.first { it.title == "ETA 2824-2" })
        println(record.values); println(record.extras)
        assertTrue(SpecFields.Frequency.key in record.values)
    }

    @Test fun grail() = live { http ->
        val source = GrailSource(http)
        val hits = source.search("ETA 7750")
        println(hits)
        assertTrue(hits.isNotEmpty())
        val record = source.detail(hits.first())
        println(record.values); println(record.extras)
        assertTrue(SpecFields.Caliber.key in record.values)
    }

    @Test fun wikidata() = live { http ->
        val source = WikidataSource(http)
        val hits = try {
            source.search("Omega Speedmaster")
        } catch (e: LookupFailure.RateLimited) {
            // Shared cloud IPs are often rate-limited by Wikimedia; a phone isn't.
            println("Rate-limited: ${e.message}")
            return@live
        }
        println(hits)
        assertTrue(hits.isNotEmpty())
        val record = source.detail(hits.first())
        println(record.values); println(record.extras.map { it.label })
    }
}
