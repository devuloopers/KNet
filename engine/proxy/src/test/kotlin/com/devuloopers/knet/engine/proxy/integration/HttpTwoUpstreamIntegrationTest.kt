package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.client.LocalProxyTlsTrust
import com.devuloopers.knet.core.http.config.HttpClientConfiguration
import com.devuloopers.knet.engine.proxy.KNetProxyServer
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
import com.devuloopers.knet.traffic.model.TrafficAttributionHeader
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.SelfSignedCertificate
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Translation and pooling qualification against a real TLS + ALPN HTTP/2 origin. */
class HttpTwoUpstreamIntegrationTest {

    @Test
    fun `slow upstream stream does not delay a fast sibling on the pooled parent`() {
        val origin = HttpTwoOriginServer()
        origin.start()
        val knetAuthority = CertificateAuthority.generate(commonName = "KNet HTTP/2 fairness CA")
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(knetAuthority, CertificateCache()),
            verifyUpstreamTls = false,
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
                URI("https://${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.port}$path"),
            ).timeout(10.seconds.toJavaDuration()).GET().build()

            assertEquals("origin:/warm", client.send(request("/warm"), HttpResponse.BodyHandlers.ofString()).body())
            val slow = client.sendAsync(request("/slow"), HttpResponse.BodyHandlers.ofString())
            origin.slowStarted.get(5, TimeUnit.SECONDS)
            val fast = client.sendAsync(request("/fast"), HttpResponse.BodyHandlers.ofString())

            assertEquals("origin:/fast", fast.get(2, TimeUnit.SECONDS).body())
            assertFalse(slow.isDone, "Slow stream completed before its independently scheduled body finished.")
            assertEquals("slow-1|slow-2|slow-3|slow-4|", slow.get(5, TimeUnit.SECONDS).body())
            assertEquals(1, origin.acceptedConnections.get())
        } finally {
            proxy.stop()
            origin.close()
        }
    }

    @Test
    fun `large hpack response header crosses the proxy without truncation`() {
        val origin = HttpTwoOriginServer()
        origin.start()
        val knetAuthority = CertificateAuthority.generate(commonName = "KNet HTTP/2 HPACK CA")
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(knetAuthority, CertificateCache()),
            verifyUpstreamTls = false,
        )
        proxy.start()

        try {
            val client = HttpClient.newBuilder()
                .proxy(ProxySelector.of(assertNotNull(proxy.boundAddress())))
                .sslContext(clientSslContext(knetAuthority.certificate))
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(10.seconds.toJavaDuration())
                .build()
            val response = client.send(
                HttpRequest.newBuilder(
                    URI("https://${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.port}/large-header"),
                ).timeout(10.seconds.toJavaDuration()).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            assertEquals(HttpClient.Version.HTTP_2, response.version())
            assertEquals(8_192, response.headers().firstValue("x-knet-large-header").orElseThrow().length)
            assertEquals("large-header", response.body())
        } finally {
            proxy.stop()
            origin.close()
        }
    }

    @Test
    fun `api studio exact http two trusts proxy ca and records local attribution`() = runTest {
        val origin = HttpTwoOriginServer()
        origin.start()
        val knetAuthority = CertificateAuthority.generate(commonName = "KNet API Studio HTTP/2 CA")
        val capture = RecordingCaptureSink()
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(knetAuthority, CertificateCache()),
            verifyUpstreamTls = false,
            captureSink = capture,
        )
        proxy.start()
        val client = KNetApiClient(
            localProxyTlsTrust = LocalProxyTlsTrust(knetAuthority.certificate.encoded),
            captureOrigin = TrafficOrigin.ApiStudio,
            configuration = HttpClientConfiguration(retryCount = 0),
        )

        try {
            val result = client.execute(
                url = "https://${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.port}/api-studio",
                method = com.devuloopers.knet.traffic.model.http.HttpMethod.POST,
                headers = emptyMap(),
                body = OutboundRequestBody.Text("api-studio-h2"),
                auth = ApiRequestAuth.None,
                proxyPort = assertNotNull(proxy.boundAddress()).port,
                httpVersionPreference = HttpVersionPreference.HTTP_2,
            )

            assertTrue(result.isSuccess, result.errorMessage)
            assertEquals("HTTP/2", result.protocol?.token)
            awaitCondition { capture.responses.size == 1 }
            assertEquals(listOf(TrafficOrigin.ApiStudio), capture.origins)
            assertEquals("HTTP/2", capture.requests.single().protocol.token)
            assertEquals("HTTP/2", capture.responses.single().protocol.token)
            assertTrue(capture.requests.single().headers.none { header ->
                header.name.value.equals(TrafficAttributionHeader.NAME, ignoreCase = true)
            })
        } finally {
            client.close()
            proxy.stop()
            origin.close()
        }
    }

    @Test
    fun `trailers and a reset remain isolated to their upstream streams`() {
        val origin = HttpTwoOriginServer()
        origin.start()
        val knetAuthority = CertificateAuthority.generate(commonName = "KNet HTTP/2 control frame CA")
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
                URI("https://${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.port}$path"),
            ).timeout(10.seconds.toJavaDuration()).GET().build()

            val warm = client.send(request("/warm"), HttpResponse.BodyHandlers.ofString())
            assertEquals(200, warm.statusCode())
            val trailerResponse = client.send(request("/trailers"), HttpResponse.BodyHandlers.ofString())
            assertEquals("body-before-trailers", trailerResponse.body())
            awaitCondition {
                capture.trailers.any { event ->
                    event.direction == TrafficDirection.SERVER_TO_CLIENT &&
                        event.fields.any { field -> field.name.value == "x-knet-trailer" }
                }
            }

            val resetResult = runCatching {
                client.send(request("/reset"), HttpResponse.BodyHandlers.ofString())
            }.getOrNull()
            assertTrue(resetResult == null || resetResult.statusCode() == 502)
            val sibling = client.send(request("/after-reset"), HttpResponse.BodyHandlers.ofString())
            assertEquals("origin:/after-reset", sibling.body())
            assertEquals(1, origin.acceptedConnections.get())
        } finally {
            proxy.stop()
            origin.close()
        }
    }

    @Test
    fun `goaway drains the parent and the next request uses a replacement`() {
        val origin = HttpTwoOriginServer()
        origin.start()
        val knetAuthority = CertificateAuthority.generate(commonName = "KNet HTTP/2 GOAWAY CA")
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(knetAuthority, CertificateCache()),
            verifyUpstreamTls = false,
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
                URI("https://${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.port}$path"),
            ).timeout(10.seconds.toJavaDuration()).GET().build()

            assertEquals("origin:/warm", client.send(request("/warm"), HttpResponse.BodyHandlers.ofString()).body())
            assertEquals("goaway", client.send(request("/goaway"), HttpResponse.BodyHandlers.ofString()).body())
            val after = client.send(request("/after-goaway"), HttpResponse.BodyHandlers.ofString())
            assertEquals("origin:/after-goaway", after.body())
            awaitCondition { origin.acceptedConnections.get() == 2 }
        } finally {
            proxy.stop()
            origin.close()
        }
    }

    @Test
    fun `http one downstream reuses one http two upstream parent and records both legs`() {
        val origin = HttpTwoOriginServer()
        origin.start()
        val knetAuthority = CertificateAuthority.generate(commonName = "KNet HTTP/2 translation CA")
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
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(10.seconds.toJavaDuration())
                .build()

            fun request(path: String): HttpRequest = HttpRequest.newBuilder(
                URI("https://${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.port}$path"),
            ).timeout(10.seconds.toJavaDuration()).GET().build()

            val first = client.send(request("/first"), HttpResponse.BodyHandlers.ofString())
            val attributedRequest = HttpRequest.newBuilder(
                URI("https://${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.port}/reserved-header"),
            )
                .timeout(10.seconds.toJavaDuration())
                .header(TrafficAttributionHeader.NAME, TrafficOrigin.ApiStudio.token)
                .GET()
                .build()
            val second = client.send(attributedRequest, HttpResponse.BodyHandlers.ofString())

            assertEquals("origin:/first", first.body())
            assertEquals("absent", second.body())
            assertEquals(HttpClient.Version.HTTP_1_1, first.version())
            assertEquals(HttpClient.Version.HTTP_1_1, second.version())
            awaitCondition { capture.responses.size == 2 }
            assertEquals(1, origin.acceptedConnections.get())
            assertEquals(setOf("HTTP/1.1"), capture.requests.map { it.protocol.token }.toSet())
            assertEquals(setOf("HTTP/2"), capture.responses.map { it.protocol.token }.toSet())
            assertTrue(capture.requests.all { requestHead ->
                requestHead.headers.none { header -> header.name.value.equals(TrafficAttributionHeader.NAME, true) }
            })
        } finally {
            proxy.stop()
            origin.close()
        }
    }

    @Test
    fun `one hundred concurrent http two streams remain multiplexed end to end`() {
        val origin = HttpTwoOriginServer()
        origin.start()
        val knetAuthority = CertificateAuthority.generate(commonName = "KNet HTTP/2 end-to-end CA")
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
                URI("https://${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.port}$path"),
            ).timeout(10.seconds.toJavaDuration()).GET().build()

            val warm = client.send(request("/warm"), HttpResponse.BodyHandlers.ofString())
            assertEquals(HttpClient.Version.HTTP_2, warm.version())
            val responses = (1..100)
                .map { index -> client.sendAsync(request("/stream-$index"), HttpResponse.BodyHandlers.ofString()) }
                .map { future -> future.join() }

            assertTrue(responses.all { response -> response.version() == HttpClient.Version.HTTP_2 })
            assertEquals((1..100).map { "origin:/stream-$it" }.toSet(), responses.map { it.body() }.toSet())
            awaitCondition { capture.responses.size == 101 }
            assertEquals(1, origin.acceptedConnections.get())
            assertEquals(setOf("HTTP/2"), capture.requests.map { it.protocol.token }.toSet())
            assertEquals(setOf("HTTP/2"), capture.responses.map { it.protocol.token }.toSet())
            assertEquals(101, capture.streamIds.size)
            assertEquals(101, capture.streamIds.distinct().size)
        } finally {
            proxy.stop()
            origin.close()
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

    private fun awaitCondition(condition: () -> Boolean) {
        repeat(150) {
            if (condition()) return
            Thread.sleep(20L)
        }
        error("Timed out waiting for HTTP/2 upstream capture events.")
    }

    private class RecordingCaptureSink : ProxyCaptureSink {
        val requests = CopyOnWriteArrayList<RequestHead>()
        val responses = CopyOnWriteArrayList<ResponseHead>()
        val streamIds = CopyOnWriteArrayList<StreamId>()
        val trailers = CopyOnWriteArrayList<TrailerEvent>()
        val origins = CopyOnWriteArrayList<TrafficOrigin>()

        override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture =
            object : ProxyConnectionCapture {
                override fun startExchange(
                    exchangeId: ExchangeId,
                    request: RequestHead,
                    occurredAtEpochMillis: Long,
                    origin: TrafficOrigin,
                    streamId: StreamId?,
                ): ProxyExchangeCapture {
                    requests += request
                    origins += origin
                    if (streamId != null) streamIds += streamId
                    return object : ProxyExchangeCapture {
                        override val exchangeId: ExchangeId = exchangeId

                        override fun tryReserveBody(
                            direction: TrafficDirection,
                            contentEncoding: ContentEncoding?,
                            requestedBytes: Int,
                        ): ProxyBodyReservation? = null

                        override fun completeBody(
                            direction: TrafficDirection,
                            observedBytes: Long,
                            occurredAtEpochMillis: Long,
                        ) = Unit

                        override fun cancelBody(
                            direction: TrafficDirection,
                            observedBytes: Long,
                            occurredAtEpochMillis: Long,
                            errorCode: String,
                        ) = Unit

                        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
                            responses += response
                        }

                        override fun observeTrailers(
                            direction: TrafficDirection,
                            trailers: List<HeaderField>,
                            occurredAtEpochMillis: Long,
                        ) {
                            this@RecordingCaptureSink.trailers += TrailerEvent(direction, trailers)
                        }

                        override fun terminate(
                            state: ExchangeState,
                            timings: ExchangeTimings,
                            occurredAtEpochMillis: Long,
                            errorCode: String?,
                        ) = Unit
                    }
                }

                override fun close(errorCode: String?) = Unit
            }
    }

    private data class TrailerEvent(
        val direction: TrafficDirection,
        val fields: List<HeaderField>,
    )

    private class HttpTwoOriginServer : AutoCloseable {
        private val certificate = SelfSignedCertificate(KNetProxyServer.DEFAULT_BIND_HOST)
        private val acceptor: EventLoopGroup = NioEventLoopGroup(1)
        private val worker: EventLoopGroup = NioEventLoopGroup(2)
        private var serverChannel: Channel? = null
        val acceptedConnections = AtomicInteger(0)
        val slowStarted = CompletableFuture<Unit>()

        val port: Int
            get() = (serverChannel?.localAddress() as? InetSocketAddress)?.port
                ?: error("HTTP/2 origin is not running.")

        fun start() {
            val sslContext = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
                .applicationProtocolConfig(
                    ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                    ),
                )
                .build()
            serverChannel = ServerBootstrap()
                .group(acceptor, worker)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        acceptedConnections.incrementAndGet()
                        channel.pipeline().addLast(sslContext.newHandler(channel.alloc()))
                        channel.pipeline().addLast(
                            object : ApplicationProtocolNegotiationHandler("unsupported") {
                                override fun configurePipeline(context: ChannelHandlerContext, protocol: String) {
                                    require(protocol == ApplicationProtocolNames.HTTP_2)
                                    context.pipeline().addLast(Http2FrameCodecBuilder.forServer().build())
                                    context.pipeline().addLast(
                                        Http2MultiplexHandler(
                                            object : ChannelInitializer<Channel>() {
                                                override fun initChannel(stream: Channel) {
                                                    stream.pipeline().addLast(Http2StreamFrameToHttpObjectCodec(true))
                                                    stream.pipeline().addLast(HttpObjectAggregator(1024 * 1024))
                                                    stream.pipeline().addLast(OriginStreamHandler())
                                                }
                                            },
                                        ),
                                    )
                                }
                            },
                        )
                    }
                })
                .bind(KNetProxyServer.DEFAULT_BIND_HOST, 0)
                .syncUninterruptibly()
                .channel()
        }

        override fun close() {
            serverChannel?.close()?.syncUninterruptibly()
            acceptor.shutdownGracefully().syncUninterruptibly()
            worker.shutdownGracefully().syncUninterruptibly()
            certificate.delete()
        }

        private inner class OriginStreamHandler : SimpleChannelInboundHandler<FullHttpRequest>() {
            override fun channelRead0(context: ChannelHandlerContext, request: FullHttpRequest) {
                when (request.uri()) {
                    "/slow" -> {
                        slowStarted.complete(Unit)
                        context.write(DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
                        (1..4).forEach { index ->
                            context.executor().schedule(
                                {
                                    context.writeAndFlush(
                                        DefaultHttpContent(
                                            Unpooled.wrappedBuffer("slow-$index|".encodeToByteArray()),
                                        ),
                                    )
                                },
                                index * 150L,
                                TimeUnit.MILLISECONDS,
                            )
                        }
                        context.executor().schedule(
                            { context.writeAndFlush(DefaultLastHttpContent()) },
                            750L,
                            TimeUnit.MILLISECONDS,
                        )
                        return
                    }

                    "/large-header" -> {
                        val body = "large-header".encodeToByteArray()
                        val response = DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.OK,
                            Unpooled.wrappedBuffer(body),
                        )
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.size)
                        response.headers().set("x-knet-large-header", "h".repeat(8_192))
                        context.writeAndFlush(response)
                        return
                    }

                    "/trailers" -> {
                        val bytes = "body-before-trailers".encodeToByteArray()
                        val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                        context.write(response)
                        context.write(DefaultHttpContent(Unpooled.wrappedBuffer(bytes)))
                        val last = DefaultLastHttpContent()
                        last.trailingHeaders().set("x-knet-trailer", "upstream-http2")
                        context.writeAndFlush(last)
                        return
                    }

                    "/reset" -> {
                        context.writeAndFlush(DefaultHttp2ResetFrame(Http2Error.CANCEL))
                        return
                    }

                    "/goaway" -> {
                        val body = "goaway".encodeToByteArray()
                        val response = DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.OK,
                            Unpooled.wrappedBuffer(body),
                        )
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.size)
                        context.writeAndFlush(response).addListener {
                            context.channel().parent().writeAndFlush(DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR))
                        }
                        return
                    }
                }
                val body = if (request.uri() == "/reserved-header") {
                    request.headers().get(TrafficAttributionHeader.NAME) ?: "absent"
                } else {
                    "origin:${request.uri()}"
                }.encodeToByteArray()
                val response = DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(body),
                )
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.size)
                context.writeAndFlush(response)
            }
        }
    }
}
