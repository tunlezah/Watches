package io.github.tunlezah.watches.search

import io.github.tunlezah.watches.core.lookup.Coverage
import io.github.tunlezah.watches.core.lookup.SearchHit
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.search.http.LookupFailure
import io.github.tunlezah.watches.search.http.PoliteHttpClient
import io.github.tunlezah.watches.search.http.RequestLog
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LookupServiceTest {

    private class FakeSource(
        id: String,
        private val wait: Long = 0,
        private val result: () -> List<SearchHit>,
        status: SourceStatus = SourceStatus.READY,
    ) : SpecSource {
        var calls = 0
        override val info = SourceInfo(id, id, "https://$id.example", Coverage.MOVEMENT, "", status)
        override suspend fun search(query: String): List<SearchHit> { calls++; delay(wait); return result() }
        override suspend fun detail(hit: SearchHit): SourceRecord = error("unused")
        override fun hitFor(url: String, title: String): SearchHit? = null
    }

    private fun hit(source: String) = SearchHit(source, "k", "T", url = "https://$source.example/k")

    private fun service(sources: List<SpecSource>, online: Boolean = true) =
        LookupService(sources, PoliteHttpClient(MockEngine { respond("") }, RequestLog()), { online })

    @Test fun resultsStreamPerSourceAndFailuresStayLocal() = runTest {
        val fast = FakeSource("fast", 100, { listOf(hit("fast")) })
        val slow = FakeSource("slow", 2_000, { listOf(hit("slow")) })
        val broken = FakeSource("broken", 500, { error("layout changed") })
        val events = service(listOf(fast, slow, broken)).search("eta 2824", setOf("fast", "slow", "broken")).toList()
        val finals = events.filter { it.state !is SourceState.Loading }
        // Fastest first: nobody waits for the slow source.
        assertEquals(listOf("fast", "broken", "slow"), finals.map { it.source })
        val failure = finals.single { it.source == "broken" }.state
        assertIs<SourceState.Failed>(failure)
        assertIs<LookupFailure.Unreadable>(failure.failure)
        assertEquals("Couldn't read broken", failure.failure.message)
    }

    @Test fun offlineSendsNothing() = runTest {
        val a = FakeSource("a", result = { listOf(hit("a")) })
        val events = service(listOf(a), online = false).search("x", setOf("a")).toList()
        assertEquals(0, a.calls)
        val state = events.single().state
        assertIs<SourceState.Failed>(state)
        assertIs<LookupFailure.Offline>(state.failure)
    }

    @Test fun onlyEnabledReadySourcesRun() = runTest {
        val on = FakeSource("on", result = { emptyList() })
        val off = FakeSource("off", result = { emptyList() })
        val unverified = FakeSource("unverified", result = { emptyList() }, status = SourceStatus.UNVERIFIED)
        service(listOf(on, off, unverified)).search("x", setOf("on", "unverified")).toList()
        assertEquals(listOf(1, 0, 0), listOf(on.calls, off.calls, unverified.calls))
    }

    @Test fun blankQueriesDoNothing() = runTest {
        val a = FakeSource("a", result = { emptyList() })
        assertTrue(service(listOf(a)).search("   ", setOf("a")).toList().isEmpty())
        assertEquals("ETA 2824-2", LookupService.cleanQuery("  ETA   2824-2 "))
    }
}
