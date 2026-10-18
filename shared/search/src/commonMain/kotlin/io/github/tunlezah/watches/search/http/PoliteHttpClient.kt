package io.github.tunlezah.watches.search.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.readByteArray
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** A response body, already checked for status and size. */
class Fetched(val url: String, val status: Int, val contentType: String?, val bytes: ByteArray) {
    val text: String get() = bytes.decodeToString()
}

/** How a request treats robots.txt. */
enum class RobotsPolicy {
    /** Fetch and obey the host's robots.txt. The default for every website. */
    Obey,

    /**
     * For documented public APIs whose own usage policy governs access (Wikimedia's API policy,
     * which welcomes identified clients). Their robots.txt is written for crawlers, and lists the
     * API paths only to keep crawlers out. The request log marks these requests as such.
     */
    ApiPolicy,
}

/** Remembers each host's robots.txt between launches, so it is fetched at most once a day. */
interface RobotsStore {
    suspend fun load(host: String): CachedRobots?
    suspend fun save(host: String, robots: CachedRobots)
}

/** [body] is null when the host has no robots.txt (any 4xx), which allows everything. */
data class CachedRobots(val fetchedAtMillis: Long, val body: String?)

class MemoryRobotsStore : RobotsStore {
    private val map = mutableMapOf<String, CachedRobots>()
    override suspend fun load(host: String) = map[host]
    override suspend fun save(host: String, robots: CachedRobots) { map[host] = robots }
}

/**
 * The only way the app talks to the network. Every request:
 * - identifies the app honestly ([USER_AGENT]) and keeps no cookies;
 * - is spaced at least [minInterval] from the previous one to the same host;
 * - is checked against the host's robots.txt first (cached for a day);
 * - times out after 10 s;
 * - lands in the [RequestLog], including requests refused before sending.
 *
 * Nothing here retries or runs in the background: each call is one user action.
 */
class PoliteHttpClient(
    engine: HttpClientEngine,
    private val log: RequestLog,
    private val robotsStore: RobotsStore = MemoryRobotsStore(),
    private val minInterval: Duration = 1.seconds,
    private val nowMillis: () -> Long = monotonicMillis(),
    private val wallClock: Clock = Clock.System,
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        followRedirects = false
        install(UserAgent) { agent = USER_AGENT }
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MS
            connectTimeoutMillis = TIMEOUT_MS
            socketTimeoutMillis = TIMEOUT_MS
        }
    }

    private class HostGate {
        val mutex = Mutex()
        /** Null until the first request; never a sentinel value, so the wait below can't overflow. */
        var nextAllowedAt: Long? = null
        var blockedUntil: Long? = null

        /** Guards this host's robots.txt only, so a slow host never holds up another. */
        val robotsMutex = Mutex()
        var robots: CachedRobots? = null
        var robotsFailedAt: Long? = null
    }

    private val gates = mutableMapOf<String, HostGate>()
    private val gatesLock = Mutex()

    suspend fun get(
        url: String,
        accept: String = "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.5",
        robots: RobotsPolicy = RobotsPolicy.Obey,
        maxBytes: Int = MAX_BYTES,
    ): Fetched {
        var current = Url(url)
        repeat(MAX_REDIRECTS + 1) {
            if (current.protocol.name != "https") throw LookupFailure.NotAllowed(current.toString())
            if (robots == RobotsPolicy.Obey && !robotsAllow(current)) {
                logRefused(current, "Refused: robots.txt")
                throw LookupFailure.RobotsDisallowed(current.toString())
            }
            val response = send(current, accept, maxBytes, note = if (robots == RobotsPolicy.ApiPolicy) " (API)" else "")
            val location = response.redirect
            if (location == null) return response.checked(current)
            current = runCatching { URLBuilder(current).takeFrom(location).build() }.getOrElse { throw LookupFailure.Http(response.status) }
        }
        throw LookupFailure.Http(310)
    }

    private class Raw(val status: Int, val contentType: String?, val bytes: ByteArray, val redirect: String?, val retryAfter: Long?, val blocked: Boolean)

    private fun Raw.checked(url: Url): Fetched = when {
        status in 200..299 -> Fetched(url.toString(), status, contentType, bytes)
        blocked -> throw LookupFailure.Blocked(url.host)
        status == 429 || (status == 503 && retryAfter != null) -> throw LookupFailure.RateLimited(url.host, retryAfter ?: DEFAULT_BACKOFF_S)
        else -> throw LookupFailure.Http(status)
    }

    private suspend fun gate(host: String) = gatesLock.withLock { gates.getOrPut(host) { HostGate() } }

    /** Sends one request after waiting for the host's slot. */
    private suspend fun send(url: Url, accept: String, maxBytes: Int, note: String = ""): Raw {
        val gate = gate(url.host)
        gate.mutex.withLock {
            val now = nowMillis()
            val blockedUntil = gate.blockedUntil
            if (blockedUntil != null && now < blockedUntil) {
                logRefused(url, "Not sent: site asked to wait")
                throw LookupFailure.RateLimited(url.host, (blockedUntil - now + 999) / 1000)
            }
            val wait = gate.nextAllowedAt?.let { it - now } ?: 0L
            if (wait > 0) delay(wait.coerceAtMost(minInterval.inWholeMilliseconds))
            gate.nextAllowedAt = nowMillis() + minInterval.inWholeMilliseconds
        }
        val started = nowMillis()
        val at = wallClock.now()
        try {
            val response = client.get(url) { header(HttpHeaders.Accept, accept) }
            val bytes = readCapped(response, maxBytes, url)
            val retryAfter = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()
            if (response.status.value == 429 || response.status.value == 503) {
                val backoff = retryAfter ?: DEFAULT_BACKOFF_S
                gate.mutex.withLock { gate.blockedUntil = nowMillis() + backoff * 1000 }
            }
            val blocked = response.status.value in setOf(403, 503) && looksLikeBotWall(response, bytes)
            val outcome = when {
                blocked -> "Blocked by bot protection"
                response.status.value in 300..399 -> "Redirect"
                response.status.value in 200..299 -> "OK"
                else -> response.status.description.ifBlank { "HTTP ${response.status.value}" }
            }
            log.add(LoggedRequest(at, url.toString(), url.host, response.status.value, outcome + note, nowMillis() - started, bytes.size))
            return Raw(
                status = response.status.value,
                contentType = response.headers[HttpHeaders.ContentType],
                bytes = bytes,
                redirect = if (response.status.value in 300..399) response.headers[HttpHeaders.Location] else null,
                retryAfter = retryAfter,
                blocked = blocked,
            )
        } catch (e: CancellationException) {
            log.add(LoggedRequest(at, url.toString(), url.host, null, "Cancelled", nowMillis() - started, null))
            throw e
        } catch (e: LookupFailure) {
            log.add(LoggedRequest(at, url.toString(), url.host, null, e.message ?: "Failed", nowMillis() - started, null))
            throw e
        } catch (e: Exception) {
            val timedOut = e is HttpRequestTimeoutException || e is ConnectTimeoutException || e is SocketTimeoutException
            log.add(LoggedRequest(at, url.toString(), url.host, null, if (timedOut) "Timed out" else "No connection", nowMillis() - started, null))
            throw if (timedOut) LookupFailure.TimedOut(url.host) else LookupFailure.Offline(e)
        }
    }

    private suspend fun readCapped(response: HttpResponse, maxBytes: Int, url: Url): ByteArray {
        val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declared != null && declared > maxBytes) throw LookupFailure.TooLarge(url.toString())
        val bytes = response.bodyAsChannel().readRemaining(maxBytes.toLong() + 1).readByteArray()
        if (bytes.size > maxBytes) throw LookupFailure.TooLarge(url.toString())
        return bytes
    }

    private fun looksLikeBotWall(response: HttpResponse, body: ByteArray): Boolean {
        if (response.headers["cf-mitigated"] != null) return true
        val text = body.decodeToString().take(4000)
        return listOf("Just a moment...", "Attention Required!", "Sorry, you have been blocked", "cf-browser-verification", "challenge-platform")
            .any { it in text }
    }

    private suspend fun robotsAllow(url: Url): Boolean {
        val robots = robotsFor(url.host)
        return robots.isAllowed(url.encodedPathAndQuery, PRODUCT_TOKEN)
    }

    private suspend fun robotsFor(host: String): RobotsTxt {
        val gate = gate(host)
        return gate.robotsMutex.withLock {
            val now = wallClock.now().toEpochMilliseconds()
            val cached = gate.robots ?: runCatching { robotsStore.load(host) }.getOrNull()?.also { gate.robots = it }
            val fresh = cached?.takeIf { now - it.fetchedAtMillis in 0 until ROBOTS_TTL.inWholeMilliseconds }
            val entry = fresh ?: run {
                // A host whose robots.txt just failed isn't asked again for a minute: searches fail fast instead.
                gate.robotsFailedAt?.let { if (nowMillis() - it < ROBOTS_RETRY_MS) throw LookupFailure.RobotsUnavailable(host) }
                val fetched = try {
                    fetchRobots(host, now)
                } catch (e: LookupFailure) {
                    if (e !is LookupFailure.Offline) gate.robotsFailedAt = nowMillis()
                    throw e
                }
                gate.robotsFailedAt = null
                gate.robots = fetched
                runCatching { robotsStore.save(host, fetched) }
                fetched
            }
            entry.body?.let(RobotsTxt::parse) ?: RobotsTxt.AllowAll
        }
    }

    private suspend fun fetchRobots(host: String, now: Long): CachedRobots {
        var url = Url("https://$host/robots.txt")
        repeat(MAX_REDIRECTS + 1) {
            val raw = send(url, "text/plain,*/*;q=0.5", maxBytes = 512 * 1024)
            when {
                raw.redirect != null -> url = runCatching { URLBuilder(url).takeFrom(raw.redirect).build() }.getOrElse { throw LookupFailure.RobotsUnavailable(host) }
                raw.status in 200..299 -> return CachedRobots(now, raw.bytes.decodeToString())
                // RFC 9309: a missing robots.txt (any 4xx) means no restrictions.
                raw.status in 400..499 && !raw.blocked -> return CachedRobots(now, null)
                // Server errors and block pages leave the rules unknown, so nothing is sent.
                else -> throw LookupFailure.RobotsUnavailable(host)
            }
        }
        throw LookupFailure.RobotsUnavailable(host)
    }

    private fun logRefused(url: Url, outcome: String) =
        log.add(LoggedRequest(wallClock.now(), url.toString(), url.host, null, outcome, 0, null, sent = false))

    fun close() = client.close()

    companion object {
        const val PRODUCT_TOKEN = "WatchCollection"
        // The owner's public GitHub profile is the contact link: it stays reachable when the repo is private.
        const val USER_AGENT = "$PRODUCT_TOKEN/1.0 (+https://github.com/tunlezah)"
        const val TIMEOUT_MS = 10_000L
        const val MAX_BYTES = 4 * 1024 * 1024
        private const val MAX_REDIRECTS = 3
        private const val DEFAULT_BACKOFF_S = 60L
        val ROBOTS_TTL = 24.hours
        private const val ROBOTS_RETRY_MS = 60_000L

        private fun monotonicMillis(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }
}
