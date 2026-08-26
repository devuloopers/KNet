package com.devuloopers.knet.ui.desktop.apistudio.websocket.persistence

import com.devuloopers.knet.application.contract.apistudio.ApiStudioDocumentLocation
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.websocket.model.WebSocketStudioState
import com.devuloopers.knet.ui.desktop.apistudio.websocket.model.WebSocketStudioMessageKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebSocketWorkspaceDraftCodecTest {
    private val codec = WebSocketWorkspaceDraftCodec()

    @Test
    fun `incomplete websocket authoring state round trips without execution validation`() {
        val state = WebSocketStudioState(
            documentId = "websocket-draft",
            url = "",
            connectTimeoutMillis = "",
            subprotocols = "graphql-transport-ws, chat",
            messageKind = WebSocketStudioMessageKind.BINARY_BASE64,
            messageContent = "AQID",
            headers = listOf(
                ApiStudioProtocolMetadataEntry("", ""),
                ApiStudioProtocolMetadataEntry("authorization", "Bearer token", enabled = false),
            ),
        )

        val restored = codec.decode(document(codec.encode(state)))

        assertEquals("", restored.url)
        assertEquals("", restored.connectTimeoutMillis)
        assertEquals(state.subprotocols, restored.subprotocols)
        assertEquals(state.messageKind, restored.messageKind)
        assertEquals(state.messageContent, restored.messageContent)
        assertEquals(state.headers, restored.headers)
    }

    @Test
    fun `new websocket editor keeps timeout blank while using an execution default`() {
        val blank = WebSocketStudioState(documentId = "")

        assertEquals("", blank.connectTimeoutMillis)
        assertTrue(blank.copy(url = "wss://example.test/socket").canConnect)
    }

    @Test
    fun `workspace content preserves websocket identity and generated name`() {
        val state = WebSocketStudioState(
            documentId = "websocket-draft",
            url = "wss://example.test/chat?room=knet",
            connectTimeoutMillis = "2500",
            subprotocols = "chat, chat, graphql-transport-ws",
            headers = listOf(ApiStudioProtocolMetadataEntry("authorization", "Bearer token")),
        )

        val content = codec.content(state)
        assertEquals(ApiStudioEditorId.WEBSOCKET, content.editorId)
        assertEquals(RequestKindId.WEBSOCKET, content.requestKind)
        assertEquals("/chat", content.suggestedName)
        assertEquals("WS", content.badgeLabel)
    }

    @Test
    fun `unknown workspace payload version fails explicitly`() {
        assertFailsWith<IllegalArgumentException> {
            codec.decode(document(byteArrayOf(), payloadVersion = 2))
        }
    }

    private fun document(
        payload: ByteArray,
        payloadVersion: Int = WebSocketWorkspaceDraftCodec.PAYLOAD_VERSION,
    ) = ApiStudioWorkspaceDocument(
        id = "websocket-draft",
        editorId = ApiStudioEditorId.WEBSOCKET,
        requestKind = RequestKindId.WEBSOCKET,
        name = WebSocketWorkspaceDraftCodec.DEFAULT_DOCUMENT_NAME,
        nameOrigin = RequestNameOrigin.GENERATED,
        badgeLabel = WebSocketWorkspaceDraftCodec.BADGE_LABEL,
        payloadVersion = payloadVersion,
        payload = payload,
        location = ApiStudioDocumentLocation.Unsaved,
    )
}
