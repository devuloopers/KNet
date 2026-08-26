package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolAuthoredMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolDraft
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMetadataEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSocketApiStudioAuthoringAdapterTest {
    private val codec = WebSocketRequestDraftCodec()
    private val adapter = WebSocketApiStudioAuthoringAdapter(codec)

    @Test
    fun `protocol neutral authoring input creates strict websocket document`() {
        val document = adapter.createDocument(
            draft(
                outboundMessages = listOf(
                    ApiStudioProtocolAuthoredMessage("hello", "text/plain; charset=UTF-8"),
                    ApiStudioProtocolAuthoredMessage("AQID", "application/octet-stream"),
                ),
            ),
        ).getOrThrow()

        val decoded = codec.decode(document).getOrThrow()

        assertEquals("wss://example.test/chat", decoded.url)
        assertEquals(listOf("chat", "graphql-transport-ws"), decoded.subprotocols)
        assertEquals("authorization", decoded.headers.single().name)
        assertEquals(WebSocketAuthoredMessageKind.TEXT, decoded.outboundMessages[0].kind)
        assertEquals(WebSocketAuthoredMessageKind.BINARY_BASE64, decoded.outboundMessages[1].kind)
    }

    @Test
    fun `engine adapter rejects unsupported authored message content type`() {
        val result = adapter.createDocument(
            draft(
                outboundMessages = listOf(ApiStudioProtocolAuthoredMessage("{}", "application/json")),
            ),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `strict websocket document restores presentation safe input`() {
        val document = adapter.createDocument(draft()).getOrThrow()

        val restored = adapter.readDocument(document).getOrThrow()

        assertEquals("wss://example.test/chat", restored.targetUri)
        assertEquals("example.test", restored.targetHost)
        assertEquals(443, restored.targetPort)
        assertTrue(restored.useTls)
        assertEquals(listOf("chat", "graphql-transport-ws"), restored.requestedProtocols)
    }

    private fun draft(
        outboundMessages: List<ApiStudioProtocolAuthoredMessage> = emptyList(),
    ) = ApiStudioProtocolDraft(
        id = "websocket-request",
        name = "/chat",
        targetHost = "",
        targetPort = 0,
        useTls = true,
        operationId = "",
        deadlineMillis = 2_500L,
        metadata = listOf(ApiStudioProtocolMetadataEntry("authorization", "Bearer token")),
        outboundMessages = outboundMessages,
        schemaSourceId = null,
        targetUri = "wss://example.test/chat",
        requestedProtocols = listOf("chat", "graphql-transport-ws"),
    )
}
