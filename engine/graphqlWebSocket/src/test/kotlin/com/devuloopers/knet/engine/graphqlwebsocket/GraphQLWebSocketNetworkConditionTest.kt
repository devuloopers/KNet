package com.devuloopers.knet.engine.graphqlwebsocket

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionBody
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionMessageInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.application.usecase.networkconditions.NetworkConditionSemanticResolver
import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionTarget
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.engine.graphqlwebsocket.networkconditions.GraphQLWebSocketNetworkConditionExtension
import com.devuloopers.knet.engine.graphqlwebsocket.networkconditions.GraphQLWebSocketNetworkConditionProtocol
import com.devuloopers.knet.engine.graphqlwebsocket.networkconditions.GraphQLWebSocketNetworkConditionTransformerFactory
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GRAPHQL_TRANSPORT_WS_SUBPROTOCOL
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformResult
import com.devuloopers.knet.engine.simulator.NetworkConditionEngine
import com.devuloopers.knet.engine.websocket.WebSocketFrameDecoder
import com.devuloopers.knet.engine.websocket.WebSocketOpcode
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
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
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphQLWebSocketNetworkConditionTest {
    private val extension = GraphQLWebSocketNetworkConditionExtension(GraphQLWebSocketEnvelopeParser())
    private val registry = NetworkConditionProtocolRegistry(listOf(extension))

    @Test
    fun `server response inherits the subscribed operation identity until completion`() {
        val criteria = serverNextCriteria("LiveFeed")
        val compiled = assertNotNull(registry.compile(criteria))

        registry.inspectMessage(criteria.protocolId, messageInput(
            direction = TrafficDirection.CLIENT_TO_SERVER,
            sequence = 1L,
            payload = subscribe("one", "LiveFeed"),
        ))
        val correlated = registry.inspectMessage(criteria.protocolId, messageInput(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            sequence = 2L,
            payload = next("one"),
        ))
        assertTrue(registry.matches(compiled, correlated))

        registry.inspectMessage(criteria.protocolId, messageInput(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            sequence = 3L,
            payload = """{"id":"one","type":"complete"}""",
        ))
        val afterCompletion = registry.inspectMessage(criteria.protocolId, messageInput(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            sequence = 4L,
            payload = next("one"),
        ))
        assertFalse(registry.matches(compiled, afterCompletion))
    }

    @Test
    fun `quick add recognizes only a valid modern GraphQL WebSocket handshake`() {
        val suggestion = assertNotNull(registry.suggestCriteria(NetworkConditionHttpInspectionInput(
            request = request(),
            requestBody = NetworkConditionBody(ByteArray(0)),
            requestBodyComplete = true,
        )))

        assertEquals(NetworkConditionProtocolId.GRAPHQL_WEBSOCKET, suggestion.protocolId)
        val invalidRequest = request().let { snapshot ->
            snapshot.copy(
                head = snapshot.head.copy(
                    headers = snapshot.head.headers.filterNot { header ->
                        header.name.value.equals("sec-websocket-version", ignoreCase = true)
                    },
                ),
            )
        }
        assertNull(registry.suggestCriteria(NetworkConditionHttpInspectionInput(
            request = invalidRequest,
            requestBody = NetworkConditionBody(ByteArray(0)),
            requestBodyComplete = true,
        )))
    }

    @Test
    fun `runtime drops only the matching GraphQL WebSocket operation`() {
        val criteria = serverNextCriteria("LiveFeed")
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(NetworkConditionRule(
                id = NetworkConditionRuleId("live-feed-offline"),
                target = NetworkConditionTarget.parse("api.example.com", 443),
                profileId = NetworkConditionBuiltIns.OFFLINE.id,
                protocolCriteria = criteria,
            )),
        )
        val resolver = NetworkConditionSemanticResolver(registry)
        val transformer = assertNotNull(
            GraphQLWebSocketNetworkConditionTransformerFactory(
                engine = NetworkConditionEngine(configuration = { configuration }),
                semanticResolver = resolver,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ).create(request(), null, null),
        )
        transformer.onEstablished(switchingResponse(), 1L)

        val subscribeResult = transformer.transform(
            TrafficDirection.CLIENT_TO_SERVER,
            clientFrame(subscribe("one", "LiveFeed")),
            2L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)
        val matchingResult = transformer.transform(
            TrafficDirection.SERVER_TO_CLIENT,
            serverFrame(next("one")),
            3L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)

        assertIs<ProxyDuplexTransformResult.Forward>(subscribeResult)
        assertIs<ProxyDuplexTransformResult.DropConnection>(matchingResult)
        transformer.cancel(null)
    }

    @Test
    fun `runtime forwards a different operation on the same endpoint`() {
        val criteria = serverNextCriteria("LiveFeed")
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(NetworkConditionRule(
                id = NetworkConditionRuleId("live-feed-offline"),
                target = NetworkConditionTarget.parse("api.example.com", 443),
                profileId = NetworkConditionBuiltIns.OFFLINE.id,
                protocolCriteria = criteria,
            )),
        )
        val transformer = assertNotNull(
            GraphQLWebSocketNetworkConditionTransformerFactory(
                engine = NetworkConditionEngine(configuration = { configuration }),
                semanticResolver = NetworkConditionSemanticResolver(registry),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ).create(request(), null, null),
        )
        transformer.onEstablished(switchingResponse(), 1L)

        transformer.transform(
            TrafficDirection.CLIENT_TO_SERVER,
            clientFrame(subscribe("two", "OtherFeed")),
            2L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)
        val result = transformer.transform(
            TrafficDirection.SERVER_TO_CLIENT,
            serverFrame(next("two")),
            3L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)

        assertIs<ProxyDuplexTransformResult.Forward>(result)
        transformer.cancel(null)
    }

    @Test
    fun `live enable applies to an operation subscribed while conditions were bypassed`() {
        val criteria = serverNextCriteria("LiveFeed")
        var configuration = NetworkConditionConfiguration(
            enabled = false,
            rules = listOf(NetworkConditionRule(
                id = NetworkConditionRuleId("live-feed-offline"),
                target = NetworkConditionTarget.parse("api.example.com", 443),
                profileId = NetworkConditionBuiltIns.OFFLINE.id,
                protocolCriteria = criteria,
            )),
        )
        val transformer = assertNotNull(
            GraphQLWebSocketNetworkConditionTransformerFactory(
                engine = NetworkConditionEngine(configuration = { configuration }),
                semanticResolver = NetworkConditionSemanticResolver(registry),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ).create(request(), null, null),
        )
        transformer.onEstablished(switchingResponse(), 1L)
        val subscribed = transformer.transform(
            TrafficDirection.CLIENT_TO_SERVER,
            clientFrame(subscribe("one", "LiveFeed")),
            2L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)
        assertIs<ProxyDuplexTransformResult.Forward>(subscribed)

        configuration = configuration.copy(enabled = true)
        val result = transformer.transform(
            TrafficDirection.SERVER_TO_CLIENT,
            serverFrame(next("one")),
            3L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)

        assertIs<ProxyDuplexTransformResult.DropConnection>(result)
        transformer.cancel(null)
    }

    @Test
    fun `timeout remains pending until connection cancellation releases it`() {
        val timeout = NetworkConditionProfile(
            id = NetworkConditionProfileId("graphql-timeout"),
            name = "GraphQL timeout",
            failure = NetworkFailureBehavior.Timeout,
        )
        val criteria = serverNextCriteria("LiveFeed")
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            customProfiles = listOf(timeout),
            rules = listOf(NetworkConditionRule(
                id = NetworkConditionRuleId("live-feed-timeout"),
                target = NetworkConditionTarget.parse("api.example.com", 443),
                profileId = timeout.id,
                protocolCriteria = criteria,
            )),
        )
        val transformer = assertNotNull(
            GraphQLWebSocketNetworkConditionTransformerFactory(
                engine = NetworkConditionEngine(configuration = { configuration }),
                semanticResolver = NetworkConditionSemanticResolver(registry),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ).create(request(), null, null),
        )
        transformer.onEstablished(switchingResponse(), 1L)
        transformer.transform(
            TrafficDirection.CLIENT_TO_SERVER,
            clientFrame(subscribe("one", "LiveFeed")),
            2L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)

        val pending = transformer.transform(
            TrafficDirection.SERVER_TO_CLIENT,
            serverFrame(next("one")),
            3L,
        ).toCompletableFuture()
        assertFalse(pending.isDone)
        transformer.cancel(null)

        assertIs<ProxyDuplexTransformResult.DropConnection>(pending.get(1, TimeUnit.SECONDS))
    }

    @Test
    fun `compressed server messages are decoded before semantic matching`() {
        val criteria = serverNextCriteria("LiveFeed")
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            rules = listOf(NetworkConditionRule(
                id = NetworkConditionRuleId("live-feed-offline"),
                target = NetworkConditionTarget.parse("api.example.com", 443),
                profileId = NetworkConditionBuiltIns.OFFLINE.id,
                protocolCriteria = criteria,
            )),
        )
        val transformer = assertNotNull(
            GraphQLWebSocketNetworkConditionTransformerFactory(
                engine = NetworkConditionEngine(configuration = { configuration }),
                semanticResolver = NetworkConditionSemanticResolver(registry),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ).create(request(), null, null),
        )
        transformer.onEstablished(switchingResponse("permessage-deflate; server_no_context_takeover"), 1L)
        transformer.transform(
            TrafficDirection.CLIENT_TO_SERVER,
            clientFrame(subscribe("one", "LiveFeed")),
            2L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)
        val compressedNext = WebSocketFrameDecoder.encode(
            WebSocketOpcode.TEXT,
            deflate(next("one").encodeToByteArray()),
            compressed = true,
        )

        val result = transformer.transform(
            TrafficDirection.SERVER_TO_CLIENT,
            compressedNext,
            3L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)

        assertIs<ProxyDuplexTransformResult.DropConnection>(result)
        transformer.cancel(null)
    }

    @Test
    fun `cancelling a delayed message completes its future without waiting for the profile delay`() {
        val delayed = NetworkConditionProfile(
            id = NetworkConditionProfileId("graphql-delay"),
            name = "GraphQL delay",
            latencyMillis = 60_000L,
        )
        val criteria = serverNextCriteria("LiveFeed")
        val configuration = NetworkConditionConfiguration(
            enabled = true,
            customProfiles = listOf(delayed),
            rules = listOf(NetworkConditionRule(
                id = NetworkConditionRuleId("live-feed-delay"),
                target = NetworkConditionTarget.parse("api.example.com", 443),
                profileId = delayed.id,
                protocolCriteria = criteria,
            )),
        )
        val transformer = assertNotNull(
            GraphQLWebSocketNetworkConditionTransformerFactory(
                engine = NetworkConditionEngine(configuration = { configuration }),
                semanticResolver = NetworkConditionSemanticResolver(registry),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ).create(request(), null, null),
        )
        transformer.onEstablished(switchingResponse(), 1L)
        transformer.transform(
            TrafficDirection.CLIENT_TO_SERVER,
            clientFrame(subscribe("one", "LiveFeed")),
            2L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)
        val pending = transformer.transform(
            TrafficDirection.SERVER_TO_CLIENT,
            serverFrame(next("one")),
            3L,
        ).toCompletableFuture()
        assertFalse(pending.isDone)

        transformer.cancel(null)

        assertIs<ProxyDuplexTransformResult.DropConnection>(pending.get(1, TimeUnit.SECONDS))
    }

    private fun serverNextCriteria(operationName: String) = assertNotNull(
        extension.createCriteria(listOf(
            ProtocolCriteriaValue(GraphQLWebSocketNetworkConditionProtocol.directionFieldId, "server"),
            ProtocolCriteriaValue(GraphQLWebSocketNetworkConditionProtocol.messageTypeFieldId, "next"),
            ProtocolCriteriaValue(GraphQLWebSocketNetworkConditionProtocol.operationNameFieldId, operationName),
            ProtocolCriteriaValue(GraphQLWebSocketNetworkConditionProtocol.operationIdFieldId, ""),
        )),
    )

    private fun messageInput(
        direction: TrafficDirection,
        sequence: Long,
        payload: String,
    ) = NetworkConditionMessageInspectionInput(
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
        body = NetworkConditionBody(payload.encodeToByteArray()),
    )

    private fun request() = HttpRequestSnapshot(RequestHead(
        method = HttpMethod.GET,
        target = RequestTarget.Absolute(
            HttpScheme.fromToken("https"),
            Authority("api.example.com", 443),
            "/graphql",
        ),
        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
        headers = listOf(
            HeaderField(HeaderName("connection"), "Upgrade"),
            HeaderField(HeaderName("upgrade"), "websocket"),
            HeaderField(HeaderName("sec-websocket-version"), "13"),
            HeaderField(HeaderName("sec-websocket-key"), "MDEyMzQ1Njc4OWFiY2RlZg=="),
            HeaderField(HeaderName("sec-websocket-protocol"), GRAPHQL_TRANSPORT_WS_SUBPROTOCOL),
        ),
    ))

    private fun switchingResponse(extension: String? = null) = ResponseHead(
        status = HttpStatus(101),
        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
        headers = buildList {
            add(HeaderField(HeaderName("connection"), "Upgrade"))
            add(HeaderField(HeaderName("upgrade"), "websocket"))
            add(HeaderField(HeaderName("sec-websocket-protocol"), GRAPHQL_TRANSPORT_WS_SUBPROTOCOL))
            extension?.let { value ->
                add(HeaderField(HeaderName("sec-websocket-extensions"), value))
            }
        },
    )

    private fun clientFrame(payload: String): ByteArray = WebSocketFrameDecoder.encode(
        WebSocketOpcode.TEXT,
        payload.encodeToByteArray(),
        maskingKey = byteArrayOf(1, 2, 3, 4),
    )

    private fun serverFrame(payload: String): ByteArray = WebSocketFrameDecoder.encode(
        WebSocketOpcode.TEXT,
        payload.encodeToByteArray(),
    )

    private fun subscribe(id: String, operationName: String): String =
        """{"id":"$id","type":"subscribe","payload":{"query":"subscription $operationName { feed }","operationName":"$operationName"}}"""

    private fun next(id: String): String =
        """{"id":"$id","type":"next","payload":{"data":{"feed":"value"}}}"""

    private fun deflate(payload: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        return try {
            deflater.setInput(payload)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(256)
            do {
                val count = deflater.deflate(buffer, 0, buffer.size, Deflater.SYNC_FLUSH)
                output.write(buffer, 0, count)
            } while (!deflater.needsInput())
            val withTail = output.toByteArray()
            withTail.copyOf(withTail.size - 4)
        } finally {
            deflater.end()
        }
    }

    private companion object {
        val EXCHANGE_ID: ExchangeId = ExchangeId("graphql-condition-exchange")
    }
}
