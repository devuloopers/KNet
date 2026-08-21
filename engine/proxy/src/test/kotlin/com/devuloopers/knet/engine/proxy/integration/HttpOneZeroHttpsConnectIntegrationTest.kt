package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Real TLS qualification for an HTTP/1.0 CONNECT tunnel intercepted by KNet. */
class HttpOneZeroHttpsConnectIntegrationTest {

    @Test
    fun `http one zero connect negotiates tls and forwards an inner request`() {
        val originAuthority = CertificateAuthority.generate(commonName = "HTTP 1.0 test origin")
        val originLeaf = CertificateCache().get(KNetProxyServer.DEFAULT_BIND_HOST, originAuthority)
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
            createContext("/secure") { exchange ->
                val body = "secure-http-1.0".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { response -> response.write(body) }
            }
            start()
        }

        val knetAuthority = CertificateAuthority.generate(commonName = "HTTP 1.0 KNet test CA")
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = TestServerTlsContextProvider(knetAuthority, CertificateCache()),
            verifyUpstreamTls = false,
        )
        proxy.start()

        try {
            val rawSocket = Socket().apply {
                connect(proxy.boundAddress())
                soTimeout = 10_000
            }
            val originEndpoint = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.address.port}"
            rawSocket.getOutputStream().apply {
                write("CONNECT $originEndpoint HTTP/1.0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            val connectInput = BufferedInputStream(rawSocket.getInputStream())
            assertEquals("HTTP/1.0 200 Connection Established", readAsciiLine(connectInput))
            readHeaders(connectInput)

            val secureSocket = clientSslContext(knetAuthority.certificate)
                .socketFactory
                .createSocket(
                    rawSocket,
                    KNetProxyServer.DEFAULT_BIND_HOST,
                    origin.address.port,
                    true,
                ) as SSLSocket
            secureSocket.soTimeout = 10_000
            secureSocket.use { client ->
                client.startHandshake()
                client.outputStream.apply {
                    write("GET /secure HTTP/1.0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    flush()
                }

                val input = BufferedInputStream(client.inputStream)
                assertEquals("HTTP/1.0 200 OK", readAsciiLine(input))
                val responseHeaders = readHeaders(input)
                assertFalse(responseHeaders.containsKey("transfer-encoding"))
                val contentLength = responseHeaders.getValue("content-length").toInt()
                assertEquals("secure-http-1.0", readExactly(input, contentLength).toString(Charsets.UTF_8))
            }
        } finally {
            proxy.stop()
            origin.stop(0)
        }
    }

    /** Creates a server TLS context with one leaf and its issuing authority. */
    private fun serverSslContext(
        privateKey: java.security.PrivateKey,
        certificateChain: Array<X509Certificate>,
    ): SSLContext {
        val password = "knet-http10-test".toCharArray()
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

    /** Creates a TLS client context that trusts only the KNet authority used by this test. */
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

    /** Reads normalized HTTP fields through the terminating empty line. */
    private fun readHeaders(input: BufferedInputStream): Map<String, String> = buildMap {
        while (true) {
            val line = readAsciiLine(input)
            if (line.isEmpty()) return@buildMap
            put(line.substringBefore(':').trim().lowercase(), line.substringAfter(':').trim())
        }
    }

    /** Reads exactly the requested body size. */
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

    /** Reads one CRLF-terminated ASCII line. */
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

    /** Reserves and releases an ephemeral loopback port for the proxy listener. */
    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }
}
