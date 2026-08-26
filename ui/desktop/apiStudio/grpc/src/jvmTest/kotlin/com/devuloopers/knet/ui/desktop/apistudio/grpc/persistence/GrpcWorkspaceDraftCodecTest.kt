package com.devuloopers.knet.ui.desktop.apistudio.grpc.persistence

import com.devuloopers.knet.application.contract.apistudio.ApiStudioDocumentLocation
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.ApiStudioOperationShape
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolOperation
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.grpc.model.GrpcStudioState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GrpcWorkspaceDraftCodecTest {
    private val codec = GrpcWorkspaceDraftCodec()

    @Test
    fun `incomplete authoring state round trips without execution validation`() {
        val state = GrpcStudioState(
            documentId = "grpc-draft",
            targetHost = "",
            targetPort = "",
            deadlineMillis = "",
            metadata = listOf(
                ApiStudioProtocolMetadataEntry("", ""),
                ApiStudioProtocolMetadataEntry("authorization", "Bearer token", enabled = false),
            ),
            outboundMessages = listOf("{}", "{\"name\":\"KNet\"}"),
            selectedOutboundIndex = 1,
        )

        val restored = codec.decode(document(payload = codec.encode(state)))

        assertEquals("", restored.targetHost)
        assertEquals("", restored.targetPort)
        assertEquals("", restored.deadlineMillis)
        assertEquals(state.metadata, restored.metadata)
        assertEquals(state.outboundMessages, restored.outboundMessages)
        assertEquals(1, restored.selectedOutboundIndex)
    }

    @Test
    fun `content derives sidebar identity from selected operation`() {
        val operation = ApiStudioProtocolOperation(
            id = "example.Echo/Unary",
            displayName = "example.Echo / Unary",
            requestType = "example.EchoRequest",
            responseType = "example.EchoResponse",
            shape = ApiStudioOperationShape.UNARY,
        )

        val content = codec.content(
            GrpcStudioState(documentId = "grpc-draft", selectedOperation = operation),
        )

        assertEquals(RequestKindId.GRPC, content.requestKind)
        assertEquals("example.Echo / Unary", content.suggestedName)
        assertEquals("gRPC", content.badgeLabel)
    }

    @Test
    fun `unknown payload version fails explicitly`() {
        assertFailsWith<IllegalArgumentException> {
            codec.decode(document(payloadVersion = 2))
        }
    }

    private fun document(
        payload: ByteArray = byteArrayOf(),
        payloadVersion: Int = GrpcWorkspaceDraftCodec.PAYLOAD_VERSION,
    ) = ApiStudioWorkspaceDocument(
        id = "grpc-draft",
        editorId = ApiStudioEditorId.GRPC,
        requestKind = RequestKindId.GRPC,
        name = GrpcWorkspaceDraftCodec.DEFAULT_DOCUMENT_NAME,
        nameOrigin = RequestNameOrigin.GENERATED,
        badgeLabel = GrpcWorkspaceDraftCodec.BADGE_LABEL,
        payloadVersion = payloadVersion,
        payload = payload,
        location = ApiStudioDocumentLocation.Unsaved,
    )
}
