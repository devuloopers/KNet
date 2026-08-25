package com.devuloopers.knet.testingserver

import com.devuloopers.knet.testingserver.http2.Http2TlsLabServer
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import reactor.core.publisher.Flux
import reactor.netty.http.Http2SslContextSpec
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.PrematureCloseException
import reactor.netty.resources.ConnectionProvider
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.time.Duration.Companion.seconds

/** Proves the independent HTTP/2 lab listener uses real TLS, ALPN, multiplexing, and stream frames. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "knet.testing-server.grpc.port=0",
        "knet.testing-server.http2-tls.port=0",
    ],
)
class Http2TlsLabIntegrationTest {
    @Autowired
    private lateinit var http2TlsServer: Http2TlsLabServer

    /** Ensures TLS ALPN selects h2 and a request body survives the native frame pipeline. */
    @Test
    fun `tls listener negotiates h2 with alpn`() = runTest {
        val result = withContext(Dispatchers.IO) {
            withTimeout(5.seconds) {
                http2Client(HttpClient.newConnection())
                    .post()
                    .uri(fixtureUrl("echo"))
                    .sendForm { _, form -> form.attr("message", "through-http2-tls") }
                    .responseSingle { response, body ->
                        body.asString().map { text -> response.version().text() to text }
                    }
                    .awaitSingle()
            }
        }

        assertEquals("HTTP/2.0", result.first)
        assertTrue(result.second.contains("through-http2-tls"))
    }

    /** Ensures trailing HEADERS remain distinguishable from the initial response headers. */
    @Test
    fun `tls listener emits trailing headers`() = runTest {
        val result = withContext(Dispatchers.IO) {
            withTimeout(5.seconds) {
                http2Client(HttpClient.newConnection())
                    .get()
                    .uri(fixtureUrl("trailers"))
                    .responseSingle { response, body ->
                        body.asString().flatMap { text ->
                            response.trailerHeaders().map { trailers ->
                                Triple(response.version().text(), text, trailers.get("x-knet-trailer"))
                            }
                        }
                    }
                    .awaitSingle()
            }
        }

        assertEquals("HTTP/2.0", result.first)
        assertEquals("body-before-trailers", result.second)
        assertEquals("protocol-lab-trailer", result.third)
    }

    /** Ensures concurrent responses interleave over one parent HTTP/2 connection without body corruption. */
    @Test
    fun `slow streams multiplex over one connection`() = runTest {
        val provider = ConnectionProvider.builder("http2-lab-multiplex")
            .maxConnections(1)
            .build()
        try {
            val client = http2Client(HttpClient.create(provider))
            val results = withContext(Dispatchers.IO) {
                withTimeout(5.seconds) {
                    Flux.merge(
                        slowStream(client, "alpha"),
                        slowStream(client, "beta"),
                    ).collectList().awaitSingle()
                }
            }

            assertEquals(setOf("alpha:1\nalpha:2\nalpha:3\n", "beta:1\nbeta:2\nbeta:3\n"), results.map { it.body }.toSet())
            assertEquals(1, results.map { it.connectionId }.toSet().size)
        } finally {
            provider.disposeLater().awaitSingleOrNull()
        }
    }

    /** Ensures encoded SSE payloads stay fragmented HTTP/2 data while remaining valid representations. */
    @Test
    fun `encoded sse fixtures remain valid over native http two frames`() = runTest {
        val client = http2Client(HttpClient.newConnection()).compress(false)

        val gzip = encodedSse(client, "gzip")
        val deflate = encodedSse(client, "deflate")

        assertEquals("HTTP/2.0", gzip.protocol)
        assertEquals("gzip", gzip.encoding)
        assertTrue(
            GZIPInputStream(ByteArrayInputStream(gzip.bytes)).use { input ->
                input.readBytes().decodeToString().contains("gzip-event")
            },
        )
        assertEquals("HTTP/2.0", deflate.protocol)
        assertEquals("deflate", deflate.encoding)
        assertTrue(
            InflaterInputStream(ByteArrayInputStream(deflate.bytes)).use { input ->
                input.readBytes().decodeToString().contains("deflate-event")
            },
        )
    }

    /** Ensures bounded large header blocks survive HPACK encoding and decoding intact. */
    @Test
    fun `large response header remains intact`() = runTest {
        val header = withContext(Dispatchers.IO) {
            withTimeout(5.seconds) {
                http2Client(HttpClient.newConnection())
                    .get()
                    .uri(fixtureUrl("large-headers?bytes=8192"))
                    .responseSingle { response, body ->
                        body.asString()
                            .defaultIfEmpty("")
                            .map { response.responseHeaders().get("x-knet-large-header") }
                    }
                    .awaitSingle()
            }
        }

        assertEquals(8_192, header.length)
    }

    /** Ensures GOAWAY drains the active request and allows the client to establish a healthy successor connection. */
    @Test
    fun `goaway drains and reconnects`() = runTest {
        val provider = ConnectionProvider.builder("http2-lab-goaway")
            .maxConnections(1)
            .build()
        try {
            val client = http2Client(HttpClient.create(provider))
            val goAwayBody = withContext(Dispatchers.IO) {
                withTimeout(5.seconds) {
                    client.get()
                        .uri(fixtureUrl("goaway"))
                        .responseSingle { _, body -> body.asString() }
                        .awaitSingle()
                }
            }
            assertEquals("GOAWAY scheduled", goAwayBody)

            val successorVersion = withContext(Dispatchers.IO) {
                withTimeout(5.seconds) {
                    client.get()
                        .uri(fixtureUrl("echo"))
                        .responseSingle { response, body ->
                            body.asString().map { response.version().text() }
                        }
                        .awaitSingle()
                }
            }
            assertEquals("HTTP/2.0", successorVersion)
        } finally {
            provider.disposeLater().awaitSingleOrNull()
        }
    }

    /** Ensures a stream reset is observable as failure while the listener remains healthy for later streams. */
    @Test
    fun `reset stream does not stop the http2 listener`() = runTest {
        val client = http2Client(HttpClient.newConnection()).disableRetry(true)

        assertThrows<PrematureCloseException> {
            withContext(Dispatchers.IO) {
                withTimeout(5.seconds) {
                    client.get()
                        .uri(fixtureUrl("reset-stream"))
                        .responseSingle { _, body -> body.asString() }
                        .awaitSingle()
                }
            }
        }

        val version = withContext(Dispatchers.IO) {
            withTimeout(5.seconds) {
                client.get()
                    .uri(fixtureUrl("echo"))
                    .responseSingle { response, body ->
                        body.asString().map { response.version().text() }
                    }
                    .awaitSingle()
            }
        }
        assertEquals("HTTP/2.0", version)
    }

    private fun slowStream(client: HttpClient, label: String) = client.get()
        .uri(fixtureUrl("slow-stream?label=$label&chunks=3&delayMillis=20"))
        .responseSingle { response, body ->
            body.asString().map { text ->
                StreamResult(
                    connectionId = response.responseHeaders().get("x-knet-connection-id"),
                    body = text,
                )
            }
        }

    private suspend fun encodedSse(client: HttpClient, encoding: String): EncodedSseResult =
        withContext(Dispatchers.IO) {
            withTimeout(5.seconds) {
                client.get()
                    .uri(fixtureUrl("sse/$encoding"))
                    .responseSingle { response, body ->
                        body.asByteArray().map { bytes ->
                            EncodedSseResult(
                                protocol = response.version().text(),
                                encoding = response.responseHeaders().get("content-encoding"),
                                bytes = bytes,
                            )
                        }
                    }
                    .awaitSingle()
            }
        }

    private fun http2Client(client: HttpClient): HttpClient {
        val sslContext = Http2SslContextSpec.forClient()
            .configure { builder -> builder.trustManager(InsecureTrustManagerFactory.INSTANCE) }
            .sslContext()
        return client
            .protocol(HttpProtocol.H2)
            .http2Settings { settings -> settings.maxHeaderListSize(64 * 1024L) }
            .secure { ssl -> ssl.sslContext(sslContext) }
    }

    private fun fixtureUrl(path: String): String = "https://127.0.0.1:${http2TlsServer.boundPort}/lab/v1/http2/$path"

    private data class StreamResult(
        val connectionId: String,
        val body: String,
    )

    private data class EncodedSseResult(
        val protocol: String,
        val encoding: String?,
        val bytes: ByteArray,
    )
}
