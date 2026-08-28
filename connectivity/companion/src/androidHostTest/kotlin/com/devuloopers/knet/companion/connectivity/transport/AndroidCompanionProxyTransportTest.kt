package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.application.contract.CompanionTransportResult
import com.devuloopers.knet.companion.connectivity.testing.companionRegistrationFixture
import com.devuloopers.knet.companion.connectivity.certificate.isServedByRoot
import com.devuloopers.knet.companion.connectivity.certificate.isValidPairingRoot
import com.devuloopers.knet.companion.connectivity.certificate.matchesPinnedTransportIdentity
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AndroidCompanionProxyTransportTest {
    @Test
    fun `readiness and CONNECT use pinned TLS authenticated carrier`() = runBlocking {
        val identity = tlsIdentity()
        val listener = identity.serverContext.serverSocketFactory.createServerSocket() as SSLServerSocket
        listener.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        val observed = CompletableFuture<List<String>>()
        val server = Thread {
            val headers = mutableListOf<String>()
            try {
                (listener.accept() as SSLSocket).use { socket ->
                    socket.startHandshake()
                    headers += readHeader(socket)
                    socket.outputStream.write(
                        "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".encodeToByteArray(),
                    )
                }
                (listener.accept() as SSLSocket).use { socket ->
                    socket.startHandshake()
                    headers += readHeader(socket)
                    socket.outputStream.write(
                        "HTTP/1.1 200 Connection Established\r\n\r\n".encodeToByteArray(),
                    )
                    socket.outputStream.flush()
                    assertContentEquals("hello".encodeToByteArray(), socket.inputStream.readNBytes(5))
                    socket.outputStream.write("world".encodeToByteArray())
                    socket.outputStream.flush()
                }
                observed.complete(headers)
            } catch (failure: Throwable) {
                observed.completeExceptionally(failure)
            }
        }.apply { isDaemon = true; start() }
        val registration = companionRegistrationFixture(
            transportIdentitySha256 = identity.leafCertificate.encoded.sha256(),
            rootCertificateSha256 = identity.rootCertificate.encoded.sha256(),
            rootCertificateBytes = identity.rootCertificate.encoded,
        ).copy(
            proxyEndpoint = CompanionServiceEndpoint("127.0.0.1", listener.localPort, scheme = CompanionEndpointScheme.HTTPS),
            rootCertificate = CompanionRootCertificate(identity.rootCertificate.encoded),
            rootCertificateSha256 = Sha256Fingerprint(identity.rootCertificate.encoded.sha256()),
        )
        val transport = AndroidCompanionProxyTransport(nowEpochMillis = { 4_000L })
        val protector = CountingProtector()
        try {
            assertTrue(identity.rootCertificate.isValidPairingRoot(registration.rootCertificateSha256.value))
            assertTrue(
                listOf(identity.leafCertificate, identity.rootCertificate)
                    .matchesPinnedTransportIdentity(registration.transportIdentitySha256.value),
            )
            assertTrue(
                listOf(identity.leafCertificate, identity.rootCertificate)
                    .isServedByRoot(identity.rootCertificate),
            )
            assertEquals(CompanionTransportResult.Connected, transport.connect(registration, "credential"))
            val connected = assertIs<CompanionConnectionState.Connected>(transport.state.value)
            assertEquals(4_000L, connected.connectedAtEpochMillis)

            val stream = requireNotNull(transport.openConnectTunnel("example.test", 443, protector))
            stream.use {
                it.output.write("hello".encodeToByteArray())
                it.output.flush()
                assertContentEquals("world".encodeToByteArray(), it.input.readNBytes(5))
            }

            val headers = observed.get(10L, TimeUnit.SECONDS)
            assertTrue(headers[0].startsWith("GET /companion/v3/proxy/readiness HTTP/1.1"))
            assertTrue(headers[1].startsWith("CONNECT example.test:443 HTTP/1.1"))
            assertTrue(headers.all { it.contains("Proxy-Authorization: Bearer device-1:credential") })
            assertEquals(1, protector.tcpCalls)
        } finally {
            transport.disconnect()
            listener.close()
            server.join(5_000L)
        }
    }

    private class CountingProtector : AndroidSocketProtector {
        var tcpCalls: Int = 0
            private set

        override fun protect(socket: Socket): Boolean {
            tcpCalls += 1
            return true
        }

        override fun protect(socket: DatagramSocket): Boolean = true
    }

    private data class TestTlsIdentity(
        val rootCertificate: java.security.cert.X509Certificate,
        val leafCertificate: java.security.cert.X509Certificate,
        val serverContext: SSLContext,
    )

    private fun tlsIdentity(): TestTlsIdentity {
        val identity = testCertificateIdentity(CompanionCertificateProtocol.TLS_SERVER_NAME)
        val password = CharArray(0)
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setKeyEntry(
                "server",
                identity.leafPrivateKey,
                password,
                arrayOf(identity.leafCertificate, identity.rootCertificate),
            )
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
        val context = SSLContext.getInstance("TLS").apply {
            init(keyManagers.keyManagers, null, SecureRandom())
        }
        return TestTlsIdentity(identity.rootCertificate, identity.leafCertificate, context)
    }

    private fun readHeader(socket: Socket): String {
        val content = StringBuilder()
        while (!content.endsWith("\r\n\r\n")) {
            val next = socket.inputStream.read()
            if (next < 0) break
            content.append(next.toChar())
        }
        return content.toString()
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { byte -> "%02x".format(byte) }
