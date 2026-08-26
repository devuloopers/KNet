package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.apistudio.ApiStudioOperationShape
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolAuthoredMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolAuthoring
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolDocument
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolDraft
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolOperation
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolSchemaImport
import com.devuloopers.knet.domain.request.descriptor.RequestKindId

/** Maps UI-neutral authoring inputs into the extension-owned native gRPC draft. */
class GrpcApiStudioAuthoringAdapter(
    private val descriptors: GrpcDescriptorRegistry,
    private val draftCodec: GrpcRequestDraftCodec,
) : ApiStudioProtocolAuthoring {
    override val kind: RequestKindId = RequestKindId.GRPC

    override fun importSchema(
        sourceId: String,
        bytes: ByteArray,
    ): Result<ApiStudioProtocolSchemaImport> = descriptors
        .importDescriptorSet(GrpcDescriptorSourceId(sourceId), bytes)
        .map { summary ->
            ApiStudioProtocolSchemaImport(
                sourceId = summary.sourceId.value,
                fileCount = summary.fileCount,
                operationCount = summary.methodCount,
            )
        }

    override fun operations(): List<ApiStudioProtocolOperation> = descriptors.methods().map { schema ->
        ApiStudioProtocolOperation(
            id = schema.identity.path,
            displayName = "${schema.identity.serviceName}/${schema.identity.methodName}",
            requestType = schema.requestType,
            responseType = schema.responseType,
            shape = schema.shape.toApplicationShape(),
        )
    }

    override fun createDocument(draft: ApiStudioProtocolDraft): Result<ApiStudioProtocolDocument> = runCatching {
        val operation = requireNotNull(descriptors.methods().firstOrNull { it.identity.path == draft.operationId }) {
            "gRPC method descriptor not found for ${draft.operationId}."
        }
        draftCodec.encode(
            id = draft.id,
            name = draft.name,
            draft = GrpcRequestDraft(
                targetHost = draft.targetHost,
                targetPort = draft.targetPort,
                useTls = draft.useTls,
                method = operation.identity,
                callShape = operation.shape,
                deadlineMillis = draft.deadlineMillis,
                metadata = draft.metadata.map { entry ->
                    GrpcMetadataEntry(entry.name, entry.value, entry.enabled)
                },
                outboundMessagesJson = draft.outboundMessages.map(ApiStudioProtocolAuthoredMessage::content),
                descriptorSourceId = draft.schemaSourceId,
            ),
        )
    }

    override fun readDocument(document: ApiStudioProtocolDocument): Result<ApiStudioProtocolDraft> =
        draftCodec.decode(document).map { draft ->
            ApiStudioProtocolDraft(
                id = document.id,
                name = document.name,
                targetHost = draft.targetHost,
                targetPort = draft.targetPort,
                useTls = draft.useTls,
                operationId = draft.method.path,
                deadlineMillis = draft.deadlineMillis,
                metadata = draft.metadata.map { entry ->
                    ApiStudioProtocolMetadataEntry(
                        name = entry.name,
                        value = entry.value,
                        enabled = entry.enabled,
                    )
                },
                outboundMessages = draft.outboundMessagesJson.map { message ->
                    ApiStudioProtocolAuthoredMessage(
                        content = message,
                        contentType = "application/json",
                    )
                },
                schemaSourceId = draft.descriptorSourceId,
            )
        }
}

private val GrpcMethodSchema.shape: GrpcCallShape
    get() = when {
        clientStreaming && serverStreaming -> GrpcCallShape.BIDIRECTIONAL_STREAMING
        clientStreaming -> GrpcCallShape.CLIENT_STREAMING
        serverStreaming -> GrpcCallShape.SERVER_STREAMING
        else -> GrpcCallShape.UNARY
    }

private fun GrpcCallShape.toApplicationShape(): ApiStudioOperationShape = when (this) {
    GrpcCallShape.UNARY -> ApiStudioOperationShape.UNARY
    GrpcCallShape.SERVER_STREAMING -> ApiStudioOperationShape.SERVER_STREAMING
    GrpcCallShape.CLIENT_STREAMING -> ApiStudioOperationShape.CLIENT_STREAMING
    GrpcCallShape.BIDIRECTIONAL_STREAMING -> ApiStudioOperationShape.BIDIRECTIONAL_STREAMING
}
