package com.devuloopers.knet.engine.graphqlwebsocket.breakpoint

import com.devuloopers.knet.application.port.breakpoint.BreakpointBody
import com.devuloopers.knet.application.port.breakpoint.BreakpointInterceptionUnit
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolExtension
import com.devuloopers.knet.application.port.breakpoint.BreakpointRuleSuggestionInput
import com.devuloopers.knet.application.port.breakpoint.CompiledProtocolCriteria
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaOption
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.port.breakpoint.ProtocolInspectionInput
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageInspectionInput
import com.devuloopers.knet.application.port.breakpoint.ProtocolObservation
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GRAPHQL_TRANSPORT_WS_SUBPROTOCOL
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelope
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketEnvelopeParser
import com.devuloopers.knet.engine.graphqlwebsocket.protocol.GraphQLWebSocketMessageType
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLOperationType
import com.devuloopers.knet.engine.websocket.WebSocketProtocol
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Stable identities owned by modern GraphQL WebSocket breakpoint rules. */
object GraphQLWebSocketBreakpointProtocol {
    /** Persisted protocol identity. */
    val id: BreakpointProtocolId = BreakpointProtocolId("graphql-websocket")

    /** Direction editor field. */
    val directionFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("direction")

    /** Modern envelope-type editor field. */
    val messageTypeFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("message-type")

    /** Optional GraphQL operation-name editor field. */
    val operationNameFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("operation-name")

    /** Optional multiplexed operation-ID editor field. */
    val operationIdFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("operation-id")
}

/** Semantic message breakpoint extension for the modern `graphql-transport-ws` protocol. */
class GraphQLWebSocketBreakpointExtension(
    private val parser: GraphQLWebSocketEnvelopeParser,
    private val json: Json = Json { ignoreUnknownKeys = false },
) : BreakpointProtocolExtension {
    private val operationState = MutableStateFlow<Map<ExchangeId, Map<String, OperationFact>>>(emptyMap())

    override val suggestionPriority: Int = 400

    override val definition: BreakpointProtocolDefinition = BreakpointProtocolDefinition(
        protocolId = GraphQLWebSocketBreakpointProtocol.id,
        displayName = "GraphQL WebSocket",
        criteriaVersion = CRITERIA_VERSION,
        interceptionUnit = BreakpointInterceptionUnit.PROTOCOL_MESSAGE,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Choice(
                id = GraphQLWebSocketBreakpointProtocol.directionFieldId,
                label = "Message Direction",
                description = "Choose which GraphQL WebSocket direction may pause.",
                options = listOf(
                    ProtocolCriteriaOption(ANY, "Client and server messages"),
                    ProtocolCriteriaOption(CLIENT, "Client messages"),
                    ProtocolCriteriaOption(SERVER, "Server messages"),
                ),
                defaultValue = ANY,
            ),
            ProtocolCriteriaFieldDefinition.Choice(
                id = GraphQLWebSocketBreakpointProtocol.messageTypeFieldId,
                label = "Message Type",
                description = "Match one modern GraphQL WebSocket envelope type.",
                options = listOf(ProtocolCriteriaOption(ANY, "Every message type")) +
                    GraphQLWebSocketMessageType.entries.map { type ->
                        ProtocolCriteriaOption(type.wireName, type.wireName)
                    },
                defaultValue = ANY,
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = GraphQLWebSocketBreakpointProtocol.operationNameFieldId,
                label = "Operation Name",
                description = "Optional exact GraphQL operation name, correlated across the connection.",
                placeholder = "e.g. LiveQuotes",
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = GraphQLWebSocketBreakpointProtocol.operationIdFieldId,
                label = "Operation ID",
                description = "Optional exact multiplexed operation ID.",
                placeholder = "e.g. subscription-1",
            ),
        ),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? =
        criteria.takeIf { it.protocolId == GraphQLWebSocketBreakpointProtocol.id }
            ?.encodedPayload
            ?.let(::decode)
            ?.let(::CompiledCriteria)

    override fun inspect(input: ProtocolInspectionInput): ProtocolObservation? = null

    override fun inspectMessage(input: ProtocolMessageInspectionInput): ProtocolObservation? {
        if (input.negotiatedSubprotocol != GRAPHQL_TRANSPORT_WS_SUBPROTOCOL ||
            input.kind != ProtocolMessageKind.TEXT
        ) return null
        val envelope = parser.parse(input.body.copyBytes()) ?: return null
        val operation = observeOperation(input.exchangeId, envelope)
        return MessageObservation(
            direction = input.direction,
            messageType = envelope.type,
            operationId = envelope.operationId,
            operationName = operation?.name,
        )
    }

    override fun releaseMessages(exchangeId: ExchangeId) {
        operationState.update { current -> current - exchangeId }
    }

    override fun validateMessageReplacement(
        input: ProtocolMessageInspectionInput,
        replacement: BreakpointBody,
    ): Boolean {
        if (input.compressed || input.kind != ProtocolMessageKind.TEXT) return false
        val original = parser.parse(input.body.copyBytes()) ?: return false
        val edited = parser.parse(replacement.copyBytes()) ?: return false
        return original.type == edited.type && original.operationId == edited.operationId
    }

    override fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> {
        val decoded = criteria.takeIf { it.protocolId == GraphQLWebSocketBreakpointProtocol.id }
            ?.encodedPayload
            ?.let(::decode)
            ?: Criteria()
        return listOf(
            ProtocolCriteriaValue(GraphQLWebSocketBreakpointProtocol.directionFieldId, decoded.direction),
            ProtocolCriteriaValue(GraphQLWebSocketBreakpointProtocol.messageTypeFieldId, decoded.messageType),
            ProtocolCriteriaValue(GraphQLWebSocketBreakpointProtocol.operationNameFieldId, decoded.operationName.orEmpty()),
            ProtocolCriteriaValue(GraphQLWebSocketBreakpointProtocol.operationIdFieldId, decoded.operationId.orEmpty()),
        )
    }

    override fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria? {
        if (values.any { value -> value.fieldId !in FIELD_IDS }) return null
        val byId = values.associate { value -> value.fieldId to value.value.trim() }
        val direction = byId[GraphQLWebSocketBreakpointProtocol.directionFieldId].orEmpty()
            .ifBlank { ANY }
            .takeIf { value -> value in DIRECTIONS } ?: return null
        val messageType = byId[GraphQLWebSocketBreakpointProtocol.messageTypeFieldId].orEmpty()
            .ifBlank { ANY }
            .takeIf { value -> value == ANY || GraphQLWebSocketMessageType.fromWireName(value) != null }
            ?: return null
        val operationName = optionalGraphQLName(byId[GraphQLWebSocketBreakpointProtocol.operationNameFieldId])
            ?: if (byId[GraphQLWebSocketBreakpointProtocol.operationNameFieldId].isNullOrBlank()) null else return null
        val operationId = optionalOperationId(byId[GraphQLWebSocketBreakpointProtocol.operationIdFieldId])
            ?: if (byId[GraphQLWebSocketBreakpointProtocol.operationIdFieldId].isNullOrBlank()) null else return null
        return ProtocolMatchCriteria(
            protocolId = GraphQLWebSocketBreakpointProtocol.id,
            encodedPayload = buildJsonObject {
                put(VERSION, CRITERIA_VERSION)
                put(DIRECTION, direction)
                put(MESSAGE_TYPE, messageType)
                put(OPERATION_NAME, operationName?.let(::JsonPrimitive) ?: JsonNull)
                put(OPERATION_ID, operationId?.let(::JsonPrimitive) ?: JsonNull)
            }.toString(),
        )
    }

    override fun suggestCriteria(input: BreakpointRuleSuggestionInput): ProtocolMatchCriteria? {
        val requested = WebSocketProtocol.requestedSubprotocols(input.request.head)
        if (GRAPHQL_TRANSPORT_WS_SUBPROTOCOL !in requested) return null
        return createCriteria(emptyList())
    }

    private fun observeOperation(exchangeId: ExchangeId, envelope: GraphQLWebSocketEnvelope): OperationFact? {
        val parsed = parser.operation(envelope)?.let { operation -> OperationFact(operation.name, operation.type) }
        var resolved: OperationFact? = parsed
        operationState.update { current ->
            val existing = current[exchangeId].orEmpty()
            resolved = parsed ?: envelope.operationId?.let(existing::get)
            val updated = when (envelope.type) {
                GraphQLWebSocketMessageType.SUBSCRIBE -> {
                    val operationId = checkNotNull(envelope.operationId)
                    val fact = parsed ?: return@update current
                    val bounded = if (operationId !in existing &&
                        existing.size >= MAXIMUM_TRACKED_OPERATIONS_PER_CONNECTION
                    ) {
                        existing - existing.keys.first()
                    } else {
                        existing
                    }
                    bounded + (operationId to fact)
                }
                GraphQLWebSocketMessageType.ERROR,
                GraphQLWebSocketMessageType.COMPLETE -> envelope.operationId?.let { operationId ->
                    existing - operationId
                } ?: existing
                else -> existing
            }
            when {
                updated.isEmpty() -> current - exchangeId
                exchangeId in current -> current + (exchangeId to updated)
                current.size >= MAXIMUM_TRACKED_CONNECTIONS ->
                    (current - current.keys.first()) + (exchangeId to updated)
                else -> current + (exchangeId to updated)
            }
        }
        return resolved
    }

    private fun decode(payload: String): Criteria? {
        if (payload.isBlank() || payload.length > MAXIMUM_CRITERIA_CHARACTERS) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root.keys.any { key -> key !in JSON_FIELDS }) return null
        if ((root[VERSION] as? JsonPrimitive)?.intOrNull != CRITERIA_VERSION) return null
        val direction = (root[DIRECTION] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { value -> value in DIRECTIONS } ?: return null
        val messageType = (root[MESSAGE_TYPE] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { value -> value == ANY || GraphQLWebSocketMessageType.fromWireName(value) != null }
            ?: return null
        val operationName = root.optionalString(OPERATION_NAME)?.let(::optionalGraphQLName)
            ?: if (root[OPERATION_NAME] == null || root[OPERATION_NAME] is JsonNull) null else return null
        val operationId = root.optionalString(OPERATION_ID)?.let(::optionalOperationId)
            ?: if (root[OPERATION_ID] == null || root[OPERATION_ID] is JsonNull) null else return null
        return Criteria(direction, messageType, operationName, operationId)
    }

    private fun optionalGraphQLName(value: String?): String? = value?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.takeIf { name -> name.length <= MAXIMUM_OPERATION_NAME_CHARACTERS && GRAPHQL_NAME.matches(name) }

    private fun optionalOperationId(value: String?): String? = value?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.takeIf { id -> id.length <= MAXIMUM_OPERATION_ID_CHARACTERS }

    private data class Criteria(
        val direction: String = ANY,
        val messageType: String = ANY,
        val operationName: String? = null,
        val operationId: String? = null,
    )

    private data class OperationFact(val name: String?, val type: GraphQLOperationType)

    private data class MessageObservation(
        val direction: TrafficDirection,
        val messageType: GraphQLWebSocketMessageType,
        val operationId: String?,
        val operationName: String?,
    ) : ProtocolObservation {
        override val protocolId: BreakpointProtocolId = GraphQLWebSocketBreakpointProtocol.id
    }

    private class CompiledCriteria(private val criteria: Criteria) : CompiledProtocolCriteria {
        override val protocolId: BreakpointProtocolId = GraphQLWebSocketBreakpointProtocol.id

        override fun matches(observation: ProtocolObservation?): Boolean {
            val message = observation as? MessageObservation ?: return false
            return (criteria.direction == ANY || criteria.direction == message.direction.token()) &&
                (criteria.messageType == ANY || criteria.messageType == message.messageType.wireName) &&
                (criteria.operationName == null || criteria.operationName == message.operationName) &&
                (criteria.operationId == null || criteria.operationId == message.operationId)
        }
    }

    private companion object {
        const val CRITERIA_VERSION: Int = 1
        const val ANY: String = "any"
        const val CLIENT: String = "client"
        const val SERVER: String = "server"
        const val VERSION: String = "version"
        const val DIRECTION: String = "direction"
        const val MESSAGE_TYPE: String = "messageType"
        const val OPERATION_NAME: String = "operationName"
        const val OPERATION_ID: String = "operationId"
        const val MAXIMUM_CRITERIA_CHARACTERS: Int = 4_096
        const val MAXIMUM_OPERATION_NAME_CHARACTERS: Int = 256
        const val MAXIMUM_OPERATION_ID_CHARACTERS: Int = 256
        const val MAXIMUM_TRACKED_CONNECTIONS: Int = 1_024
        const val MAXIMUM_TRACKED_OPERATIONS_PER_CONNECTION: Int = 1_024
        val DIRECTIONS: Set<String> = setOf(ANY, CLIENT, SERVER)
        val GRAPHQL_NAME: Regex = Regex("^[_A-Za-z][_0-9A-Za-z]*$")
        val FIELD_IDS: Set<ProtocolCriteriaFieldId> = setOf(
            GraphQLWebSocketBreakpointProtocol.directionFieldId,
            GraphQLWebSocketBreakpointProtocol.messageTypeFieldId,
            GraphQLWebSocketBreakpointProtocol.operationNameFieldId,
            GraphQLWebSocketBreakpointProtocol.operationIdFieldId,
        )
        val JSON_FIELDS: Set<String> = setOf(VERSION, DIRECTION, MESSAGE_TYPE, OPERATION_NAME, OPERATION_ID)
    }
}

private fun JsonObject.optionalString(name: String): String? = when (val value = this[name]) {
    null, JsonNull -> null
    else -> (value as? JsonPrimitive)?.contentOrNull
}

private fun TrafficDirection.token(): String = when (this) {
    TrafficDirection.CLIENT_TO_SERVER -> "client"
    TrafficDirection.SERVER_TO_CLIENT -> "server"
}
