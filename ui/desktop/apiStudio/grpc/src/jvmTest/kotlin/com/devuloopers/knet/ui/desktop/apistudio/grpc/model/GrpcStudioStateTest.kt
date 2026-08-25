package com.devuloopers.knet.ui.desktop.apistudio.grpc.model

import com.devuloopers.knet.application.port.apistudio.ApiStudioOperationShape
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolOperation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrpcStudioStateTest {
    @Test
    fun `new studio state waits for an explicit target`() {
        val state = GrpcStudioState(documentId = "draft")

        assertTrue(state.targetHost.isEmpty())
        assertTrue(state.targetPort.isEmpty())
        assertTrue(state.deadlineMillis.isEmpty())
        assertTrue(state.selectedOutboundMessage.isEmpty())
        assertFalse(state.hasValidTarget)
    }

    @Test
    fun `untouched metadata rows are omitted but partially authored rows remain validatable`() {
        val state = GrpcStudioState(
            documentId = "draft",
            metadata = listOf(
                ApiStudioProtocolMetadataEntry("", ""),
                ApiStudioProtocolMetadataEntry("authorization", "token"),
                ApiStudioProtocolMetadataEntry("", "orphan-value"),
            ),
        )

        assertTrue(state.authoredMetadata.none { it.name.isBlank() && it.value.isBlank() })
        assertTrue(state.authoredMetadata.any { it.name == "authorization" })
        assertTrue(state.authoredMetadata.any { it.value == "orphan-value" })
    }

    @Test
    fun `invoke requires an operation and valid target fields`() {
        val operation = ApiStudioProtocolOperation(
            id = "example.Service/Call",
            displayName = "example.Service / Call",
            requestType = "example.Request",
            responseType = "example.Response",
            shape = ApiStudioOperationShape.UNARY,
        )

        assertFalse(GrpcStudioState(documentId = "draft").canInvoke)
        assertTrue(
            GrpcStudioState(
                documentId = "draft",
                targetHost = "localhost",
                targetPort = "9090",
                selectedOperation = operation,
                outboundMessages = listOf("{}"),
            ).canInvoke,
        )
        assertFalse(validState(operation).copy(targetHost = " ").canInvoke)
        assertFalse(validState(operation).copy(targetPort = "0").canInvoke)
        assertFalse(validState(operation).copy(targetPort = "65536").canInvoke)
        assertFalse(validState(operation).copy(deadlineMillis = "0").canInvoke)
        assertFalse(
            validState(operation).copy(deadlineMillis = "3600001").canInvoke,
        )
    }

    private fun validState(operation: ApiStudioProtocolOperation) = GrpcStudioState(
        documentId = "draft",
        targetHost = "localhost",
        targetPort = "9090",
        selectedOperation = operation,
        outboundMessages = listOf("{}"),
    )
}
