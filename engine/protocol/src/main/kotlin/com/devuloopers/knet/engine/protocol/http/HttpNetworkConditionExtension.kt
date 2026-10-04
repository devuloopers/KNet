package com.devuloopers.knet.engine.protocol.http

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaOption
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.CompiledNetworkConditionProtocolCriteria
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionInterceptionUnit
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolExtension
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolObservation
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolCriteria
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.traffic.model.pathWithoutQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Stable editor-field identities owned by HTTP Network Conditions rules. */
object HttpNetworkConditionProtocol {
    /** Optional exact HTTP method selector. */
    val methodFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("method")

    /** Optional raw request path selector with the query string removed. */
    val pathFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("path")

    /** Exact or path-segment-prefix match mode. */
    val pathModeFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("path-mode")
}

/** Header-only HTTP method/path Network Conditions contribution. */
class HttpNetworkConditionExtension(
    private val json: Json = Json { ignoreUnknownKeys = false },
) : NetworkConditionProtocolExtension {
    override val suggestionPriority: Int = 10
    override val editorOrder: Int = 100

    override val definition: NetworkConditionProtocolDefinition = NetworkConditionProtocolDefinition(
        protocolId = NetworkConditionProtocolId.HTTP,
        displayName = "HTTP",
        criteriaVersion = CRITERIA_VERSION,
        interceptionUnit = NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Text(
                id = HttpNetworkConditionProtocol.methodFieldId,
                label = "Request Method",
                description = "Optional exact HTTP method. Leave empty to match every method.",
                placeholder = "e.g. GET",
                optional = true,
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = HttpNetworkConditionProtocol.pathFieldId,
                label = "Request Path",
                description = "Optional raw path without the query string. Leave empty to match every path.",
                placeholder = "e.g. /api/videos",
                optional = true,
            ),
            ProtocolCriteriaFieldDefinition.Choice(
                id = HttpNetworkConditionProtocol.pathModeFieldId,
                label = "Path Match",
                description = "A prefix matches the selected path and its child path segments.",
                options = listOf(
                    ProtocolCriteriaOption(PATH_EXACT, "Exact path"),
                    ProtocolCriteriaOption(PATH_PREFIX, "Path prefix"),
                ),
                defaultValue = PATH_EXACT,
            ),
        ),
    )

    override fun compile(
        criteria: NetworkConditionProtocolCriteria,
    ): CompiledNetworkConditionProtocolCriteria? {
        if (criteria.protocolId != NetworkConditionProtocolId.HTTP) return null
        return decode(criteria.encodedPayload)?.let(::CompiledCriteria)
    }

    override fun inspectHttp(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolObservation? {
        val path = input.request.pathWithoutQuery() ?: return null
        return Observation(
            method = input.request.head.method.token.uppercase(),
            path = path,
        )
    }

    override fun editorValues(criteria: NetworkConditionProtocolCriteria): List<ProtocolCriteriaValue> {
        val decoded = criteria.takeIf { value -> value.protocolId == NetworkConditionProtocolId.HTTP }
            ?.encodedPayload
            ?.let(::decode)
            ?: Criteria()
        return listOf(
            ProtocolCriteriaValue(HttpNetworkConditionProtocol.methodFieldId, decoded.method.orEmpty()),
            ProtocolCriteriaValue(HttpNetworkConditionProtocol.pathFieldId, decoded.path.orEmpty()),
            ProtocolCriteriaValue(HttpNetworkConditionProtocol.pathModeFieldId, decoded.pathMode),
        )
    }

    override fun createCriteria(values: List<ProtocolCriteriaValue>): NetworkConditionProtocolCriteria? {
        if (values.any { value -> value.fieldId !in FIELD_IDS }) return null
        val byId = values.associate { value -> value.fieldId to value.value.trim() }
        val methodValue = byId[HttpNetworkConditionProtocol.methodFieldId].orEmpty()
        val method = methodValue.takeIf(String::isNotEmpty)?.uppercase()
            ?.takeIf(::validMethod)
            ?: if (methodValue.isBlank()) null else return null
        val pathValue = byId[HttpNetworkConditionProtocol.pathFieldId].orEmpty()
        val path = pathValue.takeIf(String::isNotEmpty)
            ?.takeIf(::validPath)
            ?: if (pathValue.isBlank()) null else return null
        val pathMode = byId[HttpNetworkConditionProtocol.pathModeFieldId].orEmpty()
            .ifBlank { PATH_EXACT }
            .takeIf(PATH_MODES::contains)
            ?: return null
        return NetworkConditionProtocolCriteria(
            protocolId = NetworkConditionProtocolId.HTTP,
            encodedPayload = buildJsonObject {
                put(VERSION_FIELD, CRITERIA_VERSION)
                put(METHOD_FIELD, method?.let(::JsonPrimitive) ?: JsonNull)
                put(PATH_FIELD, path?.let(::JsonPrimitive) ?: JsonNull)
                put(PATH_MODE_FIELD, pathMode)
            }.toString(),
        )
    }

    override fun suggestCriteria(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolCriteria? {
        val observation = inspectHttp(input) as? Observation ?: return null
        return createCriteria(
            listOf(
                ProtocolCriteriaValue(HttpNetworkConditionProtocol.methodFieldId, observation.method),
                ProtocolCriteriaValue(HttpNetworkConditionProtocol.pathFieldId, observation.path),
                ProtocolCriteriaValue(HttpNetworkConditionProtocol.pathModeFieldId, PATH_EXACT),
            ),
        )
    }

    private fun decode(payload: String): Criteria? {
        if (payload.isBlank() || payload.length > MAXIMUM_CRITERIA_CHARACTERS) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root.keys.any { key -> key !in JSON_FIELDS }) return null
        if ((root[VERSION_FIELD] as? JsonPrimitive)?.intOrNull != CRITERIA_VERSION) return null
        val method = root.optionalString(METHOD_FIELD)?.uppercase()?.takeIf(::validMethod)
            ?: if (root[METHOD_FIELD] == null || root[METHOD_FIELD] == JsonNull) null else return null
        val path = root.optionalString(PATH_FIELD)?.takeIf(::validPath)
            ?: if (root[PATH_FIELD] == null || root[PATH_FIELD] == JsonNull) null else return null
        val pathMode = (root[PATH_MODE_FIELD] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(PATH_MODES::contains)
            ?: return null
        return Criteria(method = method, path = path, pathMode = pathMode)
    }

    private fun JsonObject.optionalString(key: String): String? = when (val value = this[key]) {
        null, JsonNull -> null
        else -> (value as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun validMethod(value: String): Boolean =
        value.length <= MAXIMUM_METHOD_CHARACTERS && METHOD_TOKEN.matches(value)

    private fun validPath(value: String): Boolean =
        value.length <= MAXIMUM_PATH_CHARACTERS && value.startsWith('/') && value.none(Char::isISOControl)

    private data class Criteria(
        val method: String? = null,
        val path: String? = null,
        val pathMode: String = PATH_EXACT,
    )

    private data class Observation(
        val method: String,
        val path: String,
    ) : NetworkConditionProtocolObservation {
        override val protocolId: NetworkConditionProtocolId = NetworkConditionProtocolId.HTTP
    }

    private class CompiledCriteria(
        private val criteria: Criteria,
    ) : CompiledNetworkConditionProtocolCriteria {
        override val protocolId: NetworkConditionProtocolId = NetworkConditionProtocolId.HTTP

        override fun matches(observation: NetworkConditionProtocolObservation?): Boolean {
            val http = observation as? Observation ?: return false
            return (criteria.method == null || criteria.method == http.method) && pathMatches(http.path)
        }

        private fun pathMatches(candidate: String): Boolean = when {
            criteria.path == null -> true
            criteria.pathMode == PATH_EXACT -> candidate == criteria.path
            criteria.path == "/" -> true
            else -> candidate == criteria.path || candidate.startsWith("${criteria.path.trimEnd('/')}/")
        }
    }

    private companion object {
        const val CRITERIA_VERSION: Int = 1
        const val VERSION_FIELD: String = "version"
        const val METHOD_FIELD: String = "method"
        const val PATH_FIELD: String = "path"
        const val PATH_MODE_FIELD: String = "pathMode"
        const val PATH_EXACT: String = "exact"
        const val PATH_PREFIX: String = "prefix"
        const val MAXIMUM_CRITERIA_CHARACTERS: Int = 4_096
        const val MAXIMUM_METHOD_CHARACTERS: Int = 64
        const val MAXIMUM_PATH_CHARACTERS: Int = 2_048
        val METHOD_TOKEN: Regex = Regex("^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$")
        val PATH_MODES: Set<String> = setOf(PATH_EXACT, PATH_PREFIX)
        val FIELD_IDS: Set<ProtocolCriteriaFieldId> = setOf(
            HttpNetworkConditionProtocol.methodFieldId,
            HttpNetworkConditionProtocol.pathFieldId,
            HttpNetworkConditionProtocol.pathModeFieldId,
        )
        val JSON_FIELDS: Set<String> = setOf(VERSION_FIELD, METHOD_FIELD, PATH_FIELD, PATH_MODE_FIELD)
    }
}
