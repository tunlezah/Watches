package io.github.tunlezah.watches.search.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun platformHttpEngine(): HttpClientEngine = Darwin.create {
    configureSession {
        // NSURLSession shares cookies and a disk cache by default; turn both off.
        setHTTPCookieStorage(null)
        setHTTPShouldSetCookies(false)
        setURLCache(null)
    }
}
