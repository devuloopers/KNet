package com.devuloopers.knet.products.desktop.connectivity

import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.connectivity.desktop.gateway.AuthenticatedProxyGateway
import com.devuloopers.knet.connectivity.desktop.gateway.IngressAttributionRegistry
import com.devuloopers.knet.connectivity.desktop.pairing.JvmPairingCrypto
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.LeafCertificateGenerator
import com.devuloopers.knet.engine.grpc.GrpcStreamInspectorFactory
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.capture.*
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import com.devuloopers.knet.identity.DeviceRegistrationKind
import com.devuloopers.knet.identity.RegisteredDevice
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.*
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.*
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.*
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.SslContextBuilder
import io.netty.util.ReferenceCountUtil
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

/** Real authenticated TLS carrier -> CONNECT -> production proxy -> native H2/gRPC origin. */
class CompanionH2cEndToEndTest {
    @Test
    fun `authenticated H2C preserves all grpc cardinalities trailers capture and source identity`() {
        Fixture().use { fixture ->
            val parent = fixture.connect()
            val unary = fixture.stream(parent, "Unary")
            unary.send(message("one"), end = true)
            assertContentEquals(message("one"), unary.result().body)

            val server = fixture.stream(parent, "Server")
            server.send(message("one"), end = true)
            assertContentEquals(message("one") + message("one") + message("one"), server.result().body)

            val client = fixture.stream(parent, "Client")
            repeat(3) { client.send(message("one"), end = false) }
            client.channel.writeAndFlush(DefaultHttp2HeadersFrame(DefaultHttp2Headers().set("x-request-trailer", "retained"), true))
            assertContentEquals(message("count=3"), client.result().body)

            val bidi = fixture.stream(parent, "Bidi")
            bidi.send(message("one"), end = false)
            // Prove response delivery before client half-close; buffering the whole request would deadlock.
            bidi.firstMessage.get(5, TimeUnit.SECONDS)
            bidi.send(message("two"), end = false)
            bidi.send(message("three"), end = true)
            assertContentEquals(message("one") + message("two") + message("three"), bidi.result().body)

            val failure = fixture.stream(parent, "Fail")
            failure.send(message("one"), end = true)
            val failed = failure.result("3")
            assertEquals("3", failed.headers["grpc-status"])
            assertEquals("fixture-failure", failed.headers["x-grpc-failure"])
            assertTrue(failed.body.isEmpty())

            await { fixture.capture.exchanges.size == 5 && fixture.capture.exchanges.all { it.outcome != null } }
            val captures = fixture.capture.exchanges
            assertTrue(captures.all { (it.request.target as RequestTarget.Absolute).scheme.token == "http" && it.request.protocol.token == "HTTP/2" })
            assertTrue(captures.all { it.response?.protocol?.token == "HTTP/2" })
            assertTrue(captures.all { it.outcome == ExchangeTerminalOutcome.Completed && it.timings?.tlsMillis == 0L })
            assertEquals(5, captures.map { it.streamId }.distinct().size)
            assertTrue(captures.none { row -> row.request.headers.any { it.name.value.equals("Proxy-Authorization", true) } })
            assertTrue(fixture.originHeaders.none { headers -> headers.keys.any { it.startsWith("proxy-", true) || it.startsWith("x-http2-", true) } })
            assertEquals(17, captures.sumOf { it.completedMessages.size })
            assertEquals(9, captures.sumOf { row -> row.completedMessages.count { it.direction == TrafficDirection.CLIENT_TO_SERVER } })
            assertTrue(captures.single { (it.request.target as RequestTarget.Absolute).pathAndQuery.endsWith("Client") }.trailers.any { it.name.value == "x-request-trailer" })
            assertTrue(captures.filterNot { (it.request.target as RequestTarget.Absolute).pathAndQuery.endsWith("Fail") }.all { row ->
                row.trailers.any { it.name.value == "grpc-status" && it.value == "0" }
            })
            assertEquals(setOf("h2c-device"), fixture.capture.connections.map { it.ingress.clientIdentity?.value }.toSet())
            assertEquals(setOf(IngressKind.LanPairedDevice), fixture.capture.connections.map { it.ingress.kind }.toSet())
            assertEquals(1, fixture.originParents.size)
            assertEquals(0, fixture.inspectionCertificates.get())
        }
    }

    @Test
    fun `concurrent streams cancel independently and outlive the discarded TLS setup deadline`() {
        Fixture().use { fixture ->
            val parent = fixture.connect()
            val requests = (1..12).map { index -> fixture.stream(parent, "Unary").also { it.send(message("stream-$index"), true) } }
            requests.forEachIndexed { index, stream -> assertContentEquals(message("stream-${index + 1}"), stream.result().body) }
            val cancelled = fixture.stream(parent, "Bidi")
            cancelled.send(message("before-cancel"), false)
            cancelled.firstMessage.get(5, TimeUnit.SECONDS)
            cancelled.channel.writeAndFlush(DefaultHttp2ResetFrame(Http2Error.CANCEL)).syncUninterruptibly()
            // Removing ClientHello classification must cancel its setup timer on this plaintext parent.
            Thread.sleep(1_100)
            val after = fixture.stream(parent, "Unary")
            after.send(message("after-cancel"), true)
            assertContentEquals(message("after-cancel"), after.result().body)
            assertTrue(parent.isActive)
            await { fixture.capture.exchanges.any { row -> row.outcome?.state == ExchangeState.CANCELLED } }
            assertEquals(1, fixture.originParents.size)
        }
    }

    @Test
    fun `completed H2 carrier close releases the connection before the idle deadline`() {
        Fixture().use { fixture ->
            val parent = fixture.connect()
            fixture.stream(parent, "Unary").also { it.send(message("complete"), true); it.result() }
            parent.pipeline().get(io.netty.handler.ssl.SslHandler::class.java).closeOutbound().syncUninterruptibly()
            await { fixture.capture.closedConnections.get() == 1 }
            assertEquals(1, fixture.capture.closedConnections.get())
        }
    }

    @Test
    fun `H2 connection half close preserves delayed responses on multiple complete streams`() {
        Fixture().use { fixture ->
            val parent = fixture.connect()
            val requests = (1..3).map { index ->
                fixture.stream(parent, "Delayed").also { stream -> stream.send(message("delayed-$index"), true) }
            }
            await { fixture.originHeaders.size == 3 }
            parent.pipeline().get(io.netty.handler.ssl.SslHandler::class.java).closeOutbound().syncUninterruptibly()
            Thread.sleep(100)
            assertEquals(0, fixture.capture.closedConnections.get(), "Complete requests still own their delayed responses")
            fixture.releaseResponse.complete(Unit)
            requests.forEachIndexed { index, stream -> assertContentEquals(message("delayed-${index + 1}"), stream.result().body) }
            await { fixture.capture.closedConnections.get() == 1 }
            assertTrue(fixture.capture.exchanges.all { row -> row.outcome == ExchangeTerminalOutcome.Completed })
        }
    }

    @Test
    fun `H2 connection EOF cancels a request whose body never ended`() {
        Fixture().use { fixture ->
            val parent = fixture.connect()
            fixture.stream(parent, "Unary").send(message("unfinished"), false)
            await { fixture.originHeaders.isNotEmpty() }
            parent.pipeline().get(io.netty.handler.ssl.SslHandler::class.java).closeOutbound().syncUninterruptibly()
            await { fixture.capture.closedConnections.get() == 1 }
            assertEquals(ExchangeState.CANCELLED, fixture.capture.exchanges.single().outcome?.state)
        }
    }

    @Test
    fun `TLS requests cannot reuse an existing plaintext origin parent`() {
        Fixture().use { fixture ->
            val clear = fixture.connect()
            fixture.stream(clear, "Unary").also { it.send(message("clear"), true); it.result() }
            val tls = fixture.connect(innerTls = true)
            val secure = fixture.stream(tls, "Unary")
            secure.send(message("must-use-TLS"), true)
            assertEquals("502", secure.rawResult().headers[":status"])
            val after = fixture.stream(clear, "Unary")
            after.send(message("still-clear"), true)
            assertContentEquals(message("still-clear"), after.result().body)
            assertEquals(2, fixture.originParents.size, "TLS must dial separately instead of sending secure requests on a plaintext parent")
        }
    }

    @Test
    fun `invalid carrier credential cannot reach the H2 origin or acquire capture identity`() {
        Fixture().use { fixture ->
            assertFails { fixture.connect(credential = "invalid") }
            assertTrue(fixture.originParents.isEmpty())
            assertTrue(fixture.capture.connections.isEmpty())
        }
    }

    private class Fixture : AutoCloseable {
        val capture = Capture()
        val inspectionCertificates = java.util.concurrent.atomic.AtomicInteger()
        val originParents = CopyOnWriteArrayList<Channel>()
        val originHeaders = CopyOnWriteArrayList<Map<String, String>>()
        val releaseResponse = CompletableFuture<Unit>()
        private val group = NioEventLoopGroup(2)
        private val clients = CopyOnWriteArrayList<Channel>()
        private val origin = ServerBootstrap().group(group).channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    originParents += channel
                    channel.pipeline().addLast(Http2FrameCodecBuilder.forServer().build())
                    channel.pipeline().addLast(Http2MultiplexHandler(object : ChannelInitializer<Channel>() {
                        override fun initChannel(child: Channel) { child.pipeline().addLast(OriginStream(originHeaders, releaseResponse)) }
                    }))
                }
            }).bind("127.0.0.1", 0).syncUninterruptibly().channel()
        private val originPort = (origin.localAddress() as InetSocketAddress).port
        private val authority = CertificateAuthority.generate(commonName = "H2C authenticated fixture CA")
        private val leaf = LeafCertificateGenerator.generate(CompanionCertificateProtocol.TLS_SERVER_NAME, authority)
        private val crypto = JvmPairingCrypto()
        private val credential = crypto.randomToken(48)
        private val attributions = IngressAttributionRegistry()
        private val store = DeviceStore(crypto.digest(credential))
        private val proxy = KNetProxyServer(
            port = ServerSocket(0).use { it.localPort },
            serverTlsContextProvider = ServerTlsContextProvider { host, _ ->
                inspectionCertificates.incrementAndGet()
                val certificate = LeafCertificateGenerator.generate(host, authority)
                CompletableFuture.completedFuture(SslContextBuilder.forServer(certificate.keyPair.private, certificate.certificate)
                    .applicationProtocolConfig(h2Alpn()).build())
            },
            runtimePolicy = KNetProxyRuntimePolicy(tlsHandshakeTimeoutMillis = 1_000, gracefulShutdownTimeoutMillis = 100),
            ingressAttribution = attributions,
            captureSink = capture,
            streamInspectorFactories = listOf(GrpcStreamInspectorFactory()),
        ).apply { start() }
        private val gateway = AuthenticatedProxyGateway(
            bindHost = "127.0.0.1", bindPort = 0,
            serverSocketFactory = serverContext().serverSocketFactory,
            targetProxy = { proxy.boundAddress() },
            pairing = PairingCoordinator(store, crypto, System::currentTimeMillis),
            attributions = attributions,
        ).apply { start() }

        fun connect(credential: String = this.credential, innerTls: Boolean = false): Channel {
            val ready = CompletableFuture<Channel>()
            val ssl = SslContextBuilder.forClient().trustManager(authority.certificate).build()
            val channel = Bootstrap().group(group).channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast("carrierTls", ssl.newHandler(channel.alloc(), CompanionCertificateProtocol.TLS_SERVER_NAME, gateway.boundPort!!))
                        channel.pipeline().addLast("connectCodec", HttpClientCodec())
                        channel.pipeline().addLast("connectAggregate", HttpObjectAggregator(16 * 1024))
                        channel.pipeline().addLast("connectResponse", object : SimpleChannelInboundHandler<FullHttpResponse>() {
                            override fun channelRead0(context: ChannelHandlerContext, response: FullHttpResponse) {
                                if (response.status().code() != 200) {
                                    ready.completeExceptionally(IllegalStateException("Carrier status ${response.status()}")); context.close(); return
                                }
                                context.pipeline().remove("connectAggregate")
                                context.pipeline().remove("connectCodec")
                                if (innerTls) {
                                    val inner = SslContextBuilder.forClient().trustManager(authority.certificate)
                                        .applicationProtocolConfig(h2Alpn()).build()
                                    context.pipeline().addLast("originTls", inner.newHandler(context.alloc(), "127.0.0.1", originPort))
                                }
                                context.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                                context.pipeline().addLast(Http2MultiplexHandler(object : ChannelInboundHandlerAdapter() {
                                    override fun channelRead(context: ChannelHandlerContext, message: Any) { ReferenceCountUtil.release(message) }
                                }))
                                context.pipeline().remove(this)
                                ready.complete(context.channel())
                            }
                            override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
                                ready.completeExceptionally(cause); context.close()
                            }
                        })
                    }
                }).connect("127.0.0.1", gateway.boundPort!!).syncUninterruptibly().channel()
            clients += channel
            val connect = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.CONNECT, "127.0.0.1:$originPort")
            connect.headers().set(HttpHeaderNames.HOST, "127.0.0.1:$originPort")
                .set("Proxy-Authorization", "Bearer h2c-device:$credential")
            channel.writeAndFlush(connect).syncUninterruptibly()
            return ready.get(5, TimeUnit.SECONDS)
        }

        fun stream(parent: Channel, method: String): ClientStream {
            val handler = ClientStream()
            handler.channel = Http2StreamChannelBootstrap(parent).handler(handler).open().syncUninterruptibly().getNow()
            handler.channel.writeAndFlush(DefaultHttp2HeadersFrame(DefaultHttp2Headers()
                .method("POST").scheme("http").authority("unresolvable.example.test:$originPort")
                .path("/fixture.Protocol/$method").set("content-type", "application/grpc").set("te", "trailers"), false)).syncUninterruptibly()
            return handler
        }

        private fun serverContext(): SSLContext {
            val keys = KeyStore.getInstance("PKCS12").apply {
                load(null); setKeyEntry("carrier", leaf.keyPair.private, charArrayOf(), arrayOf(leaf.certificate, authority.certificate))
            }
            val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, charArrayOf()) }
            return SSLContext.getInstance("TLS").apply { init(manager.keyManagers, null, null) }
        }

        override fun close() {
            clients.forEach { it.close().syncUninterruptibly() }
            gateway.close(); proxy.stop(); origin.close().syncUninterruptibly()
            originParents.forEach { it.close().syncUninterruptibly() }
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly()
        }
    }

    private data class Response(val body: ByteArray, val headers: Map<String, String>)

    private class ClientStream : SimpleChannelInboundHandler<Http2StreamFrame>() {
        lateinit var channel: Channel
        val firstMessage = CompletableFuture<Unit>()
        private val response = CompletableFuture<Response>()
        private val body = ByteArrayOutputStream()
        private val headers = linkedMapOf<String, String>()
        override fun channelRead0(context: ChannelHandlerContext, frame: Http2StreamFrame) {
            when (frame) {
                is Http2HeadersFrame -> { frame.headers().forEach { headers[it.key.toString()] = it.value.toString() }; if (frame.isEndStream) complete() }
                is Http2DataFrame -> {
                    val bytes = ByteArray(frame.content().readableBytes()); frame.content().getBytes(frame.content().readerIndex(), bytes)
                    body.write(bytes)
                    val accumulated = body.toByteArray()
                    if (accumulated.size >= 5 && accumulated.size >= 5 + ByteBuffer.wrap(accumulated, 1, 4).int) firstMessage.complete(Unit)
                    if (frame.isEndStream) complete()
                }
                is Http2ResetFrame -> response.completeExceptionally(IllegalStateException("Reset ${frame.errorCode()}"))
            }
        }
        private fun complete() { response.complete(Response(body.toByteArray(), headers.toMap())) }
        fun send(payload: ByteArray, end: Boolean) { channel.writeAndFlush(DefaultHttp2DataFrame(Unpooled.wrappedBuffer(payload), end)).syncUninterruptibly() }
        fun rawResult(): Response = response.get(5, TimeUnit.SECONDS)
        fun result(expectedGrpcStatus: String = "0"): Response = rawResult().also { assertEquals("200", it.headers[":status"]); assertEquals(expectedGrpcStatus, it.headers["grpc-status"]) }
        override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) { firstMessage.completeExceptionally(cause); response.completeExceptionally(cause); context.close() }
    }

    /** Independent minimal native gRPC peer; DATA frames are deframed incrementally, including live bidi replies. */
    private class OriginStream(private val observed: MutableList<Map<String, String>>, private val releaseResponse: CompletableFuture<Unit>) : SimpleChannelInboundHandler<Http2StreamFrame>() {
        private var method = ""
        private var pending = byteArrayOf()
        private val messages = mutableListOf<ByteArray>()
        private var responseStarted = false
        override fun channelRead0(context: ChannelHandlerContext, frame: Http2StreamFrame) {
            val end = when (frame) {
                is Http2HeadersFrame -> {
                    if (method.isEmpty()) { method = frame.headers().path().toString().substringAfterLast('/'); observed += frame.headers().associate { it.key.toString() to it.value.toString() } }
                    frame.isEndStream
                }
                is Http2DataFrame -> {
                    val bytes = ByteArray(frame.content().readableBytes()); frame.content().getBytes(frame.content().readerIndex(), bytes)
                    pending += bytes
                    while (pending.size >= 5) {
                        val length = ByteBuffer.wrap(pending, 1, 4).int
                        if (pending.size < 5 + length) break
                        val payload = pending.copyOfRange(5, 5 + length); messages += payload; pending = pending.copyOfRange(5 + length, pending.size)
                        if (method == "Bidi") { start(context); context.writeAndFlush(DefaultHttp2DataFrame(Unpooled.wrappedBuffer(envelope(payload)), false)) }
                    }
                    frame.isEndStream
                }
                else -> false
            }
            if (!end) return
            if (method == "Delayed") {
                releaseResponse.thenRun { context.executor().execute { respond(context) } }
                return
            }
            respond(context)
        }
        private fun respond(context: ChannelHandlerContext) {
            if (method == "Fail") {
                context.writeAndFlush(DefaultHttp2HeadersFrame(DefaultHttp2Headers().status("200").set("content-type", "application/grpc")
                    .set("grpc-status", "3").set("x-grpc-failure", "fixture-failure"), true)); return
            }
            start(context)
            val replies = when (method) { "Server" -> List(3) { messages.single() }; "Client" -> listOf("count=${messages.size}".encodeToByteArray()); "Bidi" -> emptyList(); else -> messages }
            replies.forEach { context.write(DefaultHttp2DataFrame(Unpooled.wrappedBuffer(envelope(it)), false)) }
            context.writeAndFlush(DefaultHttp2HeadersFrame(DefaultHttp2Headers().set("grpc-status", "0").set("x-response-trailer", "retained"), true))
        }
        private fun start(context: ChannelHandlerContext) {
            if (!responseStarted) { responseStarted = true; context.write(DefaultHttp2HeadersFrame(DefaultHttp2Headers().status("200").set("content-type", "application/grpc"), false)) }
        }
    }

    private class Capture : ProxyCaptureSink {
        val closedConnections = java.util.concurrent.atomic.AtomicInteger()
        val connections = CopyOnWriteArrayList<ProxyCaptureConnectionMetadata>()
        val exchanges = CopyOnWriteArrayList<CapturedExchange>()
        override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture {
            connections += metadata
            return object : ProxyConnectionCapture {
                override fun startExchange(exchangeId: ExchangeId, request: RequestHead, occurredAtEpochMillis: Long, origin: TrafficOrigin, streamId: StreamId?): ProxyExchangeCapture =
                    CapturedExchange(exchangeId, request, streamId).also(exchanges::add)
                override fun close(reason: TrafficTerminationReason?) { closedConnections.incrementAndGet() }
            }
        }
    }

    private class CapturedExchange(override val exchangeId: ExchangeId, val request: RequestHead, val streamId: StreamId?) : ProxyExchangeCapture {
        @Volatile var response: ResponseHead? = null
        @Volatile var outcome: ExchangeTerminalOutcome? = null
        @Volatile var timings: ExchangeTimings? = null
        val trailers = CopyOnWriteArrayList<HeaderField>()
        val completedMessages = CopyOnWriteArrayList<ProxyMessageCaptureMetadata>()
        override fun tryReserveBody(direction: TrafficDirection, contentEncoding: ContentEncoding?, requestedBytes: Int): ProxyBodyReservation? = null
        override fun completeBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long) = Unit
        override fun cancelBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long, reason: TrafficTerminationReason) = Unit
        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) { this.response = response }
        override fun observeTrailers(direction: TrafficDirection, trailers: List<HeaderField>, occurredAtEpochMillis: Long) { this.trailers += trailers }
        // Match StreamingProxyCaptureSession: peer teardown callbacks cannot overwrite a terminal row.
        @Synchronized
        override fun terminate(outcome: ExchangeTerminalOutcome, timings: ExchangeTimings, occurredAtEpochMillis: Long) {
            if (this.outcome != null) return
            this.timings = timings; this.outcome = outcome
        }
        override fun startMessage(metadata: ProxyMessageCaptureMetadata): ProxyMessageCapture = object : ProxyMessageCapture {
            override val messageId = metadata.messageId
            override fun tryReservePayload(requestedBytes: Int): ProxyBodyReservation? = null
            override fun complete(observedBytes: Long, occurredAtEpochMillis: Long) { completedMessages += metadata }
            override fun terminate(observedBytes: Long, state: com.devuloopers.knet.traffic.model.message.ProtocolMessageState, occurredAtEpochMillis: Long, reason: TrafficTerminationReason?) = Unit
        }
    }

    /** Starts with one previously paired ephemeral identity; real credential authentication remains enabled. */
    private class DeviceStore(digest: String) : TrustedDeviceStore {
        private val device = TrustedDevice(RegisteredDevice(RegisteredDeviceId("h2c-device"), "H2C fixture", DeviceRegistrationKind.PAIRED_COMPANION, 1, 1),
            "ephemeral-test-key", digest, setOf(DeviceScope.PROXY_STREAM), 1, Long.MAX_VALUE)
        override suspend fun getDevice(id: RegisteredDeviceId): TrustedDevice? = device.takeIf { it.id == id }
        override fun observeDevices(): Flow<List<TrustedDevice>> = MutableStateFlow(listOf(device))
        override suspend fun putDevice(device: TrustedDevice) = error("Unused")
        override suspend fun putInvitation(invitation: PendingPairingInvitation) = error("Unused")
        override suspend fun claimInvitation(id: PairingInvitationId, secretDigest: String, nowEpochMillis: Long): PendingPairingInvitation? = null
        override suspend fun rotateCredential(id: RegisteredDeviceId, expectedCredentialDigest: String, newCredentialDigest: String, credentialExpiresAtEpochMillis: Long): Boolean = false
        override suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean = false
    }

    private companion object {
        fun h2Alpn() = ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, ApplicationProtocolNames.HTTP_2)
        fun envelope(bytes: ByteArray): ByteArray = byteArrayOf(0) + ByteBuffer.allocate(4).putInt(bytes.size).array() + bytes
        fun message(text: String): ByteArray = envelope(text.encodeToByteArray())
        fun await(condition: () -> Boolean) { repeat(250) { if (condition()) return; Thread.sleep(20) }; error("Capture did not converge") }
    }
}
