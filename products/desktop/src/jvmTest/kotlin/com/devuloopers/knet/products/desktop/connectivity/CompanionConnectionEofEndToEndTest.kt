package com.devuloopers.knet.products.desktop.connectivity

import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.connectivity.desktop.gateway.AuthenticatedProxyGateway
import com.devuloopers.knet.connectivity.desktop.gateway.IngressAttributionRegistry
import com.devuloopers.knet.connectivity.desktop.pairing.JvmPairingCrypto
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.certificate.LeafCertificateGenerator
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.capture.*
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import com.devuloopers.knet.identity.*
import com.devuloopers.knet.pairing.*
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.*
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.ChannelInputShutdownEvent
import io.netty.handler.ssl.SslCloseCompletionEvent
import io.netty.handler.ssl.SslContextBuilder
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

/** Real TLS carrier and nested HTTPS EOFs must drain responses and release a constrained admission budget. */
class CompanionConnectionEofEndToEndTest {
    @Test fun `idle HTTP keepalive closes after the peer leaves without waiting for read timeout`() = idleClose(tls = false)
    @Test fun `idle inspected HTTPS closes on TLS close notification without waiting for TCP timeout`() = idleClose(tls = true)
    @Test fun `HTTP half close retains a delayed complete response`() = delayedResponse(tls = false)
    @Test fun `HTTPS half close retains a delayed complete response`() = delayedResponse(tls = true)

    @Test fun `repeated authenticated keepalive lifetimes release a four connection admission budget`() {
        Fixture(tls = false).use { fixture ->
            repeat(12) { batch ->
                val clients = (1..4).map { fixture.request() }
                clients.forEach { client -> assertEquals("done", readResponse(client.inputStream)) }
                clients.forEach(Socket::close)
                fixture.awaitClosed((batch + 1) * 4)
            }
            assertEquals(48, fixture.capture.completed.get())
            assertEquals(48, fixture.capture.closed.get())
        }
    }

    @Test fun `WebSocket close through the authenticated carrier completes without waiting for timeout`() = upgradedClose(tls = false)
    @Test fun `secure WebSocket close drains nested TLS and the authenticated carrier`() = upgradedClose(tls = true)

    private fun upgradedClose(tls: Boolean) {
        Fixture(tls, upgraded = true).use { fixture ->
            fixture.request().use { client ->
                assertTrue(readHeader(client.inputStream).startsWith("HTTP/1.1 101"))
                // Client close frames are masked. The independent peer returns the same code unmasked.
                client.outputStream.write(byteArrayOf(0x88.toByte(), 0x82.toByte(), 1, 2, 3, 4, 2, 0xea.toByte()))
                client.outputStream.flush()
                assertContentEquals(byteArrayOf(0x88.toByte(), 2, 3, 0xe8.toByte()), client.inputStream.readNBytes(4))
                client.shutdownOutput()
                assertEquals(-1, client.inputStream.read())
            }
            fixture.awaitClosed(1)
            assertEquals(1, fixture.capture.completed.get())
        }
    }

    private fun idleClose(tls: Boolean) {
        Fixture(tls).use { fixture ->
            fixture.request().use { client -> assertEquals("done", readResponse(client.inputStream)) }
            fixture.awaitClosed(1)
            assertEquals(1, fixture.capture.completed.get())
        }
    }

    private fun delayedResponse(tls: Boolean) {
        Fixture(tls, delayed = true).use { fixture ->
            fixture.request().use { client ->
                assertTrue(fixture.requestReceived.await(5, TimeUnit.SECONDS))
                client.shutdownOutput()
                fixture.respond.countDown()
                assertEquals("done", readResponse(client.inputStream))
                assertEquals(-1, client.inputStream.read())
            }
            fixture.awaitClosed(1)
            assertEquals(1, fixture.capture.completed.get())
        }
    }

    private class Fixture(private val tls: Boolean, private val delayed: Boolean = false, private val upgraded: Boolean = false) : AutoCloseable {
        val capture = Capture()
        val requestReceived = CountDownLatch(1)
        val inputEnded = CountDownLatch(1)
        val respond = CountDownLatch(if (delayed) 1 else 0)
        private val authority = CertificateAuthority.generate(commonName = "Connection EOF fixture CA")
        private val contexts = contexts(authority)
        private val cache = CertificateCache()
        private val crypto = JvmPairingCrypto()
        private val credential = crypto.randomToken(48)
        private val attributions = IngressAttributionRegistry()
        private val origin = (if (tls) contexts.server.serverSocketFactory.createServerSocket(0) else ServerSocket(0))
        private val workers = Executors.newCachedThreadPool()
        private val originTask = workers.submit {
            while (!origin.isClosed) {
                val socket = try { origin.accept() } catch (_: java.net.SocketException) { break }
                workers.submit {
                    socket.use {
                        val requestHead = readHeader(it.inputStream)
                        requestReceived.countDown()
                        check(respond.await(5, TimeUnit.SECONDS))
                        if (upgraded) {
                            check(requestHead.contains("Upgrade: websocket", ignoreCase = true))
                            it.outputStream.write(("HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n" +
                                "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n\r\n").toByteArray())
                            it.outputStream.flush()
                            check(it.inputStream.readNBytes(8).contentEquals(byteArrayOf(0x88.toByte(), 0x82.toByte(), 1, 2, 3, 4, 2, 0xea.toByte())))
                            it.outputStream.write(byteArrayOf(0x88.toByte(), 2, 3, 0xe8.toByte()))
                        } else {
                            it.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone".toByteArray())
                        }
                        it.outputStream.flush()
                    }
                }
            }
        }
        private val proxy = KNetProxyServer(
            port = ServerSocket(0).use { it.localPort },
            serverTlsContextProvider = ServerTlsContextProvider { host, executor ->
                cache.getAsync(host, authority, executor).thenApply { leaf ->
                    SslContextBuilder.forServer(leaf.keyPair.private, leaf.certificate).build()
                }
            },
            verifyUpstreamTls = false, // Only this isolated fixture's self-signed HTTPS origin.
            runtimePolicy = KNetProxyRuntimePolicy(maximumDownstreamConnections = 4, maximumConnectionsPerClient = 4,
                readIdleTimeoutMillis = 30_000, gracefulShutdownTimeoutMillis = 100),
            captureSink = capture,
            ingressAttribution = attributions,
            pipelineInitializers = listOf({ pipeline -> pipeline.addLast("observe-eof", object : ChannelInboundHandlerAdapter() {
                override fun userEventTriggered(context: ChannelHandlerContext, event: Any) {
                    if (event === ChannelInputShutdownEvent.INSTANCE || event is SslCloseCompletionEvent && event.isSuccess) inputEnded.countDown()
                    context.fireUserEventTriggered(event)
                }
            }) }),
        ).apply { start() }
        private val gateway = AuthenticatedProxyGateway("127.0.0.1", 0, contexts.server.serverSocketFactory,
            { proxy.boundAddress() }, PairingCoordinator(DeviceStore(crypto.digest(credential)), crypto, System::currentTimeMillis),
            attributions).apply { start() }

        private fun upgradeHeaders() = if (upgraded) "Connection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" else ""

        fun request(): Socket {
            val carrier = contexts.client.socketFactory.createSocket("127.0.0.1", gateway.boundPort!!) as SSLSocket
            carrier.soTimeout = 5_000
            val address = "127.0.0.1:${origin.localPort}"
            if (tls) {
                carrier.outputStream.write(("CONNECT $address HTTP/1.1\r\nHost: $address\r\n" + authorization() + "\r\n").toByteArray())
                carrier.outputStream.flush()
                check(readHeader(carrier.inputStream).startsWith("HTTP/1.1 200"))
                val client = contexts.client.socketFactory.createSocket(carrier, "127.0.0.1", origin.localPort, true) as SSLSocket
                client.soTimeout = 5_000
                client.startHandshake()
                client.outputStream.write(("GET /keepalive HTTP/1.1\r\nHost: $address\r\n" + upgradeHeaders() + "\r\n").toByteArray())
                client.outputStream.flush()
                return client
            }
            carrier.outputStream.write(("GET http://$address/keepalive HTTP/1.1\r\nHost: $address\r\n" + authorization() + upgradeHeaders() + "\r\n").toByteArray())
            carrier.outputStream.flush()
            return carrier
        }
        private fun authorization() = "Proxy-Authorization: Bearer lifecycle-device:$credential\r\n"
        fun awaitClosed(expected: Int) {
            repeat(100) { if (capture.closed.get() == expected) return; Thread.sleep(20) }
            assertEquals(expected, capture.closed.get(), "A 30-second idle deadline must not own peer-closed connections")
        }
        override fun close() {
            respond.countDown(); gateway.close(); proxy.stop(); origin.close()
            workers.shutdownNow(); workers.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private class Capture : ProxyCaptureSink {
        val closed = AtomicInteger()
        val completed = AtomicInteger()
        override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture = object : ProxyConnectionCapture {
            override fun close(reason: TrafficTerminationReason?) { closed.incrementAndGet() }
            override fun startExchange(exchangeId: ExchangeId, request: RequestHead, occurredAtEpochMillis: Long, origin: TrafficOrigin, streamId: StreamId?): ProxyExchangeCapture =
                object : ProxyExchangeCapture {
                    override val exchangeId = exchangeId
                    private val terminal = java.util.concurrent.atomic.AtomicBoolean()
                    override fun tryReserveBody(direction: TrafficDirection, contentEncoding: ContentEncoding?, requestedBytes: Int): ProxyBodyReservation? = null
                    override fun completeBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long) = Unit
                    override fun cancelBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long, reason: TrafficTerminationReason) = Unit
                    override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit
                    override fun terminate(outcome: ExchangeTerminalOutcome, timings: ExchangeTimings, occurredAtEpochMillis: Long) {
                        if (terminal.compareAndSet(false, true) && outcome == ExchangeTerminalOutcome.Completed) completed.incrementAndGet()
                    }
                }
        }
    }

    private class DeviceStore(digest: String) : TrustedDeviceStore {
        private val device = TrustedDevice(RegisteredDevice(RegisteredDeviceId("lifecycle-device"), "Lifecycle fixture", DeviceRegistrationKind.PAIRED_COMPANION, 1, 1),
            "test-key", digest, setOf(DeviceScope.PROXY_STREAM), 1, Long.MAX_VALUE)
        override suspend fun getDevice(id: RegisteredDeviceId): TrustedDevice? = device.takeIf { it.id == id }
        override fun observeDevices(): Flow<List<TrustedDevice>> = MutableStateFlow(listOf(device))
        override suspend fun putDevice(device: TrustedDevice) = error("Unused")
        override suspend fun putInvitation(invitation: PendingPairingInvitation) = error("Unused")
        override suspend fun claimInvitation(id: PairingInvitationId, secretDigest: String, nowEpochMillis: Long): PendingPairingInvitation? = null
        override suspend fun rotateCredential(id: RegisteredDeviceId, expectedCredentialDigest: String, newCredentialDigest: String, credentialExpiresAtEpochMillis: Long): Boolean = false
        override suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean = false
    }

    private data class Contexts(val server: SSLContext, val client: SSLContext)
    private companion object {
        fun contexts(authority: CertificateAuthority): Contexts {
            val leaf = LeafCertificateGenerator.generate("127.0.0.1", authority)
            val keys = KeyStore.getInstance("PKCS12").apply { load(null); setKeyEntry("server", leaf.keyPair.private, charArrayOf(), arrayOf(leaf.certificate, authority.certificate)) }
            val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, charArrayOf()) }
            val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("fixture", authority.certificate) }
            val trusted = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
            return Contexts(SSLContext.getInstance("TLS").apply { init(manager.keyManagers, null, null) }, SSLContext.getInstance("TLS").apply { init(null, trusted.trustManagers, null) })
        }
        fun readHeader(input: InputStream): String {
            val text = StringBuilder()
            while (!text.endsWith("\r\n\r\n")) { val next = input.read(); check(next >= 0); text.append(next.toChar()); check(text.length <= 64 * 1024) }
            return text.toString()
        }
        fun readResponse(input: InputStream): String {
            val header = readHeader(input); check(header.startsWith("HTTP/1.1 200"))
            val length = Regex("(?im)^Content-Length: *([0-9]+)").find(header)!!.groupValues[1].toInt()
            return input.readNBytes(length).decodeToString()
        }
    }
}
