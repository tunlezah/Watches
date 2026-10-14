package io.github.tunlezah.watches.search

import io.github.tunlezah.watches.search.http.PoliteHttpClient
import io.github.tunlezah.watches.search.http.RequestLog
import io.github.tunlezah.watches.search.http.platformHttpEngine
import io.github.tunlezah.watches.search.sources.GrailSource
import io.github.tunlezah.watches.search.sources.RanfftSource
import io.github.tunlezah.watches.search.sources.WatchBaseSource
import io.github.tunlezah.watches.search.sources.WikidataSource
import org.koin.dsl.module

/**
 * The lookup stack. Every definition is lazy, so no HTTP client exists until the first lookup.
 * The app provides a [io.github.tunlezah.watches.search.http.RobotsStore] and a [Connectivity].
 */
val searchModule = module {
    single { RequestLog() }
    single { PoliteHttpClient(platformHttpEngine(), get(), get()) }
    single { WikidataSource(get()) }
    single { RanfftSource(get()) }
    single { GrailSource(get()) }
    single { WatchBaseSource(get()) }
    single {
        LookupService(
            // The one list of sources, in the order results are shown.
            sources = listOf(get<WikidataSource>(), get<RanfftSource>(), get<GrailSource>(), get<WatchBaseSource>()),
            http = get(),
            connectivity = get(),
        )
    }
}
