package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaOption
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.CompiledNetworkConditionProtocolCriteria
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionInterceptionUnit
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionMessageInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolExtension
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolObservation
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.pathWithoutQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Stable editor-field identities owned by generic WebSocket Network Conditions rules. */
object WebSocketNetworkConditionProtocol {
    /** Optional exact handshake path selector. */
    val pathFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("path")

    /** Optional exact negotiated or uniquely requested subprotocol selector. */
    val subprotocolFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("subprotocol")

    /** Client-to-server, server-to-client, or bidirectional selector. */
    val directionFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("direction")

    /** Text, binary, control, or every logical-message selector. */
    val kindFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("kind")
}

/** Logical-message WebSocket Network Conditions contribution independent of application subprotocol. */
class WebSocketNetworkConditionExtension(
    private val json: Json = Json { ignoreUnknownKeys = false },
) : NetworkConditionProtocolExtension {
    override val suggestionPriority: Int = 200
    override val editorOrder: Int = 400

    override val definition: NetworkConditionProtocolDefinition = NetworkConditionProtocolDefinition(
        protocolId = NetworkConditionProtocolId.WEBSOCKET,
        displayName = "WebSocket",
        criteriaVersion = CRITERIA_VERSION,
        interceptionUnit = NetworkConditionInterceptionUnit.PROTOCOL_MESSAGE,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Text(
                id = WebSocketNetworkConditionProtocol.pathFieldId,
                label = "Handshake Path",
                description = "Optional exact WebSocket handshake path without the query string.",
                placeholder = "e.g. /stream",
                optional = true,
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = WebSocketNetworkConditionProtocol.subprotocolFieldId,
                label = "Subprotocol",
                description = "Optional exact negotiated Sec-WebSocket-Protocol token.",
                placeholder = "e.g. chat.v2",
                optional = true,
            ),
            ProtocolCriteriaFieldDefinition.Choice(
                id = WebSocketNetworkConditionProtocol.directionFieldId,
                label = "Message Direction",
                description = "Choose which logical-message direction receives this condition.",
                options = listOf(
                    ProtocolCriteriaOption(DIRECTION_ANY, "Client and server messages"),
                    ProtocolCriteriaOption(DIRECTION_CLIENT, "Client messages"),
                    ProtocolCriteriaOption(DIRECTION_SERVER, "Server messages"),
                ),
                defaultValue = DIRECTION_ANY,
            ),
            ProtocolCriteriaFieldDefinition.Choice(
                id = WebSocketNetworkConditionProtocol.kindFieldId,
                label = "Message Kind",
                description = "Match data and control messages by semantic kind.",
                options = listOf(
                    ProtocolCriteriaOption(KIND_ANY, "Every message kind"),
                    ProtocolCriteriaOption(ProtocolMessageKind.TEXT.value, "Text"),
                    ProtocolCriteriaOption(ProtocolMessageKind.BINARY.value, "Binary"),
                    ProtocolCriteriaOption(ProtocolMessageKind.PING.value, "Ping"),
                    ProtocolCriteriaOption(ProtocolMessageKind.PONG.value, "Pong"),
                    ProtocolCriteriaOption(ProtocolMessageKind.CLOSE.value, "Close"),
                ),
                defaultValue = KIND_ANY,
            ),
        ),
    )

    override fun compile(
        criteria: NetworkConditionProtocolCriteria,
    ): CompiledNetworkConditionProtocolCriteria? {
        if (criteria.protocolId != NetworkConditionProtocolId.WEBSOCKET) return null
        return decode(criteria.encodedPayload)?.let(::CompiledCriteria)
    }

    override fun inspectMessage(
        input: NetworkConditionMessageInspectionInput,
    ): NetworkConditionProtocolObservation? {
        if (!WebSocketProtocol.isHandshake(input.request)) return null
        val path = input.request.pathWithoutQuery() ?: return null
        return Observation(
            path = path,
            subprotocols = input.negotiatedSubprotocol?.let(::setOf)
                ?: WebSocketProtocol.requestedSubprotocols(input.request.head).toSet(),
            direction = input.direction,
            kind = input.kind,
        )
    }

    override fun editorValues(criteria: NetworkConditionProtocolCriteria): List<ProtocolCriteriaValue> {
        val decoded = criteria.takeIf { value -> value.protocolId == NetworkConditionProtocolId.WEBSOCKET }
            ?.encodedPayload
            ?.let(::decode)
            ?: Criteria()
        return listOf(
            ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.pathFieldId, decoded.path.orEmpty()),
            ProtocolCriteriaValue(
                WebSocketNetworkConditionProtocol.subprotocolFieldId,
                decoded.subprotocol.orEmpty(),
            ),
            ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.directionFieldId, decoded.direction),
            ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.kindFieldId, decoded.kind),
        )
    }

    override fun createCriteria(values: List<ProtocolCriteriaValue>): NetworkConditionProtocolCriteria? {
        if (values.any { value -> value.fieldId !in FIELD_IDS }) return null
        val byId = values.associate { value -> value.fieldId to value.value.trim() }
        val pathValue = byId[WebSocketNetworkConditionProtocol.pathFieldId].orEmpty()
        val path = pathValue.takeIf(String::isNotEmpty)
            ?.takeIf(::validPath)
            ?: if (pathValue.isBlank()) null else return null
        val subprotocolValue = byId[WebSocketNetworkConditionProtocol.subprotocolFieldId].orEmpty()
        val subprotocol = subprotocolValue.takeIf(String::isNotEmpty)
            ?.takeIf(SUBPROTOCOL::matches)
            ?: if (subprotocolValue.isBlank()) null else return null
        val direction = byId[WebSocketNetworkConditionProtocol.directionFieldId].orEmpty()
            .ifBlank { DIRECTION_ANY }
            .takeIf(DIRECTIONS::contains)
            ?: return null
        val kind = byId[WebSocketNetworkConditionProtocol.kindFieldId].orEmpty()
            .ifBlank { KIND_ANY }
            .takeIf(KINDS::contains)
            ?: return null
        return NetworkConditionProtocolCriteria(
            protocolId = NetworkConditionProtocolId.WEBSOCKET,
            encodedPayload = buildJsonObject {
                put(VERSION_FIELD, CRITERIA_VERSION)
                put(PATH_FIELD, path?.let(::JsonPrimitive) ?: JsonNull)
                put(SUBPROTOCOL_FIELD, subprotocol?.let(::JsonPrimitive) ?: JsonNull)
                put(DIRECTION_FIELD, direction)
                put(KIND_FIELD, kind)
            }.toString(),
        )
    }

    override fun suggestCriteria(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolCriteria? {
        if (!WebSocketProtocol.isHandshake(input.request)) return null
        val path = input.request.pathWithoutQuery() ?: return null
        val subprotocols = WebSocketProtocol.requestedSubprotocols(input.request.head)
        return createCriteria(
            listOf(
                ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.pathFieldId, path),
                ProtocolCriteriaValue(
                    WebSocketNetworkConditionProtocol.subprotocolFieldId,
                    subprotocols.singleOrNull().orEmpty(),
                ),
                ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.directionFieldId, DIRECTION_ANY),
                ProtocolCriteriaValue(WebSocketNetworkConditionProtocol.kindFieldId, KIND_ANY),
            ),
        )
    }

    private fun decode(payload: String): Criteria? {
        if (payload.isBlank() || payload.length > MAXIMUM_CRITERIA_CHARACTERS) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root.keys.any { key -> key !in JSON_FIELDS }) return null
        if ((root[VERSION_FIELD] as? JsonPrimitive)?.intOrNull != CRITERIA_VERSION) return null
        val path = root.optionalString(PATH_FIELD)?.takeIf(::validPath)
            ?: if (root[PATH_FIELD] == null || root[PATH_FIELD] == JsonNull) null else return null
        val subprotocol = root.optionalString(SUBPROTOCOL_FIELD)?.takeIf(SUBPROTOCOL::matches)
            ?: if (root[SUBPROTOCOL_FIELD] == null || root[SUBPROTOCOL_FIELD] == JsonNull) null else return null
        val direction = (root[DIRECTION_FIELD] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(DIRECTIONS::contains)
            ?: return null
        val kind = (root[KIND_FIELD] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(KINDS::contains)
            ?: return null
        return Criteria(path = path, subprotocol = subprotocol, direction = direction, kind = kind)
    }

    private fun JsonObject.optionalString(key: String): String? = when (val value = this[key]) {
        null, JsonNull -> null
        else -> (value as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun validPath(value: String): Boolean =
        value.length <= MAXIMUM_PATH_CHARACTERS && value.startsWith('/') && value.none(Char::isISOControl)

    private data class Criteria(
        val path: String? = null,
        val subprotocol: String? = null,
        val direction: String = DIRECTION_ANY,
        val kind: String = KIND_ANY,
    )

    private data class Observation(
        val path: String,
        val subprotocols: Set<String>,
        val direction: TrafficDirection,
        val kind: ProtocolMessageKind,
    ) : NetworkConditionProtocolObservation {
        override val protocolId: NetworkConditionProtocolId = NetworkConditionProtocolId.WEBSOCKET
    }

    private class CompiledCriteria(
        private val criteria: Criteria,
    ) : CompiledNetworkConditionProtocolCriteria {
        override val protocolId: NetworkConditionProtocolId = NetworkConditionProtocolId.WEBSOCKET

        override fun matches(observation: NetworkConditionProtocolObservation?): Boolean {
            val websocket = observation as? Observation ?: return false
            return (criteria.path == null || criteria.path == websocket.path) &&
                (criteria.subprotocol == null || criteria.subprotocol in websocket.subprotocols) &&
                (criteria.direction == DIRECTION_ANY || criteria.direction == websocket.direction.token()) &&
                (criteria.kind == KIND_ANY || criteria.kind == websocket.kind.value)
        }
    }

    private companion object {
        const val CRITERIA_VERSION: Int = 1
        const val VERSION_FIELD: String = "version"
        const val PATH_FIELD: String = "path"
        const val SUBPROTOCOL_FIELD: String = "subprotocol"
        const val DIRECTION_FIELD: String = "direction"
        const val KIND_FIELD: String = "kind"
        const val DIRECTION_ANY: String = "any"
        const val DIRECTION_CLIENT: String = "client"
        const val DIRECTION_SERVER: String = "server"
        const val KIND_ANY: String = "any"
        const val MAXIMUM_CRITERIA_CHARACTERS: Int = 4_096
        const val MAXIMUM_PATH_CHARACTERS: Int = 2_048
        val DIRECTIONS: Set<String> = setOf(DIRECTION_ANY, DIRECTION_CLIENT, DIRECTION_SERVER)
        val KINDS: Set<String> = setOf(
            KIND_ANY,
            ProtocolMessageKind.TEXT.value,
            ProtocolMessageKind.BINARY.value,
            ProtocolMessageKind.PING.value,
            ProtocolMessageKind.PONG.value,
            ProtocolMessageKind.CLOSE.value,
        )
        val SUBPROTOCOL: Regex = Regex("^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$")
        val FIELD_IDS: Set<ProtocolCriteriaFieldId> = setOf(
            WebSocketNetworkConditionProtocol.pathFieldId,
            WebSocketNetworkConditionProtocol.subprotocolFieldId,
            WebSocketNetworkConditionProtocol.directionFieldId,
            WebSocketNetworkConditionProtocol.kindFieldId,
        )
        val JSON_FIELDS: Set<String> = setOf(
            VERSION_FIELD,
            PATH_FIELD,
            SUBPROTOCOL_FIELD,
            DIRECTION_FIELD,
            KIND_FIELD,
        )
    }
}

private fun TrafficDirection.token(): String = when (this) {
    TrafficDirection.CLIENT_TO_SERVER -> "client"
    TrafficDirection.SERVER_TO_CLIENT -> "server"
}
