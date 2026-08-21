package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real-listener qualification suite for HTTP/1.0 forwarding, framing, persistence, and capture. */
class HttpOneZeroIntegrationTest {

    private val certificateAuthority = CertificateAuthority.generate()
    private val certificateCache = CertificateCache()

    @Test
    fun `absolute form request without host forwards and records http one zero`() {
        val captureSink = RecordingCaptureSink()
        withOriginAndProxy(captureSink) { origin, proxy ->
            val originCompleted = serveOrigin(origin) { connection ->
                val request = readRequest(connection)
                assertEquals("GET /absolute HTTP/1.0", request.requestLine)
                assertEquals("${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}", request.headers["host"])
                writeResponse(
                    connection,
                    "HTTP/1.0 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok",
                )
            }

            openProxyClient(proxy).use { client ->
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                writeRequest(client, "GET http://$authority/absolute HTTP/1.0\r\n\r\n")
                val input = BufferedInputStream(client.getInputStream())
                val response = readResponseHead(input)
                assertEquals("HTTP/1.0 200 OK", response.statusLine)
                assertEquals("ok", readExactly(input, 2).toString(Charsets.UTF_8))
                assertEquals(-1, input.read())
            }

            originCompleted.get(5, TimeUnit.SECONDS)
            assertTrue(captureSink.completed.await(5, TimeUnit.SECONDS))
            assertEquals("HTTP/1.0", assertNotNull(captureSink.request).protocol.token)
            assertEquals("HTTP/1.0", assertNotNull(captureSink.response).protocol.token)
            assertEquals(ExchangeState.COMPLETED, captureSink.terminalState)
        }
    }

    @Test
    fun `content length upload streams through http one zero`() {
        val captureSink = RecordingCaptureSink()
        withOriginAndProxy(captureSink) { origin, proxy ->
            val originCompleted = serveOrigin(origin) { connection ->
                val request = readRequest(connection)
                assertEquals("POST /upload HTTP/1.0", request.requestLine)
                assertEquals("payload", request.body.toString(Charsets.UTF_8))
                writeResponse(
                    connection,
                    "HTTP/1.0 201 Created\r\nContent-Length: 7\r\nConnection: close\r\n\r\ncreated",
                )
            }

            openProxyClient(proxy).use { client ->
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                writeRequest(
                    client,
                    "POST http://$authority/upload HTTP/1.0\r\nContent-Length: 7\r\n\r\npayload",
                )
                val input = BufferedInputStream(client.getInputStream())
                assertEquals("HTTP/1.0 201 Created", readResponseHead(input).statusLine)
                assertEquals("created", readExactly(input, 7).toString(Charsets.UTF_8))
            }

            originCompleted.get(5, TimeUnit.SECONDS)
            assertTrue(captureSink.completed.await(5, TimeUnit.SECONDS))
            assertEquals(7L, captureSink.observedRequestBytes)
        }
    }

    @Test
    fun `default close terminates the downstream connection after one response`() {
        withOriginAndProxy { origin, proxy ->
            val originRequests = AtomicInteger(0)
            val originCompleted = CompletableFuture<Unit>()
            origin.soTimeout = 750
            thread(name = "knet-http10-default-close-origin", isDaemon = true) {
                try {
                    origin.accept().use { connection ->
                        originRequests.incrementAndGet()
                        readRequest(connection)
                        writeResponse(
                            connection,
                            "HTTP/1.0 200 OK\r\nContent-Length: 5\r\nConnection: keep-alive\r\n\r\nfirst",
                        )
                    }
                    try {
                        origin.accept().use { originRequests.incrementAndGet() }
                    } catch (_: SocketTimeoutException) {
                        // Expected: HTTP/1.0 is non-persistent unless the client explicitly opts in.
                    }
                    originCompleted.complete(Unit)
                } catch (failure: Throwable) {
                    originCompleted.completeExceptionally(failure)
                }
            }

            openProxyClient(proxy).use { client ->
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                writeRequest(
                    client,
                    "GET http://$authority/first HTTP/1.0\r\n\r\n",
                )
                val input = BufferedInputStream(client.getInputStream())
                assertEquals("HTTP/1.0 200 OK", readResponseHead(input).statusLine)
                assertEquals("first", readExactly(input, 5).toString(Charsets.UTF_8))
                assertEquals(-1, input.read())
            }

            originCompleted.get(5, TimeUnit.SECONDS)
            assertEquals(1, originRequests.get())
        }
    }

    @Test
    fun `legacy proxy connection keep alive supports two sequential requests`() {
        withOriginAndProxy { origin, proxy ->
            val originCompleted = CompletableFuture<Unit>()
            thread(name = "knet-http10-keep-alive-origin", isDaemon = true) {
                try {
                    listOf("/first" to "first", "/second" to "second").forEach { (path, body) ->
                        origin.accept().use { connection ->
                            assertEquals("GET $path HTTP/1.0", readRequest(connection).requestLine)
                            writeResponse(
                                connection,
                                "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n" +
                                    "Connection: close\r\n\r\n$body",
                            )
                        }
                    }
                    originCompleted.complete(Unit)
                } catch (failure: Throwable) {
                    originCompleted.completeExceptionally(failure)
                }
            }

            openProxyClient(proxy).use { client ->
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                val input = BufferedInputStream(client.getInputStream())
                writeRequest(
                    client,
                    "GET http://$authority/first HTTP/1.0\r\nProxy-Connection: keep-alive\r\n\r\n",
                )
                val firstResponse = readResponseHead(input)
                assertEquals("HTTP/1.0 200 OK", firstResponse.statusLine)
                assertEquals("keep-alive", firstResponse.headers["connection"])
                assertEquals("first", readExactly(input, 5).toString(Charsets.UTF_8))

                writeRequest(
                    client,
                    "GET http://$authority/second HTTP/1.0\r\nConnection: close\r\n\r\n",
                )
                val secondResponse = readResponseHead(input)
                assertEquals("HTTP/1.0 200 OK", secondResponse.statusLine)
                assertEquals("second", readExactly(input, 6).toString(Charsets.UTF_8))
                assertEquals(-1, input.read())
            }

            originCompleted.get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `chunked upstream response becomes close delimited without trailers`() {
        withOriginAndProxy { origin, proxy ->
            val originCompleted = serveOrigin(origin) { connection ->
                readRequest(connection)
                writeResponse(
                    connection,
                    "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nTrailer: X-Origin-Trailer\r\n\r\n" +
                        "5\r\nhello\r\n0\r\nX-Origin-Trailer: value\r\n\r\n",
                )
            }

            openProxyClient(proxy).use { client ->
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                writeRequest(client, "GET http://$authority/chunked HTTP/1.0\r\n\r\n")
                val input = BufferedInputStream(client.getInputStream())
                val response = readResponseHead(input)
                assertEquals("HTTP/1.0 200 OK", response.statusLine)
                assertFalse(response.headers.containsKey("transfer-encoding"))
                assertFalse(response.headers.containsKey("trailer"))
                assertEquals("close", response.headers["connection"])
                assertEquals("hello", readUntilEof(input).toString(Charsets.UTF_8))
            }

            originCompleted.get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `generated invalid request response uses http one zero`() {
        withOriginAndProxy { _, proxy ->
            openProxyClient(proxy).use { client ->
                writeRequest(client, "GET /missing-authority HTTP/1.0\r\n\r\n")
                val input = BufferedInputStream(client.getInputStream())
                val response = readResponseHead(input)
                assertEquals("HTTP/1.0 400 Bad Request", response.statusLine)
                assertEquals("0", response.headers["content-length"])
                assertEquals("close", response.headers["connection"])
                assertEquals(-1, input.read())
            }
        }
    }

    /** Runs one scenario with isolated ephemeral origin and proxy listeners. */
    private fun withOriginAndProxy(
        captureSink: ProxyCaptureSink? = null,
        block: (ServerSocket, KNetProxyServer) -> Unit,
    ) {
        val origin = ServerSocket().apply {
            bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        }
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(
                certificateAuthority,
                certificateCache,
            ),
            captureSink = captureSink,
        )
        proxy.start()
        try {
            block(origin, proxy)
        } finally {
            proxy.stop()
            origin.close()
        }
    }

    /** Starts one origin accept operation and reports thread failures to the calling test. */
    private fun serveOrigin(origin: ServerSocket, block: (Socket) -> Unit): CompletableFuture<Unit> {
        val completed = CompletableFuture<Unit>()
        thread(name = "knet-http10-origin", isDaemon = true) {
            try {
                origin.accept().use(block)
                completed.complete(Unit)
            } catch (failure: Throwable) {
                completed.completeExceptionally(failure)
            }
        }
        return completed
    }

    /** Opens one timeout-bounded downstream connection to the active proxy. */
    private fun openProxyClient(proxy: KNetProxyServer): Socket = Socket().apply {
        connect(proxy.boundAddress())
        soTimeout = 5_000
    }

    /** Writes and flushes one raw HTTP request. */
    private fun writeRequest(socket: Socket, request: String) {
        socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().flush()
    }

    /** Writes and flushes one raw origin response. */
    private fun writeResponse(socket: Socket, response: String) {
        socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().flush()
    }

    /** Reads one request head plus its content-length-delimited body. */
    private fun readRequest(socket: Socket): RawRequest {
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readAsciiLine(input)
        val headers = readHeaders(input)
        val contentLength = headers["content-length"]?.toInt() ?: 0
        return RawRequest(requestLine, headers, readExactly(input, contentLength))
    }

    /** Reads one response status line and normalized header map. */
    private fun readResponseHead(input: BufferedInputStream): RawResponse = RawResponse(
        statusLine = readAsciiLine(input),
        headers = readHeaders(input),
    )

    /** Reads normalized HTTP fields through the terminating empty line. */
    private fun readHeaders(input: BufferedInputStream): Map<String, String> = buildMap {
        while (true) {
            val line = readAsciiLine(input)
            if (line.isEmpty()) return@buildMap
            put(line.substringBefore(':').trim().lowercase(), line.substringAfter(':').trim())
        }
    }

    /** Reads exactly the requested byte count. */
    private fun readExactly(input: BufferedInputStream, size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(bytes, offset, size - offset)
            require(read >= 0) { "Stream ended after $offset of $size bytes." }
            offset += read
        }
        return bytes
    }

    /** Reads all close-delimited bytes. */
    private fun readUntilEof(input: BufferedInputStream): ByteArray {
        val chunks = ArrayList<Byte>()
        while (true) {
            val value = input.read()
            if (value < 0) return chunks.toByteArray()
            chunks += value.toByte()
        }
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

    private data class RawRequest(
        val requestLine: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private data class RawResponse(
        val statusLine: String,
        val headers: Map<String, String>,
    )

    /** Minimal capture sink retaining only the canonical metadata needed by this qualification suite. */
    private class RecordingCaptureSink : ProxyCaptureSink {
        val completed = CountDownLatch(1)
        @Volatile
        var request: RequestHead? = null
        @Volatile
        var response: ResponseHead? = null
        @Volatile
        var observedRequestBytes: Long = -1L
        @Volatile
        var terminalState: ExchangeState? = null

        override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture =
            object : ProxyConnectionCapture {
                override fun startExchange(
                    exchangeId: ExchangeId,
                    request: RequestHead,
                    occurredAtEpochMillis: Long,
                    origin: com.devuloopers.knet.traffic.model.TrafficOrigin,
                ): ProxyExchangeCapture {
                    this@RecordingCaptureSink.request = request
                    return object : ProxyExchangeCapture {
                        override val exchangeId: ExchangeId = exchangeId

                        override fun tryReserveBody(
                            direction: TrafficDirection,
                            contentEncoding: ContentEncoding?,
                            requestedBytes: Int,
                        ): ProxyBodyReservation? = null

                        override fun completeBody(
                            direction: TrafficDirection,
                            observedBytes: Long,
                            occurredAtEpochMillis: Long,
                        ) {
                            if (direction == TrafficDirection.CLIENT_TO_SERVER) {
                                this@RecordingCaptureSink.observedRequestBytes = observedBytes
                            }
                        }

                        override fun cancelBody(
                            direction: TrafficDirection,
                            observedBytes: Long,
                            occurredAtEpochMillis: Long,
                            errorCode: String,
                        ) = Unit

                        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
                            this@RecordingCaptureSink.response = response
                        }

                        override fun terminate(
                            state: ExchangeState,
                            timings: ExchangeTimings,
                            occurredAtEpochMillis: Long,
                            errorCode: String?,
                        ) {
                            this@RecordingCaptureSink.terminalState = state
                            this@RecordingCaptureSink.completed.countDown()
                        }
                    }
                }

                override fun close(errorCode: String?) = Unit
            }
    }
}
