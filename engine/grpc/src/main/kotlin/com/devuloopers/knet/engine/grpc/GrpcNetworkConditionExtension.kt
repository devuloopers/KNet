package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.CompiledNetworkConditionProtocolCriteria
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionInterceptionUnit
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolExtension
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolObservation
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Stable editor-field identities owned by native gRPC Network Conditions rules. */
object GrpcNetworkConditionProtocol {
    /** Optional fully-qualified protobuf service selector. */
    val serviceFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("service")

    /** Optional RPC method selector. */
    val methodFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("method")
}

/** Header-only native-gRPC service/method Network Conditions contribution. */
class GrpcNetworkConditionExtension(
    private val json: Json = Json { ignoreUnknownKeys = false },
) : NetworkConditionProtocolExtension {
    override val suggestionPriority: Int = 300
    override val editorOrder: Int = 300

    override val definition: NetworkConditionProtocolDefinition = NetworkConditionProtocolDefinition(
        protocolId = NetworkConditionProtocolId.GRPC,
        displayName = "gRPC",
        criteriaVersion = CRITERIA_VERSION,
        interceptionUnit = NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Text(
                id = GrpcNetworkConditionProtocol.serviceFieldId,
                label = "Service",
                description = "Fully-qualified protobuf service. Leave empty to shape every native gRPC service.",
                placeholder = "e.g. knet.testing.v1.ProtocolLab",
                optional = true,
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = GrpcNetworkConditionProtocol.methodFieldId,
                label = "RPC Method",
                description = "Method name without the service path. Leave empty to shape every RPC method.",
                placeholder = "e.g. StreamVideo",
                optional = true,
            ),
        ),
    )

    override fun compile(
        criteria: NetworkConditionProtocolCriteria,
    ): CompiledNetworkConditionProtocolCriteria? {
        if (criteria.protocolId != NetworkConditionProtocolId.GRPC) return null
        return decode(criteria.encodedPayload)?.let(::CompiledCriteria)
    }

    override fun inspectHttp(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolObservation? {
        if (input.request.head.method != HttpMethod.POST) return null
        if (!GrpcProtocol.isNativeContentType(GrpcProtocol.header(input.request.head.headers, CONTENT_TYPE))) {
            return null
        }
        val method = GrpcMethodIdentity.fromTarget(input.request.head.target) ?: return null
        return Observation(method)
    }

    override fun editorValues(criteria: NetworkConditionProtocolCriteria): List<ProtocolCriteriaValue> {
        val decoded = criteria.takeIf { value -> value.protocolId == NetworkConditionProtocolId.GRPC }
            ?.encodedPayload
            ?.let(::decode)
            ?: Criteria()
        return listOf(
            ProtocolCriteriaValue(GrpcNetworkConditionProtocol.serviceFieldId, decoded.service.orEmpty()),
            ProtocolCriteriaValue(GrpcNetworkConditionProtocol.methodFieldId, decoded.method.orEmpty()),
        )
    }

    override fun createCriteria(values: List<ProtocolCriteriaValue>): NetworkConditionProtocolCriteria? {
        if (values.any { value -> value.fieldId !in FIELD_IDS }) return null
        val byId = values.associate { value -> value.fieldId to value.value.trim() }
        val serviceValue = byId[GrpcNetworkConditionProtocol.serviceFieldId].orEmpty()
        val service = serviceValue.takeIf(String::isNotEmpty)
            ?.takeIf(::validService)
            ?: if (serviceValue.isBlank()) null else return null
        val methodValue = byId[GrpcNetworkConditionProtocol.methodFieldId].orEmpty()
        val method = methodValue.takeIf(String::isNotEmpty)
            ?.takeIf(::validName)
            ?: if (methodValue.isBlank()) null else return null
        return NetworkConditionProtocolCriteria(
            protocolId = NetworkConditionProtocolId.GRPC,
            encodedPayload = buildJsonObject {
                put(VERSION_FIELD, CRITERIA_VERSION)
                put(SERVICE_FIELD, service?.let(::JsonPrimitive) ?: JsonNull)
                put(METHOD_FIELD, method?.let(::JsonPrimitive) ?: JsonNull)
            }.toString(),
        )
    }

    override fun suggestCriteria(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolCriteria? {
        val observation = inspectHttp(input) as? Observation ?: return null
        return createCriteria(
            listOf(
                ProtocolCriteriaValue(
                    GrpcNetworkConditionProtocol.serviceFieldId,
                    observation.method.serviceName,
                ),
                ProtocolCriteriaValue(
                    GrpcNetworkConditionProtocol.methodFieldId,
                    observation.method.methodName,
                ),
            ),
        )
    }

    private fun decode(payload: String): Criteria? {
        if (payload.isBlank() || payload.length > MAXIMUM_CRITERIA_CHARACTERS) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root.keys.any { key -> key !in JSON_FIELDS }) return null
        if ((root[VERSION_FIELD] as? JsonPrimitive)?.intOrNull != CRITERIA_VERSION) return null
        val service = root.optionalString(SERVICE_FIELD)?.takeIf(::validService)
            ?: if (root[SERVICE_FIELD] == null || root[SERVICE_FIELD] == JsonNull) null else return null
        val method = root.optionalString(METHOD_FIELD)?.takeIf(::validName)
            ?: if (root[METHOD_FIELD] == null || root[METHOD_FIELD] == JsonNull) null else return null
        return Criteria(service = service, method = method)
    }

    private fun JsonObject.optionalString(key: String): String? = when (val value = this[key]) {
        null, JsonNull -> null
        else -> (value as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun validService(value: String): Boolean =
        value.length <= MAXIMUM_NAME_CHARACTERS && value.split('.').all(::validName)

    private fun validName(value: String): Boolean =
        value.length <= MAXIMUM_NAME_CHARACTERS && NAME.matches(value)

    private data class Criteria(
        val service: String? = null,
        val method: String? = null,
    )

    private data class Observation(
        val method: GrpcMethodIdentity,
    ) : NetworkConditionProtocolObservation {
        override val protocolId: NetworkConditionProtocolId = NetworkConditionProtocolId.GRPC
    }

    private class CompiledCriteria(
        private val criteria: Criteria,
    ) : CompiledNetworkConditionProtocolCriteria {
        override val protocolId: NetworkConditionProtocolId = NetworkConditionProtocolId.GRPC

        override fun matches(observation: NetworkConditionProtocolObservation?): Boolean {
            val grpc = observation as? Observation ?: return false
            return (criteria.service == null || criteria.service == grpc.method.serviceName) &&
                (criteria.method == null || criteria.method == grpc.method.methodName)
        }
    }

    private companion object {
        const val CRITERIA_VERSION: Int = 1
        const val VERSION_FIELD: String = "version"
        const val SERVICE_FIELD: String = "service"
        const val METHOD_FIELD: String = "method"
        const val CONTENT_TYPE: String = "content-type"
        const val MAXIMUM_CRITERIA_CHARACTERS: Int = 4_096
        const val MAXIMUM_NAME_CHARACTERS: Int = 512
        val NAME: Regex = Regex("^[_A-Za-z][_0-9A-Za-z]*$")
        val FIELD_IDS: Set<ProtocolCriteriaFieldId> = setOf(
            GrpcNetworkConditionProtocol.serviceFieldId,
            GrpcNetworkConditionProtocol.methodFieldId,
        )
        val JSON_FIELDS: Set<String> = setOf(VERSION_FIELD, SERVICE_FIELD, METHOD_FIELD)
    }
}
