package com.devuloopers.knet.engine.graphqlwebsocket.apistudio

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolDocument
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.websocket.WebSocketHandshakeHeader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Validated API Studio request for one modern GraphQL WebSocket operation. */
data class GraphQLWebSocketRequestDraft(
    /** Absolute `ws` or `wss` GraphQL endpoint. */
    val url: String,
    /** Optional caller-authored handshake headers. */
    val headers: List<WebSocketHandshakeHeader> = emptyList(),
    /** WebSocket connection deadline in milliseconds. */
    val connectTimeoutMillis: Long = 30_000L,
    /** `connection_ack` deadline after connection initialization. */
    val acknowledgementTimeoutMillis: Long = 10_000L,
    /** Optional JSON object sent as `connection_init.payload`. */
    val connectionParametersJson: String = "",
    /** GraphQL query, mutation, or subscription document. */
    val query: String,
    /** Optional explicit operation name. */
    val operationName: String = "",
    /** Optional variables JSON object. */
    val variablesJson: String = "",
    /** Optional extensions JSON object. */
    val extensionsJson: String = "",
    /** Stable operation ID used for multiplexed messages. */
    val operationId: String,
) {
    init {
        require(url.startsWith("ws://") || url.startsWith("wss://")) {
            "GraphQL WebSocket URL must use ws or wss."
        }
        require(connectTimeoutMillis in 1L..3_600_000L) { "GraphQL WebSocket connect timeout is invalid." }
        require(acknowledgementTimeoutMillis in 1L..3_600_000L) {
            "GraphQL WebSocket acknowledgement timeout is invalid."
        }
        require(query.isNotBlank()) { "Enter a GraphQL operation document." }
        require(operationName.isBlank() || GRAPHQL_NAME.matches(operationName)) {
            "GraphQL operation name is invalid."
        }
        require(operationId.isNotBlank() && operationId.length <= MAXIMUM_OPERATION_ID_CHARACTERS) {
            "GraphQL WebSocket operation ID is invalid."
        }
    }

    private companion object {
        const val MAXIMUM_OPERATION_ID_CHARACTERS: Int = 256
        val GRAPHQL_NAME: Regex = Regex("^[_A-Za-z][_0-9A-Za-z]*$")
    }
}

/** Strict versioned codec for GraphQL WebSocket execution documents. */
class GraphQLWebSocketRequestDraftCodec(
    private val json: Json = Json { ignoreUnknownKeys = false },
    private val envelopeParser: GraphQLWebSocketEnvelopeParser,
) {
    /** Encodes one validated and semantically parseable execution draft. */
    fun encode(id: String, name: String, draft: GraphQLWebSocketRequestDraft): ApiStudioProtocolDocument {
        validateJsonObject(draft.connectionParametersJson, "Connection parameters")
        validateJsonObject(draft.variablesJson, "Variables")
        validateJsonObject(draft.extensionsJson, "Extensions")
        require(envelopeParser.parse(draft.subscribeEnvelope().toString().encodeToByteArray()) != null) {
            "The GraphQL operation document is invalid."
        }
        val root = buildJsonObject {
            put("url", draft.url)
            put("connectTimeoutMillis", draft.connectTimeoutMillis)
            put("acknowledgementTimeoutMillis", draft.acknowledgementTimeoutMillis)
            put("connectionParametersJson", draft.connectionParametersJson)
            put("query", draft.query)
            put("operationName", draft.operationName)
            put("variablesJson", draft.variablesJson)
            put("extensionsJson", draft.extensionsJson)
            put("operationId", draft.operationId)
            put("headers", buildJsonArray {
                draft.headers.forEach { header ->
                    add(buildJsonObject {
                        put("name", header.name)
                        put("value", header.value)
                        put("enabled", header.enabled)
                    })
                }
            })
        }
        return ApiStudioProtocolDocument(
            id = id,
            name = name,
            kind = RequestKindId.GRAPHQL_WEBSOCKET,
            schemaVersion = SCHEMA_VERSION,
            payload = root.toString().encodeToByteArray(),
        )
    }

    /** Decodes and validates one opaque GraphQL WebSocket execution document. */
    fun decode(document: ApiStudioProtocolDocument): Result<GraphQLWebSocketRequestDraft> = runCatching {
        require(document.kind == RequestKindId.GRAPHQL_WEBSOCKET) {
            "Document is not a GraphQL WebSocket draft."
        }
        require(document.schemaVersion == SCHEMA_VERSION) {
            "Unsupported GraphQL WebSocket draft schema version."
        }
        val root = json.parseToJsonElement(document.copyPayload().decodeToString()).jsonObject
        GraphQLWebSocketRequestDraft(
            url = root.requiredString("url"),
            connectTimeoutMillis = root.getValue("connectTimeoutMillis").jsonPrimitive.long,
            acknowledgementTimeoutMillis = root.getValue("acknowledgementTimeoutMillis").jsonPrimitive.long,
            connectionParametersJson = root.requiredString("connectionParametersJson"),
            query = root.requiredString("query"),
            operationName = root.requiredString("operationName"),
            variablesJson = root.requiredString("variablesJson"),
            extensionsJson = root.requiredString("extensionsJson"),
            operationId = root.requiredString("operationId"),
            headers = root.arrayOrEmpty("headers").map { element ->
                val header = element.jsonObject
                WebSocketHandshakeHeader(
                    name = header.requiredString("name"),
                    value = header.requiredString("value"),
                    enabled = header.getValue("enabled").jsonPrimitive.boolean,
                )
            },
        ).also { draft ->
            validateJsonObject(draft.connectionParametersJson, "Connection parameters")
            validateJsonObject(draft.variablesJson, "Variables")
            validateJsonObject(draft.extensionsJson, "Extensions")
            require(envelopeParser.parse(draft.subscribeEnvelope().toString().encodeToByteArray()) != null) {
                "The GraphQL operation document is invalid."
            }
        }
    }

    /** Builds the strict subscribe envelope used by both validation and execution. */
    fun GraphQLWebSocketRequestDraft.subscribeEnvelope(): JsonObject = buildJsonObject {
        put("id", operationId)
        put("type", "subscribe")
        put("payload", buildJsonObject {
            put("query", query)
            if (operationName.isNotBlank()) put("operationName", operationName)
            optionalObject(variablesJson)?.let { variables -> put("variables", variables) }
            optionalObject(extensionsJson)?.let { extensions -> put("extensions", extensions) }
        })
    }

    /** Builds the strict connection initialization envelope. */
    fun GraphQLWebSocketRequestDraft.connectionInitEnvelope(): JsonObject = buildJsonObject {
        put("type", "connection_init")
        optionalObject(connectionParametersJson)?.let { parameters -> put("payload", parameters) }
    }

    private fun validateJsonObject(value: String, label: String) {
        if (value.isBlank()) return
        require(optionalObject(value) != null) { "$label must be a JSON object." }
    }

    private fun optionalObject(value: String): JsonObject? = value.trim()
        .takeIf(String::isNotEmpty)
        ?.let { encoded -> runCatching { json.parseToJsonElement(encoded) as? JsonObject }.getOrNull() }

    private fun JsonObject.requiredString(name: String): String =
        requireNotNull(this[name]?.jsonPrimitive?.contentOrNull) { "Missing GraphQL WebSocket field '$name'." }

    private fun JsonObject.arrayOrEmpty(name: String): JsonArray = this[name]?.jsonArray ?: JsonArray(emptyList())

    companion object {
        /** Current GraphQL WebSocket execution-document schema version. */
        const val SCHEMA_VERSION: Int = 1
    }
}
