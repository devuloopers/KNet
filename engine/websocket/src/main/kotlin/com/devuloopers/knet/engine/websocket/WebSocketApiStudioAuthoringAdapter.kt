package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolAuthoredMessage
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolAuthoringPort
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolDocument
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolDraft
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolOperation
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSchemaImport
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import java.net.URI

/** Maps protocol-neutral API Studio authoring input into an engine-owned WebSocket draft. */
class WebSocketApiStudioAuthoringAdapter(
    private val draftCodec: WebSocketRequestDraftCodec,
) : ApiStudioProtocolAuthoringPort {
    override val kind: RequestKindId = RequestKindId.WEBSOCKET

    override fun importSchema(sourceId: String, bytes: ByteArray): Result<ApiStudioProtocolSchemaImport> =
        Result.failure(UnsupportedOperationException("WebSocket does not use an imported schema."))

    override fun operations(): List<ApiStudioProtocolOperation> = emptyList()

    override fun createDocument(draft: ApiStudioProtocolDraft): Result<ApiStudioProtocolDocument> = runCatching {
        val targetUri = requireNotNull(draft.targetUri?.trim()?.takeIf(String::isNotEmpty)) {
            "Enter a WebSocket URL."
        }
        draftCodec.encode(
            id = draft.id,
            name = draft.name,
            draft = WebSocketRequestDraft(
                url = targetUri,
                subprotocols = draft.requestedProtocols,
                headers = draft.metadata
                    .filterNot { entry -> entry.name.isBlank() && entry.value.isBlank() }
                    .map { entry -> WebSocketHandshakeHeader(entry.name.trim(), entry.value, entry.enabled) },
                connectTimeoutMillis = draft.deadlineMillis,
                outboundMessages = draft.outboundMessages.map(ApiStudioProtocolAuthoredMessage::toWebSocketMessage),
            ),
        )
    }

    override fun readDocument(document: ApiStudioProtocolDocument): Result<ApiStudioProtocolDraft> =
        draftCodec.decode(document).map { draft ->
            val uri = URI.create(draft.url)
            ApiStudioProtocolDraft(
                id = document.id,
                name = document.name,
                targetHost = uri.host.orEmpty(),
                targetPort = uri.port.takeIf { port -> port > 0 }
                    ?: if (uri.scheme.equals("wss", ignoreCase = true)) 443 else 80,
                useTls = uri.scheme.equals("wss", ignoreCase = true),
                operationId = "",
                deadlineMillis = draft.connectTimeoutMillis,
                metadata = draft.headers.map { header ->
                    ApiStudioProtocolMetadataEntry(header.name, header.value, header.enabled)
                },
                outboundMessages = draft.outboundMessages.map { message ->
                    ApiStudioProtocolAuthoredMessage(
                        content = message.content,
                        contentType = when (message.kind) {
                            WebSocketAuthoredMessageKind.TEXT -> TEXT_CONTENT_TYPE
                            WebSocketAuthoredMessageKind.BINARY_BASE64 -> BINARY_CONTENT_TYPE
                        },
                    )
                },
                schemaSourceId = null,
                targetUri = draft.url,
                requestedProtocols = draft.subprotocols,
            )
        }
}

private fun ApiStudioProtocolAuthoredMessage.toWebSocketMessage(): WebSocketAuthoredMessage {
    val mediaType = contentType.substringBefore(';').trim().lowercase()
    val kind = when (mediaType) {
        BINARY_CONTENT_TYPE -> WebSocketAuthoredMessageKind.BINARY_BASE64
        "text/plain" -> WebSocketAuthoredMessageKind.TEXT
        else -> throw IllegalArgumentException("Unsupported WebSocket message content type '$contentType'.")
    }
    return WebSocketAuthoredMessage(kind = kind, content = content)
}

private const val TEXT_CONTENT_TYPE: String = "text/plain; charset=UTF-8"
private const val BINARY_CONTENT_TYPE: String = "application/octet-stream"
