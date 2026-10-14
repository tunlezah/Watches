package io.github.tunlezah.watches.search.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

// OkHttp keeps no cookies and no cache unless given a CookieJar or Cache, and neither is set here.
actual fun platformHttpEngine(): HttpClientEngine = OkHttp.create {
    config { retryOnConnectionFailure(false) }
}
