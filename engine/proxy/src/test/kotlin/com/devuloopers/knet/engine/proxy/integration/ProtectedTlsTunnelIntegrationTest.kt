package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCaptureMetadata
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionDecision
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionMode
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionPolicy
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionRuleId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SNIHostName
import javax.net.ssl.TrustManagerFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** End-to-end evidence that protected TLS sees the origin certificate and emits metadata-only capture. */
class ProtectedTlsTunnelIntegrationTest {

    @Test
    fun `block policy rejects before dialing and records rule evidence`() {
        val capture = RecordingConnectionCapture()
        val proxyAuthority = CertificateAuthority.generate(commonName = "Unused blocked authority")
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(proxyAuthority, CertificateCache()),
            captureSink = ProxyCaptureSink { capture },
            tlsInterceptionPolicy = TlsInterceptionPolicy {
                TlsInterceptionDecision(
                    mode = TlsInterceptionMode.BLOCK,
                    ruleId = TlsInterceptionRuleId("blocked-test"),
                    policyGroupId = "test-block-group",
                )
            },
        )
        proxy.start()

        try {
            Socket().use { socket ->
                socket.connect(proxy.boundAddress())
                socket.soTimeout = 10_000
                socket.getOutputStream().apply {
                    write("CONNECT never-resolve.invalid:443 HTTP/1.1\r\nHost: never-resolve.invalid:443\r\n\r\n".toByteArray())
                    flush()
                }
                val input = BufferedInputStream(socket.getInputStream())
                assertEquals("HTTP/1.1 403 Forbidden", readAsciiLine(input))
                readHeaders(input)
            }

            assertTrue(capture.terminated.await(10, TimeUnit.SECONDS))
            assertEquals("blocked-test", capture.metadata?.policyRuleId)
            assertEquals("BLOCK", capture.metadata?.policyAction?.name)
            assertEquals("test-block-group", capture.metadata?.policyGroupId)
            assertEquals(0L, capture.uploaded.get())
            assertEquals(0L, capture.downloaded.get())
            val outcome = assertIs<ExchangeTerminalOutcome.Dropped>(capture.outcome)
            assertEquals(TrafficTerminationReason.Interception.PROTECTED_TRAFFIC_BLOCKED, outcome.reason)
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun `rejected interception certificate records a failed opaque tls attempt`() {
        val capture = RecordingConnectionCapture()
        val proxyAuthority = CertificateAuthority.generate(commonName = "KNet interception authority")
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(proxyAuthority, CertificateCache()),
            captureSink = ProxyCaptureSink { capture },
            tlsInterceptionPolicy = TlsInterceptionPolicy {
                TlsInterceptionDecision(
                    mode = TlsInterceptionMode.INSPECT,
                    ruleId = TlsInterceptionRuleId("inspect-test"),
                )
            },
        )
        proxy.start()

        try {
            val rawSocket = Socket().apply {
                connect(proxy.boundAddress())
                soTimeout = 10_000
            }
            rawSocket.getOutputStream().apply {
                write("CONNECT pinned.example.test:443 HTTP/1.1\r\nHost: pinned.example.test:443\r\n\r\n".toByteArray())
                flush()
            }
            val connectInput = BufferedInputStream(rawSocket.getInputStream())
            assertEquals("HTTP/1.1 200 Connection Established", readAsciiLine(connectInput))
            readHeaders(connectInput)

            val secureSocket = untrustedClientSslContext()
                .socketFactory
                .createSocket(rawSocket, "pinned.example.test", 443, true) as SSLSocket
            secureSocket.sslParameters = secureSocket.sslParameters.apply {
                serverNames = listOf(SNIHostName("pinned.example.test"))
            }
            secureSocket.soTimeout = 10_000
            secureSocket.use { client -> assertFails { client.startHandshake() } }

            assertTrue(capture.terminated.await(10, TimeUnit.SECONDS))
            assertEquals("inspect-test", capture.metadata?.policyRuleId)
            assertEquals("pinned.example.test", capture.metadata?.serverName)
            val outcome = assertIs<ExchangeTerminalOutcome.Failed>(capture.outcome)
            assertEquals(
                TrafficTerminationReason.Transport.DOWNSTREAM_TLS_HANDSHAKE_FAILED,
                outcome.reason,
            )
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun `tunnel preserves origin tls and records opaque byte counters`() {
        val originAuthority = CertificateAuthority.generate(commonName = "Protected origin authority")
        val originLeaf = CertificateCache().get(KNetProxyServer.DEFAULT_BIND_HOST, originAuthority)
        val origin = HttpsServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            httpsConfigurator = HttpsConfigurator(
                serverSslContext(
                    privateKey = originLeaf.keyPair.private,
                    certificateChain = arrayOf(originLeaf.certificate, originAuthority.certificate),
                ),
            )
            createContext("/protected") { exchange ->
                val body = "origin-tls-preserved".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { response -> response.write(body) }
            }
            start()
        }
        val capture = RecordingConnectionCapture()
        val policySources = mutableListOf<String?>()
        val proxyAuthority = CertificateAuthority.generate(commonName = "Unused KNet tunnel authority")
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(proxyAuthority, CertificateCache()),
            verifyUpstreamTls = true,
            captureSink = ProxyCaptureSink { capture },
            ingressContext = IngressContext(
                kind = IngressKind.CompanionDirect,
                sourceApplicationId = "com.example.streaming",
            ),
            tlsInterceptionPolicy = TlsInterceptionPolicy { request ->
                policySources += request.sourceApplication?.value
                if (request.serverName == null) {
                    TlsInterceptionDecision(mode = TlsInterceptionMode.INSPECT)
                } else {
                    TlsInterceptionDecision(
                        mode = TlsInterceptionMode.TUNNEL,
                        ruleId = TlsInterceptionRuleId("protected-test"),
                    )
                }
            },
        )
        proxy.start()

        try {
            val rawSocket = Socket().apply {
                connect(proxy.boundAddress())
                soTimeout = 10_000
            }
            val endpoint = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.address.port}"
            rawSocket.getOutputStream().apply {
                write("CONNECT $endpoint HTTP/1.1\r\nHost: $endpoint\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            val connectInput = BufferedInputStream(rawSocket.getInputStream())
            assertEquals("HTTP/1.1 200 Connection Established", readAsciiLine(connectInput))
            readHeaders(connectInput)

            val secureSocket = clientSslContext(originAuthority.certificate)
                .socketFactory
                .createSocket(rawSocket, "localhost", origin.address.port, true) as SSLSocket
            secureSocket.sslParameters = secureSocket.sslParameters.apply {
                serverNames = listOf(SNIHostName("protected.example.test"))
                applicationProtocols = arrayOf("h2", "http/1.1")
            }
            secureSocket.soTimeout = 10_000
            secureSocket.use { client ->
                client.startHandshake()
                assertContentEquals(
                    originLeaf.certificate.encoded,
                    client.session.peerCertificates.first().encoded,
                )
                client.outputStream.apply {
                    write("GET /protected HTTP/1.1\r\nHost: $endpoint\r\nConnection: close\r\n\r\n".toByteArray())
                    flush()
                }
                val input = BufferedInputStream(client.inputStream)
                assertEquals("HTTP/1.1 200 OK", readAsciiLine(input))
                val headers = readHeaders(input)
                val body = readExactly(input, headers.getValue("content-length").toInt())
                assertEquals("origin-tls-preserved", body.toString(Charsets.UTF_8))
                assertEquals(-1, input.read())
            }

            assertTrue(capture.terminated.await(10, TimeUnit.SECONDS))
            assertEquals("protected-test", capture.metadata?.policyRuleId)
            assertEquals("com.example.streaming", capture.metadata?.sourceApplicationId)
            assertTrue(policySources.isNotEmpty())
            assertTrue(policySources.all { it == "com.example.streaming" })
            assertEquals("protected.example.test", capture.metadata?.serverName)
            assertEquals(listOf("h2", "http/1.1"), capture.metadata?.offeredApplicationProtocols)
            assertTrue(capture.metadata?.offeredTlsVersions.orEmpty().isNotEmpty())
            assertTrue(capture.uploaded.get() > 0L)
            assertTrue(capture.downloaded.get() > 0L)
            assertIs<ExchangeTerminalOutcome.Completed>(capture.outcome)
        } finally {
            proxy.stop()
            origin.stop(0)
        }
    }

    private class RecordingConnectionCapture : ProxyConnectionCapture, ProxyOpaqueFlowCapture {
        val uploaded = AtomicLong()
        val downloaded = AtomicLong()
        val terminated = CountDownLatch(1)
        var metadata: ProxyOpaqueFlowCaptureMetadata? = null
        var outcome: ExchangeTerminalOutcome? = null

        override fun startExchange(
            exchangeId: ExchangeId,
            request: RequestHead,
            occurredAtEpochMillis: Long,
            origin: TrafficOrigin,
            streamId: StreamId?,
        ): ProxyExchangeCapture? = null

        override fun startOpaqueFlow(metadata: ProxyOpaqueFlowCaptureMetadata): ProxyOpaqueFlowCapture {
            this.metadata = metadata
            return this
        }

        override fun observeBytes(
            direction: TrafficDirection,
            byteCount: Int,
            occurredAtEpochMillis: Long,
        ) {
            when (direction) {
                TrafficDirection.CLIENT_TO_SERVER -> uploaded.addAndGet(byteCount.toLong())
                TrafficDirection.SERVER_TO_CLIENT -> downloaded.addAndGet(byteCount.toLong())
            }
        }

        override fun terminate(outcome: ExchangeTerminalOutcome, occurredAtEpochMillis: Long) {
            this.outcome = outcome
            terminated.countDown()
        }

        override fun close(reason: TrafficTerminationReason?) = Unit
    }

    private fun serverSslContext(
        privateKey: java.security.PrivateKey,
        certificateChain: Array<X509Certificate>,
    ): SSLContext {
        val password = "protected-origin".toCharArray()
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

    private fun clientSslContext(originAuthority: X509Certificate): SSLContext {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("origin", originAuthority)
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagers.trustManagers, SecureRandom())
        }
    }

    /** Builds a client with an empty trust store so the generated interception leaf is rejected. */
    private fun untrustedClientSslContext(): SSLContext {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagers.trustManagers, SecureRandom())
        }
    }

    private fun readHeaders(input: BufferedInputStream): Map<String, String> = buildMap {
        while (true) {
            val line = readAsciiLine(input)
            if (line.isEmpty()) return@buildMap
            put(line.substringBefore(':').trim().lowercase(), line.substringAfter(':').trim())
        }
    }

    private fun readExactly(input: BufferedInputStream, size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(bytes, offset, size - offset)
            require(read >= 0) { "TLS response ended after $offset of $size bytes." }
            offset += read
        }
        return bytes
    }

    private fun readAsciiLine(input: BufferedInputStream): String {
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = input.read()
            require(value >= 0) { "Connection ended before a complete HTTP line." }
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }

    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }
}
