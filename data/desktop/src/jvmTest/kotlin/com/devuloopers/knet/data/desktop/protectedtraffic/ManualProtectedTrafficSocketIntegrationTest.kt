package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.data.desktop.certificate.DesktopServerTlsContextProvider
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.tls.*
import com.devuloopers.knet.engine.proxy.capture.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import com.devuloopers.knet.traffic.model.InspectionFailureReason
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Repeated rejected TLS handshakes stay Inspect until a manual rule enables an independent client reconnect. */
class ManualProtectedTrafficSocketIntegrationTest {
    @Test fun `tls 13 requires a saved rule then preserves origin TLS and 100 kbps`() = exercise("TLSv1.3")
    @Test fun `tls 12 requires a saved rule then preserves origin TLS and 100 kbps`() = exercise("TLSv1.2")
    @Test fun `mutual tls reconnect preserves client authentication and 100 kbps`() = exercise("TLSv1.3", mutual = true)

    private fun exercise(protocol: String, mutual: Boolean = false) = runTest {
        val originAuthority = CertificateAuthority.generate(commonName = "Manual rule origin")
        val leaf = CertificateCache().get("localhost", originAuthority)
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("origin", leaf.keyPair.private, "test".toCharArray(), arrayOf(leaf.certificate, originAuthority.certificate))
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test".toCharArray()) }
        val originTrust = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null); setCertificateEntry("origin", originAuthority.certificate)
        }
        val serverTrust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(originTrust) }
        val serverContext = SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, serverTrust.trustManagers, SecureRandom()) }
        val requests = AtomicInteger()
        val origin = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            httpsConfigurator = object : HttpsConfigurator(serverContext) {
                override fun configure(parameters: com.sun.net.httpserver.HttpsParameters) {
                    parameters.setSSLParameters(serverContext.defaultSSLParameters.apply { needClientAuth = mutual })
                }
            }
            createContext("/protected") { exchange ->
                requests.incrementAndGet()
                val body = ("manual-origin-tls" + "x".repeat(12_500)).encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val configuration = AtomicReference(ProtectedTrafficConfiguration())
        val policy = ProtectedTrafficTlsInterceptionPolicy({ configuration.get() })
        val observed = CountDownLatch(2)
        val captures = ConcurrentLinkedQueue<ProxyOpaqueFlowCaptureMetadata>()
        val captureSink = ProxyCaptureSink { _ -> object : ProxyConnectionCapture {
            override fun startExchange(
                exchangeId: com.devuloopers.knet.traffic.id.ExchangeId,
                request: com.devuloopers.knet.traffic.model.http.RequestHead,
                occurredAtEpochMillis: Long,
                origin: com.devuloopers.knet.traffic.model.TrafficOrigin,
                streamId: com.devuloopers.knet.traffic.id.StreamId?,
            ): ProxyExchangeCapture? = null
            override fun startOpaqueFlow(metadata: ProxyOpaqueFlowCaptureMetadata): ProxyOpaqueFlowCapture? {
                captures.add(metadata)
                observed.countDown()
                return null
            }
            override fun close(reason: com.devuloopers.knet.traffic.model.TrafficTerminationReason?) = Unit
        } }
        val port = ServerSocket(0).use { it.localPort }
        val conditions = com.devuloopers.knet.engine.simulator.NetworkConditionEngine(configuration = {
            com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration(enabled = true,
                globalProfileId = com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns.STREAMING_100_KBPS.id)
        })
        val proxy = KNetProxyServer(port = port,
            pipelineInitializers = listOf { pipeline -> pipeline.addLast("conditions", com.devuloopers.knet.engine.simulator.NetworkConditionChannelHandler(conditions)) },
            serverTlsContextProvider = DesktopServerTlsContextProvider(CertificateAuthority.generate(commonName = "Manual rule inspection"), CertificateCache()),
            tlsInterceptionPolicy = policy, captureSink = captureSink)
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null); setCertificateEntry("origin", originAuthority.certificate)
        }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
        val clientContext = SSLContext.getInstance("TLS").apply { init(if (mutual) keyManagers.keyManagers else null, trust.trustManagers, SecureRandom()) }
        fun client(): SSLSocket {
            val socket = Socket().apply { connect(proxy.boundAddress()); soTimeout = 10_000 }
            socket.getOutputStream().apply {
                write("CONNECT 127.0.0.1:${origin.address.port} HTTP/1.1\r\nHost: 127.0.0.1:${origin.address.port}\r\n\r\n".encodeToByteArray()); flush()
            }
            val input = socket.getInputStream()
            val header = StringBuilder()
            while (!header.endsWith("\r\n\r\n")) {
                val value = input.read(); check(value >= 0); header.append(value.toChar())
                check(header.length < 8192)
            }
            check(header.startsWith("HTTP/1.1 200"))
            return (clientContext.socketFactory.createSocket(socket, "localhost", origin.address.port, true) as SSLSocket).apply {
                soTimeout = 10_000
                enabledProtocols = arrayOf(protocol)
                sslParameters = sslParameters.apply { serverNames = listOf(SNIHostName("localhost")) }
            }
        }
        proxy.start()
        try {
            repeat(2) { client().use { assertFails { it.startHandshake() } } }
            assertTrue(observed.await(10, TimeUnit.SECONDS))
            assertTrue(captures.all { it.inspectionFailureReason == InspectionFailureReason.CERTIFICATE_REJECTED })
            assertTrue(captures.all { it.policyAction == ProxyOpaquePolicyAction.INSPECT })
            assertTrue(configuration.get().rules.isEmpty(), "Failures must never create a handling rule")
            assertEquals(0, requests.get(), "Rejected interception must not send or replay an application request")
            val rule = ProtectedTrafficRule(ProtectedTrafficRuleId("user-tunnel"), action = ProtectedTrafficAction.TUNNEL,
                destination = ProtectedDestinationSelector.parse("localhost"), port = origin.address.port,
                transport = ProtectedTransportProtocol.TCP)
            configuration.set(configuration.get().copy(rules = listOf(rule)))
            val started = System.nanoTime()
            client().use { socket ->
                socket.startHandshake()
                assertContentEquals(leaf.certificate.encoded, socket.session.peerCertificates.first().encoded)
                socket.getOutputStream().apply {
                    write("GET /protected HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".encodeToByteArray()); flush()
                }
                val response = BufferedInputStream(socket.inputStream).readBytes().decodeToString()
                assertTrue(response.contains("manual-origin-tls"))
            }
            assertTrue((System.nanoTime() - started) / 1_000_000 >= 750, "Protected payload escaped the 100 kbps budget")
            assertEquals(1, requests.get())
            assertEquals(com.devuloopers.knet.traffic.model.ProtectionDecisionOrigin.MANUAL, captures.last().protectionOrigin)
            assertEquals(rule.id.value, captures.last().policyRuleId)
            configuration.set(configuration.get().copy(rules = listOf(rule.copy(action = ProtectedTrafficAction.INSPECT))))
            client().use { assertFails { it.startHandshake() } }
            assertEquals(1, requests.get(), "Inspect next time must restore inspection without replaying application data")
        } finally { proxy.stop(); origin.stop(0) }
    }
}
