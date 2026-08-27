package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsExchange
import com.sun.net.httpserver.HttpsServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec
import io.netty.handler.codec.http2.HttpConversionUtil
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.util.ReferenceCountUtil
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Verifies SNI-aware interception remains intact when downstream ALPN selects HTTP/2. */
class SniHttpTwoConnectIntegrationTest {
    @Test
    fun `IP CONNECT plus hostname SNI negotiates HTTP two and routes to original IP`() {
        val serverName = "mobile-h2.example.test"
        val originAuthority = CertificateAuthority.generate(commonName = "SNI HTTP2 origin")
        val originLeaf = CertificateCache().get(KNetProxyServer.DEFAULT_BIND_HOST, originAuthority)
        val observedHosts = CopyOnWriteArrayList<String>()
        val observedUpstreamServerNames = CopyOnWriteArrayList<String>()
        val origin = HttpsServer.create(
            InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0),
            0,
        ).apply {
            httpsConfigurator = HttpsConfigurator(
                serverSslContext(
                    originLeaf.keyPair.private,
                    arrayOf(originLeaf.certificate, originAuthority.certificate),
                ),
            )
            createContext("/h2-sni") { exchange ->
                observedHosts += exchange.requestHeaders.getFirst("Host")
                observedUpstreamServerNames += (exchange as HttpsExchange).sslSession.requestedServerName()
                val body = "h2-sni-routed".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { response -> response.write(body) }
            }
            start()
        }

        val knetAuthority = CertificateAuthority.generate(commonName = "SNI HTTP2 KNet CA")
        val resolvedCertificateNames = CopyOnWriteArrayList<String>()
        val delegate = TestServerTlsContextProvider(knetAuthority, CertificateCache())
        val provider = ServerTlsContextProvider { host, executor ->
            resolvedCertificateNames += host
            delegate.resolve(host, executor)
        }
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = provider,
            verifyUpstreamTls = false,
        )
        proxy.start()

        val group = NioEventLoopGroup(1)
        var parent: Channel? = null
        try {
            val ready = CompletableFuture<Channel>()
            val channel = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast("connectCodec", HttpClientCodec())
                        channel.pipeline().addLast("connectAggregator", HttpObjectAggregator(8 * 1024))
                        channel.pipeline().addLast(
                            "connectHandler",
                            ConnectThenTlsHttpTwoHandler(
                                ready = ready,
                                authority = knetAuthority.certificate,
                                serverName = serverName,
                                originPort = origin.address.port,
                            ),
                        )
                    }
                })
                .connect(assertNotNull(proxy.boundAddress()))
                .syncUninterruptibly()
                .channel()
            parent = channel
            val connectIp = KNetProxyServer.DEFAULT_BIND_HOST
            val connect = DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.CONNECT,
                "$connectIp:${origin.address.port}",
            ).apply {
                headers().set(HttpHeaderNames.HOST, "$connectIp:${origin.address.port}")
            }
            channel.writeAndFlush(connect).syncUninterruptibly()

            val httpTwoParent = ready.get(10L, TimeUnit.SECONDS)
            assertEquals(
                "h2-sni-routed",
                sendRequest(httpTwoParent, serverName, origin.address.port).get(10L, TimeUnit.SECONDS),
            )
            assertEquals(listOf(serverName), resolvedCertificateNames)
            assertEquals(listOf("$serverName:${origin.address.port}"), observedHosts)
            assertEquals(listOf(serverName), observedUpstreamServerNames)
        } finally {
            parent?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            proxy.stop()
            origin.stop(0)
        }
    }

    private class ConnectThenTlsHttpTwoHandler(
        private val ready: CompletableFuture<Channel>,
        private val authority: X509Certificate,
        private val serverName: String,
        private val originPort: Int,
    ) : ChannelInboundHandlerAdapter() {
        override fun channelRead(context: ChannelHandlerContext, message: Any) {
            if (message !is FullHttpResponse) {
                context.fireChannelRead(message)
                return
            }
            try {
                check(message.status() == HttpResponseStatus.OK) {
                    "CONNECT failed with ${message.status()}."
                }
                val pipeline = context.pipeline()
                pipeline.remove("connectHandler")
                pipeline.remove("connectAggregator")
                pipeline.remove("connectCodec")
                val clientContext = SslContextBuilder.forClient()
                    .trustManager(authority)
                    .applicationProtocolConfig(
                        ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2,
                        ),
                    )
                    .build()
                val ssl = clientContext.newHandler(context.alloc(), serverName, originPort)
                ssl.engine().sslParameters = ssl.engine().sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                }
                ssl.handshakeFuture().addListener { handshake ->
                    if (!handshake.isSuccess) ready.completeExceptionally(handshake.cause())
                }
                pipeline.addLast("tls", ssl)
                pipeline.addLast(
                    "alpn",
                    object : ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_2) {
                        override fun configurePipeline(context: ChannelHandlerContext, protocol: String) {
                            check(protocol == ApplicationProtocolNames.HTTP_2) {
                                "Expected HTTP/2 but negotiated $protocol."
                            }
                            context.pipeline().addAfter(
                                "alpn",
                                "h2Codec",
                                Http2FrameCodecBuilder.forClient().build(),
                            )
                            context.pipeline().addAfter(
                                "h2Codec",
                                "h2Multiplex",
                                Http2MultiplexHandler(DiscardInboundStreamHandler()),
                            )
                            ready.complete(context.channel())
                        }
                    },
                )
            } catch (failure: Throwable) {
                ready.completeExceptionally(failure)
                context.close()
            } finally {
                ReferenceCountUtil.release(message)
            }
        }

        override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
            ready.completeExceptionally(cause)
            context.close()
        }
    }

    private fun sendRequest(parent: Channel, serverName: String, originPort: Int): CompletableFuture<String> {
        val response = CompletableFuture<String>()
        val stream = Http2StreamChannelBootstrap(parent)
            .handler(object : ChannelInitializer<Channel>() {
                override fun initChannel(channel: Channel) {
                    channel.pipeline().addLast(Http2StreamFrameToHttpObjectCodec(false))
                    channel.pipeline().addLast(HttpObjectAggregator(1024 * 1024))
                    channel.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                        override fun channelRead(context: ChannelHandlerContext, message: Any) {
                            if (message is FullHttpResponse) {
                                response.complete(message.content().toString(Charsets.UTF_8))
                                ReferenceCountUtil.release(message)
                                context.close()
                            } else {
                                context.fireChannelRead(message)
                            }
                        }

                        override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
                            response.completeExceptionally(cause)
                            context.close()
                        }
                    })
                }
            })
            .open()
            .syncUninterruptibly()
            .getNow()
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/h2-sni")
        request.headers().set(HttpHeaderNames.HOST, "$serverName:$originPort")
        request.headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https")
        stream.writeAndFlush(request).addListener { write ->
            if (!write.isSuccess) response.completeExceptionally(write.cause())
        }
        return response
    }

    private class DiscardInboundStreamHandler : ChannelInboundHandlerAdapter() {
        override fun channelRead(context: ChannelHandlerContext, message: Any) {
            ReferenceCountUtil.release(message)
        }
    }

    private fun serverSslContext(
        privateKey: java.security.PrivateKey,
        certificateChain: Array<X509Certificate>,
    ): SSLContext {
        val password = "knet-sni-h2-test".toCharArray()
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("origin", privateKey, password, certificateChain)
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers.keyManagers, null, SecureRandom())
        }
    }

    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }

    private fun javax.net.ssl.SSLSession.requestedServerName(): String =
        ((this as ExtendedSSLSession).requestedServerNames.single() as SNIHostName).asciiName
}
