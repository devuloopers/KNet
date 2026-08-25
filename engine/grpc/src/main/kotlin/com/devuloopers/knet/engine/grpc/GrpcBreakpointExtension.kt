package com.devuloopers.knet.engine.grpc

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
import com.devuloopers.knet.traffic.model.TrafficDirection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Stable identities used by native-gRPC breakpoint rules and presentation. */
object GrpcBreakpointProtocol {
    val id: BreakpointProtocolId = BreakpointProtocolId("grpc")
    val serviceFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("service")
    val methodFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("method")
    val directionFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("direction")
    val sequenceFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("sequence")
}

/**
 * Message-level breakpoint contribution for native gRPC.
 *
 * The extension owns its versioned criteria and semantic observation. The common coordinator and
 * rule editor remain unaware of service paths, gRPC directions, or message sequence semantics.
 */
class GrpcBreakpointExtension(
    private val json: Json = Json { ignoreUnknownKeys = false },
) : BreakpointProtocolExtension {
    override val suggestionPriority: Int = 200

    override val definition: BreakpointProtocolDefinition = BreakpointProtocolDefinition(
        protocolId = GrpcBreakpointProtocol.id,
        displayName = "gRPC",
        criteriaVersion = CRITERIA_VERSION,
        interceptionUnit = BreakpointInterceptionUnit.PROTOCOL_MESSAGE,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Text(
                id = GrpcBreakpointProtocol.serviceFieldId,
                label = "Service",
                description = "Fully-qualified protobuf service. Leave empty to match every service.",
                placeholder = "e.g. knet.testing.v1.ProtocolLab",
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = GrpcBreakpointProtocol.methodFieldId,
                label = "RPC Method",
                description = "Method name without the service path. Leave empty to match every method.",
                placeholder = "e.g. UnaryEcho",
            ),
            ProtocolCriteriaFieldDefinition.Choice(
                id = GrpcBreakpointProtocol.directionFieldId,
                label = "Message Direction",
                description = "Choose which framed messages may pause.",
                options = listOf(
                    ProtocolCriteriaOption(DIRECTION_ANY, "Client and server messages"),
                    ProtocolCriteriaOption(DIRECTION_CLIENT, "Client messages"),
                    ProtocolCriteriaOption(DIRECTION_SERVER, "Server messages"),
                ),
                defaultValue = DIRECTION_ANY,
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = GrpcBreakpointProtocol.sequenceFieldId,
                label = "Message Sequence",
                description = "Optional one-based message number within the selected direction.",
                placeholder = "e.g. 1",
            ),
        ),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? {
        if (criteria.protocolId != GrpcBreakpointProtocol.id) return null
        val decoded = decode(criteria.encodedPayload) ?: return null
        return GrpcCompiledCriteria(decoded)
    }

    /** HTTP exchange inspection is intentionally disabled because gRPC pauses operate per message. */
    override fun inspect(input: ProtocolInspectionInput): ProtocolObservation? = null

    override fun inspectMessage(input: ProtocolMessageInspectionInput): ProtocolObservation? {
        val method = GrpcMethodIdentity.fromTarget(input.request.head.target) ?: return null
        return GrpcBreakpointObservation(
            method = method,
            direction = input.direction,
            sequence = input.sequence,
        )
    }

    override fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> {
        val decoded = criteria.takeIf { it.protocolId == GrpcBreakpointProtocol.id }
            ?.encodedPayload
            ?.let(::decode)
            ?: GrpcCriteria()
        return listOf(
            ProtocolCriteriaValue(GrpcBreakpointProtocol.serviceFieldId, decoded.service.orEmpty()),
            ProtocolCriteriaValue(GrpcBreakpointProtocol.methodFieldId, decoded.method.orEmpty()),
            ProtocolCriteriaValue(GrpcBreakpointProtocol.directionFieldId, decoded.direction),
            ProtocolCriteriaValue(GrpcBreakpointProtocol.sequenceFieldId, decoded.sequence?.toString().orEmpty()),
        )
    }

    override fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria? {
        if (values.any { it.fieldId !in FIELD_IDS }) return null
        val byId = values.associate { it.fieldId to it.value.trim() }
        val service = byId[GrpcBreakpointProtocol.serviceFieldId]
            ?.takeIf(String::isNotEmpty)
            ?.takeIf(::isValidService) ?: if (byId[GrpcBreakpointProtocol.serviceFieldId].isNullOrBlank()) null else return null
        val method = byId[GrpcBreakpointProtocol.methodFieldId]
            ?.takeIf(String::isNotEmpty)
            ?.takeIf(NAME::matches) ?: if (byId[GrpcBreakpointProtocol.methodFieldId].isNullOrBlank()) null else return null
        val direction = byId[GrpcBreakpointProtocol.directionFieldId].orEmpty().ifBlank { DIRECTION_ANY }
            .takeIf { it in DIRECTIONS } ?: return null
        val sequenceText = byId[GrpcBreakpointProtocol.sequenceFieldId].orEmpty()
        val sequence = if (sequenceText.isBlank()) null else sequenceText.toLongOrNull()?.takeIf { it > 0L }
            ?: return null
        return ProtocolMatchCriteria(
            protocolId = GrpcBreakpointProtocol.id,
            encodedPayload = buildJsonObject {
                put(VERSION, CRITERIA_VERSION)
                put(SERVICE, service?.let(::JsonPrimitive) ?: JsonNull)
                put(METHOD, method?.let(::JsonPrimitive) ?: JsonNull)
                put(DIRECTION, direction)
                put(SEQUENCE, sequence?.let(::JsonPrimitive) ?: JsonNull)
            }.toString(),
        )
    }

    override fun suggestCriteria(input: BreakpointRuleSuggestionInput): ProtocolMatchCriteria? {
        if (!GrpcProtocol.isNativeContentType(GrpcProtocol.header(input.request.head.headers, CONTENT_TYPE))) {
            return null
        }
        val method = GrpcMethodIdentity.fromTarget(input.request.head.target) ?: return null
        return createCriteria(
            listOf(
                ProtocolCriteriaValue(GrpcBreakpointProtocol.serviceFieldId, method.serviceName),
                ProtocolCriteriaValue(GrpcBreakpointProtocol.methodFieldId, method.methodName),
                ProtocolCriteriaValue(GrpcBreakpointProtocol.directionFieldId, DIRECTION_ANY),
                ProtocolCriteriaValue(GrpcBreakpointProtocol.sequenceFieldId, ""),
            ),
        )
    }

    private fun decode(payload: String): GrpcCriteria? {
        if (payload.isBlank() || payload.length > MAXIMUM_CRITERIA_CHARACTERS) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root.keys.any { it !in JSON_FIELDS }) return null
        if ((root[VERSION] as? JsonPrimitive)?.intOrNull != CRITERIA_VERSION) return null
        val service = root.optionalString(SERVICE)?.takeIf(::isValidService) ?: if (root[SERVICE] == JsonNull) null else return null
        val method = root.optionalString(METHOD)?.takeIf(NAME::matches) ?: if (root[METHOD] == JsonNull) null else return null
        val direction = (root[DIRECTION] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it in DIRECTIONS } ?: return null
        val sequence = when (val value = root[SEQUENCE]) {
            null, JsonNull -> null
            else -> (value as? JsonPrimitive)?.longOrNull?.takeIf { it > 0L } ?: return null
        }
        return GrpcCriteria(service, method, direction, sequence)
    }

    private fun JsonObject.optionalString(key: String): String? = when (val value = this[key]) {
        null, JsonNull -> null
        else -> (value as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun isValidService(value: String): Boolean =
        value.length <= MAXIMUM_NAME_CHARACTERS && value.split('.').all(NAME::matches)

    private data class GrpcCriteria(
        val service: String? = null,
        val method: String? = null,
        val direction: String = DIRECTION_ANY,
        val sequence: Long? = null,
    )

    private data class GrpcBreakpointObservation(
        val method: GrpcMethodIdentity,
        val direction: TrafficDirection,
        val sequence: Long,
    ) : ProtocolObservation {
        override val protocolId: BreakpointProtocolId = GrpcBreakpointProtocol.id
    }

    private class GrpcCompiledCriteria(
        private val criteria: GrpcCriteria,
    ) : CompiledProtocolCriteria {
        override val protocolId: BreakpointProtocolId = GrpcBreakpointProtocol.id

        override fun matches(observation: ProtocolObservation?): Boolean {
            val grpc = observation as? GrpcBreakpointObservation ?: return false
            return (criteria.service == null || criteria.service == grpc.method.serviceName) &&
                (criteria.method == null || criteria.method == grpc.method.methodName) &&
                (criteria.direction == DIRECTION_ANY || criteria.direction == grpc.direction.token()) &&
                (criteria.sequence == null || criteria.sequence == grpc.sequence)
        }
    }

    private companion object {
        const val CRITERIA_VERSION: Int = 1
        const val VERSION: String = "version"
        const val SERVICE: String = "service"
        const val METHOD: String = "method"
        const val DIRECTION: String = "direction"
        const val SEQUENCE: String = "sequence"
        const val DIRECTION_ANY: String = "any"
        const val DIRECTION_CLIENT: String = "client"
        const val DIRECTION_SERVER: String = "server"
        const val CONTENT_TYPE: String = "content-type"
        const val MAXIMUM_CRITERIA_CHARACTERS: Int = 4_096
        const val MAXIMUM_NAME_CHARACTERS: Int = 512
        val DIRECTIONS: Set<String> = setOf(DIRECTION_ANY, DIRECTION_CLIENT, DIRECTION_SERVER)
        val JSON_FIELDS: Set<String> = setOf(VERSION, SERVICE, METHOD, DIRECTION, SEQUENCE)
        val FIELD_IDS: Set<ProtocolCriteriaFieldId> = setOf(
            GrpcBreakpointProtocol.serviceFieldId,
            GrpcBreakpointProtocol.methodFieldId,
            GrpcBreakpointProtocol.directionFieldId,
            GrpcBreakpointProtocol.sequenceFieldId,
        )
        val NAME: Regex = Regex("^[_A-Za-z][_0-9A-Za-z]*$")
    }
}

private fun TrafficDirection.token(): String = when (this) {
    TrafficDirection.CLIENT_TO_SERVER -> "client"
    TrafficDirection.SERVER_TO_CLIENT -> "server"
}
