package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import com.sun.net.httpserver.HttpServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpClientUpgradeHandler
import io.netty.handler.codec.http.HttpMethod as NettyHttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpVersion as NettyHttpVersion
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Real CONNECT + TLS ALPN qualification for multiplexed downstream HTTP/2 streams. */
class HttpTwoDownstreamIntegrationTest {

    @Test
    fun `HTTP two bridge headers stay inside Netty when forwarding to HTTP one`() {
        val observedHeaderNames = CopyOnWriteArrayList<String>()
        val origin = HttpServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            createContext("/bridge-boundary") { exchange ->
                observedHeaderNames += exchange.requestHeaders.keys
                val body = "clean".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { output -> output.write(body) }
            }
            start()
        }
        val authority = CertificateAuthority.generate(commonName = "HTTP/2 KNet bridge boundary CA")
        val capture = RecordingCaptureSink()
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(authority, CertificateCache()),
            captureSink = capture,
        )
        proxy.start()
        val group = NioEventLoopGroup(1)
        var clientChannel: Channel? = null
        try {
            val channel = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                        channel.pipeline().addLast(Http2MultiplexHandler(DiscardInboundStreamHandler()))
                    }
                })
                .connect(assertNotNull(proxy.boundAddress()))
                .syncUninterruptibly()
                .channel()
            clientChannel = channel

            assertEquals(
                "clean",
                sendH2cRequest(channel, origin.address.port, "/bridge-boundary").get(10, TimeUnit.SECONDS),
            )
            awaitCondition { capture.requests.size == 1 }
            assertTrue(observedHeaderNames.none { name -> name.startsWith("x-http2-", ignoreCase = true) })
            assertTrue(
                capture.requests.single().request.headers.none { header ->
                    header.name.value.startsWith("x-http2-", ignoreCase = true)
                },
            )
            assertNotNull(capture.requests.single().streamId)
        } finally {
            clientChannel?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            proxy.stop()
            origin.stop(0)
        }
    }

    @Test
    fun `oversized header list rejects its connection and preserves the listener`() {
        val origin = HttpServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            createContext("/") { exchange ->
                val body = exchange.requestURI.path.encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val authority = CertificateAuthority.generate(commonName = "HTTP/2 KNet header limit CA")
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(authority, CertificateCache()),
            runtimePolicy = KNetProxyRuntimePolicy(maximumHttp2HeaderListBytes = 512L),
        )
        proxy.start()
        val group = NioEventLoopGroup(1)
        val clientChannels = mutableListOf<Channel>()
        try {
            val channel = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                        channel.pipeline().addLast(Http2MultiplexHandler(DiscardInboundStreamHandler()))
                    }
                })
                .connect(assertNotNull(proxy.boundAddress()))
                .syncUninterruptibly()
                .channel()
            clientChannels += channel

            val oversized = sendH2cRequest(channel, origin.address.port, "/oversized") { request ->
                request.headers().set("x-oversized", "x".repeat(4_096))
            }
            assertFailsWith<java.util.concurrent.ExecutionException> {
                oversized.get(10, TimeUnit.SECONDS)
            }
            channel.closeFuture().awaitUninterruptibly(10, TimeUnit.SECONDS)

            val replacement = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                        channel.pipeline().addLast(Http2MultiplexHandler(DiscardInboundStreamHandler()))
                    }
                })
                .connect(assertNotNull(proxy.boundAddress()))
                .syncUninterruptibly()
                .channel()
            clientChannels += replacement
            assertEquals(
                "/healthy",
                sendH2cRequest(replacement, origin.address.port, "/healthy").get(10, TimeUnit.SECONDS),
            )
            assertTrue(replacement.isActive, "A header-list violation must not terminate the proxy listener.")
        } finally {
            clientChannels.forEach { channel -> channel.close().syncUninterruptibly() }
            group.shutdownGracefully().syncUninterruptibly()
            proxy.stop()
            origin.stop(0)
        }
    }

    @Test
    fun `one hundred concurrent h2c streams complete without cross stream data`() {
        val originExecutor = Executors.newCachedThreadPool()
        val origin = HttpServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            executor = originExecutor
            createContext("/") { exchange ->
                val body = exchange.requestURI.path.encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val authority = CertificateAuthority.generate(commonName = "HTTP/2 KNet concurrency CA")
        val capture = RecordingCaptureSink()
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(authority, CertificateCache()),
            captureSink = capture,
        )
        proxy.start()
        val group = NioEventLoopGroup(1)
        var clientChannel: Channel? = null
        try {
            val channel = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                        channel.pipeline().addLast(Http2MultiplexHandler(DiscardInboundStreamHandler()))
                    }
                })
                .connect(assertNotNull(proxy.boundAddress()))
                .syncUninterruptibly()
                .channel()
            clientChannel = channel

            val responses = (1..100)
                .map { index -> sendH2cRequest(channel, origin.address.port, "/item-$index") }
                .map { future -> future.get(20, TimeUnit.SECONDS) }
            assertEquals((1..100).map { "/item-$it" }.toSet(), responses.toSet())
            awaitCondition { capture.requests.size == 100 }
            assertEquals(100, capture.requests.mapNotNull(RecordedRequest::streamId).distinct().size)
            assertEquals(1, capture.openedConnections)
        } finally {
            clientChannel?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            proxy.stop()
            origin.stop(0)
            originExecutor.shutdownNow()
        }
    }

    @Test
    fun `request trailers remain separate from initial headers`() {
        val origin = HttpServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            createContext("/request-trailers") { exchange ->
                val body = "accepted".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val authority = CertificateAuthority.generate(commonName = "HTTP/2 KNet request trailer CA")
        val capture = RecordingCaptureSink()
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(authority, CertificateCache()),
            captureSink = capture,
        )
        proxy.start()
        val group = NioEventLoopGroup(1)
        var clientChannel: Channel? = null
        try {
            val channel = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                        channel.pipeline().addLast(Http2MultiplexHandler(DiscardInboundStreamHandler()))
                    }
                })
                .connect(assertNotNull(proxy.boundAddress()))
                .syncUninterruptibly()
                .channel()
            clientChannel = channel

            assertEquals("accepted", sendH2cRequestWithTrailers(channel, origin.address.port).get(10, TimeUnit.SECONDS))
            awaitCondition { capture.trailers.isNotEmpty() }
            val event = capture.trailers.single()
            assertEquals(TrafficDirection.CLIENT_TO_SERVER, event.direction)
            assertEquals("request-trailer", event.fields.single().name.value)
            assertEquals("preserved", event.fields.single().value)
            assertTrue(capture.requests.single().request.headers.none { header -> header.name.value == "request-trailer" })
        } finally {
            clientChannel?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            proxy.stop()
            origin.stop(0)
        }
    }

    @Test
    fun `h2c upgrade preserves the upgrade request as stream one`() {
        val origin = HttpServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            createContext("/upgrade") { exchange ->
                val body = "upgraded".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val authority = CertificateAuthority.generate(commonName = "HTTP/2 KNet upgrade test CA")
        val capture = RecordingCaptureSink()
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(authority, CertificateCache()),
            captureSink = capture,
        )
        proxy.start()

        val group = NioEventLoopGroup(1)
        var clientChannel: Channel? = null
        try {
            val response = CompletableFuture<String>()
            val channel = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        val sourceCodec = HttpClientCodec()
                        val frameCodec = Http2FrameCodecBuilder.forClient().build()
                        val upgradeStream = responseStreamInitializer(response)
                        val multiplex = Http2MultiplexHandler(DiscardInboundStreamHandler(), upgradeStream)
                        val upgradeCodec = Http2ClientUpgradeCodec(
                            frameCodec as io.netty.handler.codec.http2.Http2ConnectionHandler,
                            multiplex,
                        )
                        channel.pipeline().addLast(sourceCodec)
                        channel.pipeline().addLast(
                            HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 1024 * 1024),
                        )
                    }
                })
                .connect(assertNotNull(proxy.boundAddress()))
                .syncUninterruptibly()
                .channel()
            clientChannel = channel
            val absoluteUrl = "http://${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.address.port}/upgrade"
            val request = DefaultFullHttpRequest(
                NettyHttpVersion.HTTP_1_1,
                NettyHttpMethod.GET,
                absoluteUrl,
            )
            request.headers().set(HttpHeaderNames.HOST, "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.address.port}")
            channel.writeAndFlush(request).syncUninterruptibly()

            assertEquals("upgraded", response.get(10, TimeUnit.SECONDS))
            awaitCondition { capture.requests.size == 1 }
            assertEquals("HTTP/2", capture.requests.single().request.protocol.token)
            assertNotNull(capture.requests.single().streamId)
        } finally {
            clientChannel?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            proxy.stop()
            origin.stop(0)
        }
    }

    @Test
    fun `h2c prior knowledge serves multiplexed proxy requests`() {
        val origin = HttpServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            createContext("/first") { exchange ->
                val body = "first".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/second") { exchange ->
                val body = "second".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val authority = CertificateAuthority.generate(commonName = "HTTP/2 KNet h2c test CA")
        val capture = RecordingCaptureSink()
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(authority, CertificateCache()),
            captureSink = capture,
        )
        proxy.start()

        val group = NioEventLoopGroup(1)
        var clientChannel: Channel? = null
        try {
            val channel = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                        channel.pipeline().addLast(Http2MultiplexHandler(DiscardInboundStreamHandler()))
                    }
                })
                .connect(assertNotNull(proxy.boundAddress()))
                .syncUninterruptibly()
                .channel()
            clientChannel = channel

            val first = sendH2cRequest(channel, origin.address.port, "/first")
            val second = sendH2cRequest(channel, origin.address.port, "/second")
            assertEquals("first", first.get(10, TimeUnit.SECONDS))
            assertEquals("second", second.get(10, TimeUnit.SECONDS))
            awaitCondition { capture.requests.size == 2 }
            assertEquals(1, capture.openedConnections)
            assertEquals(setOf("HTTP/2"), capture.requests.map { it.request.protocol.token }.toSet())
            assertEquals(2, capture.requests.mapNotNull(RecordedRequest::streamId).distinct().size)
        } finally {
            clientChannel?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            proxy.stop()
            origin.stop(0)
        }
    }

    @Test
    fun `tls alpn serves concurrent http two streams and preserves their identities`() {
        val originAuthority = CertificateAuthority.generate(commonName = "HTTP/2 origin")
        val originLeaf = CertificateCache().get(KNetProxyServer.DEFAULT_BIND_HOST, originAuthority)
        val origin = HttpsServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            httpsConfigurator = HttpsConfigurator(
                serverSslContext(
                    originLeaf.keyPair.private,
                    arrayOf(originLeaf.certificate, originAuthority.certificate),
                )
            )
            createContext("/one") { exchange ->
                val body = "one".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/warm") { exchange ->
                val body = "warm".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/two") { exchange ->
                val body = "two".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        val knetAuthority = CertificateAuthority.generate(commonName = "HTTP/2 KNet test CA")
        val capture = RecordingCaptureSink()
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(knetAuthority, CertificateCache()),
            verifyUpstreamTls = false,
            captureSink = capture,
        )
        proxy.start()

        try {
            val client = HttpClient.newBuilder()
                .proxy(ProxySelector.of(assertNotNull(proxy.boundAddress())))
                .sslContext(clientSslContext(knetAuthority.certificate))
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(10.seconds.toJavaDuration())
                .build()
            fun request(path: String): HttpRequest = HttpRequest.newBuilder(
                URI("https://${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.address.port}$path")
            ).timeout(10.seconds.toJavaDuration()).GET().build()

            val warm = client.send(request("/warm"), HttpResponse.BodyHandlers.ofString())
            assertEquals(HttpClient.Version.HTTP_2, warm.version())
            val first = client.sendAsync(request("/one"), HttpResponse.BodyHandlers.ofString())
            val second = client.sendAsync(request("/two"), HttpResponse.BodyHandlers.ofString())
            val responses = listOf(first.join(), second.join())

            assertEquals(listOf("one", "two"), responses.map(HttpResponse<String>::body))
            assertTrue(responses.all { it.version() == HttpClient.Version.HTTP_2 })
            awaitCondition { capture.requests.size == 3 }
            assertEquals(1, capture.openedConnections)
            assertEquals(setOf("HTTP/2"), capture.requests.map { it.request.protocol.token }.toSet())
            assertEquals(3, capture.requests.mapNotNull(RecordedRequest::streamId).distinct().size)
        } finally {
            proxy.stop()
            origin.stop(0)
        }
    }

    private fun serverSslContext(
        privateKey: java.security.PrivateKey,
        certificateChain: Array<X509Certificate>,
    ): SSLContext {
        val password = "knet-http2-test".toCharArray()
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

    private fun clientSslContext(certificateAuthority: X509Certificate): SSLContext {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("knet", certificateAuthority)
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagers.trustManagers, SecureRandom())
        }
    }

    private fun availableLoopbackPort(): Int = ServerSocket(0, 1).use { it.localPort }

    private fun sendH2cRequest(
        parent: Channel,
        originPort: Int,
        path: String,
        configure: (DefaultFullHttpRequest) -> Unit = {},
    ): CompletableFuture<String> {
        val response = CompletableFuture<String>()
        val stream = Http2StreamChannelBootstrap(parent)
            .handler(responseStreamInitializer(response))
            .open()
            .syncUninterruptibly()
            .getNow()
        val absoluteUrl = "http://${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort$path"
        val request = DefaultFullHttpRequest(
            NettyHttpVersion.HTTP_1_1,
            NettyHttpMethod.GET,
            absoluteUrl,
        )
        request.headers().set(HttpHeaderNames.HOST, "${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort")
        configure(request)
        stream.writeAndFlush(request).addListener { result ->
            if (!result.isSuccess) response.completeExceptionally(result.cause())
        }
        return response
    }

    private fun sendH2cRequestWithTrailers(parent: Channel, originPort: Int): CompletableFuture<String> {
        val response = CompletableFuture<String>()
        val stream = Http2StreamChannelBootstrap(parent)
            .handler(responseStreamInitializer(response))
            .open()
            .syncUninterruptibly()
            .getNow()
        val absoluteUrl = "http://${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort/request-trailers"
        val request = DefaultHttpRequest(NettyHttpVersion.HTTP_1_1, NettyHttpMethod.POST, absoluteUrl)
        request.headers().set(HttpHeaderNames.HOST, "${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort")
        val last = DefaultLastHttpContent()
        last.trailingHeaders().set("request-trailer", "preserved")
        stream.write(request)
        stream.writeAndFlush(last).addListener { write ->
            if (!write.isSuccess) response.completeExceptionally(write.cause())
        }
        return response
    }

    private fun responseStreamInitializer(response: CompletableFuture<String>): ChannelInitializer<Channel> =
        object : ChannelInitializer<Channel>() {
            override fun initChannel(channel: Channel) {
                channel.pipeline().addLast(Http2StreamFrameToHttpObjectCodec(false))
                channel.pipeline().addLast(HttpObjectAggregator(1024 * 1024))
                channel.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                    override fun channelRead(context: ChannelHandlerContext, message: Any) {
                        if (message is FullHttpResponse) {
                            response.complete(message.content().toString(Charsets.UTF_8))
                            message.release()
                            context.close()
                        } else {
                            context.fireChannelRead(message)
                        }
                    }

                    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
                        response.completeExceptionally(cause)
                        context.close()
                    }

                    override fun channelInactive(context: ChannelHandlerContext) {
                        response.completeExceptionally(
                            IllegalStateException("HTTP/2 stream closed before a complete response."),
                        )
                        context.fireChannelInactive()
                    }
                })
            }
        }

    private fun awaitCondition(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(20L)
        }
        error("Timed out waiting for HTTP/2 capture events.")
    }

    private data class RecordedRequest(val request: RequestHead, val streamId: StreamId?)

    private data class RecordedTrailers(
        val direction: TrafficDirection,
        val fields: List<com.devuloopers.knet.traffic.model.http.HeaderField>,
    )

    private class DiscardInboundStreamHandler : ChannelInboundHandlerAdapter() {
        override fun channelRead(context: ChannelHandlerContext, message: Any) {
            io.netty.util.ReferenceCountUtil.release(message)
        }
    }

    private class RecordingCaptureSink : ProxyCaptureSink {
        @Volatile
        var openedConnections: Int = 0
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val trailers = CopyOnWriteArrayList<RecordedTrailers>()

        override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture {
            openedConnections += 1
            return object : ProxyConnectionCapture {
                override fun startExchange(
                    exchangeId: ExchangeId,
                    request: RequestHead,
                    occurredAtEpochMillis: Long,
                    origin: TrafficOrigin,
                    streamId: StreamId?,
                ): ProxyExchangeCapture {
                    requests += RecordedRequest(request, streamId)
                    return NoOpExchangeCapture(exchangeId) { direction, fields ->
                        trailers += RecordedTrailers(direction, fields)
                    }
                }

                override fun close(errorCode: String?) = Unit
            }
        }
    }

    private class NoOpExchangeCapture(
        override val exchangeId: ExchangeId,
        private val onTrailers: (
            TrafficDirection,
            List<com.devuloopers.knet.traffic.model.http.HeaderField>,
        ) -> Unit = { _, _ -> },
    ) : ProxyExchangeCapture {
        override fun tryReserveBody(
            direction: TrafficDirection,
            contentEncoding: ContentEncoding?,
            requestedBytes: Int,
        ): ProxyBodyReservation? = null

        override fun completeBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long) = Unit

        override fun cancelBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
            errorCode: String,
        ) = Unit

        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit

        override fun observeTrailers(
            direction: TrafficDirection,
            trailers: List<com.devuloopers.knet.traffic.model.http.HeaderField>,
            occurredAtEpochMillis: Long,
        ) = onTrailers(direction, trailers)

        override fun terminate(
            state: ExchangeState,
            timings: ExchangeTimings,
            occurredAtEpochMillis: Long,
            errorCode: String?,
        ) = Unit
    }
}
