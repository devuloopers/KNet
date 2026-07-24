package com.devuloopers.knet.interceptor

import com.devuloopers.knet.crypto.CertificateAuthority
import com.devuloopers.knet.crypto.CertificateCache
import com.devuloopers.knet.engine.KNetProxyServer
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * End-to-end integration tests validating non-blocking breakpoint interception,
 * request/response manual modifications, and connection drops.
 */
class KNetInterceptorIntegrationTest {

    private var httpServer: HttpServer? = null
    private var proxyServer: KNetProxyServer? = null
    private var proxyPort = 0
    private var httpPort = 0
    private var ca: CertificateAuthority? = null
    private var lastReceivedHeaders = mutableMapOf<String, String>()

    @BeforeTest
    fun setUp() {
        // Register the Interceptor pipeline modifier
        KNetProxyServer.pipelineInitializers.clear()
        KNetProxyServer.pipelineInitializers.add { pipeline ->
            pipeline.addLast("interceptor", KNetInterceptorHandler())
        }

        // 1. Generate Root CA
        ca = CertificateAuthority.generate(
            commonName = "KNet Interceptor Test CA",
            org = "Test Org",
            validityDays = 2
        )

        // 2. Start KNet Proxy
        val tempServer = KNetProxyServer(0, ca!!, CertificateCache())
        tempServer.start()
        proxyServer = tempServer

        // Get local proxy port
        val field = KNetProxyServer::class.java.getDeclaredField("serverChannel")
        field.isAccessible = true
        val channel = field.get(tempServer) as io.netty.channel.Channel
        val localAddress = channel.localAddress() as InetSocketAddress
        proxyPort = localAddress.port

        // 3. Start a local target HTTP server
        httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/hello", SimpleHttpHandler("Original Target Body"))
            executor = null
            start()
        }
        httpPort = httpServer!!.address.port

        BreakpointManager.clearRules()
        BreakpointManager.clearSuspensions()
        lastReceivedHeaders.clear()
    }

    @AfterTest
    fun tearDown() {
        proxyServer?.stop()
        httpServer?.stop(0)
        KNetProxyServer.pipelineInitializers.clear()
    }

    @Test
    fun testRequestBreakpointModification() = runBlocking {
        // Add a request-only breakpoint rule matching "/hello"
        BreakpointManager.addRule(
            BreakpointRule(
                id = "rule-1",
                urlRegex = ".*/hello.*",
                method = "GET",
                isRequestEnabled = true,
                isResponseEnabled = false
            )
        )

        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .proxy(ProxySelector.of(InetSocketAddress("127.0.0.1", proxyPort)))
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$httpPort/hello"))
            .header("X-Test-Header", "InitialValue")
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build()

        val responseDeferred = CompletableDeferred<HttpResponse<String>>()

        // Execute client request in separate coroutine since it will suspend and block
        val clientJob = launch(Dispatchers.IO) {
            try {
                val res = client.send(request, HttpResponse.BodyHandlers.ofString())
                responseDeferred.complete(res)
            } catch (e: Exception) {
                responseDeferred.completeExceptionally(e)
            }
        }

        // Wait up to 2 seconds for the breakpoint to intercept and pause the connection
        withTimeout(2000.milliseconds) {
            while (BreakpointManager.getActiveEvents().isEmpty()) {
                Thread.sleep(50)
            }
        }

        val activeEvents = BreakpointManager.getActiveEvents()
        assertEquals(1, activeEvents.size)

        val event = activeEvents.first()
        assertEquals("GET", event.request.method)
        assertTrue(event.request.url.contains("/hello"))

        // Modify the request header and resume
        val editedHeaders = event.request.headers.toMutableList().apply {
            removeIf { it.first == "X-Test-Header" }
            add(Pair("X-Test-Header", "ModifiedValue"))
        }

        val modifiedRequest = com.devuloopers.knet.model.HttpRequest(
            id = event.request.id,
            method = event.request.method,
            url = event.request.url,
            protocol = event.request.protocol,
            headers = editedHeaders,
            body = event.request.body,
            timestamp = event.request.timestamp
        )

        // Resume request and let it flush
        BreakpointManager.resume(event.id, InterceptResult.Resume(modifiedRequest, null))

        // Wait for connection to resolve
        val response = responseDeferred.await()
        assertEquals(200, response.statusCode())
        assertEquals("Original Target Body", response.body())

        // Verify the mock server received the modified header value
        assertEquals("ModifiedValue", lastReceivedHeaders["x-test-header"])

        clientJob.join()
    }

    @Test
    fun testResponseBreakpointModification() = runBlocking {
        // Add a response-only breakpoint rule matching "/hello"
        BreakpointManager.addRule(
            BreakpointRule(
                id = "rule-2",
                urlRegex = ".*/hello.*",
                method = "GET",
                isRequestEnabled = false,
                isResponseEnabled = true
            )
        )

        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .proxy(ProxySelector.of(InetSocketAddress("127.0.0.1", proxyPort)))
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$httpPort/hello"))
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build()

        val responseDeferred = CompletableDeferred<HttpResponse<String>>()

        val clientJob = launch(Dispatchers.IO) {
            try {
                val res = client.send(request, HttpResponse.BodyHandlers.ofString())
                responseDeferred.complete(res)
            } catch (e: Exception) {
                responseDeferred.completeExceptionally(e)
            }
        }

        // Wait for the response to trigger the breakpoint suspension
        withTimeout(2000.milliseconds) {
            while (BreakpointManager.getActiveEvents().isEmpty()) {
                Thread.sleep(50)
            }
        }

        val event = BreakpointManager.getActiveEvents().first()
        assertEquals(200, event.response?.statusCode)
        assertEquals("Original Target Body", event.response?.body?.let { String(it) })

        // Modify the response body and status code
        val modifiedResponse = com.devuloopers.knet.model.HttpResponse(
            statusCode = 201,
            statusText = "Created",
            headers = event.response!!.headers,
            body = "Interception Successful".toByteArray(),
            timestamp = event.response.timestamp
        )

        // Resume response
        BreakpointManager.resume(event.id, InterceptResult.Resume(null, modifiedResponse))

        val response = responseDeferred.await()
        assertEquals(201, response.statusCode())
        assertEquals("Interception Successful", response.body())

        clientJob.join()
    }

    @Test
    fun testBreakpointConnectionDrop() = runBlocking {
        // Add a request breakpoint rule
        BreakpointManager.addRule(
            BreakpointRule(
                id = "rule-3",
                urlRegex = ".*",
                method = "GET",
                isRequestEnabled = true,
                isResponseEnabled = false
            )
        )

        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .proxy(ProxySelector.of(InetSocketAddress("127.0.0.1", proxyPort)))
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$httpPort/hello"))
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build()

        val responseDeferred = CompletableDeferred<HttpResponse<String>>()

        val clientJob = launch(Dispatchers.IO) {
            try {
                val res = client.send(request, HttpResponse.BodyHandlers.ofString())
                responseDeferred.complete(res)
            } catch (e: Exception) {
                responseDeferred.completeExceptionally(e)
            }
        }

        // Wait for suspension
        withTimeout(2000.milliseconds) {
            while (BreakpointManager.getActiveEvents().isEmpty()) {
                Thread.sleep(50)
            }
        }

        val event = BreakpointManager.getActiveEvents().first()

        // Resume with Drop result
        BreakpointManager.resume(event.id, InterceptResult.Drop)

        // Verify that the client request throws exception (connection closed)
        assertFails {
            responseDeferred.await()
        }

        clientJob.join()
    }

    /**
     * Simple HTTP handler that captures inbound request headers into [lastReceivedHeaders].
     */
    private inner class SimpleHttpHandler(private val responseBody: String) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            // Capture received headers
            exchange.requestHeaders.forEach { (key, list) ->
                lastReceivedHeaders[key.lowercase()] = list.firstOrNull() ?: ""
            }

            val bytes = responseBody.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
