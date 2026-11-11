package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

/** Actual chunked SSE bytes cross the proxy shaper; no full-body interpreter substitutes for transport delivery. */
class SseNetworkConditionIntegrationTest {
    @Test fun `events comments and reconnect cursor survive incremental 100 kbps delivery`() = exercise(false)
    @Test fun `live bypass releases queued SSE bytes without losing event order`() = exercise(true)

    private fun exercise(disableAfterQueue: Boolean) {
        val prefix = ": keepalive\n\nid: 1\ndata: first\n\n".encodeToByteArray()
        val tail = ("id: 2\ndata: " + "x".repeat(if (disableAfterQueue) 125_000 else 12_500) + "\n\n").encodeToByteArray()
        val resumed = ": resumed\n\nid: 3\ndata: last\n\n".encodeToByteArray()
        val releaseOrigin = CountDownLatch(1)
        val originFinished = CompletableFuture<Unit>()
        val cursor = CompletableFuture<String>()
        val workers = Executors.newFixedThreadPool(2)
        val origin = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = workers
            createContext("/events") { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { output ->
                    val previous = exchange.requestHeaders.getFirst("Last-Event-ID")
                    if (previous != null) {
                        cursor.complete(previous)
                        output.write(resumed)
                    } else {
                        output.write(prefix); output.flush()
                        check(releaseOrigin.await(10, TimeUnit.SECONDS))
                        output.write(tail); output.flush()
                        originFinished.complete(Unit)
                    }
                }
                exchange.close()
            }
            start()
        }
        val configuration = MutableStateFlow(NetworkConditionConfiguration(enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = NetworkConditionEngine(configuration, scope)
        val proxy = KNetProxyServer(port = ServerSocket(0).use { it.localPort },
            serverTlsContextProvider = ServerTlsContextProvider { _, _ ->
                CompletableFuture.failedFuture(AssertionError("Plain SSE must not request a TLS certificate"))
            },
            pipelineInitializers = listOf { it.addLast("sseConditions", NetworkConditionChannelHandler(engine)) })
        proxy.start()
        val client = HttpClient.newBuilder().proxy(ProxySelector.of(proxy.boundAddress()))
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(10.seconds.toJavaDuration()).build()
        val endpoint = URI("http://127.0.0.1:${origin.address.port}/events")
        try {
            val response = client.send(HttpRequest.newBuilder(endpoint).timeout(20.seconds.toJavaDuration()).build(),
                HttpResponse.BodyHandlers.ofInputStream())
            assertEquals(200, response.statusCode())
            assertEquals("text/event-stream", response.headers().firstValue("content-type").orElse(null))
            response.body().use { input ->
                assertContentEquals(prefix, input.readNBytes(prefix.size))
                assertFalse(originFinished.isDone, "First event must arrive while the origin is still open and paused")
                releaseOrigin.countDown()
                if (disableAfterQueue) {
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    do { engine.refreshTelemetry(); Thread.sleep(5) }
                    while (engine.snapshot.value.queuedBytes == 0L && System.nanoTime() < deadline)
                    assertTrue(engine.snapshot.value.queuedBytes > 0L)
                    configuration.value = configuration.value.copy(enabled = false)
                }
                val started = System.nanoTime()
                val actual = input.readBytes()
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                assertContentEquals(tail, actual)
                if (disableAfterQueue) assertTrue(elapsed < 2_000, "Live bypass left stale 10-second reservations: ${elapsed}ms")
                else assertTrue(elapsed >= 750, "SSE data escaped the real 100 kbps budget: ${elapsed}ms")
            }
            originFinished.get(5, TimeUnit.SECONDS)
            val reconnect = client.send(HttpRequest.newBuilder(endpoint).header("Last-Event-ID", "2")
                .timeout(10.seconds.toJavaDuration()).build(), HttpResponse.BodyHandlers.ofByteArray())
            assertContentEquals(resumed, reconnect.body())
            assertEquals("2", cursor.get(5, TimeUnit.SECONDS))
        } finally {
            releaseOrigin.countDown(); proxy.stop(); client.close(); origin.stop(0)
            workers.shutdownNow(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS)); scope.cancel()
            engine.refreshTelemetry()
            assertEquals(0L, engine.snapshot.value.queuedBytes)
            assertEquals(0, engine.snapshot.value.activeFlows)
        }
    }
}
