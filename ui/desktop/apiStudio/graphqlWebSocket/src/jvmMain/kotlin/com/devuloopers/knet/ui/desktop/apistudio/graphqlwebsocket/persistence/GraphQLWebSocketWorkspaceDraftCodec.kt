package com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.persistence

import com.devuloopers.knet.application.contract.apistudio.ApiStudioDocumentLocation
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.CapturedApiStudioMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceContent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketAuthoringTab
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketStudioState
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
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

/** Versioned persistence codec for incomplete GraphQL WebSocket authoring state. */
class GraphQLWebSocketWorkspaceDraftCodec(
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    /** Builds a GraphQL subscription draft from its captured `graphql-transport-ws` handshake. */
    fun importedDocument(
        id: String,
        spec: NetworkRequestSpec,
        messages: List<CapturedApiStudioMessage> = emptyList(),
    ): ApiStudioWorkspaceDocument {
        val recovered = recoverAuthoring(messages)
        return unsavedDocument(GraphQLWebSocketStudioState(
            documentId = id,
            url = spec.url.toWebSocketUrl(),
            headers = spec.headers
                .filterNot { (name, _) -> name.isGeneratedWebSocketHandshakeHeader() }
                .map { (name, value) -> ApiStudioProtocolMetadataEntry(name, value) },
            connectionParametersJson = recovered.connectionParametersJson,
            query = recovered.query,
            operationName = recovered.operationName,
            variablesJson = recovered.variablesJson,
            extensionsJson = recovered.extensionsJson,
            operationId = recovered.operationId,
            isDirty = false,
        ))
    }

    /** Recovers only valid client authoring envelopes; responses and malformed/truncated content are ignored. */
    private fun recoverAuthoring(messages: List<CapturedApiStudioMessage>): RecoveredAuthoring {
        val envelopes = messages.asSequence()
            .filter { message ->
                message.direction == TrafficDirection.CLIENT_TO_SERVER && message.kind == ProtocolMessageKind.TEXT
            }
            .mapNotNull { message ->
                runCatching {
                    json.parseToJsonElement(
                        message.copyPayload().decodeToString(throwOnInvalidSequence = true),
                    ) as? JsonObject
                }.getOrNull()
            }
            .toList()
        val connectionParameters = envelopes
            .firstOrNull { it.stringOrEmpty("type") == "connection_init" }
            ?.get("payload") as? JsonObject
        val subscribe = envelopes.firstOrNull { it.stringOrEmpty("type") == "subscribe" }
        val payload = subscribe?.get("payload") as? JsonObject
        return RecoveredAuthoring(
            connectionParametersJson = connectionParameters?.toString().orEmpty(),
            query = payload?.stringOrEmpty("query").orEmpty(),
            operationName = payload?.stringOrEmpty("operationName").orEmpty(),
            variablesJson = payload?.get("variables")?.toString().orEmpty(),
            extensionsJson = payload?.get("extensions")?.toString().orEmpty(),
            operationId = subscribe?.stringOrEmpty("id").orEmpty(),
        )
    }

    private data class RecoveredAuthoring(
        val connectionParametersJson: String = "",
        val query: String = "",
        val operationName: String = "",
        val variablesJson: String = "",
        val extensionsJson: String = "",
        val operationId: String = "",
    )

    /** Encodes one incomplete draft without requiring a connectable endpoint. */
    fun encode(state: GraphQLWebSocketStudioState): ByteArray = buildJsonObject {
        put("url", state.url)
        put("connectTimeoutMillis", state.connectTimeoutMillis)
        put("acknowledgementTimeoutMillis", state.acknowledgementTimeoutMillis)
        put("connectionParametersJson", state.connectionParametersJson)
        put("query", state.query)
        put("operationName", state.operationName)
        put("variablesJson", state.variablesJson)
        put("extensionsJson", state.extensionsJson)
        put("operationId", state.operationId)
        put("selectedAuthoringTab", state.selectedAuthoringTab.name)
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

    /** Restores one GraphQL WebSocket draft from the common workspace store. */
    fun decode(document: ApiStudioWorkspaceDocument): GraphQLWebSocketStudioState {
        require(document.editorId == ApiStudioEditorId.GRAPHQL_WEBSOCKET) {
            "Document is not a GraphQL WebSocket workspace draft."
        }
        require(document.payloadVersion == PAYLOAD_VERSION) {
            "Unsupported GraphQL WebSocket workspace payload version ${document.payloadVersion}."
        }
        val root = json.parseToJsonElement(document.copyPayload().decodeToString()).jsonObject
        return GraphQLWebSocketStudioState(
            documentId = document.id,
            url = root.stringOrEmpty("url"),
            connectTimeoutMillis = root.stringOrEmpty("connectTimeoutMillis"),
            acknowledgementTimeoutMillis = root.stringOrEmpty("acknowledgementTimeoutMillis"),
            connectionParametersJson = root.stringOrEmpty("connectionParametersJson"),
            query = root.stringOrEmpty("query"),
            operationName = root.stringOrEmpty("operationName"),
            variablesJson = root.stringOrEmpty("variablesJson"),
            extensionsJson = root.stringOrEmpty("extensionsJson"),
            operationId = root.stringOrEmpty("operationId"),
            selectedAuthoringTab = root.stringOrEmpty("selectedAuthoringTab")
                .takeIf(String::isNotBlank)
                ?.let(GraphQLWebSocketAuthoringTab::valueOf)
                ?: GraphQLWebSocketAuthoringTab.QUERY,
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

    /** Builds the protocol-neutral workspace metadata and opaque payload. */
    fun content(state: GraphQLWebSocketStudioState): ApiStudioWorkspaceContent = ApiStudioWorkspaceContent(
        editorId = ApiStudioEditorId.GRAPHQL_WEBSOCKET,
        requestKind = RequestKindId.GRAPHQL_WEBSOCKET,
        suggestedName = suggestedName(state),
        badgeLabel = BADGE_LABEL,
        payloadVersion = PAYLOAD_VERSION,
        payload = encode(state),
    )

    /** Materializes the first meaningful edit as a common unsaved workspace document. */
    fun unsavedDocument(state: GraphQLWebSocketStudioState): ApiStudioWorkspaceDocument {
        require(state.documentId.isNotBlank()) { "A GraphQL WebSocket workspace document requires an ID." }
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

    private fun suggestedName(state: GraphQLWebSocketStudioState): String {
        if (state.operationName.isNotBlank()) return state.operationName.trim()
        val match = OPERATION_NAME.find(state.query)?.groupValues?.getOrNull(1)
        if (!match.isNullOrBlank()) return match
        val endpoint = state.url.trim().substringAfter("://", state.url.trim())
            .substringAfter('/', "")
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
        return if (endpoint.isBlank()) DEFAULT_DOCUMENT_NAME else "/$endpoint"
    }

    companion object {
        /** Current workspace payload version. */
        const val PAYLOAD_VERSION: Int = 1

        /** Name used until an operation name or endpoint path is authored. */
        const val DEFAULT_DOCUMENT_NAME: String = "Untitled GraphQL Subscription"

        /** Compact sidebar badge for GraphQL WebSocket documents. */
        const val BADGE_LABEL: String = "GQL WS"

        private val OPERATION_NAME: Regex = Regex("\\b(?:subscription|query|mutation)\\s+([_A-Za-z][_0-9A-Za-z]*)")
    }
}

private fun JsonObject.stringOrEmpty(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.arrayOrEmpty(name: String): JsonArray = this[name]?.jsonArray ?: JsonArray(emptyList())

private fun String.toWebSocketUrl(): String = when {
    startsWith("https://", ignoreCase = true) -> "wss://${substring(8)}"
    startsWith("http://", ignoreCase = true) -> "ws://${substring(7)}"
    else -> this
}

private fun String.isGeneratedWebSocketHandshakeHeader(): Boolean =
    equals("connection", ignoreCase = true) ||
        equals("upgrade", ignoreCase = true) ||
        startsWith("sec-websocket-", ignoreCase = true)
