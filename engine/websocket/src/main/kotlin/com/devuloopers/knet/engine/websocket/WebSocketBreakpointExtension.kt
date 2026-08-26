package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.contract.breakpoint.BreakpointInterceptionUnit
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolExtension
import com.devuloopers.knet.application.contract.breakpoint.BreakpointRuleSuggestionInput
import com.devuloopers.knet.application.contract.breakpoint.CompiledProtocolCriteria
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaOption
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.breakpoint.ProtocolInspectionInput
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageInspectionInput
import com.devuloopers.knet.application.contract.breakpoint.ProtocolObservation
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Stable identities owned by WebSocket message breakpoint rules. */
object WebSocketBreakpointProtocol {
    /** Stable protocol identifier persisted by WebSocket breakpoint criteria. */
    val id: BreakpointProtocolId = BreakpointProtocolId("websocket")

    /** Field identifier for client-to-server or server-to-client message matching. */
    val directionFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("direction")

    /** Field identifier for text, binary, and control-message matching. */
    val kindFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("kind")

    /** Field identifier for an optional requested WebSocket subprotocol. */
    val subprotocolFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("subprotocol")

    /** Field identifier for an optional one-based directional message sequence. */
    val sequenceFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("sequence")
}

/** Message-level WebSocket breakpoint criteria independent from proxy and presentation code. */
class WebSocketBreakpointExtension(
    private val json: Json = Json { ignoreUnknownKeys = false },
) : BreakpointProtocolExtension {
    override val suggestionPriority: Int = 250

    override val definition: BreakpointProtocolDefinition = BreakpointProtocolDefinition(
        protocolId = WebSocketBreakpointProtocol.id,
        displayName = "WebSocket",
        criteriaVersion = CRITERIA_VERSION,
        interceptionUnit = BreakpointInterceptionUnit.PROTOCOL_MESSAGE,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Choice(
                id = WebSocketBreakpointProtocol.directionFieldId,
                label = "Message Direction",
                description = "Choose which WebSocket direction may pause.",
                options = listOf(
                    ProtocolCriteriaOption(DIRECTION_ANY, "Client and server messages"),
                    ProtocolCriteriaOption(DIRECTION_CLIENT, "Client messages"),
                    ProtocolCriteriaOption(DIRECTION_SERVER, "Server messages"),
                ),
                defaultValue = DIRECTION_ANY,
            ),
            ProtocolCriteriaFieldDefinition.Choice(
                id = WebSocketBreakpointProtocol.kindFieldId,
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
            ProtocolCriteriaFieldDefinition.Text(
                id = WebSocketBreakpointProtocol.subprotocolFieldId,
                label = "Subprotocol",
                description = "Optional negotiated Sec-WebSocket-Protocol token.",
                placeholder = "e.g. graphql-transport-ws",
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = WebSocketBreakpointProtocol.sequenceFieldId,
                label = "Message Sequence",
                description = "Optional one-based logical-message number in the selected direction.",
                placeholder = "e.g. 1",
            ),
        ),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? {
        if (criteria.protocolId != WebSocketBreakpointProtocol.id) return null
        return decode(criteria.encodedPayload)?.let(::CompiledWebSocketCriteria)
    }

    override fun inspect(input: ProtocolInspectionInput): ProtocolObservation? = null

    override fun inspectMessage(input: ProtocolMessageInspectionInput): ProtocolObservation? {
        if (!WebSocketProtocol.isHandshake(input.request)) return null
        return WebSocketObservation(
            direction = input.direction,
            kind = input.kind,
            subprotocols = input.negotiatedSubprotocol?.let(::setOf)
                ?: WebSocketProtocol.requestedSubprotocols(input.request.head).toSet(),
            sequence = input.sequence,
        )
    }

    override fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> {
        val decoded = criteria.takeIf { it.protocolId == WebSocketBreakpointProtocol.id }
            ?.encodedPayload
            ?.let(::decode)
            ?: WebSocketCriteria()
        return listOf(
            ProtocolCriteriaValue(WebSocketBreakpointProtocol.directionFieldId, decoded.direction),
            ProtocolCriteriaValue(WebSocketBreakpointProtocol.kindFieldId, decoded.kind),
            ProtocolCriteriaValue(WebSocketBreakpointProtocol.subprotocolFieldId, decoded.subprotocol.orEmpty()),
            ProtocolCriteriaValue(WebSocketBreakpointProtocol.sequenceFieldId, decoded.sequence?.toString().orEmpty()),
        )
    }

    override fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria? {
        if (values.any { value -> value.fieldId !in FIELD_IDS }) return null
        val byId = values.associate { value -> value.fieldId to value.value.trim() }
        val direction = byId[WebSocketBreakpointProtocol.directionFieldId].orEmpty()
            .ifBlank { DIRECTION_ANY }
            .takeIf { value -> value in DIRECTIONS } ?: return null
        val kind = byId[WebSocketBreakpointProtocol.kindFieldId].orEmpty()
            .ifBlank { KIND_ANY }
            .takeIf { value -> value in KINDS } ?: return null
        val subprotocol = byId[WebSocketBreakpointProtocol.subprotocolFieldId]
            ?.takeIf(String::isNotBlank)
            ?.takeIf(SUBPROTOCOL::matches)
            ?: if (byId[WebSocketBreakpointProtocol.subprotocolFieldId].isNullOrBlank()) null else return null
        val sequenceText = byId[WebSocketBreakpointProtocol.sequenceFieldId].orEmpty()
        val sequence = if (sequenceText.isBlank()) null else sequenceText.toLongOrNull()?.takeIf { it > 0L }
            ?: return null
        return ProtocolMatchCriteria(
            protocolId = WebSocketBreakpointProtocol.id,
            encodedPayload = buildJsonObject {
                put(VERSION, CRITERIA_VERSION)
                put(DIRECTION, direction)
                put(KIND, kind)
                put(SUBPROTOCOL_FIELD, subprotocol?.let(::JsonPrimitive) ?: JsonNull)
                put(SEQUENCE, sequence?.let(::JsonPrimitive) ?: JsonNull)
            }.toString(),
        )
    }

    override fun suggestCriteria(input: BreakpointRuleSuggestionInput): ProtocolMatchCriteria? {
        if (!WebSocketProtocol.isHandshake(input.request)) return null
        return createCriteria(
            listOf(
                ProtocolCriteriaValue(WebSocketBreakpointProtocol.directionFieldId, DIRECTION_ANY),
                ProtocolCriteriaValue(WebSocketBreakpointProtocol.kindFieldId, KIND_ANY),
                ProtocolCriteriaValue(
                    WebSocketBreakpointProtocol.subprotocolFieldId,
                    WebSocketProtocol.requestedSubprotocols(input.request.head).firstOrNull().orEmpty(),
                ),
                ProtocolCriteriaValue(WebSocketBreakpointProtocol.sequenceFieldId, ""),
            ),
        )
    }

    private fun decode(payload: String): WebSocketCriteria? {
        if (payload.isBlank() || payload.length > MAXIMUM_CRITERIA_CHARACTERS) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root.keys.any { key -> key !in JSON_FIELDS }) return null
        if ((root[VERSION] as? JsonPrimitive)?.intOrNull != CRITERIA_VERSION) return null
        val direction = (root[DIRECTION] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { value -> value in DIRECTIONS } ?: return null
        val kind = (root[KIND] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { value -> value in KINDS } ?: return null
        val subprotocol = when (val value = root[SUBPROTOCOL_FIELD]) {
            null, JsonNull -> null
            else -> (value as? JsonPrimitive)?.contentOrNull?.takeIf(SUBPROTOCOL::matches) ?: return null
        }
        val sequence = when (val value = root[SEQUENCE]) {
            null, JsonNull -> null
            else -> (value as? JsonPrimitive)?.longOrNull?.takeIf { it > 0L } ?: return null
        }
        return WebSocketCriteria(direction, kind, subprotocol, sequence)
    }

    private data class WebSocketCriteria(
        val direction: String = DIRECTION_ANY,
        val kind: String = KIND_ANY,
        val subprotocol: String? = null,
        val sequence: Long? = null,
    )

    private data class WebSocketObservation(
        val direction: TrafficDirection,
        val kind: ProtocolMessageKind,
        val subprotocols: Set<String>,
        val sequence: Long,
    ) : ProtocolObservation {
        override val protocolId: BreakpointProtocolId = WebSocketBreakpointProtocol.id
    }

    private class CompiledWebSocketCriteria(
        private val criteria: WebSocketCriteria,
    ) : CompiledProtocolCriteria {
        override val protocolId: BreakpointProtocolId = WebSocketBreakpointProtocol.id

        override fun matches(observation: ProtocolObservation?): Boolean {
            val websocket = observation as? WebSocketObservation ?: return false
            return (criteria.direction == DIRECTION_ANY || criteria.direction == websocket.direction.token()) &&
                (criteria.kind == KIND_ANY || criteria.kind == websocket.kind.value) &&
                (criteria.subprotocol == null || criteria.subprotocol in websocket.subprotocols) &&
                (criteria.sequence == null || criteria.sequence == websocket.sequence)
        }
    }

    private companion object {
        const val CRITERIA_VERSION: Int = 1
        const val DIRECTION: String = "direction"
        const val DIRECTION_ANY: String = "any"
        const val DIRECTION_CLIENT: String = "client"
        const val DIRECTION_SERVER: String = "server"
        const val KIND: String = "kind"
        const val KIND_ANY: String = "any"
        const val MAXIMUM_CRITERIA_CHARACTERS: Int = 4_096
        const val SEQUENCE: String = "sequence"
        const val SUBPROTOCOL_FIELD: String = "subprotocol"
        const val VERSION: String = "version"
        val DIRECTIONS: Set<String> = setOf(DIRECTION_ANY, DIRECTION_CLIENT, DIRECTION_SERVER)
        val KINDS: Set<String> = setOf(
            KIND_ANY,
            ProtocolMessageKind.TEXT.value,
            ProtocolMessageKind.BINARY.value,
            ProtocolMessageKind.PING.value,
            ProtocolMessageKind.PONG.value,
            ProtocolMessageKind.CLOSE.value,
        )
        val FIELD_IDS: Set<ProtocolCriteriaFieldId> = setOf(
            WebSocketBreakpointProtocol.directionFieldId,
            WebSocketBreakpointProtocol.kindFieldId,
            WebSocketBreakpointProtocol.subprotocolFieldId,
            WebSocketBreakpointProtocol.sequenceFieldId,
        )
        val JSON_FIELDS: Set<String> = setOf(VERSION, DIRECTION, KIND, SUBPROTOCOL_FIELD, SEQUENCE)
        val SUBPROTOCOL: Regex = Regex("^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$")
    }
}

private fun TrafficDirection.token(): String = when (this) {
    TrafficDirection.CLIENT_TO_SERVER -> "client"
    TrafficDirection.SERVER_TO_CLIENT -> "server"
}
