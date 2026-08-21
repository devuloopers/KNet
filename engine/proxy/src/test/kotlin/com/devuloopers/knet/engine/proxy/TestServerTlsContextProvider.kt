package com.devuloopers.knet.engine.proxy

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

internal class TestServerTlsContextProvider(
    private val certificateAuthority: CertificateAuthority,
    private val certificateCache: CertificateCache,
) : ServerTlsContextProvider {
    override fun resolve(host: String, executor: Executor): CompletableFuture<SslContext> =
        certificateCache.getAsync(host, certificateAuthority, executor)
            .thenApplyAsync(
                { leaf ->
                    SslContextBuilder.forServer(leaf.keyPair.private, leaf.certificate)
                        .applicationProtocolConfig(
                            ApplicationProtocolConfig(
                                ApplicationProtocolConfig.Protocol.ALPN,
                                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                ApplicationProtocolNames.HTTP_2,
                                ApplicationProtocolNames.HTTP_1_1,
                            )
                        )
                        .build()
                },
                executor,
            )
}
