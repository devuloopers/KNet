package com.devuloopers.knet.core.http.client

import io.ktor.client.HttpClient

/** Platform cache for proxy-specific clients whose synchronization is implementation-dependent. */
internal expect class PlatformHttpClientCache(factory: (Int) -> HttpClient) {
    /** Returns the client owned by [port], creating it atomically when absent. */
    fun get(port: Int): HttpClient

    /** Closes every cached client and clears the cache. */
    fun close()
}
