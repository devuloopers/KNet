package com.devuloopers.knet.engine.proxy.performance

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.sun.management.UnixOperatingSystemMXBean
import io.netty.buffer.PooledByteBufAllocator
import java.io.BufferedInputStream
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reproducible loopback capacity and resource-recovery gates for the aggregated HTTP/1 proxy. */
class ProxyCapacityBaselineTest {

    /** Measures the largest supported aggregated response below the current ten-mebibyte codec limit. */
    @Test
    fun `large aggregated response forwards within the temporary Phase 9 memory budget`() {
        val responseBody = ByteArray(LARGE_RESPONSE_BODY_BYTES) { index -> (index % 251).toByte() }
        val origin = ConcurrentOrigin(responseBody)
        val proxy = createProxy(availableLoopbackPort(), STANDARD_POLICY)
        val metrics = ResourceMetrics.capture()
        val sampler = ResourceSampler(metrics)

        origin.start()
        proxy.start()
        sampler.start()
        val received: ByteArray
        val elapsedMillis = try {
            lateinit var captured: ByteArray
            val measured = measureTimeMillis {
                captured = requestThroughProxy(proxy.port, origin.port)
            }
            received = captured
            measured
        } finally {
            sampler.stop()
            proxy.stop()
            origin.close()
        }

        assertEquals(responseBody.size, received.size)
        assertEquals(responseBody.first(), received.first())
        assertEquals(responseBody.last(), received.last())
        assertTrue(origin.failures.isEmpty(), origin.failures.joinToString { failure -> failure.toString() })
        val heapPeakDelta = (sampler.peakHeapBytes.get() - metrics.heapBytes).coerceAtLeast(0L)
        val directPeakDelta = metricDelta(metrics.directMemoryBytes, sampler.peakDirectMemoryBytes.get())
        assertTrue(elapsedMillis <= MAX_LARGE_BODY_ELAPSED_MILLIS)
        assertTrue(heapPeakDelta <= MAX_LARGE_BODY_HEAP_DELTA_BYTES)
        assertTrue(directPeakDelta < 0L || directPeakDelta <= MAX_LARGE_BODY_DIRECT_DELTA_BYTES)
        System.out.println(
            "KNET_PROXY_LARGE_BODY_BASELINE responseBytes=$LARGE_RESPONSE_BODY_BYTES elapsedMs=$elapsedMillis " +
                "heapPeakDeltaBytes=$heapPeakDelta directPeakDeltaBytes=$directPeakDelta"
        )
    }

    /** Proves the streaming response path forwards the Phase 11 qualification size without body-sized allocation. */
    @Test
    fun `five hundred mebibyte response streams with bounded transport and capture memory`() {
        val origin = StreamingOrigin(STREAMING_QUALIFICATION_BYTES)
        val captureSink = QualificationCaptureSink(STREAM_CAPTURE_PREFIX_BYTES)
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(
                CertificateAuthority.generate(), CertificateCache(),
            ),
            captureSink = captureSink,
            runtimePolicy = STANDARD_POLICY.copy(
                readIdleTimeoutMillis = STREAMING_TIMEOUT_MILLIS,
                writeIdleTimeoutMillis = STREAMING_TIMEOUT_MILLIS,
            ),
        )
        val metrics = ResourceMetrics.capture()
        val sampler = ResourceSampler(metrics)

        origin.start()
        proxy.start()
        sampler.start()
        val receivedBytes: Long
        val elapsedMillis = try {
            lateinit var measured: Pair<Long, Long>
            val duration = measureTimeMillis {
                measured = drainResponseThroughProxy(proxy.port, origin.port)
            }
            receivedBytes = measured.first
            assertEquals(STREAMING_QUALIFICATION_BYTES, receivedBytes)
            assertEquals(STREAMING_FILL_BYTE.toLong() and 0xffL, measured.second)
            duration
        } finally {
            sampler.stop()
            proxy.stop()
            origin.close()
        }

        assertTrue(captureSink.completed.await(5L, TimeUnit.SECONDS), "Bounded capture sink did not complete.")
        assertEquals(STREAM_CAPTURE_PREFIX_BYTES, captureSink.capturedBodyBytes.get())
        assertEquals(STREAMING_QUALIFICATION_BYTES, captureSink.observedBodyBytes.get())
        assertEquals(ExchangeState.COMPLETED, captureSink.terminalState)
        assertTrue(!captureSink.invalidPayloadByte.get())
        assertTrue(origin.failures.isEmpty(), origin.failures.joinToString { failure -> failure.toString() })
        val heapPeakDelta = (sampler.peakHeapBytes.get() - metrics.heapBytes).coerceAtLeast(0L)
        val directPeakDelta = metricDelta(metrics.directMemoryBytes, sampler.peakDirectMemoryBytes.get())
        assertTrue(elapsedMillis <= MAX_STREAMING_ELAPSED_MILLIS)
        assertTrue(
            heapPeakDelta <= MAX_STREAMING_HEAP_DELTA_BYTES,
            "Streaming proxy heap grew with the response body: $heapPeakDelta bytes.",
        )
        assertTrue(directPeakDelta < 0L || directPeakDelta <= MAX_STREAMING_DIRECT_DELTA_BYTES)
        System.out.println(
            "KNET_PROXY_STREAMING_QUALIFICATION responseBytes=$receivedBytes elapsedMs=$elapsedMillis " +
                "capturedPrefixBytes=${captureSink.capturedBodyBytes.get()} heapPeakDeltaBytes=$heapPeakDelta " +
                "directPeakDeltaBytes=$directPeakDelta"
        )
    }

    /** Proves uploads larger than the removed aggregator limit stream without body-sized allocation. */
    @Test
    fun `large request body streams with bounded transport and capture memory`() {
        val origin = StreamingUploadOrigin(STREAMING_UPLOAD_QUALIFICATION_BYTES)
        val captureSink = QualificationCaptureSink(
            maximumCapturedBytes = STREAM_CAPTURE_PREFIX_BYTES,
            capturedDirection = TrafficDirection.CLIENT_TO_SERVER,
        )
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(
                CertificateAuthority.generate(), CertificateCache(),
            ),
            captureSink = captureSink,
            runtimePolicy = STANDARD_POLICY.copy(
                readIdleTimeoutMillis = STREAMING_TIMEOUT_MILLIS,
                writeIdleTimeoutMillis = STREAMING_TIMEOUT_MILLIS,
            ),
        )
        val metrics = ResourceMetrics.capture()
        val sampler = ResourceSampler(metrics)

        origin.start()
        proxy.start()
        sampler.start()
        val elapsedMillis = try {
            measureTimeMillis {
                sendStreamingUpload(proxy.port, origin.port, STREAMING_UPLOAD_QUALIFICATION_BYTES)
            }
        } finally {
            sampler.stop()
            proxy.stop()
            origin.close()
        }

        assertTrue(origin.completed.await(5L, TimeUnit.SECONDS), "Origin did not receive the complete upload.")
        assertEquals(STREAMING_UPLOAD_QUALIFICATION_BYTES, origin.receivedBytes.get())
        assertTrue(captureSink.completed.await(5L, TimeUnit.SECONDS), "Upload capture did not complete.")
        assertEquals(STREAM_CAPTURE_PREFIX_BYTES, captureSink.capturedBodyBytes.get())
        assertEquals(STREAMING_UPLOAD_QUALIFICATION_BYTES, captureSink.observedBodyBytes.get())
        assertEquals(ExchangeState.COMPLETED, captureSink.terminalState)
        assertTrue(!captureSink.invalidPayloadByte.get())
        assertTrue(origin.failures.isEmpty(), origin.failures.joinToString { failure -> failure.toString() })
        val heapPeakDelta = (sampler.peakHeapBytes.get() - metrics.heapBytes).coerceAtLeast(0L)
        val directPeakDelta = metricDelta(metrics.directMemoryBytes, sampler.peakDirectMemoryBytes.get())
        assertTrue(elapsedMillis <= MAX_STREAMING_ELAPSED_MILLIS)
        assertTrue(heapPeakDelta <= MAX_STREAMING_HEAP_DELTA_BYTES)
        assertTrue(directPeakDelta < 0L || directPeakDelta <= MAX_STREAMING_DIRECT_DELTA_BYTES)
        System.out.println(
            "KNET_PROXY_STREAMING_UPLOAD requestBytes=${origin.receivedBytes.get()} elapsedMs=$elapsedMillis " +
                "capturedPrefixBytes=${captureSink.capturedBodyBytes.get()} heapPeakDeltaBytes=$heapPeakDelta " +
                "directPeakDeltaBytes=$directPeakDelta"
        )
    }

    /** Measures concurrent payload forwarding, heap/direct-memory peaks, throughput, and descriptor recovery. */
    @Test
    fun `concurrent loopback payload baseline stays inside declared resource budgets`() {
        val responseBody = ByteArray(RESPONSE_BODY_BYTES) { index -> (index % 251).toByte() }
        val origin = ConcurrentOrigin(responseBody)
        val proxy = createProxy(availableLoopbackPort(), STANDARD_POLICY)
        val clientExecutor = Executors.newFixedThreadPool(CONCURRENT_CLIENTS)
        val startGate = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val metrics = ResourceMetrics.capture()
        val sampler = ResourceSampler(metrics)

        origin.start()
        proxy.start()
        sampler.start()
        val elapsedMillis = try {
            val futures = List(CONCURRENT_CLIENTS) {
                clientExecutor.submit {
                    try {
                        assertTrue(startGate.await(5L, TimeUnit.SECONDS))
                        val received = requestThroughProxy(proxy.port, origin.port)
                        assertEquals(responseBody.size, received.size)
                        assertEquals(responseBody.first(), received.first())
                        assertEquals(responseBody.last(), received.last())
                    } catch (failure: Throwable) {
                        failures += failure
                    }
                }
            }
            measureTimeMillis {
                startGate.countDown()
                futures.forEach { future -> future.get(CLIENT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS) }
            }
        } finally {
            sampler.stop()
            clientExecutor.shutdownNow()
            proxy.stop()
            origin.close()
        }

        assertTrue(failures.isEmpty(), failures.joinToString { failure -> failure.toString() })
        assertTrue(origin.failures.isEmpty(), origin.failures.joinToString { failure -> failure.toString() })
        val recoveredDescriptors = awaitDescriptorRecovery(metrics.openFileDescriptors, DESCRIPTOR_RECOVERY_ALLOWANCE)
        val heapPeakDelta = (sampler.peakHeapBytes.get() - metrics.heapBytes).coerceAtLeast(0L)
        val directPeakDelta = metricDelta(metrics.directMemoryBytes, sampler.peakDirectMemoryBytes.get())
        val descriptorPeakDelta = metricDelta(metrics.openFileDescriptors, sampler.peakOpenFileDescriptors.get())
        val transferredBytes = CONCURRENT_CLIENTS.toLong() * RESPONSE_BODY_BYTES
        val throughputBytesPerSecond = transferredBytes * 1_000L / elapsedMillis.coerceAtLeast(1L)

        assertTrue(elapsedMillis <= MAX_CONCURRENT_ELAPSED_MILLIS)
        assertTrue(heapPeakDelta <= MAX_HEAP_DELTA_BYTES, "Concurrent proxy heap delta exceeded its baseline budget.")
        assertTrue(directPeakDelta < 0L || directPeakDelta <= MAX_DIRECT_MEMORY_DELTA_BYTES)
        assertTrue(descriptorPeakDelta < 0L || descriptorPeakDelta <= MAX_DESCRIPTOR_PEAK_DELTA)
        assertTrue(
            recoveredDescriptors < 0L || recoveredDescriptors <= metrics.openFileDescriptors + DESCRIPTOR_RECOVERY_ALLOWANCE,
            "Proxy descriptors did not recover after concurrent clients and shutdown.",
        )

        System.out.println(
            "KNET_PROXY_CAPACITY_BASELINE clients=$CONCURRENT_CLIENTS responseBytes=$RESPONSE_BODY_BYTES " +
                "elapsedMs=$elapsedMillis throughputBytesPerSecond=$throughputBytesPerSecond " +
                "heapPeakDeltaBytes=$heapPeakDelta directPeakDeltaBytes=$directPeakDelta " +
                "descriptorPeakDelta=$descriptorPeakDelta recoveredDescriptors=$recoveredDescriptors"
        )
    }

    /** Exercises the declared Phase 11 workload without allocating one body per connection. */
    @Test
    fun `one hundred concurrent ten mebibyte responses stay bounded`() {
        val origin = ConcurrentStreamingOrigin(CONCURRENT_STREAMING_CLIENTS, CONCURRENT_STREAMING_BODY_BYTES)
        val proxy = createProxy(
            availableLoopbackPort(),
            STANDARD_POLICY.copy(
                readIdleTimeoutMillis = STREAMING_TIMEOUT_MILLIS,
                writeIdleTimeoutMillis = STREAMING_TIMEOUT_MILLIS,
            ),
        )
        val clients = Executors.newFixedThreadPool(CONCURRENT_STREAMING_WORKERS)
        val startGate = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val metrics = ResourceMetrics.capture()
        val sampler = ResourceSampler(metrics)

        origin.start()
        proxy.start()
        sampler.start()
        val elapsedMillis = try {
            val futures = List(CONCURRENT_STREAMING_CLIENTS) {
                clients.submit {
                    try {
                        assertTrue(startGate.await(5L, TimeUnit.SECONDS))
                        val received = drainSizedResponseThroughProxy(
                            proxy.port,
                            origin.port,
                            CONCURRENT_STREAMING_BODY_BYTES,
                        )
                        assertEquals(CONCURRENT_STREAMING_BODY_BYTES, received)
                    } catch (failure: Throwable) {
                        failures += failure
                    }
                }
            }
            measureTimeMillis {
                startGate.countDown()
                futures.forEach { future ->
                    future.get(MAX_CONCURRENT_STREAMING_ELAPSED_MILLIS, TimeUnit.MILLISECONDS)
                }
            }
        } finally {
            sampler.stop()
            clients.shutdownNow()
            proxy.stop()
            origin.close()
        }

        assertTrue(
            failures.isEmpty(),
            failures.joinToString(separator = "\n\n") { failure -> failure.stackTraceToString() },
        )
        assertTrue(origin.failures.isEmpty(), origin.failures.joinToString { failure -> failure.toString() })
        assertEquals(CONCURRENT_STREAMING_CLIENTS.toLong(), origin.completedResponses.get())
        val heapPeakDelta = (sampler.peakHeapBytes.get() - metrics.heapBytes).coerceAtLeast(0L)
        val directPeakDelta = metricDelta(metrics.directMemoryBytes, sampler.peakDirectMemoryBytes.get())
        assertTrue(elapsedMillis <= MAX_CONCURRENT_STREAMING_ELAPSED_MILLIS)
        assertTrue(heapPeakDelta <= MAX_CONCURRENT_STREAMING_HEAP_DELTA_BYTES)
        assertTrue(directPeakDelta < 0L || directPeakDelta <= MAX_CONCURRENT_STREAMING_DIRECT_DELTA_BYTES)
        System.out.println(
            "KNET_PROXY_CONCURRENT_STREAMING clients=$CONCURRENT_STREAMING_CLIENTS " +
                "responseBytes=$CONCURRENT_STREAMING_BODY_BYTES elapsedMs=$elapsedMillis " +
                "heapPeakDeltaBytes=$heapPeakDelta directPeakDeltaBytes=$directPeakDelta"
        )
    }

    /** Verifies slow upstream peers and abrupt downstream disconnects converge within timeout/resource budgets. */
    @Test
    fun `slow peers and abrupt disconnects release bounded proxy resources`() {
        val slowOrigin = SlowOrigin(SLOW_ORIGIN_HOLD_MILLIS)
        val metrics = ResourceMetrics.capture()
        val proxy = createProxy(
            port = availableLoopbackPort(),
            policy = STANDARD_POLICY.copy(
                readIdleTimeoutMillis = SLOW_PEER_TIMEOUT_MILLIS,
                writeIdleTimeoutMillis = SLOW_PEER_TIMEOUT_MILLIS,
            ),
        )
        slowOrigin.start()
        proxy.start()
        val elapsedMillis = try {
            repeat(ABRUPT_DISCONNECTS) {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxy.port), 2_000)
                }
            }
            measureTimeMillis {
                repeat(SLOW_CLIENTS) {
                    Socket().use { client ->
                        client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxy.port), 2_000)
                        client.soTimeout = SLOW_CLIENT_SOCKET_TIMEOUT_MILLIS
                        val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${slowOrigin.port}"
                        client.getOutputStream().write(
                            (
                                "GET http://$authority/slow HTTP/1.1\r\n" +
                                    "Host: $authority\r\nConnection: close\r\n\r\n"
                            ).toByteArray()
                        )
                        client.getOutputStream().flush()
                        val input = client.getInputStream()
                        while (input.read() >= 0) {
                            // Drain any generated error response until timeout-driven close.
                        }
                    }
                }
            }
        } finally {
            proxy.stop()
            slowOrigin.close()
        }

        assertTrue(elapsedMillis <= MAX_SLOW_PEER_ELAPSED_MILLIS)
        assertTrue(slowOrigin.failures.isEmpty(), slowOrigin.failures.joinToString { failure -> failure.toString() })
        val recoveredDescriptors = awaitDescriptorRecovery(metrics.openFileDescriptors, DESCRIPTOR_RECOVERY_ALLOWANCE)
        assertTrue(
            recoveredDescriptors < 0L || recoveredDescriptors <= metrics.openFileDescriptors + DESCRIPTOR_RECOVERY_ALLOWANCE,
            "Proxy descriptors did not recover after slow peers and disconnects.",
        )
        System.out.println(
            "KNET_PROXY_TIMEOUT_BASELINE slowClients=$SLOW_CLIENTS abruptDisconnects=$ABRUPT_DISCONNECTS " +
                "elapsedMs=$elapsedMillis recoveredDescriptors=$recoveredDescriptors"
        )
    }

    /** Verifies repeated start/stop cycles do not accumulate listener descriptors. */
    @Test
    fun `repeated lifecycle baseline recovers listener descriptors`() {
        val metrics = ResourceMetrics.capture()
        val proxy = createProxy(availableLoopbackPort(), STANDARD_POLICY)
        val elapsedMillis = measureTimeMillis {
            repeat(LIFECYCLE_REPETITIONS) {
                proxy.start()
                assertTrue(proxy.isRunning())
                proxy.stop()
                assertTrue(!proxy.isRunning())
            }
        }
        val recoveredDescriptors = awaitDescriptorRecovery(metrics.openFileDescriptors, DESCRIPTOR_RECOVERY_ALLOWANCE)
        assertTrue(elapsedMillis <= MAX_LIFECYCLE_ELAPSED_MILLIS)
        assertTrue(
            recoveredDescriptors < 0L || recoveredDescriptors <= metrics.openFileDescriptors + DESCRIPTOR_RECOVERY_ALLOWANCE,
            "Proxy listener descriptors accumulated across lifecycle repetitions.",
        )
        System.out.println(
            "KNET_PROXY_LIFECYCLE_BASELINE repetitions=$LIFECYCLE_REPETITIONS " +
                "elapsedMs=$elapsedMillis recoveredDescriptors=$recoveredDescriptors"
        )
    }

    /** Creates a strict loopback proxy with the supplied runtime policy. */
    private fun createProxy(port: Int, policy: KNetProxyRuntimePolicy): KNetProxyServer = KNetProxyServer(
        port = port,
        serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(
            CertificateAuthority.generate(), CertificateCache(),
        ),
        runtimePolicy = policy,
    )

    /** Sends one absolute-form request through KNet and returns the content-length-delimited body. */
    private fun requestThroughProxy(proxyPort: Int, originPort: Int): ByteArray = Socket().use { client ->
        client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort), 2_000)
        client.soTimeout = CLIENT_TIMEOUT_SECONDS * 1_000
        val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort"
        client.getOutputStream().write(
            (
                "GET http://$authority/baseline HTTP/1.1\r\n" +
                    "Host: $authority\r\nConnection: close\r\n\r\n"
            ).toByteArray()
        )
        client.getOutputStream().flush()
        readHttpResponseBody(BufferedInputStream(client.getInputStream()))
    }

    /** Drains a content-length response without allocating an array proportional to the body. */
    private fun drainResponseThroughProxy(proxyPort: Int, originPort: Int): Pair<Long, Long> = Socket().use { client ->
        client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort), 2_000)
        client.soTimeout = (STREAMING_TIMEOUT_MILLIS * 2L).toInt()
        val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort"
        client.getOutputStream().write(
            (
                "GET http://$authority/streaming-qualification HTTP/1.1\r\n" +
                    "Host: $authority\r\nConnection: close\r\n\r\n"
            ).toByteArray()
        )
        client.getOutputStream().flush()
        val input = BufferedInputStream(client.getInputStream(), STREAMING_CHUNK_BYTES)
        val statusLine = readAsciiLine(input)
        require(statusLine.startsWith("HTTP/1.1 200")) { "Unexpected proxy response: $statusLine" }
        var contentLength = -1L
        while (true) {
            val header = readAsciiLine(input)
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = header.substringAfter(':').trim().toLong()
            }
        }
        require(contentLength == STREAMING_QUALIFICATION_BYTES)
        val buffer = ByteArray(STREAMING_CHUNK_BYTES)
        var received = 0L
        var lastByte = -1L
        while (received < contentLength) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), contentLength - received).toInt())
            require(read >= 0) { "Streaming response ended at $received of $contentLength bytes." }
            if (read > 0) lastByte = buffer[read - 1].toLong() and 0xffL
            received += read
        }
        received to lastByte
    }

    /** Drains a generated response and returns its observed body byte count. */
    private fun drainSizedResponseThroughProxy(
        proxyPort: Int,
        originPort: Int,
        expectedBodyBytes: Long,
    ): Long = Socket().use { client ->
        client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort), 5_000)
        client.soTimeout = STREAMING_TIMEOUT_MILLIS.toInt()
        val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort"
        client.getOutputStream().write(
            (
                "GET http://$authority/concurrent-stream HTTP/1.1\r\n" +
                    "Host: $authority\r\nConnection: close\r\n\r\n"
            ).toByteArray()
        )
        client.getOutputStream().flush()
        val input = BufferedInputStream(client.getInputStream(), STREAMING_CHUNK_BYTES)
        require(readAsciiLine(input).startsWith("HTTP/1.1 200"))
        var contentLength = -1L
        while (true) {
            val header = readAsciiLine(input)
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = header.substringAfter(':').trim().toLong()
            }
        }
        require(contentLength == expectedBodyBytes)
        val chunk = ByteArray(STREAMING_CHUNK_BYTES)
        var received = 0L
        while (received < contentLength) {
            val count = input.read(chunk, 0, minOf(chunk.size.toLong(), contentLength - received).toInt())
            require(count >= 0)
            received += count
        }
        received
    }

    /** Generates one fixed-byte upload without retaining an array proportional to the request body. */
    private fun sendStreamingUpload(proxyPort: Int, originPort: Int, bodyBytes: Long) {
        Socket().use { client ->
            client.connect(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort), 2_000)
            client.soTimeout = (STREAMING_TIMEOUT_MILLIS * 2L).toInt()
            val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort"
            val output = client.getOutputStream().buffered(STREAMING_CHUNK_BYTES)
            output.write(
                (
                    "POST http://$authority/streaming-upload HTTP/1.1\r\n" +
                        "Host: $authority\r\nContent-Length: $bodyBytes\r\n" +
                        "Content-Type: application/octet-stream\r\nConnection: close\r\n\r\n"
                ).toByteArray()
            )
            val chunk = ByteArray(STREAMING_CHUNK_BYTES) { STREAMING_FILL_BYTE }
            var written = 0L
            while (written < bodyBytes) {
                val count = minOf(chunk.size.toLong(), bodyBytes - written).toInt()
                output.write(chunk, 0, count)
                written += count
            }
            output.flush()

            val input = BufferedInputStream(client.getInputStream())
            val statusLine = readAsciiLine(input)
            require(statusLine.startsWith("HTTP/1.1 200")) { "Unexpected upload response: $statusLine" }
            while (readAsciiLine(input).isNotEmpty()) {
                // Response metadata is sufficient for this upload qualification.
            }
        }
    }

    /** Reads one successful HTTP response without retaining header objects. */
    private fun readHttpResponseBody(input: BufferedInputStream): ByteArray {
        val statusLine = readAsciiLine(input)
        require(statusLine.startsWith("HTTP/1.1 200")) { "Unexpected proxy response: $statusLine" }
        var contentLength = -1
        while (true) {
            val header = readAsciiLine(input)
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = header.substringAfter(':').trim().toInt()
            }
        }
        require(contentLength >= 0) { "Baseline response did not include a content length." }
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < body.size) {
            val read = input.read(body, offset, body.size - offset)
            require(read >= 0) { "Baseline response ended before its declared body length." }
            offset += read
        }
        return body
    }

    /** Reserves and releases a loopback port for a subsequent proxy bind. */
    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }

    /** Polls the process descriptor count until it returns to the declared post-test allowance. */
    private fun awaitDescriptorRecovery(initial: Long, allowance: Long): Long {
        if (initial < 0L) return -1L
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
        var current = openFileDescriptors()
        while (current > initial + allowance && System.nanoTime() < deadline) {
            Thread.sleep(25L)
            current = openFileDescriptors()
        }
        return current
    }

    /** Returns a meaningful metric delta or `-1` when the JVM does not expose the metric. */
    private fun metricDelta(initial: Long, peak: Long): Long =
        if (initial < 0L || peak < 0L) -1L else (peak - initial).coerceAtLeast(0L)

    /** Concurrent loopback origin returning one fixed response per accepted connection. */
    private class ConcurrentOrigin(private val body: ByteArray) : AutoCloseable {
        private val server = ServerSocket()
        private val running = AtomicBoolean(false)
        private val handlers = Executors.newFixedThreadPool(CONCURRENT_CLIENTS)
        private var acceptThread: Thread? = null
        val failures: ConcurrentLinkedQueue<Throwable> = ConcurrentLinkedQueue()
        val port: Int get() = server.localPort

        /** Binds the origin and starts its bounded accept loop. */
        fun start() {
            server.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
            running.set(true)
            acceptThread = thread(name = "knet-capacity-origin", isDaemon = true) {
                while (running.get()) {
                    try {
                        val socket = server.accept()
                        handlers.execute { respond(socket) }
                    } catch (failure: Throwable) {
                        if (running.get()) failures += failure
                    }
                }
            }
        }

        /** Reads request headers and writes the fixed response. */
        private fun respond(socket: Socket) {
            try {
                socket.use { connection ->
                    val input = BufferedInputStream(connection.getInputStream())
                    while (readAsciiLine(input).isNotEmpty()) {
                        // Header values are irrelevant to the fixed loopback response.
                    }
                    connection.getOutputStream().write(
                        (
                            "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\n" +
                                "Content-Type: application/octet-stream\r\nConnection: close\r\n\r\n"
                        ).toByteArray()
                    )
                    connection.getOutputStream().write(body)
                    connection.getOutputStream().flush()
                }
            } catch (failure: Throwable) {
                failures += failure
            }
        }

        /** Stops accepting, closes active executor tasks, and releases the listener. */
        override fun close() {
            running.set(false)
            server.close()
            acceptThread?.join(1_000L)
            handlers.shutdownNow()
            handlers.awaitTermination(5L, TimeUnit.SECONDS)
        }
    }

    /** Origin that generates a fixed byte stream without retaining the qualification body. */
    private class StreamingOrigin(private val bodyBytes: Long) : AutoCloseable {
        private val server = ServerSocket()
        private var responder: Thread? = null
        val failures: ConcurrentLinkedQueue<Throwable> = ConcurrentLinkedQueue()
        val port: Int get() = server.localPort

        fun start() {
            server.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
            responder = thread(name = "knet-streaming-origin", isDaemon = true) {
                try {
                    server.accept().use { connection ->
                        val input = BufferedInputStream(connection.getInputStream())
                        while (readAsciiLine(input).isNotEmpty()) {
                            // Drain request headers before producing the response.
                        }
                        val output = connection.getOutputStream().buffered(STREAMING_CHUNK_BYTES)
                        output.write(
                            (
                                "HTTP/1.1 200 OK\r\nContent-Length: $bodyBytes\r\n" +
                                    "Content-Type: application/octet-stream\r\nConnection: close\r\n\r\n"
                            ).toByteArray()
                        )
                        val chunk = ByteArray(STREAMING_CHUNK_BYTES) { STREAMING_FILL_BYTE }
                        var written = 0L
                        while (written < bodyBytes) {
                            val count = minOf(chunk.size.toLong(), bodyBytes - written).toInt()
                            output.write(chunk, 0, count)
                            written += count
                        }
                        output.flush()
                    }
                } catch (failure: Throwable) {
                    if (!server.isClosed) failures += failure
                }
            }
        }

        override fun close() {
            server.close()
            responder?.join(5_000L)
        }
    }

    /** Origin that drains a large request incrementally before returning an empty success response. */
    private class StreamingUploadOrigin(private val expectedBodyBytes: Long) : AutoCloseable {
        private val server = ServerSocket()
        private var responder: Thread? = null
        val failures: ConcurrentLinkedQueue<Throwable> = ConcurrentLinkedQueue()
        val receivedBytes = AtomicLong(0L)
        val completed = CountDownLatch(1)
        val port: Int get() = server.localPort

        fun start() {
            server.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
            responder = thread(name = "knet-streaming-upload-origin", isDaemon = true) {
                try {
                    server.accept().use { connection ->
                        val input = BufferedInputStream(connection.getInputStream(), STREAMING_CHUNK_BYTES)
                        var contentLength = -1L
                        while (true) {
                            val header = readAsciiLine(input)
                            if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = header.substringAfter(':').trim().toLong()
                            }
                        }
                        require(contentLength == expectedBodyBytes)
                        val chunk = ByteArray(STREAMING_CHUNK_BYTES)
                        var received = 0L
                        while (received < contentLength) {
                            val count = input.read(
                                chunk,
                                0,
                                minOf(chunk.size.toLong(), contentLength - received).toInt(),
                            )
                            require(count >= 0) { "Upload ended at $received of $contentLength bytes." }
                            if (count > 0) {
                                require(chunk[0] == STREAMING_FILL_BYTE && chunk[count - 1] == STREAMING_FILL_BYTE)
                                received += count
                            }
                        }
                        receivedBytes.set(received)
                        connection.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
                        )
                        connection.getOutputStream().flush()
                        completed.countDown()
                    }
                } catch (failure: Throwable) {
                    if (!server.isClosed) failures += failure
                }
            }
        }

        override fun close() {
            server.close()
            responder?.join(5_000L)
        }
    }

    /** Bounded worker origin that generates concurrent response bodies from one reusable chunk. */
    private class ConcurrentStreamingOrigin(
        private val expectedConnections: Int,
        private val bodyBytes: Long,
    ) : AutoCloseable {
        private val server = ServerSocket()
        private val handlers = Executors.newFixedThreadPool(CONCURRENT_STREAMING_WORKERS)
        private var acceptThread: Thread? = null
        val failures: ConcurrentLinkedQueue<Throwable> = ConcurrentLinkedQueue()
        val completedResponses = AtomicLong(0L)
        val port: Int get() = server.localPort

        fun start() {
            server.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
            acceptThread = thread(name = "knet-concurrent-stream-origin", isDaemon = true) {
                try {
                    repeat(expectedConnections) {
                        val connection = server.accept()
                        handlers.execute { respond(connection) }
                    }
                } catch (failure: Throwable) {
                    if (!server.isClosed) failures += failure
                }
            }
        }

        private fun respond(connection: Socket) {
            try {
                connection.use { socket ->
                    val input = BufferedInputStream(socket.getInputStream())
                    while (readAsciiLine(input).isNotEmpty()) {
                        // Drain the request head before generating the response.
                    }
                    val output = socket.getOutputStream().buffered(STREAMING_CHUNK_BYTES)
                    output.write(
                        (
                            "HTTP/1.1 200 OK\r\nContent-Length: $bodyBytes\r\n" +
                                "Content-Type: application/octet-stream\r\nConnection: close\r\n\r\n"
                        ).toByteArray()
                    )
                    val chunk = ByteArray(STREAMING_CHUNK_BYTES) { STREAMING_FILL_BYTE }
                    var written = 0L
                    while (written < bodyBytes) {
                        val count = minOf(chunk.size.toLong(), bodyBytes - written).toInt()
                        output.write(chunk, 0, count)
                        written += count
                    }
                    output.flush()
                    completedResponses.incrementAndGet()
                }
            } catch (failure: Throwable) {
                failures += failure
            }
        }

        override fun close() {
            server.close()
            acceptThread?.join(5_000L)
            handlers.shutdownNow()
            handlers.awaitTermination(10L, TimeUnit.SECONDS)
        }
    }

    /** Minimal non-blocking capture sink enforcing a fixed reservation budget for qualification. */
    private class QualificationCaptureSink(
        private val maximumCapturedBytes: Long,
        private val capturedDirection: TrafficDirection = TrafficDirection.SERVER_TO_CLIENT,
    ) : ProxyCaptureSink {
        val capturedBodyBytes = AtomicLong(0L)
        val observedBodyBytes = AtomicLong(0L)
        val invalidPayloadByte = AtomicBoolean(false)
        val completed = CountDownLatch(1)
        @Volatile
        var terminalState: ExchangeState? = null

        override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture =
            object : ProxyConnectionCapture {
                override fun startExchange(
                    exchangeId: ExchangeId,
                    request: RequestHead,
                    occurredAtEpochMillis: Long,
                    origin: com.devuloopers.knet.traffic.model.TrafficOrigin,
                    streamId: com.devuloopers.knet.traffic.id.StreamId?,
                ): ProxyExchangeCapture = object : ProxyExchangeCapture {
                    override val exchangeId: ExchangeId = exchangeId

                    override fun tryReserveBody(
                        direction: TrafficDirection,
                        contentEncoding: ContentEncoding?,
                        requestedBytes: Int,
                    ): ProxyBodyReservation? {
                        if (direction != capturedDirection) return null
                        while (true) {
                            val current = capturedBodyBytes.get()
                            val remaining = maximumCapturedBytes - current
                            if (remaining <= 0L) return null
                            val size = minOf(requestedBytes.toLong(), remaining).toInt()
                            // Reserve the byte budget before allocating, mirroring production ingress.
                            if (!capturedBodyBytes.compareAndSet(current, current + size)) continue
                            return object : ProxyBodyReservation {
                                private val terminal = AtomicBoolean(false)
                                override val writableBytes: ByteArray = ByteArray(size)

                                override fun publish(occurredAtEpochMillis: Long): Boolean {
                                    check(terminal.compareAndSet(false, true))
                                    if (
                                        writableBytes.isNotEmpty() &&
                                        (writableBytes.first() != STREAMING_FILL_BYTE ||
                                            writableBytes.last() != STREAMING_FILL_BYTE)
                                    ) {
                                        invalidPayloadByte.set(true)
                                    }
                                    return true
                                }

                                override fun cancel() {
                                    if (terminal.compareAndSet(false, true)) {
                                        capturedBodyBytes.addAndGet(-size.toLong())
                                    }
                                }
                            }
                        }
                    }

                    override fun completeBody(
                        direction: TrafficDirection,
                        observedBytes: Long,
                        occurredAtEpochMillis: Long,
                    ) {
                        if (direction == capturedDirection) {
                            observedBodyBytes.set(observedBytes)
                        }
                    }

                    override fun cancelBody(
                        direction: TrafficDirection,
                        observedBytes: Long,
                        occurredAtEpochMillis: Long,
                        reason: TrafficTerminationReason,
                    ) {
                        require(reason.code.value.isNotBlank())
                        if (direction == capturedDirection) {
                            observedBodyBytes.set(observedBytes)
                        }
                    }

                    override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit

                    override fun terminate(
                        outcome: ExchangeTerminalOutcome,
                        timings: ExchangeTimings,
                        occurredAtEpochMillis: Long,
                    ) {
                        terminalState = outcome.state
                        completed.countDown()
                    }
                }

                override fun close(reason: TrafficTerminationReason?) = Unit
            }
    }

    /** Loopback origin that accepts complete requests but deliberately withholds response bytes. */
    private class SlowOrigin(private val holdMillis: Long) : AutoCloseable {
        private val server = ServerSocket()
        private val running = AtomicBoolean(false)
        private val handlers = Executors.newFixedThreadPool(SLOW_CLIENTS)
        private var acceptThread: Thread? = null
        val failures: ConcurrentLinkedQueue<Throwable> = ConcurrentLinkedQueue()
        val port: Int get() = server.localPort

        /** Binds the slow origin and starts accepting timeout scenarios. */
        fun start() {
            server.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
            running.set(true)
            acceptThread = thread(name = "knet-slow-origin", isDaemon = true) {
                while (running.get()) {
                    try {
                        val socket = server.accept()
                        handlers.execute { hold(socket) }
                    } catch (failure: Throwable) {
                        if (running.get()) failures += failure
                    }
                }
            }
        }

        /** Holds one upstream connection beyond KNet's configured read-idle timeout. */
        private fun hold(socket: Socket) {
            try {
                socket.use { connection ->
                    val input = BufferedInputStream(connection.getInputStream())
                    while (readAsciiLine(input).isNotEmpty()) {
                        // Consume the complete request before simulating a stalled upstream.
                    }
                    Thread.sleep(holdMillis)
                }
            } catch (failure: Throwable) {
                failures += failure
            }
        }

        /** Stops the slow origin and releases held connections. */
        override fun close() {
            running.set(false)
            server.close()
            acceptThread?.join(1_000L)
            handlers.shutdownNow()
            handlers.awaitTermination(5L, TimeUnit.SECONDS)
            failures.removeIf { failure -> failure is InterruptedException }
        }
    }

    /** Snapshot of process resource counters before a measured scenario. */
    private data class ResourceMetrics(
        val heapBytes: Long,
        val directMemoryBytes: Long,
        val openFileDescriptors: Long,
    ) {
        companion object {
            /** Captures the JVM/process counters available on the current platform. */
            fun capture(): ResourceMetrics = ResourceMetrics(
                heapBytes = usedHeapBytes(),
                directMemoryBytes = usedDirectMemoryBytes(),
                openFileDescriptors = openFileDescriptors(),
            )
        }
    }

    /** Short-lived sampler recording peak counters while a concurrent scenario runs. */
    private class ResourceSampler(initial: ResourceMetrics) {
        private val running = AtomicBoolean(false)
        private var samplingThread: Thread? = null
        val peakHeapBytes: AtomicLong = AtomicLong(initial.heapBytes)
        val peakDirectMemoryBytes: AtomicLong = AtomicLong(initial.directMemoryBytes)
        val peakOpenFileDescriptors: AtomicLong = AtomicLong(initial.openFileDescriptors)

        /** Starts the daemon sampler. */
        fun start() {
            running.set(true)
            samplingThread = thread(name = "knet-capacity-sampler", isDaemon = true) {
                while (running.get()) {
                    peakHeapBytes.accumulateAndGet(usedHeapBytes(), ::maxOf)
                    peakDirectMemoryBytes.accumulateAndGet(usedDirectMemoryBytes(), ::maxOf)
                    peakOpenFileDescriptors.accumulateAndGet(openFileDescriptors(), ::maxOf)
                    Thread.sleep(5L)
                }
            }
        }

        /** Stops sampling and joins the daemon thread. */
        fun stop() {
            running.set(false)
            samplingThread?.join(1_000L)
        }
    }

    private companion object {
        private val STANDARD_POLICY = KNetProxyRuntimePolicy(
            connectTimeoutMillis = 5_000L,
            tlsHandshakeTimeoutMillis = 5_000L,
            readIdleTimeoutMillis = 5_000L,
            writeIdleTimeoutMillis = 5_000L,
            gracefulShutdownTimeoutMillis = 5_000L,
            maximumDownstreamConnections = 128,
            maximumConnectionsPerClient = 128,
            maximumUpstreamConnections = 128,
        )
        private const val CONCURRENT_CLIENTS = 24
        private const val CONCURRENT_STREAMING_CLIENTS = 100
        private const val CONCURRENT_STREAMING_WORKERS = 100
        private const val CONCURRENT_STREAMING_BODY_BYTES = 10L * 1_024L * 1_024L
        private const val RESPONSE_BODY_BYTES = 256 * 1_024
        private const val LARGE_RESPONSE_BODY_BYTES = 8 * 1_024 * 1_024
        private const val STREAMING_QUALIFICATION_BYTES = 500L * 1_024L * 1_024L
        private const val STREAMING_UPLOAD_QUALIFICATION_BYTES = 128L * 1_024L * 1_024L
        private const val STREAMING_CHUNK_BYTES = 64 * 1_024
        private const val STREAM_CAPTURE_PREFIX_BYTES = 10L * 1_024L * 1_024L
        private const val STREAMING_FILL_BYTE: Byte = 0x5a
        private const val CLIENT_TIMEOUT_SECONDS = 20
        private const val ABRUPT_DISCONNECTS = 24
        private const val SLOW_CLIENTS = 6
        private const val SLOW_PEER_TIMEOUT_MILLIS = 250L
        private const val SLOW_ORIGIN_HOLD_MILLIS = 1_000L
        private const val SLOW_CLIENT_SOCKET_TIMEOUT_MILLIS = 3_000
        private const val LIFECYCLE_REPETITIONS = 10
        private const val MAX_CONCURRENT_ELAPSED_MILLIS = 30_000L
        private const val MAX_CONCURRENT_STREAMING_ELAPSED_MILLIS = 120_000L
        private const val MAX_LARGE_BODY_ELAPSED_MILLIS = 30_000L
        private const val STREAMING_TIMEOUT_MILLIS = 120_000L
        private const val MAX_STREAMING_ELAPSED_MILLIS = 120_000L
        private const val MAX_SLOW_PEER_ELAPSED_MILLIS = 20_000L
        private const val MAX_LIFECYCLE_ELAPSED_MILLIS = 20_000L
        private const val MAX_HEAP_DELTA_BYTES = 256L * 1_024L * 1_024L
        private const val MAX_DIRECT_MEMORY_DELTA_BYTES = 128L * 1_024L * 1_024L
        private const val MAX_LARGE_BODY_HEAP_DELTA_BYTES = 192L * 1_024L * 1_024L
        private const val MAX_LARGE_BODY_DIRECT_DELTA_BYTES = 64L * 1_024L * 1_024L
        private const val MAX_STREAMING_HEAP_DELTA_BYTES = 192L * 1_024L * 1_024L
        private const val MAX_STREAMING_DIRECT_DELTA_BYTES = 64L * 1_024L * 1_024L
        private const val MAX_CONCURRENT_STREAMING_HEAP_DELTA_BYTES = 384L * 1_024L * 1_024L
        private const val MAX_CONCURRENT_STREAMING_DIRECT_DELTA_BYTES = 256L * 1_024L * 1_024L
        private const val MAX_DESCRIPTOR_PEAK_DELTA = 256L
        private const val DESCRIPTOR_RECOVERY_ALLOWANCE = 32L

        /** Returns used JVM heap bytes. */
        private fun usedHeapBytes(): Long = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

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

        /** Returns Netty pooled direct-memory usage or `-1` when unavailable. */
        private fun usedDirectMemoryBytes(): Long = runCatching {
            PooledByteBufAllocator.DEFAULT.metric().usedDirectMemory()
        }.getOrDefault(-1L)

        /** Returns the Unix process descriptor count or `-1` on unsupported platforms. */
        private fun openFileDescriptors(): Long =
            (ManagementFactory.getOperatingSystemMXBean() as? UnixOperatingSystemMXBean)
                ?.openFileDescriptorCount
                ?: -1L
    }
}
