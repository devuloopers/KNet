package com.devuloopers.knet.engine.graphqlwebsocket.protocol

import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLDocumentParser
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLOperation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Modern GraphQL-over-WebSocket application subprotocol supported by KNet. */
const val GRAPHQL_TRANSPORT_WS_SUBPROTOCOL: String = "graphql-transport-ws"

/** Closed modern `graphql-transport-ws` envelope types. */
enum class GraphQLWebSocketMessageType(val wireName: String) {
    CONNECTION_INIT("connection_init"),
    CONNECTION_ACK("connection_ack"),
    SUBSCRIBE("subscribe"),
    NEXT("next"),
    ERROR("error"),
    COMPLETE("complete"),
    PING("ping"),
    PONG("pong");

    companion object {
        /** Resolves a supported wire token without accepting legacy or unknown message types. */
        fun fromWireName(value: String): GraphQLWebSocketMessageType? = entries.firstOrNull { it.wireName == value }
    }
}

/** One strictly validated and bounded `graphql-transport-ws` JSON envelope. */
data class GraphQLWebSocketEnvelope(
    /** Protocol message category. */
    val type: GraphQLWebSocketMessageType,
    /** Multiplexed operation identity for operation-scoped messages. */
    val operationId: String?,
    /** Optional message payload retained as an immutable JSON tree. */
    val payload: JsonElement?,
    /** Original validated root used for deterministic formatted presentation. */
    val root: JsonObject,
)

/** Bounded strict parser for modern GraphQL-over-WebSocket messages. */
class GraphQLWebSocketEnvelopeParser(
    private val json: Json = Json { ignoreUnknownKeys = false },
    private val graphQLParser: GraphQLDocumentParser = GraphQLDocumentParser(),
) {
    /** Parses [bytes], returning null for malformed, legacy, unknown, or structurally invalid messages. */
    fun parse(bytes: ByteArray): GraphQLWebSocketEnvelope? {
        if (bytes.isEmpty() || bytes.size > MAXIMUM_ENVELOPE_BYTES) return null
        val text = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull() ?: return null
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        if (root.keys.any { key -> key !in ENVELOPE_FIELDS }) return null
        val typeToken = (root[TYPE] as? JsonPrimitive)?.contentOrNull ?: return null
        val type = GraphQLWebSocketMessageType.fromWireName(typeToken) ?: return null
        val operationId = when (val id = root[ID]) {
            null, JsonNull -> null
            else -> (id as? JsonPrimitive)?.contentOrNull
                ?.takeIf { value -> value.isNotBlank() && value.length <= MAXIMUM_OPERATION_ID_CHARACTERS }
                ?: return null
        }
        val payload = root[PAYLOAD]?.takeUnless { it is JsonNull }
        if (!validShape(type, operationId, payload)) return null
        return GraphQLWebSocketEnvelope(type, operationId, payload, root)
    }

    /** Returns the first valid GraphQL operation authored by a `subscribe` envelope. */
    fun operation(envelope: GraphQLWebSocketEnvelope): GraphQLOperation? {
        if (envelope.type != GraphQLWebSocketMessageType.SUBSCRIBE) return null
        return graphQLParser.parse(envelope.payload.toString().encodeToByteArray())?.operations?.singleOrNull()
    }

    /** Produces stable pretty JSON only after strict envelope validation. */
    fun formatted(envelope: GraphQLWebSocketEnvelope): String = PRETTY_JSON.encodeToString(
        JsonObject.serializer(),
        envelope.root,
    )

    private fun validShape(
        type: GraphQLWebSocketMessageType,
        operationId: String?,
        payload: JsonElement?,
    ): Boolean = when (type) {
        GraphQLWebSocketMessageType.CONNECTION_INIT,
        GraphQLWebSocketMessageType.CONNECTION_ACK,
        GraphQLWebSocketMessageType.PING,
        GraphQLWebSocketMessageType.PONG -> operationId == null && (payload == null || payload is JsonObject)

        GraphQLWebSocketMessageType.SUBSCRIBE ->
            operationId != null && payload is JsonObject && graphQLParser.parse(payload.toString().encodeToByteArray()) != null

        GraphQLWebSocketMessageType.NEXT -> operationId != null && payload is JsonObject
        GraphQLWebSocketMessageType.ERROR -> operationId != null && payload is JsonArray
        GraphQLWebSocketMessageType.COMPLETE -> operationId != null && payload == null
    }

    companion object {
        /** Maximum UTF-8 bytes parsed by the semantic layer for one envelope. */
        const val MAXIMUM_ENVELOPE_BYTES: Int = 1_048_576

        /** Maximum operation-ID characters retained by semantic state. */
        const val MAXIMUM_OPERATION_ID_CHARACTERS: Int = 256

        private const val ID: String = "id"
        private const val TYPE: String = "type"
        private const val PAYLOAD: String = "payload"
        private val ENVELOPE_FIELDS: Set<String> = setOf(ID, TYPE, PAYLOAD)
        private val PRETTY_JSON: Json = Json { prettyPrint = true }
    }
}
