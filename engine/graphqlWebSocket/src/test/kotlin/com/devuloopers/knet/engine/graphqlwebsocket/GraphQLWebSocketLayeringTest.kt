package com.devuloopers.knet.engine.graphqlwebsocket

import com.devuloopers.knet.application.port.breakpoint.BreakpointBody
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageInspectionInput
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePayloadInput
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePresentationRegistry
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraphQLWebSocketLayeringTest {
    private val parser = GraphQLWebSocketEnvelopeParser()

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
