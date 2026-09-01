package com.devuloopers.knet.testingserver

import com.devuloopers.knet.testingserver.grpc.GrpcServerLifecycle
import com.devuloopers.knet.testingserver.grpc.v1.EchoRequest
import com.devuloopers.knet.testingserver.grpc.v1.EchoReply
import com.devuloopers.knet.testingserver.grpc.v1.FailureRequest
import com.devuloopers.knet.testingserver.grpc.v1.ProtocolLabGrpc
import com.devuloopers.knet.testingserver.grpc.v1.StreamRequest
import com.devuloopers.knet.testingserver.grpc.v1.StreamSummary
import com.devuloopers.knet.testingserver.http2.Http2TlsLabServer
import com.devuloopers.knet.testingserver.payload.NdjsonRecord
import com.devuloopers.knet.testingserver.stream.StreamEvent
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.graphql.client.WebSocketGraphQlClient
import org.springframework.graphql.client.SubscriptionErrorException
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Mono
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.WebsocketClientSpec
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.time.Duration.Companion.seconds

/** Verifies every advertised protocol family through a real bound local server. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "knet.testing-server.grpc.port=0",
        "knet.testing-server.http2-tls.port=0",
    ],
)
class ProtocolLabIntegrationTest {
    private lateinit var webClient: WebTestClient

    @Autowired
    private lateinit var grpcServer: GrpcServerLifecycle

    @Autowired
    private lateinit var http2TlsServer: Http2TlsLabServer

    @LocalServerPort
    private var httpPort: Int = 0

    private val channels = mutableListOf<io.grpc.ManagedChannel>()

    /** Binds the HTTP client to the real random-port listener created for this test context. */
    @BeforeEach
    fun bindWebClient() {
        webClient = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:$httpPort")
            .build()
    }

    /** Releases native client channels after each scenario. */
    @AfterEach
    fun closeChannels() {
        channels.forEach { channel -> channel.shutdownNow() }
        channels.clear()
    }

    /** Ensures discovery reports real bound listeners and keeps future transports explicitly planned. */
    @Test
    fun `manifest reports executable and planned capabilities truthfully`() {
        webClient.get()
            .uri("/lab/v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.apiVersion").isEqualTo("1")
            .jsonPath("$.grpcPort").isEqualTo(grpcServer.boundPort)
            .jsonPath("$.http2TlsPort").isEqualTo(http2TlsServer.boundPort)
            .jsonPath("$.capabilities[?(@.id == 'websocket')].maturity").isEqualTo("AVAILABLE")
            .jsonPath("$.capabilities[?(@.id == 'http2-tls')].maturity").isEqualTo("AVAILABLE")
            .jsonPath("$.capabilities[?(@.id == 'http3')].maturity").isEqualTo("PLANNED")
    }

    /** Ensures manual strict-TLS tests can retrieve only the listener's public certificate. */
    @Test
    fun `http2 public certificate is downloadable`() {
        webClient.get()
            .uri("/lab/v1/http2/certificate.pem")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType("application/x-pem-file")
            .expectBody(String::class.java)
            .consumeWith { result ->
                assertTrue(result.responseBody?.contains("BEGIN CERTIFICATE") == true)
            }
    }

    /** Ensures HTTP request bodies and repeated metadata reach the echo endpoint intact. */
    @Test
    fun `http echo preserves method metadata and body`() {
        webClient.post()
            .uri("/lab/v1/http/echo?mode=first&mode=second")
            .header("X-KNet-Test", "one", "two")
            .bodyValue("request-body")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.method").isEqualTo("POST")
            .jsonPath("$.headers.X-KNet-Test.length()").isEqualTo(2)
            .jsonPath("$.query.mode.length()").isEqualTo(2)
            .jsonPath("$.bodyText").isEqualTo("request-body")
    }

    /** Ensures the advertised clear-text HTTP/2 listener negotiates H2C instead of silently using HTTP/1.1. */
    @Test
    fun `http endpoint negotiates h2c`() = runBlocking {
        val negotiatedVersion = withContext(Dispatchers.IO) {
            withTimeout(5.seconds) {
                HttpClient.create()
                    .protocol(HttpProtocol.H2C)
                    .get()
                    .uri("http://127.0.0.1:$httpPort/lab/v1/payload/text")
                    .responseSingle { response, body ->
                        body.asString().map { response.version().text() }
                    }
                    .awaitSingle()
            }
        }

        assertEquals("HTTP/2.0", negotiatedVersion)
    }

    /** Ensures NDJSON and SSE are emitted as multiple independently decoded records. */
    @Test
    fun `streaming endpoints emit every bounded record`() {
        webClient.get()
            .uri("/lab/v1/payload/ndjson?count=3")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(NdjsonRecord::class.java)
            .hasSize(3)

        webClient.get()
            .uri("/lab/v1/streams/sse?count=3&delayMillis=0")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(StreamEvent::class.java)
            .hasSize(3)
    }

    /** Ensures SSE edge fixtures preserve raw framing and resume from the supplied event cursor. */
    @Test
    fun `sse lab exposes multiline fragmentation malformed bytes gzip and resume fixtures`() {
        webClient.get()
            .uri("/lab/v1/streams/sse/multiline")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .consumeWith { result ->
                assertTrue(result.responseBody.orEmpty().contains("data: first\ndata: second\n\n"))
            }

        webClient.get()
            .uri("/lab/v1/streams/sse/fragmented")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .consumeWith { result ->
                assertTrue(result.responseBody.orEmpty().contains("event: fragmented"))
            }

        webClient.get()
            .uri("/lab/v1/streams/sse/malformed")
            .exchange()
            .expectStatus().isOk
            .expectBody(ByteArray::class.java)
            .consumeWith { result ->
                val body = result.responseBody ?: byteArrayOf()
                assertTrue(body.any { byte -> byte == 0xC3.toByte() })
                assertTrue(body.decodeToString().contains("valid-after-gap"))
            }

        // WebTestClient transparently decompresses encoded responses and removes Content-Encoding. Use the
        // underlying Reactor Netty client with decompression disabled so this fixture verifies the actual wire.
        val gzipResponse = HttpClient.create()
            .compress(false)
            .get()
            .uri("http://127.0.0.1:$httpPort/lab/v1/streams/sse/gzip")
            .responseSingle { response, body ->
                body.asByteArray().map { bytes ->
                    response.responseHeaders()["Content-Encoding"] to bytes
                }
            }
            .block() ?: error("The gzip SSE fixture did not return a response.")
        assertEquals("gzip", gzipResponse.first)
        assertTrue(gzipResponse.second.size >= 2)
        assertEquals(0x1F.toByte(), gzipResponse.second[0])
        assertEquals(0x8B.toByte(), gzipResponse.second[1])

        val deflateResponse = rawSseResponse("deflate")
        assertEquals("deflate", deflateResponse.first)
        assertTrue(
            InflaterInputStream(ByteArrayInputStream(deflateResponse.second)).use { input ->
                input.readBytes().decodeToString().contains("deflate-event")
            },
        )

        val corruptResponse = rawSseResponse("corrupt-gzip")
        assertEquals("gzip", corruptResponse.first)
        assertThrows<Exception> {
            GZIPInputStream(ByteArrayInputStream(corruptResponse.second)).use { input -> input.readBytes() }
        }

        val expansionResponse = rawSseResponse("expansion")
        assertEquals("gzip", expansionResponse.first)
        assertTrue(expansionResponse.second.size < 16 * 1_024)
        assertTrue(
            GZIPInputStream(ByteArrayInputStream(expansionResponse.second)).use { input -> input.readBytes().size } >
                2 * 1_024 * 1_024,
        )

        val resumedEvents: List<StreamEvent> = webClient.get()
            .uri("/lab/v1/streams/sse/resume?count=2")
            .header("Last-Event-ID", "4")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(StreamEvent::class.java)
            .returnResult()
            .responseBody
            .orEmpty()
        assertEquals(listOf(5, 6), resumedEvents.map(StreamEvent::sequence))
    }

    private fun rawSseResponse(name: String): Pair<String?, ByteArray> = HttpClient.create()
        .compress(false)
        .get()
        .uri("http://127.0.0.1:$httpPort/lab/v1/streams/sse/$name")
        .responseSingle { response, body ->
            body.asByteArray().map { bytes -> response.responseHeaders()["Content-Encoding"] to bytes }
        }
        .block() ?: error("The $name SSE fixture did not return a response.")

    /** Ensures a named GraphQL operation executes through the configured HTTP transport. */
    @Test
    fun `graphql endpoint executes named operations`() {
        val request = mapOf(
            "query" to "query NamedEcho(\$message: String!) { echo(message: \$message) { message operation } }",
            "operationName" to "NamedEcho",
            "variables" to mapOf("message" to "through-knet"),
        )
        webClient.post()
            .uri("/lab/v1/graphql")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.echo.message").isEqualTo("through-knet")
            .jsonPath("$.data.echo.operation").isEqualTo("query")
    }

    /** Ensures the GraphQL WebSocket transport executes a named finite subscription. */
    @Test
    fun `graphql websocket emits subscription events`() = runBlocking {
        val graphQlClient = WebSocketGraphQlClient.builder(
            "ws://127.0.0.1:$httpPort/lab/v1/graphql/ws",
            ReactorNettyWebSocketClient(),
        ).build()

        try {
            val sequences = withContext(Dispatchers.IO) {
                withTimeout(5.seconds) {
                    graphQlClient.document(
                        """
                        subscription NamedTicker(${'$'}message: String!, ${'$'}count: Int!, ${'$'}delay: Int!) {
                          ticker(message: ${'$'}message, count: ${'$'}count, delayMillis: ${'$'}delay) { sequence message }
                        }
                        """.trimIndent(),
                    )
                        .operationName("NamedTicker")
                        .variable("message", "subscription-through-knet")
                        .variable("count", 3)
                        .variable("delay", 0)
                        .retrieveSubscription("ticker.sequence")
                        .toEntity(Int::class.java)
                        .collectList()
                        .awaitSingle()
                }
            }

            assertEquals(listOf(1, 2, 3), sequences)
        } finally {
            graphQlClient.stop().awaitSingleOrNull()
        }
    }

    /** Ensures the real GraphQL listener explicitly selects the modern subprotocol and answers protocol ping. */
    @Test
    fun `graphql websocket negotiates modern protocol and answers ping`() = runBlocking {
        val selectedProtocol = CompletableDeferred<String?>()
        val receivedMessages = CompletableDeferred<List<String>>()
        val client = ReactorNettyWebSocketClient(
            HttpClient.create(),
            { WebsocketClientSpec.builder().protocols("graphql-transport-ws") },
        )

        withContext(Dispatchers.IO) {
            withTimeout(5.seconds) {
                client.execute(URI.create("ws://127.0.0.1:$httpPort/lab/v1/graphql/ws")) { session ->
                    selectedProtocol.complete(session.handshakeInfo.subProtocol)
                    val outbound = session.send(
                        reactor.core.publisher.Flux.just(
                            session.textMessage("""{"type":"connection_init"}"""),
                            session.textMessage("""{"type":"ping","payload":{"probe":"knet"}}"""),
                        ),
                    )
                    val inbound = session.receive()
                        .take(2)
                        .map { message -> message.payloadAsText }
                        .collectList()
                        .doOnNext(receivedMessages::complete)
                        .then()
                    Mono.`when`(outbound, inbound).then(session.close())
                }.awaitSingleOrNull()
            }
        }

        assertEquals("graphql-transport-ws", selectedProtocol.await())
        val messages = receivedMessages.await()
        assertTrue(messages.any { message -> message.contains("connection_ack") })
        assertTrue(messages.any { message -> message.contains("pong") })
    }

    /** Ensures cancellation of one multiplexed operation does not terminate a concurrent sibling. */
    @Test
    fun `graphql websocket isolates concurrent subscriptions and cancellation`() = runBlocking {
        val graphQlClient = WebSocketGraphQlClient.builder(
            "ws://127.0.0.1:$httpPort/lab/v1/graphql/ws",
            ReactorNettyWebSocketClient(),
        ).build()
        val document = """
            subscription NamedTicker(${'$'}message: String!, ${'$'}count: Int!, ${'$'}delay: Int!) {
              ticker(message: ${'$'}message, count: ${'$'}count, delayMillis: ${'$'}delay) { sequence message }
            }
        """.trimIndent()

        try {
            val (cancelled, sibling) = withContext(Dispatchers.IO) {
                withTimeout(5.seconds) {
                    coroutineScope {
                        val cancelledOperation = async {
                            graphQlClient.document(document)
                                .operationName("NamedTicker")
                                .variable("message", "cancelled")
                                .variable("count", 100)
                                .variable("delay", 5)
                                .retrieveSubscription("ticker.message")
                                .toEntity(String::class.java)
                                .take(1)
                                .collectList()
                                .awaitSingle()
                        }
                        val siblingOperation = async {
                            graphQlClient.document(document)
                                .operationName("NamedTicker")
                                .variable("message", "sibling")
                                .variable("count", 3)
                                .variable("delay", 0)
                                .retrieveSubscription("ticker.message")
                                .toEntity(String::class.java)
                                .collectList()
                                .awaitSingle()
                        }
                        cancelledOperation.await() to siblingOperation.await()
                    }
                }
            }

            assertEquals(listOf("cancelled-1"), cancelled)
            assertEquals(listOf("sibling-1", "sibling-2", "sibling-3"), sibling)
        } finally {
            graphQlClient.stop().awaitSingleOrNull()
        }
    }

    /** Ensures stream failures become stable GraphQL error envelopes after any emitted data prefix. */
    @Test
    fun `graphql websocket exposes typed subscription errors`() = runBlocking {
        val graphQlClient = WebSocketGraphQlClient.builder(
            "ws://127.0.0.1:$httpPort/lab/v1/graphql/ws",
            ReactorNettyWebSocketClient(),
        ).build()

        try {
            val failure = runCatching {
                withContext(Dispatchers.IO) {
                    withTimeout(5.seconds) {
                        graphQlClient.document(
                            """
                            subscription FailingTicker {
                              failingTicker(message: "typed", countBeforeError: 1) { sequence message }
                            }
                            """.trimIndent(),
                        )
                            .operationName("FailingTicker")
                            .executeSubscription()
                            .collectList()
                            .awaitSingle()
                    }
                }
            }.exceptionOrNull()
            val subscriptionFailure = failure as? SubscriptionErrorException

            assertTrue(subscriptionFailure != null)
            assertEquals(1, subscriptionFailure?.errors?.size)
            assertEquals(
                org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR,
                subscriptionFailure?.errors?.single()?.errorType,
            )
        } finally {
            graphQlClient.stop().awaitSingleOrNull()
        }
    }

    /** Ensures raw WebSocket text frames traverse a real upgrade and full-duplex session. */
    @Test
    fun `websocket endpoint echoes a text frame`() = runBlocking {
        val echoedText = CompletableDeferred<String>()
        val client = ReactorNettyWebSocketClient()
        withContext(Dispatchers.IO) {
            withTimeout(5.seconds) {
                client.execute(URI.create("ws://127.0.0.1:$httpPort/lab/v1/websocket/echo")) { session ->
                    session.send(Mono.just(session.textMessage("websocket-through-knet")))
                        .then(
                            session.receive()
                                .next()
                                .doOnNext { message -> echoedText.complete(message.payloadAsText) }
                                .then(),
                        )
                }.awaitSingleOrNull()
            }
        }
        assertEquals("websocket-through-knet", echoedText.await())
    }

    /** Ensures native gRPC unary, streaming, status, and trailer behavior uses the bound listener. */
    @Test
    fun `grpc endpoint supports messages streams status and trailers`() {
        val channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpcServer.boundPort)
            .usePlaintext()
            .build()
            .also(channels::add)
        val stub = ProtocolLabGrpc.newBlockingStub(channel)

        val unary = stub.unaryEcho(EchoRequest.newBuilder().setMessage("grpc-through-knet").build())
        assertEquals("grpc-through-knet", unary.message)
        assertEquals(1, unary.sequence)

        val stream = stub.serverStream(
            StreamRequest.newBuilder().setMessage("stream").setCount(3).build(),
        ).asSequence().toList()
        assertEquals(listOf(1, 2, 3), stream.map { reply -> reply.sequence })

        val failure = assertThrows<StatusRuntimeException> {
            stub.fail(FailureRequest.newBuilder().setDescription("expected failure").build())
        }
        assertEquals(Status.Code.INVALID_ARGUMENT, failure.status.code)
        assertTrue(failure.trailers?.get(TEST_TRAILER_KEY) == "protocol-lab-failure")
    }

    /** Ensures native client-streaming and bidirectional RPC cardinalities remain executable. */
    @Test
    fun `grpc endpoint supports client and bidirectional streams`() = runBlocking {
        val channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpcServer.boundPort)
            .usePlaintext()
            .build()
            .also(channels::add)
        val stub = ProtocolLabGrpc.newStub(channel)

        val clientStreamResult = CompletableDeferred<StreamSummary>()
        val clientRequests = stub.clientStream(deferredObserver(clientStreamResult))
        listOf("one", "two", "three").forEach { message ->
            clientRequests.onNext(EchoRequest.newBuilder().setMessage(message).build())
        }
        clientRequests.onCompleted()

        val bidirectionalReplies = mutableListOf<EchoReply>()
        val bidirectionalCompletion = CompletableDeferred<Unit>()
        val bidirectionalRequests = stub.bidirectionalEcho(
            object : StreamObserver<EchoReply> {
                override fun onNext(reply: EchoReply) {
                    bidirectionalReplies += reply
                }

                override fun onError(throwable: Throwable) {
                    bidirectionalCompletion.completeExceptionally(throwable)
                }

                override fun onCompleted() {
                    bidirectionalCompletion.complete(Unit)
                }
            },
        )
        listOf("alpha", "beta").forEach { message ->
            bidirectionalRequests.onNext(EchoRequest.newBuilder().setMessage(message).build())
        }
        bidirectionalRequests.onCompleted()

        withContext(Dispatchers.IO) {
            withTimeout(5.seconds) {
                val summary = clientStreamResult.await()
                bidirectionalCompletion.await()
                assertEquals(3, summary.receivedCount)
                assertEquals(listOf("one", "two", "three"), summary.messagesList)
                assertEquals(listOf("alpha", "beta"), bidirectionalReplies.map { reply -> reply.message })
                assertEquals(listOf(1, 2), bidirectionalReplies.map { reply -> reply.sequence })
            }
        }
    }

    /** Qualifies bounded large messages, gzip requests, deadlines, cancellation, and sibling-call isolation. */
    @Test
    fun `grpc endpoint supports transport pressure and lifecycle scenarios`() = runBlocking {
        val channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpcServer.boundPort)
            .usePlaintext()
            .build()
            .also(channels::add)
        val blockingStub = ProtocolLabGrpc.newBlockingStub(channel)

        val largeMessage = "k".repeat(512 * 1_024)
        val compressedReply = withContext(Dispatchers.IO) {
            blockingStub.withCompression("gzip")
                .unaryEcho(EchoRequest.newBuilder().setMessage(largeMessage).build())
        }
        assertEquals(largeMessage.length, compressedReply.message.length)

        val replies = coroutineScope {
            (1..20).map { sequence ->
                async(Dispatchers.IO) {
                    blockingStub.unaryEcho(
                        EchoRequest.newBuilder().setMessage("parallel-$sequence").build(),
                    )
                }
            }.awaitAll()
        }
        assertEquals((1..20).map { "parallel-$it" }, replies.map(EchoReply::getMessage))

        val deadlineFailure = CompletableDeferred<Throwable>()
        ProtocolLabGrpc.newStub(channel)
            .withDeadlineAfter(25, TimeUnit.MILLISECONDS)
            .clientStream(failureObserver(deadlineFailure))
        val deadlineStatus = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5.seconds) { Status.fromThrowable(deadlineFailure.await()) }
        }
        assertEquals(Status.Code.DEADLINE_EXCEEDED, deadlineStatus.code)

        val cancellationFailure = CompletableDeferred<Throwable>()
        val cancellationCall = ProtocolLabGrpc.newStub(channel).bidirectionalEcho(
            object : StreamObserver<EchoReply> {
                override fun onNext(value: EchoReply) = Unit

                override fun onError(throwable: Throwable) {
                    cancellationFailure.complete(throwable)
                }

                override fun onCompleted() = Unit
            },
        )
        (cancellationCall as ClientCallStreamObserver<EchoRequest>)
            .cancel("protocol-lab cancellation", null)
        val cancellationStatus = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5.seconds) { Status.fromThrowable(cancellationFailure.await()) }
        }
        assertEquals(Status.Code.CANCELLED, cancellationStatus.code)
    }

    private fun <Value> deferredObserver(result: CompletableDeferred<Value>): StreamObserver<Value> =
        object : StreamObserver<Value> {
            override fun onNext(value: Value) {
                result.complete(value)
            }

            override fun onError(throwable: Throwable) {
                result.completeExceptionally(throwable)
            }

            override fun onCompleted() = Unit
        }

    private fun <Value> failureObserver(result: CompletableDeferred<Throwable>): StreamObserver<Value> =
        object : StreamObserver<Value> {
            override fun onNext(value: Value) = Unit

            override fun onError(throwable: Throwable) {
                result.complete(throwable)
            }

            override fun onCompleted() {
                result.complete(AssertionError("Expected the gRPC call to fail."))
            }
        }

    private companion object {
        val TEST_TRAILER_KEY: Metadata.Key<String> = Metadata.Key.of(
            "knet-test-trailer",
            Metadata.ASCII_STRING_MARSHALLER,
        )
    }
}
