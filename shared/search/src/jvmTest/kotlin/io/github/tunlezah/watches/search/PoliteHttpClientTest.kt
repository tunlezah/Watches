package io.github.tunlezah.watches.search

import io.github.tunlezah.watches.search.http.LookupFailure
import io.github.tunlezah.watches.search.http.MemoryRobotsStore
import io.github.tunlezah.watches.search.http.PoliteHttpClient
import io.github.tunlezah.watches.search.http.RequestLog
import io.github.tunlezah.watches.search.http.RobotsPolicy
import io.github.tunlezah.watches.search.http.RobotsTxt
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class PoliteHttpClientTest {

    private class Recorder {
        val requests = mutableListOf<Pair<Long, HttpRequestData>>()
    }

    private val fixedClock = object : Clock {
        override fun now() = Instant.fromEpochMilliseconds(1_791_000_000_000)
    }

    private fun TestScope.client(
        log: RequestLog = RequestLog(),
        recorder: Recorder = Recorder(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): PoliteHttpClient {
        // The engine runs on the test scheduler, so request times are virtual time as the client saw it.
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                recorder.requests += testScheduler.currentTime to request
                handler(request)
            }
        })
        return PoliteHttpClient(engine, log, MemoryRobotsStore(), nowMillis = { testScheduler.currentTime }, wallClock = fixedClock)
    }

    private val robots = """
        User-agent: *
        Disallow: /*?*sort=
        Disallow: /private/
        Allow: /private/ok
    """.trimIndent()

    private fun MockRequestHandleScope.site(request: HttpRequestData): HttpResponseData = when (request.url.encodedPath) {
        "/robots.txt" -> respond(robots)
        else -> respond("<html>ok ${request.url.encodedPathAndQuery}</html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
    }

    @Test fun robotsRules() {
        val r = RobotsTxt.parse(robots + "\n\nUser-agent: WatchCollection\nDisallow: /mine/\n")
        // Our own group replaces the * group entirely.
        assertFalse(r.isAllowed("/mine/x", "WatchCollection"))
        assertTrue(r.isAllowed("/private/x", "WatchCollection"))
        val star = RobotsTxt.parse(robots)
        assertFalse(star.isAllowed("/calibers?quick=x&sort=name", "WatchCollection"))
        assertTrue(star.isAllowed("/calibers?quick=x", "WatchCollection"))
        assertFalse(star.isAllowed("/private/no", "WatchCollection"))
        assertTrue(star.isAllowed("/private/ok", "WatchCollection"))
        assertTrue(RobotsTxt.parse("User-agent: *\nDisallow:\n").isAllowed("/anything", "WatchCollection"))
        assertFalse(RobotsTxt.parse("User-agent: *\nDisallow: /*.php$\n").isAllowed("/a.php", "WatchCollection"))
        assertTrue(RobotsTxt.parse("User-agent: *\nDisallow: /*.php$\n").isAllowed("/a.php?x=1", "WatchCollection"))
    }

    @Test fun identifiesHonestlyAndCachesRobots() = runTest {
        val rec = Recorder()
        val http = client(recorder = rec) { site(it) }
        http.get("https://example.org/a")
        http.get("https://example.org/b")
        val paths = rec.requests.map { it.second.url.encodedPath }
        assertEquals(listOf("/robots.txt", "/a", "/b"), paths)
        assertTrue(rec.requests.all { it.second.headers[HttpHeaders.UserAgent] == PoliteHttpClient.USER_AGENT })
        assertTrue(rec.requests.none { it.second.headers[HttpHeaders.Cookie] != null })
    }

    @Test fun oneRequestPerSecondPerHost() = runTest {
        val rec = Recorder()
        val http = client(recorder = rec) { site(it) }
        val a = async { http.get("https://example.org/1") }
        val b = async { http.get("https://example.org/2") }
        val c = async { http.get("https://other.example/1") }
        a.await(); b.await(); c.await()
        val byHost = rec.requests.groupBy({ it.second.url.host }, { it.first })
        for ((host, times) in byHost) {
            times.zipWithNext().forEach { (x, y) -> assertTrue(y - x >= 1000, "$host requests $x and $y are under 1 s apart") }
        }
        // Different hosts don't wait for each other.
        assertEquals(0L, byHost.getValue("other.example").first())
    }

    @Test fun firstRequestIsNotDelayedWhateverTheClock() = runTest {
        // A monotonic clock rarely starts at zero. The first request to a host must go straight out.
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond("") }
        })
        val start = 5_000_000_000L
        val http = PoliteHttpClient(engine, RequestLog(), MemoryRobotsStore(), nowMillis = { start + testScheduler.currentTime }, wallClock = fixedClock)
        http.get("https://example.org/a", robots = RobotsPolicy.ApiPolicy)
        assertEquals(0L, testScheduler.currentTime)
        http.get("https://example.org/b", robots = RobotsPolicy.ApiPolicy)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test fun refusesDisallowedUrlsWithoutSending() = runTest {
        val rec = Recorder()
        val log = RequestLog()
        val http = client(log, rec) { site(it) }
        assertFailsWith<LookupFailure.RobotsDisallowed> { http.get("https://example.org/calibers?quick=a&sort=name") }
        assertEquals(listOf("/robots.txt"), rec.requests.map { it.second.url.encodedPath })
        val refused = log.entries.value.first()
        assertFalse(refused.sent)
        assertEquals("Refused: robots.txt", refused.outcome)
    }

    @Test fun apiPolicySkipsRobots() = runTest {
        val rec = Recorder()
        val log = RequestLog()
        val http = client(log, rec) { site(it) }
        http.get("https://example.org/private/api", robots = RobotsPolicy.ApiPolicy)
        assertEquals(listOf("/private/api"), rec.requests.map { it.second.url.encodedPath })
        assertTrue(log.entries.value.first().outcome.endsWith("(API)"))
    }

    @Test fun missingRobotsAllowsEverything() = runTest {
        val http = client { req ->
            if (req.url.encodedPath == "/robots.txt") respond("", HttpStatusCode.NotFound) else respond("fine")
        }
        assertEquals("fine", http.get("https://example.org/private/x").text)
    }

    @Test fun brokenRobotsBlocksTheHost() = runTest {
        val rec = Recorder()
        val http = client(recorder = rec) { req ->
            if (req.url.encodedPath == "/robots.txt") respond("", HttpStatusCode.InternalServerError) else respond("fine")
        }
        assertFailsWith<LookupFailure.RobotsUnavailable> { http.get("https://example.org/x") }
        assertEquals(1, rec.requests.size)
    }

    @Test fun aFailedRobotsFetchIsNotRetriedForAMinute() = runTest {
        val rec = Recorder()
        val http = client(recorder = rec) { req ->
            if (req.url.encodedPath == "/robots.txt") respond("", HttpStatusCode.ServiceUnavailable) else respond("fine")
        }
        assertFailsWith<LookupFailure.RobotsUnavailable> { http.get("https://slow.example/x") }
        assertFailsWith<LookupFailure.RobotsUnavailable> { http.get("https://slow.example/y") }
        assertEquals(1, rec.requests.size)
    }

    @Test fun oneHostsRobotsDoesNotHoldUpAnother() = runTest {
        val rec = Recorder()
        val http = client(recorder = rec) { req ->
            if (req.url.host == "slow.example" && req.url.encodedPath == "/robots.txt") delay(9_000)
            if (req.url.encodedPath == "/robots.txt") respond("") else respond("fine")
        }
        val slow = async { http.get("https://slow.example/x") }
        val fast = async { http.get("https://fast.example/x") }
        fast.await()
        val fastDone = testScheduler.currentTime
        slow.await()
        assertTrue(fastDone < 9_000, "fast host finished at $fastDone ms")
    }

    @Test fun backsOffWhenAskedToWait() = runTest {
        val rec = Recorder()
        val http = client(recorder = rec) { req ->
            if (req.url.encodedPath == "/robots.txt") respond("")
            else respond("slow down", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "30"))
        }
        val first = assertFailsWith<LookupFailure.RateLimited> { http.get("https://example.org/a") }
        assertEquals(30, first.retryAfterSeconds)
        // The next attempt inside the window isn't sent at all.
        assertFailsWith<LookupFailure.RateLimited> { http.get("https://example.org/b") }
        assertEquals(listOf("/robots.txt", "/a"), rec.requests.map { it.second.url.encodedPath })
    }

    @Test fun recognisesBotWalls() = runTest {
        val http = client { req ->
            if (req.url.encodedPath == "/robots.txt") respond("")
            else respond("<title>Just a moment...</title>", HttpStatusCode.Forbidden, headersOf("cf-mitigated", "challenge"))
        }
        assertFailsWith<LookupFailure.Blocked> { http.get("https://example.org/a") }
    }

    @Test fun followsRedirectsThroughTheSameChecks() = runTest {
        val rec = Recorder()
        val http = client(recorder = rec) { req ->
            when (req.url.encodedPath) {
                "/robots.txt" -> respond(robots)
                "/old" -> respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "/private/no"))
                else -> respond("x")
            }
        }
        assertFailsWith<LookupFailure.RobotsDisallowed> { http.get("https://example.org/old") }
    }

    @Test fun timesOutAfterTenSeconds() = runTest {
        val http = client { req ->
            if (req.url.encodedPath == "/robots.txt") respond("") else { delay(15_000); respond("late") }
        }
        assertFailsWith<LookupFailure.TimedOut> { http.get("https://example.org/a") }
    }

    @Test fun networkErrorsReadAsOffline() = runTest {
        val http = client { throw java.net.UnknownHostException("no network") }
        assertFailsWith<LookupFailure.Offline> { http.get("https://example.org/a", robots = RobotsPolicy.ApiPolicy) }
    }

    @Test fun refusesPlainHttpAndHugeBodies() = runTest {
        val http = client { req ->
            if (req.url.encodedPath == "/robots.txt") respond("") else respond(ByteArray(2_000))
        }
        assertFailsWith<LookupFailure.NotAllowed> { http.get("http://example.org/a") }
        assertFailsWith<LookupFailure.TooLarge> { http.get("https://example.org/a", maxBytes = 1_000) }
    }
}
