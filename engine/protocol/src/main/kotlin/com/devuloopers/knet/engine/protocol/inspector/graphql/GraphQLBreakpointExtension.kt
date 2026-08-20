package com.devuloopers.knet.engine.protocol.inspector.graphql

import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolExtension
import com.devuloopers.knet.application.port.breakpoint.BreakpointRuleSuggestionInput
import com.devuloopers.knet.application.port.breakpoint.CompiledProtocolCriteria
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.port.breakpoint.ProtocolInspectionInput
import com.devuloopers.knet.application.port.breakpoint.ProtocolObservation
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.absoluteUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Public identities used by GraphQL breakpoint criteria and extension registration. */
object GraphQLBreakpointProtocol {
    /** Stable GraphQL extension identity. */
    val id: BreakpointProtocolId = BreakpointProtocolId("graphql")

    /** Optional operation-name editor field. */
    val operationNameFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("operation-name")
}

/** One bounded GraphQL operation fact retained for live matching. */
private data class GraphQLBreakpointOperation(
    val name: String?,
    val type: GraphQLOperationType,
)

/** Compact GraphQL request facts retained across request and response breakpoint phases. */
private data class GraphQLBreakpointObservation(
    val operations: List<GraphQLBreakpointOperation>,
) : ProtocolObservation {
    override val protocolId: BreakpointProtocolId = GraphQLBreakpointProtocol.id
}

/**
 * Additive live-breakpoint extension for GraphQL HTTP requests.
 *
 * The extension owns its versioned JSON criteria, parser, typed observation, and compiled matcher.
 * Core breakpoint coordination therefore contains no GraphQL branches.
 */
class GraphQLBreakpointExtension(
    private val parser: GraphQLDocumentParser = GraphQLDocumentParser(),
    private val json: Json = Json { ignoreUnknownKeys = false },
) : BreakpointProtocolExtension {
    override val suggestionPriority: Int = 100

    override val definition: BreakpointProtocolDefinition = BreakpointProtocolDefinition(
        protocolId = GraphQLBreakpointProtocol.id,
        displayName = "GraphQL",
        criteriaVersion = CRITERIA_VERSION,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Text(
                id = GraphQLBreakpointProtocol.operationNameFieldId,
                label = "Operation Name",
                description = "Leave empty to pause every GraphQL operation matching the HTTP filters.",
                placeholder = "e.g. GetUserProfile or UpdateCart",
                optional = true,
            ),
        ),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? {
        if (criteria.protocolId != GraphQLBreakpointProtocol.id) return null
        val decoded = decodeCriteria(criteria.encodedPayload) ?: return null
        return GraphQLCompiledCriteria(decoded.operationName)
    }

    override fun inspect(input: ProtocolInspectionInput): ProtocolObservation? {
        val retained = input.requestObservation as? GraphQLBreakpointObservation
        if (input.candidate.phase == BreakpointPhase.RESPONSE && retained != null) return retained

        return inspectRequest(
            request = input.candidate.request,
            bodyBytes = input.candidate.requestBody?.copyBytes(),
        )
    }

    override fun suggestCriteria(input: BreakpointRuleSuggestionInput): ProtocolMatchCriteria? {
        val observation = inspectRequest(
            request = input.request,
            bodyBytes = input.requestBody
                ?.takeIf { input.requestBodyComplete }
                ?.copyBytes(),
        ) ?: return null
        val operationName = observation.operations.singleOrNull()?.name.orEmpty()
        return createCriteria(
            listOf(
                ProtocolCriteriaValue(
                    fieldId = GraphQLBreakpointProtocol.operationNameFieldId,
                    value = operationName,
                ),
            ),
        )
    }

    private fun inspectRequest(
        request: HttpRequestSnapshot,
        bodyBytes: ByteArray?,
    ): GraphQLBreakpointObservation? {
        val absoluteUrl = request.absoluteUrl()
        val contentType = request.head.headers.firstOrNull {
            it.name.value.equals(CONTENT_TYPE_HEADER, ignoreCase = true)
        }?.value.orEmpty()
        val endpointHint = absoluteUrl.contains(GRAPHQL_PATH_HINT, ignoreCase = true) ||
            contentType.contains(GRAPHQL_MEDIA_HINT, ignoreCase = true)
        val document = bodyBytes?.let(parser::parse)
        if (document != null) {
            return GraphQLBreakpointObservation(
                document.operations.map { operation ->
                    GraphQLBreakpointOperation(operation.name, operation.type)
                },
            )
        }

        val operationName = queryParameter(absoluteUrl, OPERATION_NAME_QUERY_PARAMETER)
            ?.takeIf { it.length <= MAXIMUM_OPERATION_NAME_CHARACTERS && GRAPHQL_NAME.matches(it) }
        return if (endpointHint) {
            GraphQLBreakpointObservation(
                operations = operationName?.let {
                    listOf(GraphQLBreakpointOperation(it, GraphQLOperationType.QUERY))
                }.orEmpty(),
            )
        } else {
            null
        }
    }

    override fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> {
        val operationName = if (criteria.protocolId == GraphQLBreakpointProtocol.id) {
            decodeCriteria(criteria.encodedPayload)?.operationName.orEmpty()
        } else {
            ""
        }
        return listOf(
            ProtocolCriteriaValue(GraphQLBreakpointProtocol.operationNameFieldId, operationName),
        )
    }

    override fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria? {
        if (values.any { it.fieldId != GraphQLBreakpointProtocol.operationNameFieldId }) return null
        val operationName = values.firstOrNull {
            it.fieldId == GraphQLBreakpointProtocol.operationNameFieldId
        }?.value?.trim()?.takeIf(String::isNotEmpty)?.also { name ->
            if (name.length > MAXIMUM_OPERATION_NAME_CHARACTERS || !GRAPHQL_NAME.matches(name)) return null
        }
        val payload = buildJsonObject {
            put(VERSION_FIELD, CRITERIA_VERSION)
            put(OPERATION_NAME_FIELD, operationName?.let(::JsonPrimitive) ?: JsonNull)
        }
        return ProtocolMatchCriteria(
            protocolId = GraphQLBreakpointProtocol.id,
            encodedPayload = payload.toString(),
        )
    }

    private fun decodeCriteria(payload: String): GraphQLCriteria? {
        if (payload.isBlank() || payload.length > MAXIMUM_CRITERIA_CHARACTERS) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return null
        if (root.keys.any { it !in CRITERIA_FIELDS }) return null
        if ((root[VERSION_FIELD] as? JsonPrimitive)?.intOrNull != CRITERIA_VERSION) return null
        val operationName = when (val operationElement = root[OPERATION_NAME_FIELD]) {
            null, JsonNull -> null
            else -> (operationElement as? JsonPrimitive)?.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return null
        }
        if (operationName != null &&
            (operationName.length > MAXIMUM_OPERATION_NAME_CHARACTERS || !GRAPHQL_NAME.matches(operationName))
        ) {
            return null
        }
        return GraphQLCriteria(operationName)
    }

    private fun queryParameter(url: String, name: String): String? = url
        .substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .asSequence()
        .map { parameter -> parameter.substringBefore('=') to parameter.substringAfter('=', "") }
        .firstOrNull { it.first == name }
        ?.second
        ?.takeIf(String::isNotBlank)

    private data class GraphQLCriteria(val operationName: String?)

    private class GraphQLCompiledCriteria(
        private val operationName: String?,
    ) : CompiledProtocolCriteria {
        override val protocolId: BreakpointProtocolId = GraphQLBreakpointProtocol.id

        override fun matches(observation: ProtocolObservation?): Boolean {
            val graphQL = observation as? GraphQLBreakpointObservation ?: return false
            return operationName == null || graphQL.operations.any { it.name == operationName }
        }
    }

    private companion object {
        const val CRITERIA_VERSION = 1
        const val VERSION_FIELD = "version"
        const val OPERATION_NAME_FIELD = "operationName"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val GRAPHQL_PATH_HINT = "graphql"
        const val GRAPHQL_MEDIA_HINT = "graphql"
        const val OPERATION_NAME_QUERY_PARAMETER = "operationName"
        const val MAXIMUM_OPERATION_NAME_CHARACTERS = 256
        const val MAXIMUM_CRITERIA_CHARACTERS = 4_096
        val CRITERIA_FIELDS = setOf(VERSION_FIELD, OPERATION_NAME_FIELD)
        val GRAPHQL_NAME = Regex("^[_A-Za-z][_0-9A-Za-z]*$")
    }
}
