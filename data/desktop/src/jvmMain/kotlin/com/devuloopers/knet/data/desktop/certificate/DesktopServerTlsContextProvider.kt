package com.devuloopers.knet.data.desktop.certificate

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/** Desktop bridge from certificate generation/cache ownership to the proxy's TLS context port. */
class DesktopServerTlsContextProvider(
    private val certificateAuthority: CertificateAuthority,
    private val certificateCache: CertificateCache,
) : ServerTlsContextProvider {
    override fun resolve(host: String, executor: Executor): CompletableFuture<SslContext> =
        certificateCache.getAsync(host, certificateAuthority, executor)
            .thenApplyAsync(
                { leaf -> SslContextBuilder.forServer(leaf.keyPair.private, leaf.certificate).build() },
                executor,
            )
}
