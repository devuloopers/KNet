package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.data.desktop.protectedtraffic.ProtectedTrafficTlsInterceptionPolicy
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.engine.proxy.tls.*
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.capture.entity.OpaqueFlowEntity
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.KeyStore
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.*

/** Real encrypted HTTP keep-alive distinguishes finished requests from the still-open transport connection. */
class OpaqueKeepAliveCaptureTest {
    @Test fun `tls 12 responses reuse a quiet tunnel beyond the HTTP inactivity deadline`() = exercise("TLSv1.2")
    @Test fun `tls 13 SNI scoped tunnel survives quiet time beyond the HTTP deadline`() = exercise("TLSv1.3", sniScoped = true)
    @Test fun `explicit tunnel inactivity retains its cause after a completed operation`() = exercise("TLSv1.3", rawIdleTimeoutMillis = 1500)

    private fun exercise(protocol: String, sniScoped: Boolean = false, rawIdleTimeoutMillis: Long = 0) = runBlocking {
        val root = Files.createTempDirectory("knet-opaque-keepalive-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodies = FileBodyStore(root.resolve("bodies"))
        val capture = CanonicalCaptureSessionFactory(database, bodies, bodies).openStreamingProxy(8080)
        val authority = CertificateAuthority.generate(commonName = "Keep-alive origin")
        val leaf = CertificateCache().get("localhost", authority)
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("origin", leaf.keyPair.private, "test".toCharArray(), arrayOf(leaf.certificate, authority.certificate))
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, "test".toCharArray())
        }
        val serverContext = SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, null, null) }
        val requests = AtomicInteger()
        val origin = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            httpsConfigurator = HttpsConfigurator(serverContext)
            createContext("/products") { exchange ->
                val body = "products-${requests.incrementAndGet()}".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val policy: TlsInterceptionPolicy = if (sniScoped) ProtectedTrafficTlsInterceptionPolicy({
            ProtectedTrafficConfiguration(rules = listOf(ProtectedTrafficRule(ProtectedTrafficRuleId("fixture-manual"),
                action = ProtectedTrafficAction.TUNNEL, destination = ProtectedDestinationSelector.parse("localhost"),
                port = origin.address.port, transport = ProtectedTransportProtocol.TCP)))
        }) else TlsInterceptionPolicy { TlsInterceptionDecision(TlsInterceptionMode.TUNNEL) }
        val proxy = KNetProxyServer(port = ServerSocket(0).use { it.localPort }, captureSink = capture,
            runtimePolicy = KNetProxyRuntimePolicy(readIdleTimeoutMillis = 1000, rawTunnelIdleTimeoutMillis = rawIdleTimeoutMillis),
            serverTlsContextProvider = ServerTlsContextProvider { _, _ ->
                CompletableFuture.failedFuture(AssertionError("A tunnel must preserve the origin TLS certificate"))
            }, tlsInterceptionPolicy = policy)
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null); setCertificateEntry("origin", authority.certificate)
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
        val clientContext = SSLContext.getInstance("TLS").apply { init(null, trustManagers.trustManagers, null) }
        suspend fun awaitFlow(predicate: (OpaqueFlowEntity) -> Boolean): OpaqueFlowEntity = withTimeout(10_000) {
            database.canonicalCaptureDao().observeOpaqueFlowChangeScalar(capture.sessionId.value)
                .map { database.canonicalCaptureDao().getOpaqueFlows(capture.sessionId.value, 10).singleOrNull() }
                .filterNotNull().first(predicate)
        }
        proxy.start()
        try {
            val transport = Socket().apply { connect(proxy.boundAddress()); soTimeout = 10_000 }
            transport.outputStream.apply {
                write("CONNECT 127.0.0.1:${origin.address.port} HTTP/1.1\r\nHost: localhost\r\n\r\n".encodeToByteArray()); flush()
            }
            assertTrue(readHeaders(transport.inputStream).startsWith("HTTP/1.1 200"))
            val socket = (clientContext.socketFactory.createSocket(transport, "localhost", origin.address.port, true) as SSLSocket).apply {
                soTimeout = 10_000; enabledProtocols = arrayOf(protocol)
                sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS"; serverNames = listOf(SNIHostName("localhost")) }
            }
            socket.use {
                socket.startHandshake()
                assertContentEquals(leaf.certificate.encoded, socket.session.peerCertificates.first().encoded)
                fun request(): String {
                    socket.outputStream.apply {
                        write("GET /products HTTP/1.1\r\nHost: localhost\r\nConnection: keep-alive\r\n\r\n".encodeToByteArray()); flush()
                    }
                    val headers = readHeaders(socket.inputStream)
                    assertTrue(headers.startsWith("HTTP/1.1 200"))
                    val length = headers.lineSequence().first { it.startsWith("Content-length:", true) }.substringAfter(':').trim().toInt()
                    return socket.inputStream.readNBytes(length).decodeToString()
                }
                assertEquals("products-1", request())
                // Do not flush capture: the periodic sampler must publish while the same TLS socket remains open.
                val first = awaitFlow { it.uploadedBytes > 0 && it.downloadedBytes > 0 }
                assertEquals("ACTIVE", first.state); assertNull(first.completedAtEpochMillis)
                if (rawIdleTimeoutMillis > 0) {
                    val timedOut = awaitFlow { it.state != "ACTIVE" }
                    assertEquals("FAILED", timedOut.state)
                    assertEquals(TrafficTerminationReason.Transport.READ_TIMED_OUT.code.value, timedOut.terminalErrorCode)
                    assertEquals("KNET", timedOut.closureInitiator)
                    assertEquals("INACTIVITY_TIMEOUT", timedOut.closureReason)
                    assertNotNull(timedOut.closureObservedAtEpochMillis)
                    assertEquals(1, requests.get())
                } else {
                    // Cross the former HTTP/setup cutoff without network traffic, then reuse the same TLS socket.
                    delay(2200)
                    assertEquals("ACTIVE", database.canonicalCaptureDao().getOpaqueFlow(first.id)!!.state)
                    assertEquals("products-2", request())
                    val second = awaitFlow { it.uploadedBytes > first.uploadedBytes && it.downloadedBytes > first.downloadedBytes }
                    assertEquals(first.id, second.id); assertEquals("ACTIVE", second.state)
                    assertNull(second.completedAtEpochMillis); assertNull(second.terminalErrorCode)
                    assertEquals(2, requests.get())
                }
            }
            val ended = awaitFlow { it.state != "ACTIVE" }
            assertEquals(if (rawIdleTimeoutMillis > 0) "FAILED" else "COMPLETED", ended.state)
            assertNotNull(ended.completedAtEpochMillis)
            assertTrue(ended.uploadedBytes > 0 && ended.downloadedBytes > 0)
        } finally { proxy.stop(); origin.stop(0); capture.close(); database.close(); root.deleteRecursively() }
    }

    private fun readHeaders(input: InputStream): String {
        val result = StringBuilder()
        while (!result.endsWith("\r\n\r\n")) {
            val value = input.read()
            check(value >= 0) { "Connection ended before the response headers" }
            result.append(value.toChar())
            check(result.length <= 8192)
        }
        return result.toString()
    }
}
