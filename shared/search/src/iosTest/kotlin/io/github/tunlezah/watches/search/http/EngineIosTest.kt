package io.github.tunlezah.watches.search.http

import platform.Foundation.NSURLSessionConfiguration
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EngineIosTest {

    @Test fun lookupSessionsKeepNoCookiesAndNoCache() {
        val config = NSURLSessionConfiguration.defaultSessionConfiguration
        config.keepNothing()
        assertNull(config.HTTPCookieStorage)
        assertFalse(config.HTTPShouldSetCookies)
        assertNull(config.URLCache)
    }
}
