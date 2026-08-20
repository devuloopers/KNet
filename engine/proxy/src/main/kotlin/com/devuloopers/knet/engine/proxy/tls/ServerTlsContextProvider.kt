package com.devuloopers.knet.engine.proxy.tls

import io.netty.handler.ssl.SslContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Supplies a downstream server TLS context for one CONNECT hostname.
 *
 * The proxy owns scheduling and pipeline installation; certificate generation, caching, and private-key
 * ownership stay behind this boundary and may be implemented by desktop, a remote signer, or an HSM.
 */
fun interface ServerTlsContextProvider {
    fun resolve(host: String, executor: Executor): CompletableFuture<SslContext>
}
