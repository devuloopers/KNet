package com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.persistence

import com.devuloopers.knet.application.contract.apistudio.CapturedApiStudioMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketAuthoringTab
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketStudioState
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphQLWebSocketWorkspaceDraftCodecTest {
    private val codec = GraphQLWebSocketWorkspaceDraftCodec()

    @Test
    fun `round trip preserves incomplete subscription authoring state`() {
        val state = GraphQLWebSocketStudioState(
            documentId = "subscription-one",
            url = "wss://example.test/graphql",
            connectTimeoutMillis = "25000",
            acknowledgementTimeoutMillis = "5000",
            headers = listOf(ApiStudioProtocolMetadataEntry("authorization", "Bearer hidden")),
            connectionParametersJson = """{"token":"hidden"}""",
            query = "subscription LivePrices { ticker }",
            operationName = "LivePrices",
            variablesJson = """{"symbol":"KNET"}""",
            extensionsJson = """{"client":"desktop"}""",
            operationId = "prices-one",
            selectedAuthoringTab = GraphQLWebSocketAuthoringTab.VARIABLES,
        )

        val restored = codec.decode(codec.unsavedDocument(state))

        assertEquals(state.copy(isDirty = false), restored)
        assertEquals("LivePrices", codec.content(state).suggestedName)
        assertEquals("GQL WS", codec.content(state).badgeLabel)
    }

    @Test
    fun `generated name falls back to document operation then endpoint path`() {
        val parsed = GraphQLWebSocketStudioState(
            documentId = "parsed",
            query = "subscription PriceUpdates { ticker }",
        )
        val endpoint = GraphQLWebSocketStudioState(
            documentId = "endpoint",
            url = "wss://example.test/subscriptions/live?token=hidden",
        )

        assertEquals("PriceUpdates", codec.content(parsed).suggestedName)
        assertEquals("/subscriptions/live", codec.content(endpoint).suggestedName)
    }

    @Test
    fun `blank editor contains no sample request values and remains connectable with operational defaults`() {
        val blank = GraphQLWebSocketStudioState(documentId = "")
        val connectable = blank.copy(
            url = "wss://example.test/graphql",
            query = "subscription LivePrices { ticker }",
        )

        assertEquals("", blank.operationId)
        assertEquals("", blank.connectTimeoutMillis)
        assertEquals("", blank.acknowledgementTimeoutMillis)
        assertTrue(connectable.canConnect)
    }

    @Test
    fun `round trip preserves blank optional and timeout values`() {
        val state = GraphQLWebSocketStudioState(documentId = "blank-values")

        val restored = codec.decode(codec.unsavedDocument(state))

        assertEquals(state.copy(isDirty = false), restored)
    }

    @Test
    fun `captured graphql websocket handshake imports native endpoint and headers`() {
        val document = codec.importedDocument(
            id = "captured-graphql-websocket",
            spec = NetworkRequestSpec(
                url = "http://example.test/graphql",
                headers = listOf(
                    "Connection" to "Upgrade",
                    "Upgrade" to "websocket",
                    "Sec-WebSocket-Protocol" to "graphql-transport-ws",
                    "Authorization" to "Bearer token",
                ),
            ),
            messages = listOf(
                capturedText("""{"type":"connection_init","payload":{"Authorization":"Bearer socket"}}"""),
                capturedText(
                    """{"id":"prices","type":"subscribe","payload":{"query":"subscription Prices { prices }","operationName":"Prices","variables":{"market":"IN"},"extensions":{"trace":true}}}""",
                ),
            ),
        )

        val restored = codec.decode(document)

        assertEquals("ws://example.test/graphql", restored.url)
        assertEquals(listOf(ApiStudioProtocolMetadataEntry("Authorization", "Bearer token")), restored.headers)
        assertEquals("{\"Authorization\":\"Bearer socket\"}", restored.connectionParametersJson)
        assertEquals("subscription Prices { prices }", restored.query)
        assertEquals("Prices", restored.operationName)
        assertEquals("{\"market\":\"IN\"}", restored.variablesJson)
        assertEquals("{\"trace\":true}", restored.extensionsJson)
        assertEquals("prices", restored.operationId)
    }

    private fun capturedText(payload: String) = CapturedApiStudioMessage(
        kind = ProtocolMessageKind.TEXT,
        direction = TrafficDirection.CLIENT_TO_SERVER,
        payload = payload.encodeToByteArray(),
    )
}
