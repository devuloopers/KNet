package com.devuloopers.knet.engine.certificate.interoperability

import com.devuloopers.knet.engine.certificate.LeafCertificateGenerator
import com.devuloopers.knet.engine.certificate.util.TestCertificateFactory
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals

class TlsHandshakeIntegrationTest {

    @Test
    fun testRealHttpsServerTlsHandshake() {
        val ca = TestCertificateFactory.createTestCa("Handshake CA", "Handshake Org")
        val leaf = LeafCertificateGenerator.generate("localhost", ca)

        // 1. Setup KeyStore for HTTPS Server
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        val chain = arrayOf<X509Certificate>(leaf.certificate, ca.certificate)
        keyStore.setKeyEntry("localhost", leaf.keyPair.private, "password".toCharArray(), chain)

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, "password".toCharArray())

        val serverSslContext = SSLContext.getInstance("TLS")
        serverSslContext.init(kmf.keyManagers, null, SecureRandom())

        // 2. Start local HttpsServer
        val server = HttpsServer.create(InetSocketAddress(0), 0)
        server.httpsConfigurator = HttpsConfigurator(serverSslContext)
        server.createContext("/test", object : HttpHandler {
            override fun handle(exchange: HttpExchange) {
                val response = "TLS Handshake Success!"
                exchange.sendResponseHeaders(200, response.length.toLong())
                exchange.responseBody.use { it.write(response.toByteArray()) }
            }
        })
        server.start()

        val port = server.address.port

        try {
            // 3. Setup HttpClient trusting Root CA
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustStore.load(null, null)
            trustStore.setCertificateEntry("ca", ca.certificate)

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(trustStore)

            val clientSslContext = SSLContext.getInstance("TLS")
            clientSslContext.init(null, tmf.trustManagers, SecureRandom())

            val client = HttpClient.newBuilder()
                .sslContext(clientSslContext)
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://localhost:$port/test"))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            assertEquals("TLS Handshake Success!", response.body())
        } finally {
            server.stop(0)
        }
    }
}
