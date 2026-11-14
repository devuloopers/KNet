package com.devuloopers.knet.engine.graphqlwebsocket

import com.devuloopers.knet.application.contract.breakpoint.BreakpointBody
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageInspectionInput
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadInput
import com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentationRegistry
import com.devuloopers.knet.engine.graphqlwebsocket.breakpoint.GraphQLWebSocketBreakpointExtension
import com.devuloopers.knet.engine.graphqlwebsocket.breakpoint.GraphQLWebSocketBreakpointLayer
import com.devuloopers.knet.engine.graphqlwebsocket.breakpoint.GraphQLWebSocketBreakpointProtocol
import com.devuloopers.knet.engine.graphqlwebsocket.inspection.GraphQLWebSocketProtocolMessageDecoder
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GRAPHQL_TRANSPORT_WS_SUBPROTOCOL
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.websocket.WebSocketProtocolMessageDecoder
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import com.devuloopers.knet.traffic.model.http.StandardHttpScheme
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointCandidate
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointGate
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformResult
import com.devuloopers.knet.engine.websocket.WebSocketBreakpointTransformerFactory
import com.devuloopers.knet.engine.websocket.WebSocketFrameDecoder
import com.devuloopers.knet.engine.websocket.WebSocketOpcode
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraphQLWebSocketLayeringTest {
    private val parser = GraphQLWebSocketEnvelopeParser()

    @Test
    fun `graphql websocket semantic route preserves admitted workspace and bypasses inactive sibling`() {
        val owner = PolicyWorkspace.Device(RegisteredDeviceId("owner"))
        val sibling = PolicyWorkspace.Device(RegisteredDeviceId("sibling"))
        val seen = mutableListOf<ProtocolMessageBreakpointCandidate>()
        val gate = object : ProtocolMessageBreakpointGate {
            override fun mayInterceptMessage(request: HttpRequestSnapshot, protocolRoute: List<BreakpointProtocolId>, direction: TrafficDirection): Boolean =
                error("Scoped factories must use authenticated workspace prefilters.")
            override fun mayInterceptMessageFor(workspace: PolicyWorkspace, request: HttpRequestSnapshot,
                protocolRoute: List<BreakpointProtocolId>, direction: TrafficDirection): Boolean =
                workspace == owner && GraphQLWebSocketBreakpointProtocol.id in protocolRoute
            override suspend fun interceptMessage(candidate: ProtocolMessageBreakpointCandidate): ProtocolMessageBreakpointDecision {
                seen += candidate
                return ProtocolMessageBreakpointDecision.ContinueUnchanged
            }
            override fun cancelProtocolMessages(exchangeId: ExchangeId) = Unit
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val factory = WebSocketBreakpointTransformerFactory(gate, scope, listOf(GraphQLWebSocketBreakpointLayer(parser)))
            assertNull(factory.createForWorkspace(sibling, request(), null, WorkspaceCapture()))
            val transformer = assertNotNull(factory.createForWorkspace(owner, request(), null, WorkspaceCapture()))
            transformer.onEstablished(responseHead(), 0L)
            val wire = WebSocketFrameDecoder.encode(WebSocketOpcode.TEXT, subscribe("one", "LivePrices").encodeToByteArray(), maskingKey=byteArrayOf(1,2,3,4))
            val result = transformer.transform(TrafficDirection.CLIENT_TO_SERVER, wire, 1L).toCompletableFuture().get(1, TimeUnit.SECONDS)
            assertContentEquals(wire, assertIs<ProxyDuplexTransformResult.Forward>(result).copyPayload())
            assertEquals(owner, seen.single().workspace)
            assertEquals(GraphQLWebSocketBreakpointProtocol.id, seen.single().primaryProtocolId)
            transformer.cancel(null)
        } finally { scope.cancel() }
    }



    @Test
    fun `semantic decoder runs before raw websocket and falls back for invalid envelopes`() {
        val registry = ProtocolMessagePresentationRegistry(
            listOf(GraphQLWebSocketProtocolMessageDecoder(parser), WebSocketProtocolMessageDecoder()),
        )
        val semantic = registry.decode(payloadInput(subscribe("one", "LivePrices")))
        val fallback = registry.decode(payloadInput("""{"custom":true}"""))

        assertEquals("GraphQL subscribe one - LivePrices", semantic?.title)
        assertEquals("SUBSCRIPTION", semantic?.schemaName)
        assertEquals("WebSocket text message", fallback?.title)
    }

    @Test
    fun `semantic layer requires negotiated protocol and valid complete text envelope`() {
        val layer = GraphQLWebSocketBreakpointLayer(parser)
        val payload = subscribe("one", "LivePrices").encodeToByteArray()

        assertTrue(layer.mayApply(request()))
        assertTrue(layer.applies(
            request(), GRAPHQL_TRANSPORT_WS_SUBPROTOCOL, ProtocolMessageKind.TEXT,
            TrafficDirection.CLIENT_TO_SERVER, payload,
        ))
        assertFalse(layer.applies(
            request(), "chat", ProtocolMessageKind.TEXT, TrafficDirection.CLIENT_TO_SERVER, payload,
        ))
        assertFalse(layer.applies(
            request(), GRAPHQL_TRANSPORT_WS_SUBPROTOCOL, ProtocolMessageKind.BINARY,
            TrafficDirection.CLIENT_TO_SERVER, payload,
        ))
    }

    @Test
    fun `breakpoint criteria isolate operation names sharing the same endpoint and validate replacement identity`() {
        val extension = GraphQLWebSocketBreakpointExtension(parser)
        val criteria = assertNotNull(extension.createCriteria(listOf(
            ProtocolCriteriaValue(GraphQLWebSocketBreakpointProtocol.directionFieldId, "server"),
            ProtocolCriteriaValue(GraphQLWebSocketBreakpointProtocol.messageTypeFieldId, "next"),
            ProtocolCriteriaValue(GraphQLWebSocketBreakpointProtocol.operationNameFieldId, "LivePrices"),
            ProtocolCriteriaValue(GraphQLWebSocketBreakpointProtocol.operationIdFieldId, "one"),
        )))
        val compiled = assertNotNull(extension.compile(criteria))

        extension.inspectMessage(messageInput(
            direction = TrafficDirection.CLIENT_TO_SERVER,
            payload = subscribe("one", "LivePrices"),
        ))
        extension.inspectMessage(messageInput(
            direction = TrafficDirection.CLIENT_TO_SERVER,
            payload = subscribe("two", "LiveNews"),
            sequence = 2L,
        ))
        val priceNext = messageInput(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            payload = """{"id":"one","type":"next","payload":{"data":{"price":42}}}""",
            sequence = 3L,
        )
        val newsNext = messageInput(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            payload = """{"id":"two","type":"next","payload":{"data":{"headline":"news"}}}""",
            sequence = 4L,
        )

        assertTrue(compiled.matches(extension.inspectMessage(priceNext)))
        assertFalse(compiled.matches(extension.inspectMessage(newsNext)))
        assertTrue(extension.validateMessageReplacement(
            priceNext,
            BreakpointBody("""{"id":"one","type":"next","payload":{"data":{"price":43}}}""".encodeToByteArray()),
        ))
        assertFalse(extension.validateMessageReplacement(
            priceNext,
            BreakpointBody("""{"id":"two","type":"next","payload":{"data":{"price":43}}}""".encodeToByteArray()),
        ))
        assertFalse(extension.validateMessageReplacement(
            priceNext,
            BreakpointBody("""{"id":"one","type":"complete"}""".encodeToByteArray()),
        ))
        assertFalse(extension.validateMessageReplacement(
            priceNext,
            BreakpointBody("not-json".encodeToByteArray()),
        ))
        assertFalse(extension.validateMessageReplacement(
            priceNext.copy(compressed = true, compressionEncoding = "permessage-deflate"),
            BreakpointBody("""{"id":"one","type":"next","payload":{"data":{"price":43}}}""".encodeToByteArray()),
        ))
    }

    @Test
    fun `selected next is correlated to its own multiplexed operation and newest reused ID`() {
        val decoder = GraphQLWebSocketProtocolMessageDecoder(parser)
        val next = payloadInput("""{"id":"one","type":"next","payload":{"data":{}}}""")
            .copy(message = message().copy(id = ProtocolMessageId("selected-next"), direction = TrafficDirection.SERVER_TO_CLIENT))
        fun entry(id: String, name: String, suffix: String) = com.devuloopers.knet.application.contract.traffic.ProtocolMessageContextEntry(
            message().copy(id = ProtocolMessageId(suffix)), subscribe(id, name).encodeToByteArray(), false)
        val result = decoder.decode(next.copy(context = listOf(entry("two", "News", "2"), entry("one", "Prices", "1"))))!!
        assertEquals("Prices", result.fields.first { it.label == "Operation" }.value)
        assertEquals("one", result.fields.first { it.label == "Operation ID" }.value)
        val reused = decoder.decode(next.copy(context = listOf(entry("one", "NewPrices", "3"), entry("one", "OldPrices", "1"))))!!
        assertEquals("NewPrices", reused.fields.first { it.label == "Operation" }.value)
        val lookup = decoder.contextQuery(next)!!
        assertEquals(next.message.id, lookup.beforeMessageId)
        assertEquals(next.message.exchangeId, lookup.exchangeId)
        assertEquals(null, lookup.direction)
        assertEquals(null, decoder.contextQuery(payloadInput(subscribe("one", "Prices"))))
    }

    @Test
    fun `correlation stops at complete missing truncated malformed or foreign history`() {
        val decoder = GraphQLWebSocketProtocolMessageDecoder(parser)
        val next = payloadInput("""{"id":"one","type":"next","payload":{"data":{}}}""")
            .copy(message = message().copy(id = ProtocolMessageId("next")))
        fun entry(payload: String?, truncated: Boolean = false, foreign: Boolean = false) =
            com.devuloopers.knet.application.contract.traffic.ProtocolMessageContextEntry(
                message().copy(id = ProtocolMessageId("prior"), exchangeId = if (foreign) ExchangeId("other") else EXCHANGE_ID),
                payload?.encodeToByteArray(), truncated)
        val good = entry(subscribe("one", "OldPrices"))
        for (gap in listOf(entry(null), entry(subscribe("one", "Prefix"), true), entry("broken"),
            entry(subscribe("one", "Foreign"), foreign = true), entry("""{"id":"one","type":"complete"}"""),
            entry("""{"id":"one","type":"error","payload":[]}"""))) {
            val result = decoder.decode(next.copy(context = listOf(gap, good)))!!
            assertFalse(result.fields.any { it.label == "Operation" }, result.toString())
            assertEquals("one", result.fields.first { it.label == "Operation ID" }.value)
        }
        assertEquals(null, decoder.decode(next.copy(truncated = true)))
        assertEquals(null, decoder.contextQuery(next.copy(truncated = true)))
        assertFalse(decoder.decode(next)!!.fields.any { it.label == "Operation" })
    }

    @Test
    fun `binary control payload uses safe fallback while close has captured code and reason`() {
        val decoder = WebSocketProtocolMessageDecoder()
        val input = payloadInput("")
        assertEquals(null, decoder.decode(input.copy(message = message().copy(kind = ProtocolMessageKind.PING),
            payload = byteArrayOf(0, -1, 5))))
        val close = decoder.decode(input.copy(message = message().copy(kind = ProtocolMessageKind.CLOSE),
            payload = byteArrayOf(3, -24) + "finished".encodeToByteArray()))!!
        assertEquals("1000", close.fields.first { it.label == "Close code" }.value)
        assertEquals("finished", close.fields.first { it.label == "Close reason" }.value)
    }

    private fun payloadInput(payload: String) = ProtocolMessagePayloadInput(
        parentExchange = exchange(),
        message = message(),
        payload = payload.encodeToByteArray(),
    )

    private fun messageInput(
        direction: TrafficDirection,
        payload: String,
        sequence: Long = 1L,
    ) = ProtocolMessageInspectionInput(
        exchangeId = EXCHANGE_ID,
        request = request(),
        messageId = ProtocolMessageId("message-$sequence"),
        kind = ProtocolMessageKind.TEXT,
        negotiatedSubprotocol = GRAPHQL_TRANSPORT_WS_SUBPROTOCOL,
        direction = direction,
        sequence = sequence,
        declaredBytes = payload.encodeToByteArray().size.toLong(),
        compressed = false,
        compressionEncoding = null,
        body = BreakpointBody(payload.encodeToByteArray()),
    )

    private fun exchange() = HttpExchangeSnapshot(
        id = EXCHANGE_ID,
        connectionId = CONNECTION_ID,
        request = request(),
        response = HttpResponseSnapshot(responseHead()),
        state = ExchangeState.COMPLETED,
        startedAtEpochMillis = 1L,
    )

    private fun request() = HttpRequestSnapshot(RequestHead(
        method = HttpMethod.GET,
        target = RequestTarget.Absolute(
            HttpScheme.Standard(StandardHttpScheme.HTTPS),
            Authority("example.test", 443),
            "/graphql",
        ),
        protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
        headers = listOf(
            HeaderField(HeaderName("connection"), "Upgrade"),
            HeaderField(HeaderName("upgrade"), "websocket"),
            HeaderField(HeaderName("sec-websocket-version"), "13"),
            HeaderField(HeaderName("sec-websocket-key"), "MDEyMzQ1Njc4OWFiY2RlZg=="),
            HeaderField(HeaderName("sec-websocket-protocol"), GRAPHQL_TRANSPORT_WS_SUBPROTOCOL),
        ),
    ))

    private fun responseHead() = ResponseHead(
        status = HttpStatus(101),
        protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
        headers = listOf(
            HeaderField(HeaderName("connection"), "Upgrade"),
            HeaderField(HeaderName("upgrade"), "websocket"),
            HeaderField(HeaderName("sec-websocket-protocol"), GRAPHQL_TRANSPORT_WS_SUBPROTOCOL),
        ),
    )

    private fun message() = ProtocolMessageSnapshot(
        id = ProtocolMessageId("message"),
        connectionId = CONNECTION_ID,
        exchangeId = EXCHANGE_ID,
        streamId = null,
        protocol = MessageProtocolId.WEBSOCKET,
        kind = ProtocolMessageKind.TEXT,
        direction = TrafficDirection.CLIENT_TO_SERVER,
        sequence = 1L,
        occurredAtEpochMillis = 2L,
        declaredBytes = 1L,
        observedBytes = 1L,
        compressed = false,
        compressionEncoding = null,
        body = MessageBodyRef.Empty,
        state = ProtocolMessageState.COMPLETE,
    )

    private fun subscribe(id: String, operationName: String): String =
        """{"id":"$id","type":"subscribe","payload":{"query":"subscription $operationName { ticker }","operationName":"$operationName"}}"""

    private companion object {
        val EXCHANGE_ID = ExchangeId("graphql-websocket-exchange")
        val CONNECTION_ID = ConnectionId("graphql-websocket-connection")
    }
}

/** Capture identity only; this test exercises framing and admission without retaining payloads. */
private class WorkspaceCapture : ProxyExchangeCapture {
    override val exchangeId = ExchangeId("workspace-graphql-ws")
    override fun tryReserveBody(direction: TrafficDirection, contentEncoding: ContentEncoding?, requestedBytes: Int): ProxyBodyReservation? = null
    override fun completeBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long) = Unit
    override fun cancelBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long, reason: TrafficTerminationReason) = Unit
    override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit
    override fun terminate(outcome: ExchangeTerminalOutcome, timings: ExchangeTimings, occurredAtEpochMillis: Long) = Unit
}
