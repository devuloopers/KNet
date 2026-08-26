package com.devuloopers.knet.ui.desktop.apistudio.websocket.persistence

import com.devuloopers.knet.application.contract.apistudio.ApiStudioDocumentLocation
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceContent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.websocket.model.WebSocketStudioState
import com.devuloopers.knet.ui.desktop.apistudio.websocket.model.WebSocketStudioMessageKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Versioned codec for incomplete WebSocket workspace authoring state. */
class WebSocketWorkspaceDraftCodec(
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    /** Encodes incomplete UI state without requiring a connectable URL. */
    fun encode(state: WebSocketStudioState): ByteArray = buildJsonObject {
        put("url", state.url)
        put("connectTimeoutMillis", state.connectTimeoutMillis)
        put("subprotocols", state.subprotocols)
        put("messageKind", state.messageKind.name)
        put("messageContent", state.messageContent)
        put("headers", buildJsonArray {
            state.headers.forEach { header ->
                add(buildJsonObject {
                    put("name", header.name)
                    put("value", header.value)
                    put("enabled", header.enabled)
                })
            }
        })
    }.toString().encodeToByteArray()

    /** Restores one incomplete workspace document. */
    fun decode(document: ApiStudioWorkspaceDocument): WebSocketStudioState {
        require(document.editorId == ApiStudioEditorId.WEBSOCKET) { "Document is not a WebSocket workspace draft." }
        require(document.payloadVersion == PAYLOAD_VERSION) {
            "Unsupported WebSocket workspace payload version ${document.payloadVersion}."
        }
        val root = json.parseToJsonElement(document.copyPayload().decodeToString()).jsonObject
        return WebSocketStudioState(
            documentId = document.id,
            url = root.stringOrEmpty("url"),
            connectTimeoutMillis = root.stringOrEmpty("connectTimeoutMillis"),
            subprotocols = root.stringOrEmpty("subprotocols"),
            messageKind = root.stringOrEmpty("messageKind")
                .takeIf(String::isNotBlank)
                ?.let(WebSocketStudioMessageKind::valueOf)
                ?: WebSocketStudioMessageKind.TEXT,
            messageContent = root.stringOrEmpty("messageContent"),
            headers = root.arrayOrEmpty("headers").map { element ->
                val header = element.jsonObject
                ApiStudioProtocolMetadataEntry(
                    name = header.stringOrEmpty("name"),
                    value = header.stringOrEmpty("value"),
                    enabled = header["enabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                )
            },
            isDirty = false,
        )
    }

    /** Builds common shell content while retaining protocol-specific payload ownership here. */
    fun content(state: WebSocketStudioState): ApiStudioWorkspaceContent = ApiStudioWorkspaceContent(
        editorId = ApiStudioEditorId.WEBSOCKET,
        requestKind = RequestKindId.WEBSOCKET,
        suggestedName = suggestedName(state.url),
        badgeLabel = BADGE_LABEL,
        payloadVersion = PAYLOAD_VERSION,
        payload = encode(state),
    )

    /** Creates the durable unsaved document produced by the first meaningful edit. */
    fun unsavedDocument(state: WebSocketStudioState): ApiStudioWorkspaceDocument {
        require(state.documentId.isNotBlank()) { "A WebSocket workspace document requires an ID." }
        val content = content(state)
        return ApiStudioWorkspaceDocument(
            id = state.documentId,
            editorId = content.editorId,
            requestKind = content.requestKind,
            name = content.suggestedName,
            nameOrigin = RequestNameOrigin.GENERATED,
            badgeLabel = content.badgeLabel,
            payloadVersion = content.payloadVersion,
            payload = content.copyPayload(),
            location = ApiStudioDocumentLocation.Unsaved,
        )
    }

    private fun suggestedName(url: String): String {
        val withoutScheme = url.trim().substringAfter("://", missingDelimiterValue = url.trim())
        val path = withoutScheme.substringAfter('/', missingDelimiterValue = "")
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
        return if (path.isBlank()) DEFAULT_DOCUMENT_NAME else "/$path"
    }

    companion object {
        /** Current incomplete workspace-payload version. */
        const val PAYLOAD_VERSION: Int = 1

        /** Generated name used until the endpoint provides a meaningful path. */
        const val DEFAULT_DOCUMENT_NAME: String = "Untitled WebSocket Request"

        /** Compact sidebar badge for a WebSocket workspace document. */
        const val BADGE_LABEL: String = "WS"
    }
}

private fun JsonObject.stringOrEmpty(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.arrayOrEmpty(name: String): JsonArray = this[name]?.jsonArray ?: JsonArray(emptyList())
