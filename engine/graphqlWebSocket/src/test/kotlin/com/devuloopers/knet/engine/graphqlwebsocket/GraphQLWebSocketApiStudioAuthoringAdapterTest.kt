package com.devuloopers.knet.engine.graphqlwebsocket

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolAuthoredMessage
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolDraft
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolParameter
import com.devuloopers.knet.engine.graphqlwebsocket.apistudio.GraphQLWebSocketApiStudioAuthoringAdapter
import com.devuloopers.knet.engine.graphqlwebsocket.apistudio.GraphQLWebSocketRequestDraftCodec
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import kotlin.test.Test
import kotlin.test.assertEquals

class GraphQLWebSocketApiStudioAuthoringAdapterTest {
    private val adapter = GraphQLWebSocketApiStudioAuthoringAdapter(
        codec = GraphQLWebSocketRequestDraftCodec(envelopeParser = GraphQLWebSocketEnvelopeParser()),
        operationIdFactory = { "generated-operation-id" },
    )

    @Test
    fun `blank operation id is generated only when execution document is created`() {
        val document = adapter.createDocument(draft(operationId = "")).getOrThrow()

        assertEquals("generated-operation-id", adapter.readDocument(document).getOrThrow().operationId)
    }

    @Test
    fun `authored operation id remains supported`() {
        val document = adapter.createDocument(draft(operationId = "authored-operation-id")).getOrThrow()

        assertEquals("authored-operation-id", adapter.readDocument(document).getOrThrow().operationId)
    }

    private fun draft(operationId: String): ApiStudioProtocolDraft = ApiStudioProtocolDraft(
        id = "graphql-websocket-request",
        name = "LivePrices",
        targetHost = "",
        targetPort = 0,
        useTls = true,
        operationId = operationId,
        deadlineMillis = 30_000L,
        metadata = emptyList(),
        outboundMessages = listOf(
            ApiStudioProtocolAuthoredMessage(
                content = "subscription LivePrices { ticker }",
                contentType = GraphQLWebSocketApiStudioAuthoringAdapter.GRAPHQL_DOCUMENT_CONTENT_TYPE,
            ),
        ),
        schemaSourceId = null,
        targetUri = "wss://example.test/graphql",
        parameters = listOf(
            ApiStudioProtocolParameter(
                GraphQLWebSocketApiStudioAuthoringAdapter.ACKNOWLEDGEMENT_TIMEOUT,
                "",
            ),
        ),
    )
}
