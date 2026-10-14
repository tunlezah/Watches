package io.github.tunlezah.watches.search.http

/** Why a source couldn't answer. The UI turns each into a short message on that source's card. */
sealed class LookupFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The device has no connection, or the host couldn't be reached at all. */
    class Offline(cause: Throwable? = null) : LookupFailure("No connection", cause)
    class TimedOut(val host: String) : LookupFailure("$host took longer than 10 s")
    /** The site's robots.txt doesn't allow this URL, so nothing was sent. */
    class RobotsDisallowed(val url: String) : LookupFailure("robots.txt disallows $url")
    /** robots.txt couldn't be read, so the site's wishes are unknown and nothing was sent. */
    class RobotsUnavailable(val host: String) : LookupFailure("Couldn't read robots.txt for $host")
    /** The site asked us to slow down. Nothing more goes to [host] until the wait is over. */
    class RateLimited(val host: String, val retryAfterSeconds: Long) : LookupFailure("$host asked to wait $retryAfterSeconds s")
    /** A bot challenge or block page. The app never disguises itself to get past one. */
    class Blocked(val host: String) : LookupFailure("$host blocked the request")
    class Http(val status: Int) : LookupFailure("HTTP $status")
    class TooLarge(val url: String) : LookupFailure("Response from $url is too large")
    /** The page arrived but didn't look as expected, usually because the site changed its layout. */
    class Unreadable(message: String, cause: Throwable? = null) : LookupFailure(message, cause)
    class NotAllowed(val url: String) : LookupFailure("Not a URL this source may fetch: $url")
}
