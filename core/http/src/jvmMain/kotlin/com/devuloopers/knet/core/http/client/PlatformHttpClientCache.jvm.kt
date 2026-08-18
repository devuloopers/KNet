package com.devuloopers.knet.core.http.client

import io.ktor.client.HttpClient
import java.util.concurrent.ConcurrentHashMap

/** JVM client cache using a concurrent map for requests executed from multiple coroutines. */
internal actual class PlatformHttpClientCache actual constructor(
    private val factory: (Int) -> HttpClient,
) {
    private val clients = ConcurrentHashMap<Int, HttpClient>()

    /** Returns or atomically installs the client for [port]. */
    actual fun get(port: Int): HttpClient = clients.computeIfAbsent(port, factory)

    /** Closes a stable client snapshot before clearing the cache. */
    actual fun close() {
        clients.values.toSet().forEach(HttpClient::close)
        clients.clear()
    }
}
