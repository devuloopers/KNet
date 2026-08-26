package com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.persistence

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketAuthoringTab
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketStudioState
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
}
