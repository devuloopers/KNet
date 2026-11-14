package com.devuloopers.knet.products.desktop.policy

import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.application.contract.proxy.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.application.contract.breakpoint.BreakpointDecision
import com.devuloopers.knet.connectivity.desktop.gateway.*
import com.devuloopers.knet.connectivity.desktop.pairing.JvmPairingCrypto
import com.devuloopers.knet.connectivity.model.*
import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureSessionFactory
import com.devuloopers.knet.data.desktop.proxy.repository.DesktopProxyRuntimeAdapter
import com.devuloopers.knet.data.desktop.runtime.ProxyRuntimeRepository
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficQueryAdapter
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.data.desktop.protectedtraffic.ProtectedTrafficTlsInterceptionPolicy
import com.devuloopers.knet.engine.simulator.NetworkConditionEngine
import com.devuloopers.knet.domain.rules.model.*
import com.devuloopers.knet.engine.certificate.*
import com.devuloopers.knet.engine.grpc.GrpcStreamInspectorFactory
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.engine.sse.capture.SseStreamInspectorFactory
import com.devuloopers.knet.engine.websocket.WebSocketDuplexInspectorFactory
import com.devuloopers.knet.identity.*
import com.devuloopers.knet.pairing.*
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.model.*
import io.netty.bootstrap.*
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import io.netty.handler.ssl.SslContextBuilder
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import kotlin.test.*

/** Production runtime, authenticated carriers, canonical Room/body storage and real protocol origins. */
class DeviceCaptureSocketEndToEndTest {
    @Test fun recordingChoicesAreIndependentAcrossHttpHttpsGraphqlReconnectAndClearRotation(): Unit = runBlocking {
        Fixture().use { fixture ->
            val runtimeId = (fixture.runtime.state.value as ProxyRuntimeState.Running).handle.runtimeId
            val session = (fixture.runtime.captureState.value as CaptureSessionState.Capturing).sessionId
            fixture.http("owner", "/first")
            fixture.http("sibling", "/not-started")
            fixture.awaitCount("owner", 1); fixture.awaitCount("sibling", 0)
            fixture.runtime.resume(sibling)
            fixture.runtime.pause(owner)
            for ((path, secure, body) in listOf(Triple("/http", false, ""), Triple("/https", true, ""),
                Triple("/graphql?query=%7Bviewer%7Bid%7D%7D", false, ""), Triple("/graphql", false, "{\"query\":\"{viewer{id}}\"}"))) {
                fixture.http("owner", path, secure, body)
                fixture.http("sibling", path, secure, body)
            }
            fixture.awaitCount("owner", 1); fixture.awaitCount("sibling", 4)
            fixture.http("new-device", "/no-inherited-admission")
            fixture.awaitCount("new-device", 0)
            fixture.runtime.rotateForSourceTrafficClear()
            fixture.http("owner", "/still-paused-after-rotation")
            fixture.http("sibling", "/still-recording-after-rotation")
            fixture.awaitCount("owner", 1); fixture.awaitCount("sibling", 5)
            fixture.runtime.resume(owner)
            fixture.http("owner", "/reconnected")
            fixture.awaitCount("owner", 2)
            assertEquals(runtimeId, (fixture.runtime.state.value as ProxyRuntimeState.Running).handle.runtimeId)
            assertNotEquals(session, (fixture.runtime.captureState.value as CaptureSessionState.Capturing).sessionId)
            assertEquals(1, fixture.database.canonicalCaptureDao().countActiveSessions())
            assertEquals(CaptureSessionState.Paused, fixture.runtime.workspaceCaptureState.value.stateFor(PolicyWorkspace.Other))
        }
    }

    @Test fun pausingDuringAnHttpBodyOrSseStreamDoesNotInterruptEitherDevicesPayload(): Unit = runBlocking {
        Fixture().use { fixture ->
            fixture.runtime.resume(sibling)
            for (kind in listOf("body", "sse")) {
                fixture.runtime.resume(owner)
                val ownerSocket = fixture.connect("owner")
                val siblingSocket = fixture.connect("sibling")
                ownerSocket.use { first -> siblingSocket.use { second ->
                    val path = "/hold/$kind"
                    fixture.send(first, path); fixture.send(second, path)
                    assertTrue(readHeader(first.inputStream).startsWith("HTTP/1.1 200"))
                    assertTrue(readHeader(second.inputStream).startsWith("HTTP/1.1 200"))
                    val prefix = if (kind == "sse") "data: x\n\n" else "x"
                    val suffix = if (kind == "sse") "data: y\n\n" else "yz"
                    assertContentEquals(prefix.toByteArray(), first.inputStream.readNBytes(prefix.length))
                    assertContentEquals(prefix.toByteArray(), second.inputStream.readNBytes(prefix.length))
                    fixture.awaitPath("owner", path)
                    fixture.runtime.pause(owner)
                    fixture.releases.getValue(path).complete(Unit)
                    assertEquals(suffix, first.inputStream.readNBytes(suffix.length).decodeToString())
                    assertEquals(suffix, second.inputStream.readNBytes(suffix.length).decodeToString())
                    if (kind == "sse") {
                        val siblingRow = fixture.awaitPath("sibling", path).single()
                        fixture.awaitMessages(siblingRow.exchange.id.value, 2)
                    }
                    val cut = fixture.reader.query(TrafficPageQuery(limit = 50, source = source("owner"))).items.single {
                        it.exchange.request.head.target.toString().contains(path)
                    }.exchange
                    assertEquals(ExchangeState.CANCELLED, cut.state)
                    assertEquals("capture_paused", cut.terminalOutcome?.reason?.code?.value)
                } }
            }
        }
    }

    @Test fun websocketAndGraphqlWebsocketRemainUsableWhileOnlyOneDevicesRecordingIsPaused(): Unit = runBlocking {
        Fixture().use { fixture ->
            fixture.runtime.resume(sibling)
            for (subprotocol in listOf<String?>(null, "graphql-transport-ws")) {
                fixture.runtime.resume(owner)
                fixture.websocket("owner", subprotocol).use { first -> fixture.websocket("sibling", subprotocol).use { second ->
                    val opening = if (subprotocol == null) "before" else """{"type":"connection_init"}"""
                    echo(first, opening); echo(second, opening)
                    val firstRow = fixture.awaitPath("owner", "/ws").first()
                    val secondRow = fixture.awaitPath("sibling", "/ws").first()
                    fixture.awaitMessages(firstRow.exchange.id.value, 2)
                    fixture.runtime.pause(owner)
                    val cutCount = fixture.database.canonicalCaptureDao().countDuplexMessages(firstRow.exchange.id.value)
                    val next = if (subprotocol == null) "while-paused" else """{"type":"ping"}"""
                    echo(first, next); echo(second, next)
                    fixture.awaitMessages(secondRow.exchange.id.value, 4)
                    assertEquals(cutCount, fixture.database.canonicalCaptureDao().countDuplexMessages(firstRow.exchange.id.value))
                    fixture.runtime.resume(owner)
                    fixture.websocket("owner", subprotocol).use { reconnected -> echo(reconnected, opening) }
                } }
            }
        }
    }

    @Test fun nativeGrpcAndH2ReuseAuthenticatedConnectionsAcrossIndependentPauseAndResume(): Unit = runBlocking {
        Fixture().use { fixture ->
            fixture.runtime.resume(sibling)
            val first = fixture.h2("owner"); val second = fixture.h2("sibling")
            val framed = byteArrayOf(0, 0, 0, 0, 2, 65, 66)
            assertContentEquals(framed, fixture.grpc(first, "before", framed))
            assertContentEquals(framed, fixture.grpc(second, "before", framed))
            fixture.awaitCount("owner", 1); fixture.awaitCount("sibling", 1)
            fixture.runtime.pause(owner)
            assertContentEquals(framed, fixture.grpc(first, "paused", framed))
            assertContentEquals(framed, fixture.grpc(second, "still-recording", framed))
            fixture.awaitCount("owner", 1); fixture.awaitCount("sibling", 2)
            fixture.runtime.resume(owner)
            assertContentEquals(framed, fixture.grpc(first, "resumed", framed))
            fixture.awaitCount("owner", 2)
            assertTrue(first.isActive); assertTrue(second.isActive)
        }
    }

    @Test fun pausedRecordingStillEnforcesItsNetworkAndProtectedTrafficPolicies(): Unit = runBlocking {
        Fixture().use { fixture ->
            fixture.runtime.resume(sibling)
            fixture.runtime.pause(owner)
            fixture.conditions.value = mapOf(owner to NetworkConditionConfiguration(enabled = true,
                globalProfileId = NetworkConditionBuiltIns.OFFLINE.id))
            withTimeout(5_000) { while (!fixture.network.configurationSnapshot(owner).enabled) delay(10) }
            assertFails { fixture.http("owner", "/offline-while-not-recording") }
            fixture.http("sibling", "/sibling-unaffected")
            fixture.conditions.value = emptyMap()
            withTimeout(5_000) { while (fixture.network.configurationSnapshot(owner).enabled) delay(10) }
            fixture.protection.value = mapOf(owner to ProtectedTrafficConfiguration(defaultAction = ProtectedTrafficAction.BLOCK))
            assertFails { fixture.http("owner", "/blocked-while-not-recording", secure = true) }
            fixture.protection.value = mapOf(owner to ProtectedTrafficConfiguration(defaultAction = ProtectedTrafficAction.TUNNEL))
            fixture.http("owner", "/tunneled-while-not-recording", secure = true)
            fixture.http("sibling", "/sibling-inspected", secure = true)
            fixture.awaitCount("owner", 0); fixture.awaitCount("sibling", 2)
        }
    }

    @Test fun pendingRequestRemainsActionableAfterPausingItsDeviceWhileSiblingForwards(): Unit = runBlocking {
        Fixture().use { fixture ->
            fixture.runtime.resume(sibling)
            fixture.coordinator.replaceWorkspaceRules(mapOf(owner to listOf(
                BreakpointRule("pending", urlPattern = "*", phase = BreakpointPhase.REQUEST))))
            val waiting = async(Dispatchers.IO) { fixture.http("owner", "/pending") }
            val pending = withTimeout(5_000) { fixture.coordinator.pendingBreakpoints.first { it.isNotEmpty() }.single() }
            fixture.runtime.pause(owner)
            assertEquals(pending.id, fixture.coordinator.pendingBreakpoints.value.single().id)
            fixture.http("sibling", "/sibling")
            assertTrue(fixture.coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged))
            waiting.await()
            fixture.http("owner", "/not-paused-by-breakpoint")
            assertTrue(fixture.coordinator.pendingBreakpoints.value.isEmpty())
        }
    }

    private class Fixture : AutoCloseable {
        private val directory = Files.createTempDirectory("knet-device-capture-socket-").toFile()
        val database = DatabaseFactory.create(directory.resolve("traffic.db"))
        private val store = FileBodyStore(directory.resolve("bodies"))
        val reader = DesktopTrafficQueryAdapter(database.canonicalCaptureDao(), store)
        val coordinator = BreakpointCoordinator()
        private val ca = CertificateAuthority.generate(commonName = "Disposable source capture fixture")
        private val contexts = contexts(ca)
        private val crypto = JvmPairingCrypto()
        private val credentials = listOf("owner", "sibling", "new-device").associateWith { crypto.randomToken(48) }
        private val attribution = IngressAttributionRegistry()
        private val workers = Executors.newCachedThreadPool()
        private val origin = ServerSocket(0)
        private val secureOrigin = contexts.first.serverSocketFactory.createServerSocket(0)
        private val proxyPort = ServerSocket(0).use { it.localPort }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val conditions = MutableStateFlow<Map<PolicyWorkspace, NetworkConditionConfiguration>>(emptyMap())
        val protection = MutableStateFlow<Map<PolicyWorkspace, ProtectedTrafficConfiguration>>(emptyMap())
        val network = NetworkConditionEngine({ NetworkConditionConfiguration() })
        val releases = ConcurrentHashMap<String, CompletableFuture<Unit>>()
        private data class HttpRoute(val id: String, val port: Int, val secure: Boolean)
        private val routes = ConcurrentHashMap<Socket, HttpRoute>()
        private val h2Group = NioEventLoopGroup(2)
        private val h2Connections = CopyOnWriteArrayList<Channel>()
        private val h2Origin = ServerBootstrap().group(h2Group).channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    h2Connections += channel
                    channel.pipeline().addLast(Http2FrameCodecBuilder.forServer().build(), Http2MultiplexHandler(
                        object : ChannelInitializer<Channel>() {
                            override fun initChannel(child: Channel) {
                                child.pipeline().addLast(object : SimpleChannelInboundHandler<Http2StreamFrame>() {
                                    private val body = ByteArrayOutputStream()
                                    override fun channelRead0(context: ChannelHandlerContext, frame: Http2StreamFrame) {
                                        if (frame is Http2DataFrame) { val bytes = ByteArray(frame.content().readableBytes()); frame.content().readBytes(bytes); body.write(bytes) }
                                        if (frame is Http2DataFrame && frame.isEndStream) {
                                            context.write(DefaultHttp2HeadersFrame(DefaultHttp2Headers().status("200").set("content-type", "application/grpc"), false))
                                            context.write(DefaultHttp2DataFrame(Unpooled.wrappedBuffer(body.toByteArray()), false))
                                            context.writeAndFlush(DefaultHttp2HeadersFrame(DefaultHttp2Headers().set("grpc-status", "0"), true))
                                        }
                                    }
                                })
                            }
                        }))
                }
            }).bind("127.0.0.1", 0).syncUninterruptibly().channel()
        val runtime = DesktopProxyRuntimeAdapter(ProxyRuntimeRepository(ca, CertificateCache(), breakpointGate = coordinator,
            ingressAttribution = attribution, streamInspectorFactories = listOf(GrpcStreamInspectorFactory(), SseStreamInspectorFactory()),
            duplexInspectorFactories = listOf(WebSocketDuplexInspectorFactory()), networkConditionEngine = network,
            tlsInterceptionPolicy = ProtectedTrafficTlsInterceptionPolicy({ ProtectedTrafficConfiguration() },
                configurationForWorkspace = { protection.value[it] ?: ProtectedTrafficConfiguration() })),
            CanonicalCaptureSessionFactory(database, store, store), coordinator)
        private val gateway: AuthenticatedProxyGateway
        init {
            network.bindWorkspaces(conditions, scope)
            for (server in listOf(origin, secureOrigin)) workers.submit {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: java.net.SocketException) { break }
                    workers.submit { runCatching { socket.use { serve(it) } } }
                }
            }
            runBlocking {
                assertIs<CaptureResumeResult.ProxyInactive>(runtime.resume(owner))
                assertIs<ProxyStartResult.Running>(runtime.start(ProxyRuntimeConfiguration(
                    listOf(ProxyBindingConfiguration("127.0.0.1", proxyPort, ProxyEndpointScope.LOOPBACK)), false,
                    ProxyTimeoutPolicy(5_000, 5_000, 60_000, 60_000, 100), ProxyConnectionLimits(128, 64, 128))))
            }
            val deviceStore = object : TrustedDeviceStore {
                private val devices = credentials.map { (id, credential) -> TrustedDevice(
                    RegisteredDevice(RegisteredDeviceId(id), "Same display name", DeviceRegistrationKind.PAIRED_COMPANION, 1, 1),
                    "fixture", crypto.digest(credential), setOf(DeviceScope.PROXY_STREAM), 1, Long.MAX_VALUE) }
                override suspend fun getDevice(id: RegisteredDeviceId) = devices.firstOrNull { it.id == id }
                override fun observeDevices(): Flow<List<TrustedDevice>> = MutableStateFlow(devices)
                override suspend fun putDevice(device: TrustedDevice) = error("Unused")
                override suspend fun putInvitation(invitation: PendingPairingInvitation) = error("Unused")
                override suspend fun claimInvitation(id: PairingInvitationId, secretDigest: String, nowEpochMillis: Long): PendingPairingInvitation? = null
                override suspend fun rotateCredential(id: RegisteredDeviceId, expectedCredentialDigest: String, newCredentialDigest: String, credentialExpiresAtEpochMillis: Long) = false
                override suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long) = false
            }
            gateway = AuthenticatedProxyGateway("127.0.0.1", 0, contexts.first.serverSocketFactory,
                { InetSocketAddress("127.0.0.1", proxyPort) }, PairingCoordinator(deviceStore, crypto, System::currentTimeMillis), attribution)
            gateway.start()
        }
        private fun serve(socket: Socket) {
            socket.soTimeout = 10_000
            while (true) {
                val head = readHeader(socket.inputStream)
                val path = head.lineSequence().first().split(' ')[1]
                val length = Regex("(?im)^Content-Length: *([0-9]+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                val payload = socket.inputStream.readNBytes(length)
                if (head.contains("Upgrade: websocket", true)) {
                    val key = Regex("(?im)^Sec-WebSocket-Key: *([^\\r\\n]+)").find(head)!!.groupValues[1].trim()
                    val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
                    val graphql = head.contains("graphql-transport-ws")
                    val protocolHeader = if (graphql) "Sec-WebSocket-Protocol: graphql-transport-ws\r\n" else ""
                    socket.outputStream.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n${protocolHeader}\r\n".toByteArray()); socket.outputStream.flush()
                    while (true) {
                        val opcode = socket.inputStream.read(); if (opcode < 0) return
                        val size = socket.inputStream.read(); check(size in 128..253)
                        val mask = socket.inputStream.readNBytes(4)
                        val bytes = socket.inputStream.readNBytes(size and 127)
                        for (index in bytes.indices) bytes[index] = (bytes[index].toInt() xor mask[index % 4].toInt()).toByte()
                        val reply = when {
                            graphql && bytes.decodeToString().contains("connection_init") -> """{"type":"connection_ack"}""".toByteArray()
                            graphql && bytes.decodeToString().contains("ping") -> """{"type":"pong"}""".toByteArray()
                            else -> bytes
                        }
                        socket.outputStream.write(byteArrayOf(opcode.toByte(), reply.size.toByte()) + reply); socket.outputStream.flush()
                    }
                }
                val body = when {
                    path == "/hold/sse" -> "data: x\n\ndata: y\n\n".toByteArray()
                    path.startsWith("/hold/") -> "xyz".toByteArray()
                    else -> ("payload:$path:" + payload.decodeToString()).toByteArray()
                }
                val contentType = if (path == "/hold/sse") "text/event-stream" else "application/json"
                socket.outputStream.write("HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray())
                if (path.startsWith("/hold/")) {
                    val prefixSize = if (path == "/hold/sse") "data: x\n\n".length else 1
                    socket.outputStream.write(body, 0, prefixSize); socket.outputStream.flush()
                    releases.computeIfAbsent(path) { CompletableFuture() }.get(5, TimeUnit.SECONDS)
                    socket.outputStream.write(body, prefixSize, body.size - prefixSize)
                } else socket.outputStream.write(body)
                socket.outputStream.flush()
            }
        }
        fun connect(id: String, secure: Boolean = false, port: Int = if (secure) secureOrigin.localPort else origin.localPort): Socket {
            val carrier = contexts.second.socketFactory.createSocket("127.0.0.1", gateway.boundPort!!) as SSLSocket
            carrier.soTimeout = 5_000
            if (!secure) {
                routes[carrier] = HttpRoute(id, port, false)
                return carrier
            }
            val address = "127.0.0.1:$port"
            carrier.outputStream.write("CONNECT $address HTTP/1.1\r\nHost: $address\r\nProxy-Authorization: Bearer $id:${credentials.getValue(id)}\r\n\r\n".toByteArray()); carrier.outputStream.flush()
            check(readHeader(carrier.inputStream).startsWith("HTTP/1.1 200"))
            return (contexts.second.socketFactory.createSocket(carrier, "127.0.0.1", port, true) as SSLSocket).apply {
                soTimeout = 5_000; routes[this] = HttpRoute(id, port, true)
            }
        }
        fun send(socket: Socket, path: String, body: String = "") {
            val route = routes.getValue(socket)
            val target = if (route.secure) path else "http://127.0.0.1:${route.port}$path"
            val auth = if (route.secure) "" else "Proxy-Authorization: Bearer ${route.id}:${credentials.getValue(route.id)}\r\n"
            socket.outputStream.write("${if (body.isEmpty()) "GET" else "POST"} $target HTTP/1.1\r\nHost: 127.0.0.1:${route.port}\r\n${auth}Content-Length: ${body.toByteArray().size}\r\n\r\n$body".toByteArray()); socket.outputStream.flush()
        }
        fun http(id: String, path: String, secure: Boolean = false, body: String = "") {
            connect(id, secure).use { socket ->
                send(socket, path, body)
                val head = readHeader(socket.inputStream); check(head.startsWith("HTTP/1.1 200")) { head }
                val length = Regex("(?im)^Content-Length: *([0-9]+)").find(head)!!.groupValues[1].toInt()
                assertEquals("payload:$path:$body", socket.inputStream.readNBytes(length).decodeToString())
            }
        }
        suspend fun websocket(id: String, protocol: String?): Socket {
            val previousCount = reader.query(TrafficPageQuery(limit = 50, source = source(id))).totalCount
            val socket = connect(id)
            socket.outputStream.write(("GET http://127.0.0.1:${origin.localPort}/ws HTTP/1.1\r\nHost: 127.0.0.1:${origin.localPort}\r\nProxy-Authorization: Bearer $id:${credentials.getValue(id)}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                (protocol?.let { "Sec-WebSocket-Protocol: $it\r\n" } ?: "") + "\r\n").toByteArray()); socket.outputStream.flush()
            assertTrue(readHeader(socket.inputStream).startsWith("HTTP/1.1 101"))
            awaitCount(id, previousCount + 1)
            return socket
        }
        suspend fun awaitCount(id: String, expected: Long) = withTimeout(5_000) {
            while (reader.query(TrafficPageQuery(limit = 50, source = source(id))).totalCount != expected) delay(10)
        }
        suspend fun awaitPath(id: String, path: String): List<TrafficPageItem> = withTimeout(5_000) {
            var rows: List<TrafficPageItem>
            do { rows = reader.query(TrafficPageQuery(limit = 50, source = source(id))).items.filter { it.exchange.request.head.target.toString().contains(path) }; if (rows.isEmpty()) delay(10) } while (rows.isEmpty())
            rows
        }
        suspend fun awaitMessages(id: String, expected: Long) = withTimeout(5_000) {
            while (database.canonicalCaptureDao().countDuplexMessages(id) < expected) delay(10)
        }
        fun h2(id: String): Channel {
            val ready = CompletableFuture<Channel>()
            val port = (h2Origin.localAddress() as InetSocketAddress).port
            val ssl = SslContextBuilder.forClient().trustManager(ca.certificate).build()
            val channel = Bootstrap().group(h2Group).channel(NioSocketChannel::class.java).handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    channel.pipeline().addLast("tls", ssl.newHandler(channel.alloc(), "127.0.0.1", gateway.boundPort!!))
                    channel.pipeline().addLast("codec", HttpClientCodec()); channel.pipeline().addLast("aggregate", HttpObjectAggregator(16_384))
                    channel.pipeline().addLast(object : SimpleChannelInboundHandler<FullHttpResponse>() {
                        override fun channelRead0(context: ChannelHandlerContext, response: FullHttpResponse) {
                            check(response.status().code() == 200)
                            context.pipeline().remove("aggregate"); context.pipeline().remove("codec")
                            context.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), Http2MultiplexHandler(object : ChannelInboundHandlerAdapter() {
                                override fun channelRead(context: ChannelHandlerContext, message: Any) { ReferenceCountUtil.release(message) }
                            }))
                            context.pipeline().remove(this); ready.complete(context.channel())
                        }
                        override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) { ready.completeExceptionally(cause); context.close() }
                    })
                }
            }).connect("127.0.0.1", gateway.boundPort!!).syncUninterruptibly().channel()
            h2Connections += channel
            val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.CONNECT, "127.0.0.1:$port")
            request.headers().set(HttpHeaderNames.HOST, "127.0.0.1:$port").set("Proxy-Authorization", "Bearer $id:${credentials.getValue(id)}")
            channel.writeAndFlush(request).syncUninterruptibly()
            return ready.get(5, TimeUnit.SECONDS)
        }
        fun grpc(parent: Channel, name: String, payload: ByteArray): ByteArray {
            val reply = CompletableFuture<ByteArray>(); val bytes = ByteArrayOutputStream()
            val channel = Http2StreamChannelBootstrap(parent).handler(object : SimpleChannelInboundHandler<Http2StreamFrame>() {
                override fun channelRead0(context: ChannelHandlerContext, frame: Http2StreamFrame) {
                    if (frame is Http2DataFrame) { val chunk = ByteArray(frame.content().readableBytes()); frame.content().readBytes(chunk); bytes.write(chunk) }
                    if (frame is Http2HeadersFrame && frame.isEndStream) { assertEquals("0", frame.headers().get("grpc-status").toString()); reply.complete(bytes.toByteArray()) }
                }
                override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) { reply.completeExceptionally(cause); context.close() }
            }).open().syncUninterruptibly().getNow()
            val port = (h2Origin.localAddress() as InetSocketAddress).port
            channel.write(DefaultHttp2HeadersFrame(DefaultHttp2Headers().method("POST").scheme("http").authority("127.0.0.1:$port")
                .path("/fixture.Service/$name").set("content-type", "application/grpc").set("te", "trailers"), false))
            channel.writeAndFlush(DefaultHttp2DataFrame(Unpooled.wrappedBuffer(payload), true)).syncUninterruptibly()
            return reply.get(5, TimeUnit.SECONDS)
        }
        override fun close() {
            releases.values.forEach { it.complete(Unit) }
            h2Connections.forEach { it.close().syncUninterruptibly() }
            gateway.close(); runBlocking { runtime.stop(ProxyStopReason.APPLICATION_SHUTDOWN) }; runtime.close()
            origin.close(); secureOrigin.close(); h2Origin.close().syncUninterruptibly()
            h2Group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly()
            workers.shutdownNow(); workers.awaitTermination(2, TimeUnit.SECONDS)
            runBlocking { scope.coroutineContext.job.cancelAndJoin() }
            database.close(); directory.deleteRecursively()
        }
    }
    private companion object {
        val owner = PolicyWorkspace.Device(RegisteredDeviceId("owner"))
        val sibling = PolicyWorkspace.Device(RegisteredDeviceId("sibling"))
        fun source(id: String) = TrafficSource.Device(RegisteredDeviceId(id))
        fun readHeader(input: InputStream): String {
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) { val value = input.read(); check(value >= 0) { "Peer closed before headers" }; head.append(value.toChar()); check(head.length < 65_536) }
            return head.toString()
        }
        fun echo(socket: Socket, message: String) {
            val bytes = message.toByteArray(); check(bytes.size < 126)
            socket.outputStream.write(byteArrayOf(0x81.toByte(), (0x80 or bytes.size).toByte(), 0, 0, 0, 0) + bytes); socket.outputStream.flush()
            val expected = when {
                message.contains("connection_init") -> """{"type":"connection_ack"}""".toByteArray()
                message.contains("ping") -> """{"type":"pong"}""".toByteArray()
                else -> bytes
            }
            assertEquals(0x81, socket.inputStream.read()); assertEquals(expected.size, socket.inputStream.read())
            assertContentEquals(expected, socket.inputStream.readNBytes(expected.size))
        }
        fun contexts(ca: CertificateAuthority): Pair<SSLContext, SSLContext> {
            val leaf = LeafCertificateGenerator.generate("127.0.0.1", ca)
            val keys = KeyStore.getInstance("PKCS12").apply { load(null); setKeyEntry("fixture", leaf.keyPair.private, charArrayOf(), arrayOf(leaf.certificate, ca.certificate)) }
            val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, charArrayOf()) }
            val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("fixture", ca.certificate) }
            val trusted = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
            return SSLContext.getInstance("TLS").apply { init(manager.keyManagers, null, null) } to SSLContext.getInstance("TLS").apply { init(null, trusted.trustManagers, null) }
        }
    }
}
