package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.core.http.config.HttpClientConfiguration
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionEvent
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.testingserver.http2.Http2TlsLabProperties
import com.devuloopers.knet.testingserver.http2.Http2TlsLabServer
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Transport-level qualification for live SSE delivery and structured cancellation. */
class ServerSentEventsStreamingTest {
    @Test
    fun `http one publishes response head and body before completion and cancellation closes the call`() = runBlocking {
        val server = ServerSocket(0)
        val serverObservedClose = CompletableDeferred<Boolean>()
        val serverJob = launch(Dispatchers.IO) {
            runCatching { server.accept() }.getOrNull()?.use { socket ->
                readRequestHead(socket.getInputStream())
                val event = "id: 1\ndata: first\n\n".encodeToByteArray()
                socket.getOutputStream().apply {
                    write(
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/event-stream\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Transfer-Encoding: chunked\r\n\r\n",
                    )
                    write(event.size.toString(16))
                    write("\r\n")
                    write(event)
                    write("\r\n")
                    flush()
                }
                serverObservedClose.complete(socket.getInputStream().read() < 0)
            }
        }
        val client = KNetApiClient(configuration = HttpClientConfiguration(retryCount = 0))
        val received = Channel<HttpExecutionEvent>(Channel.UNLIMITED)
        val collection = launch {
            client.executeStreaming(
                url = "http://127.0.0.1:${server.localPort}/events",
                method = HttpMethod.GET,
                httpVersionPreference = HttpVersionPreference.HTTP_1_1,
            ).collect(received::send)
        }

        try {
            val head = withTimeout(3.seconds) { received.receive() }
            val chunk = withTimeout(3.seconds) { received.receive() }

            assertEquals(200, assertIs<HttpExecutionEvent.ResponseHead>(head).value.statusCode)
            assertEquals(
                "id: 1\ndata: first\n\n",
                assertIs<HttpExecutionEvent.BodyChunk>(chunk).value.copyBytes().decodeToString(),
            )
            collection.cancelAndJoin()
            assertTrue(withTimeout(3.seconds) { serverObservedClose.await() })
            assertTrue(received.tryReceive().getOrNull() !is HttpExecutionEvent.Completed)
        } finally {
            client.close()
            server.close()
            serverJob.cancelAndJoin()
            received.close()
        }
    }

    @Test
    fun `exact http two streams event frames and reports negotiated protocol`() = runTest {
        val server = Http2TlsLabServer(
            Http2TlsLabProperties(host = "127.0.0.1", port = 0),
        )
        server.start()
        val client = KNetApiClient(
            configuration = HttpClientConfiguration(verifySsl = false, retryCount = 0),
        )

        try {
            val events = withContext(Dispatchers.IO) {
                withTimeout(5.seconds) {
                    client.executeStreaming(
                        url = "https://localhost:${server.boundPort}/lab/v1/http2/sse?events=3&delayMillis=10",
                        httpVersionPreference = HttpVersionPreference.HTTP_2,
                    ).toList()
                }
            }
            val head = events.filterIsInstance<HttpExecutionEvent.ResponseHead>().single().value
            val body = events.filterIsInstance<HttpExecutionEvent.BodyChunk>()
                .joinToString(separator = "") { it.value.copyBytes().decodeToString() }
            val terminal = events.filterIsInstance<HttpExecutionEvent.Completed>().single().result

            assertEquals("HTTP/2", head.protocol?.token)
            assertTrue(body.contains("data: event-1"))
            assertTrue(body.contains("data: event-2"))
            assertTrue(body.contains("data: event-3"))
            assertTrue(terminal.isSuccess, terminal.errorMessage)
            assertEquals("", terminal.responseBody)
        } finally {
            client.close()
            server.stop()
        }
    }

    private fun readRequestHead(input: InputStream) {
        var matched = 0
        val delimiter = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (matched < delimiter.size) {
            val next = input.read()
            check(next >= 0) { "Client closed before sending a complete HTTP request head." }
            matched = if (next.toByte() == delimiter[matched]) matched + 1 else 0
        }
    }

    private fun java.io.OutputStream.write(value: String) {
        write(value.encodeToByteArray())
    }
}
