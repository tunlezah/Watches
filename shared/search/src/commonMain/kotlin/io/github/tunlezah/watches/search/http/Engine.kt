package io.github.tunlezah.watches.search.http

import io.ktor.client.engine.HttpClientEngine

/** The platform's HTTP stack, configured with no cookie storage and no response cache. */
expect fun platformHttpEngine(): HttpClientEngine
