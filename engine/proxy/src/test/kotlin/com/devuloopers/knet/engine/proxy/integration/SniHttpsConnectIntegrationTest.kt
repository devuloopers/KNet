package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsExchange
import com.sun.net.httpserver.HttpsServer
import io.netty.handler.ssl.SslContext
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Real CONNECT-IP + ClientHello-SNI qualification for Android VPN interception. */
class SniHttpsConnectIntegrationTest {
    @Test
    fun `asynchronous certificate failure closes only the affected TLS tunnel`() {
        val requestedNames = CopyOnWriteArrayList<String>()
        val provider = ServerTlsContextProvider { host, executor ->
            requestedNames += host
            CompletableFuture<SslContext>().also { result ->
                executor.execute {
                    result.completeExceptionally(IllegalStateException("test certificate failure"))
                }
            }
        }
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = provider,
        )
        proxy.start()

        try {
            val rawSocket = Socket().apply {
                connect(proxy.boundAddress())
                soTimeout = 10_000
            }
            rawSocket.outputStream.apply {
                write("CONNECT 192.0.2.10:443 HTTP/1.1\r\nHost: 192.0.2.10:443\r\n\r\n".toByteArray())
                flush()
            }
            val input = BufferedInputStream(rawSocket.inputStream)
            assertEquals("HTTP/1.1 200 Connection Established", readAsciiLine(input))
            readHeaders(input)

            val serverName = "failure.example.test"
            val secureSocket = SSLContext.getDefault().socketFactory
                .createSocket(rawSocket, serverName, 443, true) as SSLSocket
            secureSocket.soTimeout = 10_000
            secureSocket.use { client ->
                assertFails { client.startHandshake() }
            }
            assertTrue(requestedNames.isNotEmpty())
            assertEquals(setOf(serverName), requestedNames.toSet())
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun `SNI selects downstream certificate while CONNECT IP remains upstream route`() {
        val serverName = "mobile-api.example.test"
        val originAuthority = CertificateAuthority.generate(commonName = "SNI test origin")
        val originLeaf = CertificateCache().get(KNetProxyServer.DEFAULT_BIND_HOST, originAuthority)
        val observedHosts = CopyOnWriteArrayList<String>()
        val observedUpstreamServerNames = CopyOnWriteArrayList<String>()
        val origin = HttpsServer.create(
            InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0),
            0,
        ).apply {
            httpsConfigurator = HttpsConfigurator(
                serverSslContext(
                    privateKey = originLeaf.keyPair.private,
                    certificateChain = arrayOf(originLeaf.certificate, originAuthority.certificate),
                ),
            )
            createContext("/mobile") { exchange ->
                observedHosts += exchange.requestHeaders.getFirst("Host")
                observedUpstreamServerNames += (exchange as HttpsExchange).sslSession.requestedServerName()
                val body = "sni-routed".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { response -> response.write(body) }
            }
            start()
        }

        val knetAuthority = CertificateAuthority.generate(commonName = "SNI KNet test CA")
        val resolvedCertificateNames = CopyOnWriteArrayList<String>()
        val delegate = TestServerTlsContextProvider(knetAuthority, CertificateCache())
        val recordingProvider = ServerTlsContextProvider { host, executor ->
            resolvedCertificateNames += host
            delegate.resolve(host, executor)
        }
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = recordingProvider,
            verifyUpstreamTls = false,
        )
        proxy.start()

        try {
            val rawSocket = Socket().apply {
                connect(proxy.boundAddress())
                soTimeout = 10_000
            }
            val connectIp = KNetProxyServer.DEFAULT_BIND_HOST
            rawSocket.outputStream.apply {
                write("CONNECT $connectIp:${origin.address.port} HTTP/1.1\r\nHost: $connectIp:${origin.address.port}\r\n\r\n".toByteArray())
                flush()
            }
            val connectInput = BufferedInputStream(rawSocket.inputStream)
            assertEquals("HTTP/1.1 200 Connection Established", readAsciiLine(connectInput))
            readHeaders(connectInput)
            assertTrue(resolvedCertificateNames.isEmpty(), "Certificate generation must wait for ClientHello SNI.")

            val secureSocket = clientSslContext(knetAuthority.certificate)
                .socketFactory
                .createSocket(rawSocket, serverName, origin.address.port, true) as SSLSocket
            secureSocket.soTimeout = 10_000
            secureSocket.sslParameters = secureSocket.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
            }
            secureSocket.use { client ->
                client.startHandshake()
                client.outputStream.apply {
                    write(
                        "GET /mobile HTTP/1.1\r\nHost: $serverName:${origin.address.port}\r\nConnection: close\r\n\r\n"
                            .toByteArray(),
                    )
                    flush()
                }

                val input = BufferedInputStream(client.inputStream)
                assertEquals("HTTP/1.1 200 OK", readAsciiLine(input))
                val headers = readHeaders(input)
                val contentLength = headers.getValue("content-length").toInt()
                assertEquals("sni-routed", readExactly(input, contentLength).decodeToString())
            }

            assertEquals(listOf(serverName), resolvedCertificateNames)
            assertEquals(listOf("$serverName:${origin.address.port}"), observedHosts)
            assertEquals(listOf(serverName), observedUpstreamServerNames)
        } finally {
            proxy.stop()
            origin.stop(0)
        }
    }

    private fun serverSslContext(
        privateKey: java.security.PrivateKey,
        certificateChain: Array<X509Certificate>,
    ): SSLContext {
        val password = "knet-sni-test".toCharArray()
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
        return bytes.toByteArray().decodeToString()
    }

    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }

    private fun javax.net.ssl.SSLSession.requestedServerName(): String =
        ((this as ExtendedSSLSession).requestedServerNames.single() as SNIHostName).asciiName
}
