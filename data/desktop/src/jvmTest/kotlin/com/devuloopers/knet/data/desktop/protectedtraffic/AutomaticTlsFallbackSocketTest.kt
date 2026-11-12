package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureSessionFactory
import com.devuloopers.knet.data.desktop.certificate.DesktopServerTlsContextProvider
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.engine.certificate.*
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.engine.simulator.*
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.model.*
import com.sun.net.httpserver.*
import java.net.*
import java.nio.file.Files
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

/** Real client rejection/reconnects retain the origin certificate, opaque Room evidence and actual shaping. */
class AutomaticTlsFallbackSocketTest {
    @Test fun `tls 12 rejection automatically tunnels reconnect while manual inspection remains authoritative`() = exercise("TLSv1.2")
    @Test fun `tls 13 rejection automatically tunnels reconnect while manual inspection remains authoritative`() = exercise("TLSv1.3")
    @Test fun `automatic tunnel preserves mutual TLS client authentication`() = exercise("TLSv1.3", mutual = true)

    @Test fun `TLS 12 origin resumption offer can relearn after a temporary decision is cleared`() = exercise("TLSv1.2", relearn = true)
    @Test fun `TLS 13 origin resumption offer can relearn after a temporary decision is cleared`() = exercise("TLSv1.3", relearn = true)

    private fun exercise(protocol: String, mutual: Boolean = false, relearn: Boolean = false) = runBlocking {
        val root = Files.createTempDirectory("knet-auto-tls-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodies = FileBodyStore(root.resolve("bodies"))
        val capture = CanonicalCaptureSessionFactory(database, bodies, bodies).openStreamingProxy(8080)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val fallback = InMemoryAutomaticTlsFallback(scope)
        fallback.activateSession(capture.sessionId)
        val repository = RoomProtectedTrafficRepository(database.protectedTrafficDao(), scope)
        repository.configuration.first { it.configurationReady }
        val originAuthority = CertificateAuthority.generate(commonName = "Automatic fallback origin")
        val leaf = CertificateCache().get("localhost", originAuthority)
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("origin", leaf.keyPair.private, "test".toCharArray(), arrayOf(leaf.certificate, originAuthority.certificate))
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test".toCharArray()) }
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setCertificateEntry("origin", originAuthority.certificate) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
        val serverContext = SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, trust.trustManagers, null) }
        val requests = AtomicInteger()
        val body = ("origin-application-response:" + "x".repeat(12_500)).encodeToByteArray()
        val origin = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            httpsConfigurator = object : HttpsConfigurator(serverContext) {
                override fun configure(parameters: HttpsParameters) {
                    parameters.setSSLParameters(serverContext.defaultSSLParameters.apply { needClientAuth = mutual })
                }
            }
            createContext("/products") { exchange ->
                requests.incrementAndGet()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val inspectionAuthority = CertificateAuthority.generate(commonName = "Automatic fallback inspection")
        val policy = ProtectedTrafficTlsInterceptionPolicy({ repository.configuration.value }, automaticFallback = fallback,
            inspectionAuthorityIdentity = { "fixture-authority".takeIf { runCatching { inspectionAuthority.certificate.checkValidity() }.isSuccess } })
        val conditions = NetworkConditionEngine(configuration = {
            NetworkConditionConfiguration(enabled = true, globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id)
        })
        val proxy = KNetProxyServer(port = ServerSocket(0).use { it.localPort }, captureSink = capture,
            serverTlsContextProvider = DesktopServerTlsContextProvider(inspectionAuthority, CertificateCache()),
            tlsInterceptionPolicy = policy, pipelineInitializers = listOf { pipeline -> pipeline.addLast("conditions", NetworkConditionChannelHandler(conditions)) })
        val clientContext = SSLContext.getInstance("TLS").apply { init(if (mutual) keyManagers.keyManagers else null, trust.trustManagers, null) }
        fun client(): SSLSocket {
            val transport = Socket().apply { connect(proxy.boundAddress()); soTimeout = 10_000 }
            transport.outputStream.apply {
                write("CONNECT 127.0.0.1:${origin.address.port} HTTP/1.1\r\nHost: localhost\r\n\r\n".encodeToByteArray()); flush()
            }
            val header = StringBuilder()
            while (!header.endsWith("\r\n\r\n")) {
                val value = transport.inputStream.read(); check(value >= 0); header.append(value.toChar()); check(header.length < 8192)
            }
            check(header.startsWith("HTTP/1.1 200"))
            return (clientContext.socketFactory.createSocket(transport, "localhost", origin.address.port, true) as SSLSocket).apply {
                enabledProtocols = arrayOf(protocol); soTimeout = 10_000
                sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS"; serverNames = listOf(SNIHostName("localhost")) }
            }
        }
        suspend fun await(predicate: suspend () -> Boolean) = withTimeout(10_000) { while (!predicate()) delay(10) }
        proxy.start()
        try {
            client().use { assertFails { it.startHandshake() } }
            await { fallback.state.value.keys.size == 1 }
            assertEquals(0, requests.get(), "A rejected TLS handshake must not replay an HTTP operation")
            assertTrue(repository.configuration.value.rules.isEmpty(), "Temporary fallback must not create a permanent rule")
            val started = System.nanoTime()
            client().use { socket ->
                socket.startHandshake()
                assertContentEquals(leaf.certificate.encoded, socket.session.peerCertificates.first().encoded)
                socket.outputStream.apply { write("GET /products HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".encodeToByteArray()); flush() }
                assertTrue(socket.inputStream.readBytes().decodeToString().contains(body.decodeToString()))
            }
            assertTrue((System.nanoTime() - started) / 1_000_000 >= 750, "Automatic tunnelling must retain actual 100 kbps shaping")
            assertEquals(1, requests.get())
            capture.flush()
            await { database.canonicalCaptureDao().getOpaqueFlows(capture.sessionId.value, 20).any { it.protectionOrigin == "AUTOMATIC" && it.state != "ACTIVE" } }
            val flows = database.canonicalCaptureDao().getOpaqueFlows(capture.sessionId.value, 20)
            val rejected = flows.single { it.policyAction == "INSPECT" }
            assertEquals("CERTIFICATE_REJECTED", rejected.inspectionFailureReason)
            assertEquals("GLOBAL", rejected.protectionOrigin)
            val tunnel = flows.single { it.protectionOrigin == "AUTOMATIC" }
            assertEquals("TUNNEL", tunnel.policyAction)
            assertEquals("CERTIFICATE_REJECTED", tunnel.inspectionFailureReason)
            assertTrue(tunnel.uploadedBytes > 0 && tunnel.downloadedBytes > 0)
            assertNull(tunnel.policyRuleId)
            if (relearn) {
                // The same client SSLContext now holds an origin session/ticket. KNet cannot resume it and must
                // independently prove its replacement certificate flight before learning another rejection.
                fallback.clear()
                client().use { assertFails { it.startHandshake() } }
                await { fallback.state.value.keys.size == 1 }
                client().use { socket ->
                    socket.startHandshake()
                    assertContentEquals(leaf.certificate.encoded, socket.session.peerCertificates.first().encoded)
                    socket.outputStream.apply { write("GET /products HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".encodeToByteArray()); flush() }
                    assertTrue(socket.inputStream.readBytes().decodeToString().contains(body.decodeToString()))
                }
                assertEquals(2, requests.get())
            }
            val completedOperations = requests.get()
            val rule = ProtectedTrafficRule(ProtectedTrafficRuleId("explicit-inspect"), action = ProtectedTrafficAction.INSPECT,
                destination = ProtectedDestinationSelector.parse("localhost"), port = origin.address.port, transport = ProtectedTransportProtocol.TCP)
            repository.upsertRule(rule)
            repository.configuration.first { rule in it.rules }
            repeat(2) { client().use { assertFails { it.startHandshake() } } }
            assertTrue(fallback.state.value.keys.isEmpty(), "An explicit Inspect choice must not be overridden after another rejection")
            assertEquals(completedOperations, requests.get())
            fallback.activateSession(null)
            assertTrue(fallback.state.value.keys.isEmpty())
        } finally {
            proxy.stop(); origin.stop(0); fallback.activateSession(null)
            scope.coroutineContext.job.cancelAndJoin(); capture.close(); database.close(); root.deleteRecursively()
        }
    }
}
