package com.devuloopers.knet.testingserver

import com.devuloopers.knet.testingserver.grpc.GrpcServerLifecycle
import com.devuloopers.knet.testingserver.grpc.v1.EchoRequest
import com.devuloopers.knet.testingserver.grpc.v1.EchoReply
import com.devuloopers.knet.testingserver.grpc.v1.FailureRequest
import com.devuloopers.knet.testingserver.grpc.v1.ProtocolLabGrpc
import com.devuloopers.knet.testingserver.grpc.v1.StreamRequest
import com.devuloopers.knet.testingserver.grpc.v1.StreamSummary
import com.devuloopers.knet.testingserver.payload.NdjsonRecord
import com.devuloopers.knet.testingserver.stream.StreamEvent
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.graphql.client.WebSocketGraphQlClient
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Mono
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import java.net.URI
import kotlin.time.Duration.Companion.seconds

/** Verifies every advertised protocol family through a real bound local server. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["knet.testing-server.grpc.port=0"],
)
class ProtocolLabIntegrationTest {
    @Autowired
    private lateinit var webClient: WebTestClient

    @Autowired
    private lateinit var grpcServer: GrpcServerLifecycle

    @LocalServerPort
    private var httpPort: Int = 0

    private val channels = mutableListOf<io.grpc.ManagedChannel>()

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
            .jsonPath("$.capabilities[?(@.id == 'websocket')].maturity").isEqualTo("AVAILABLE")
            .jsonPath("$.capabilities[?(@.id == 'http3')].maturity").isEqualTo("PLANNED")
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
    fun `http endpoint negotiates h2c`() = runTest {
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
    fun `graphql websocket emits subscription events`() = runTest {
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

    /** Ensures raw WebSocket text frames traverse a real upgrade and full-duplex session. */
    @Test
    fun `websocket endpoint echoes a text frame`() = runTest {
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
    fun `grpc endpoint supports client and bidirectional streams`() = runTest {
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

    private companion object {
        val TEST_TRAILER_KEY: Metadata.Key<String> = Metadata.Key.of(
            "knet-test-trailer",
            Metadata.ASCII_STRING_MARSHALLER,
        )
    }
}
