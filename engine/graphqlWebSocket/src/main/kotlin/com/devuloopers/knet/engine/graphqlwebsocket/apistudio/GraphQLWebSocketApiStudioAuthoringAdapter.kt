package com.devuloopers.knet.engine.graphqlwebsocket.apistudio

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolAuthoredMessage
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolAuthoringPort
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolDocument
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolDraft
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolOperation
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolParameter
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSchemaImport
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.engine.websocket.WebSocketHandshakeHeader
import java.net.URI
import kotlin.uuid.Uuid

/** Maps generic API Studio authoring state to the GraphQL WebSocket engine draft. */
class GraphQLWebSocketApiStudioAuthoringAdapter(
    private val codec: GraphQLWebSocketRequestDraftCodec,
    private val operationIdFactory: () -> String = { Uuid.random().toString() },
) : ApiStudioProtocolAuthoringPort {
    override val kind: RequestKindId = RequestKindId.GRAPHQL_WEBSOCKET

    override fun importSchema(sourceId: String, bytes: ByteArray): Result<ApiStudioProtocolSchemaImport> =
        Result.failure(UnsupportedOperationException("GraphQL WebSocket schema import is not available."))

    override fun operations(): List<ApiStudioProtocolOperation> = emptyList()

    override fun createDocument(draft: ApiStudioProtocolDraft): Result<ApiStudioProtocolDocument> = runCatching {
        val parameters = draft.parameters.associate { parameter -> parameter.id to parameter.value }
        codec.encode(
            id = draft.id,
            name = draft.name,
            draft = GraphQLWebSocketRequestDraft(
                url = requireNotNull(draft.targetUri?.trim()?.takeIf(String::isNotEmpty)) {
                    "Enter a GraphQL WebSocket URL."
                },
                headers = draft.metadata
                    .filterNot { entry -> entry.name.isBlank() && entry.value.isBlank() }
                    .map { entry -> WebSocketHandshakeHeader(entry.name.trim(), entry.value, entry.enabled) },
                connectTimeoutMillis = draft.deadlineMillis,
                acknowledgementTimeoutMillis = parameters[ACKNOWLEDGEMENT_TIMEOUT]?.toLongOrNull()
                    ?: DEFAULT_ACKNOWLEDGEMENT_TIMEOUT_MILLIS,
                connectionParametersJson = parameters[CONNECTION_PARAMETERS].orEmpty(),
                query = draft.outboundMessages.firstOrNull { message ->
                    message.contentType == GRAPHQL_DOCUMENT_CONTENT_TYPE
                }?.content.orEmpty(),
                operationName = parameters[OPERATION_NAME].orEmpty(),
                variablesJson = parameters[VARIABLES].orEmpty(),
                extensionsJson = parameters[EXTENSIONS].orEmpty(),
                operationId = draft.operationId.trim().ifBlank(operationIdFactory),
            )
        )
    }

    override fun readDocument(document: ApiStudioProtocolDocument): Result<ApiStudioProtocolDraft> =
        codec.decode(document).map { draft ->
            val uri = URI.create(draft.url)
            ApiStudioProtocolDraft(
                id = document.id,
                name = document.name,
                targetHost = uri.host.orEmpty(),
                targetPort = uri.port.takeIf { port -> port > 0 }
                    ?: if (uri.scheme.equals("wss", ignoreCase = true)) 443 else 80,
                useTls = uri.scheme.equals("wss", ignoreCase = true),
                operationId = draft.operationId,
                deadlineMillis = draft.connectTimeoutMillis,
                metadata = draft.headers.map { header ->
                    ApiStudioProtocolMetadataEntry(header.name, header.value, header.enabled)
                },
                outboundMessages = listOf(
                    ApiStudioProtocolAuthoredMessage(draft.query, GRAPHQL_DOCUMENT_CONTENT_TYPE),
                ),
                schemaSourceId = null,
                targetUri = draft.url,
                requestedProtocols = emptyList(),
                parameters = listOf(
                    ApiStudioProtocolParameter(ACKNOWLEDGEMENT_TIMEOUT, draft.acknowledgementTimeoutMillis.toString()),
                    ApiStudioProtocolParameter(CONNECTION_PARAMETERS, draft.connectionParametersJson),
                    ApiStudioProtocolParameter(OPERATION_NAME, draft.operationName),
                    ApiStudioProtocolParameter(VARIABLES, draft.variablesJson),
                    ApiStudioProtocolParameter(EXTENSIONS, draft.extensionsJson),
                ),
            )
        }

    companion object {
        /** GraphQL document media type used inside the protocol-neutral authored message list. */
        const val GRAPHQL_DOCUMENT_CONTENT_TYPE: String = "application/graphql"

        /** Parameter identity for the acknowledgement deadline. */
        const val ACKNOWLEDGEMENT_TIMEOUT: String = "acknowledgement-timeout-millis"

        /** Parameter identity for optional connection initialization JSON. */
        const val CONNECTION_PARAMETERS: String = "connection-parameters-json"

        /** Parameter identity for an optional explicit GraphQL operation name. */
        const val OPERATION_NAME: String = "operation-name"

        /** Parameter identity for variables JSON. */
        const val VARIABLES: String = "variables-json"

        /** Parameter identity for extensions JSON. */
        const val EXTENSIONS: String = "extensions-json"

        /** Default acknowledgement deadline. */
        const val DEFAULT_ACKNOWLEDGEMENT_TIMEOUT_MILLIS: Long = 10_000L
    }
}
