package io.github.tunlezah.watches.search.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLSessionConfiguration

actual fun platformHttpEngine(): HttpClientEngine = Darwin.create {
    configureSession { keepNothing() }
}

/** NSURLSession shares cookies and a disk cache by default; Lookup keeps neither. */
internal fun NSURLSessionConfiguration.keepNothing() {
    setHTTPCookieStorage(null)
    setHTTPShouldSetCookies(false)
    setURLCache(null)
}
