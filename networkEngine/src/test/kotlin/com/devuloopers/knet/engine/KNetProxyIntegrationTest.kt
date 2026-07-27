package com.devuloopers.knet.engine

import com.devuloopers.knet.crypto.CertificateAuthority
import com.devuloopers.knet.crypto.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.sun.net.httpserver.*
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end integration tests validating KNet's HTTP and HTTPS proxying and MITM decryption.
 */
class KNetProxyIntegrationTest {

    private var httpServer: HttpServer? = null
    private var httpsServer: HttpsServer? = null
    private var proxyServer: KNetProxyServer? = null
    private var proxyPort = 0
    private var httpPort = 0
    private var httpsPort = 0
    private var ca: CertificateAuthority? = null

    @BeforeTest
    fun setUp() {
        // 1. Generate Root CA for proxy SSL decryption
        ca = CertificateAuthority.generate(
            commonName = "KNet Integration Test CA",
            org = "Test Org",
            validityDays = 2
        )

        // 2. Start KNet Proxy Server on a dynamic port
        val tempServer = KNetProxyServer(0, ca!!, CertificateCache())
        tempServer.start()
        proxyServer = tempServer

        // Find assigned local port using reflection
        val field = KNetProxyServer::class.java.getDeclaredField("serverChannel")
        field.isAccessible = true
        val channel = field.get(tempServer) as io.netty.channel.Channel
        val localAddress = channel.localAddress() as InetSocketAddress
        proxyPort = localAddress.port

        // 3. Start a local target HTTP server on dynamic port (binding specifically to 127.0.0.1)
        httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/hello", SimpleHttpHandler("Plain HTTP Response"))
            executor = null
            start()
        }
        httpPort = httpServer!!.address.port

        // 4. Start a local target HTTPS server on dynamic port (binding specifically to 127.0.0.1)
        val sslContext = createTestServerSslContext()
        httpsServer = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            httpsConfigurator = object : HttpsConfigurator(sslContext) {
                override fun configure(params: HttpsParameters) {
                    params.protocols = arrayOf("TLSv1.2", "TLSv1.3")
                }
            }
            createContext("/hello-secure", SimpleHttpHandler("Secure HTTPS Response"))
            executor = null
            start()
        }
        httpsPort = httpsServer!!.address.port
    }

    @AfterTest
    fun tearDown() {
        proxyServer?.stop()
        httpServer?.stop(0)
        httpsServer?.stop(0)
    }

    @Test
    fun testPlainHttpProxying() {
        // Configure HTTP client to use our Netty proxy, with 5-second connect timeout
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(ProxySelector.of(InetSocketAddress("127.0.0.1", proxyPort)))
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$httpPort/hello"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertEquals("Plain HTTP Response", response.body())
    }

    @Test
    fun testHttpsMitmProxying() {
        // Prepare the client-side SSL Context to trust the custom CA certificate from KNet.
        val clientSslContext = createClientSslContext(ca!!.certificate)

        // Configure client to route HTTPS through our Netty proxy with 5-second connect timeout
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(ProxySelector.of(InetSocketAddress("127.0.0.1", proxyPort)))
            .sslContext(clientSslContext)
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://127.0.0.1:$httpsPort/hello-secure"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertEquals("Secure HTTPS Response", response.body())
    }

    /**
     * Creates an SSL context for the mock target HTTPS server using a temporary self-signed certificate.
     */
    private fun createTestServerSslContext(): SSLContext {
        // Generate a separate self-signed certificate to serve on the mock remote HTTPS server.
        val tempCA = CertificateAuthority.generate(commonName = "Mock Target Server", validityDays = 1)

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            "key",
            tempCA.privateKey,
            charArrayOf(),
            arrayOf(tempCA.certificate)
        )

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, charArrayOf())

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, null)
        return sslContext
    }

    /**
     * Creates an SSL context for the test client that trusts KNet's Root CA certificate.
     */
    private fun createClientSslContext(caCert: X509Certificate): SSLContext {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("ca", caCert)

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(keyStore)

        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leafCert = chain?.firstOrNull()
                leafCert?.verify(caCert.publicKey)
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(caCert)
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAllManager), null)
        return sslContext
    }

    /**
     * Simple handler returning a plaintext response.
     */
    private class SimpleHttpHandler(private val responseBody: String) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val bytes = responseBody.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
