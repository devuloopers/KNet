package com.devuloopers.knet.engine.protocol.inspector.graphql

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
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.model.absoluteUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Stable field identities used by GraphQL HTTP Network Conditions rules. */
object GraphQLNetworkConditionProtocol {
    /** Optional exact operation-name selector. */
    val operationNameFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("operation-name")

    /** Query, mutation, subscription, or any operation selector. */
    val operationTypeFieldId: ProtocolCriteriaFieldId = ProtocolCriteriaFieldId("operation-type")
}

/**
 * Bounded semantic condition extension for GraphQL operations delivered over HTTP.
 *
 * Matching is intentionally based only on compact operation name/type facts. Variables and complete query text
 * never enter persisted rules or the bandwidth scheduler.
 */
class GraphQLNetworkConditionExtension(
    private val parser: GraphQLDocumentParser = GraphQLDocumentParser(),
    private val json: Json = Json { ignoreUnknownKeys = false },
) : NetworkConditionProtocolExtension {
    override val suggestionPriority: Int = 100
    override val editorOrder: Int = 200

    override val definition: NetworkConditionProtocolDefinition = NetworkConditionProtocolDefinition(
        protocolId = NetworkConditionProtocolId.GRAPHQL_HTTP,
        displayName = "GraphQL",
        criteriaVersion = CRITERIA_VERSION,
        interceptionUnit = NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
        requiresRequestBody = true,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Text(
                id = GraphQLNetworkConditionProtocol.operationNameFieldId,
                label = "Operation Name",
                description = "Leave empty to shape every detected GraphQL operation on this destination.",
                placeholder = "e.g. LoadVideoFeed",
                optional = true,
            ),
            ProtocolCriteriaFieldDefinition.Choice(
                id = GraphQLNetworkConditionProtocol.operationTypeFieldId,
                label = "Operation Type",
                description = "Optionally restrict the condition to queries, mutations, or subscriptions.",
                options = listOf(
                    ProtocolCriteriaOption(ANY, "Every operation type"),
                    ProtocolCriteriaOption(QUERY, "Query"),
                    ProtocolCriteriaOption(MUTATION, "Mutation"),
                    ProtocolCriteriaOption(SUBSCRIPTION, "Subscription"),
                ),
                defaultValue = ANY,
            ),
        ),
    )

    override fun compile(
        criteria: NetworkConditionProtocolCriteria,
    ): CompiledNetworkConditionProtocolCriteria? {
        if (criteria.protocolId != NetworkConditionProtocolId.GRAPHQL_HTTP) return null
        return decode(criteria.encodedPayload)?.let(::CompiledCriteria)
    }

    override fun inspectHttp(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolObservation? {
        val absoluteUrl = input.request.absoluteUrl()
        val contentType = input.request.head.headers.firstOrNull { header ->
            header.name.value.equals(CONTENT_TYPE_HEADER, ignoreCase = true)
        }?.value.orEmpty()
        val endpointHint = absoluteUrl.contains(GRAPHQL_PATH_HINT, ignoreCase = true) ||
            contentType.contains(GRAPHQL_MEDIA_HINT, ignoreCase = true)
        val document = input.requestBody
            ?.takeIf { input.requestBodyComplete }
            ?.copyBytes()
            ?.let(parser::parse)
        if (document != null) {
            return Observation(
                document.operations.map { operation -> Operation(operation.name, operation.type.token()) },
            )
        }
        val operationName = queryParameter(absoluteUrl, OPERATION_NAME_QUERY_PARAMETER)
            ?.takeIf(::validOperationName)
        return if (endpointHint) {
            Observation(operationName?.let { name -> listOf(Operation(name, QUERY)) }.orEmpty())
        } else {
            null
        }
    }

    override fun shouldInspectHttpBody(input: NetworkConditionHttpInspectionInput): Boolean {
        val absoluteUrl = input.request.absoluteUrl()
        if (absoluteUrl.contains(GRAPHQL_PATH_HINT, ignoreCase = true)) return true
        val contentType = input.request.head.headers.firstOrNull { header ->
            header.name.value.equals(CONTENT_TYPE_HEADER, ignoreCase = true)
        }?.value?.substringBefore(';')?.trim()?.lowercase()
        return when {
            contentType == null -> input.request.head.method.token.equals(POST_METHOD, ignoreCase = true)
            contentType.contains(GRAPHQL_MEDIA_HINT) -> true
            contentType == JSON_MEDIA_TYPE || contentType.endsWith(JSON_SUFFIX) -> true
            else -> false
        }
    }

    override fun editorValues(criteria: NetworkConditionProtocolCriteria): List<ProtocolCriteriaValue> {
        val decoded = criteria.takeIf { it.protocolId == NetworkConditionProtocolId.GRAPHQL_HTTP }
            ?.encodedPayload
            ?.let(::decode)
            ?: Criteria()
        return listOf(
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationNameFieldId, decoded.operationName.orEmpty()),
            ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationTypeFieldId, decoded.operationType),
        )
    }

    override fun createCriteria(values: List<ProtocolCriteriaValue>): NetworkConditionProtocolCriteria? {
        if (values.any { value -> value.fieldId !in FIELD_IDS }) return null
        val byId = values.associate { value -> value.fieldId to value.value.trim() }
        val operationName = byId[GraphQLNetworkConditionProtocol.operationNameFieldId]
            ?.takeIf(String::isNotEmpty)
            ?.takeIf(::validOperationName)
            ?: if (byId[GraphQLNetworkConditionProtocol.operationNameFieldId].isNullOrBlank()) null else return null
        val operationType = byId[GraphQLNetworkConditionProtocol.operationTypeFieldId].orEmpty()
            .ifBlank { ANY }
            .takeIf(TYPES::contains)
            ?: return null
        return NetworkConditionProtocolCriteria(
            protocolId = NetworkConditionProtocolId.GRAPHQL_HTTP,
            encodedPayload = buildJsonObject {
                put(VERSION_FIELD, CRITERIA_VERSION)
                put(OPERATION_NAME_FIELD, operationName?.let(::JsonPrimitive) ?: JsonNull)
                put(OPERATION_TYPE_FIELD, operationType)
            }.toString(),
        )
    }

    override fun suggestCriteria(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolCriteria? {
        criteriaFromTrafficAnnotation(input)?.let { criteria -> return criteria }
        val observation = inspectHttp(input) as? Observation ?: return null
        val operation = observation.operations.singleOrNull()
        return createCriteria(
            listOf(
                ProtocolCriteriaValue(
                    GraphQLNetworkConditionProtocol.operationNameFieldId,
                    operation?.name.orEmpty(),
                ),
                ProtocolCriteriaValue(
                    GraphQLNetworkConditionProtocol.operationTypeFieldId,
                    operation?.type ?: ANY,
                ),
            ),
        )
    }

    private fun criteriaFromTrafficAnnotation(
        input: NetworkConditionHttpInspectionInput,
    ): NetworkConditionProtocolCriteria? {
        val document = input.trafficAnnotations.asSequence()
            .filter { annotation ->
                annotation.inspectorId == GraphQLInspectionSchema.inspectorId &&
                    annotation.schemaVersion == GraphQLInspectionSchema.VERSION &&
                    annotation.state == InspectionAnnotationState.COMPLETED
            }
            .sortedByDescending { annotation -> annotation.createdAtEpochMillis }
            .mapNotNull { annotation -> annotation.document }
            .firstOrNull { candidate -> candidate.kind == GraphQLInspectionSchema.DOCUMENT_KIND }
            ?: return null
        val fields = document.fields.associate { field -> field.label to field.value.trim() }
        val batchSize = fields[GraphQLInspectionSchema.BATCH_SIZE_FIELD]?.toIntOrNull() ?: 1
        if (batchSize > 1) return createCriteria(emptyList())
        val operationName = fields[GraphQLInspectionSchema.OPERATION_NAME_FIELD].orEmpty()
        val operationType = when (fields[GraphQLInspectionSchema.OPERATION_TYPE_FIELD]?.lowercase()) {
            QUERY -> QUERY
            MUTATION -> MUTATION
            SUBSCRIPTION -> SUBSCRIPTION
            else -> ANY
        }
        return createCriteria(
            listOf(
                ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationNameFieldId, operationName),
                ProtocolCriteriaValue(GraphQLNetworkConditionProtocol.operationTypeFieldId, operationType),
            ),
        )
    }

    private fun decode(payload: String): Criteria? {
        if (payload.isBlank() || payload.length > MAXIMUM_CRITERIA_CHARACTERS) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        if (root.keys.any { key -> key !in JSON_FIELDS }) return null
        if ((root[VERSION_FIELD] as? JsonPrimitive)?.intOrNull != CRITERIA_VERSION) return null
        val operationName = when (val value = root[OPERATION_NAME_FIELD]) {
            null, JsonNull -> null
            else -> (value as? JsonPrimitive)?.contentOrNull?.takeIf(::validOperationName) ?: return null
        }
        val operationType = (root[OPERATION_TYPE_FIELD] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(TYPES::contains)
            ?: return null
        return Criteria(operationName, operationType)
    }

    private fun queryParameter(url: String, name: String): String? = url
        .substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .asSequence()
        .map { parameter -> parameter.substringBefore('=') to parameter.substringAfter('=', "") }
        .firstOrNull { parameter -> parameter.first == name }
        ?.second
        ?.takeIf(String::isNotBlank)

    private fun validOperationName(value: String): Boolean =
        value.length <= MAXIMUM_OPERATION_NAME_CHARACTERS && GRAPHQL_NAME.matches(value)

    private data class Criteria(
        val operationName: String? = null,
        val operationType: String = ANY,
    )

    private data class Operation(val name: String?, val type: String)

    private data class Observation(
        val operations: List<Operation>,
    ) : NetworkConditionProtocolObservation {
        override val protocolId: NetworkConditionProtocolId = NetworkConditionProtocolId.GRAPHQL_HTTP
    }

    private class CompiledCriteria(
        private val criteria: Criteria,
    ) : CompiledNetworkConditionProtocolCriteria {
        override val protocolId: NetworkConditionProtocolId = NetworkConditionProtocolId.GRAPHQL_HTTP

        override fun matches(observation: NetworkConditionProtocolObservation?): Boolean {
            val graphQL = observation as? Observation ?: return false
            if (criteria.operationName == null && criteria.operationType == ANY) return true
            return graphQL.operations.any { operation ->
                (criteria.operationName == null || criteria.operationName == operation.name) &&
                    (criteria.operationType == ANY || criteria.operationType == operation.type)
            }
        }
    }

    private companion object {
        const val ANY: String = "any"
        const val QUERY: String = "query"
        const val MUTATION: String = "mutation"
        const val SUBSCRIPTION: String = "subscription"
        const val CRITERIA_VERSION: Int = 1
        const val VERSION_FIELD: String = "version"
        const val OPERATION_NAME_FIELD: String = "operationName"
        const val OPERATION_TYPE_FIELD: String = "operationType"
        const val CONTENT_TYPE_HEADER: String = "Content-Type"
        const val GRAPHQL_PATH_HINT: String = "graphql"
        const val GRAPHQL_MEDIA_HINT: String = "graphql"
        const val JSON_MEDIA_TYPE: String = "application/json"
        const val JSON_SUFFIX: String = "+json"
        const val POST_METHOD: String = "POST"
        const val OPERATION_NAME_QUERY_PARAMETER: String = "operationName"
        const val MAXIMUM_OPERATION_NAME_CHARACTERS: Int = 256
        const val MAXIMUM_CRITERIA_CHARACTERS: Int = 4_096
        val TYPES: Set<String> = setOf(ANY, QUERY, MUTATION, SUBSCRIPTION)
        val FIELD_IDS: Set<ProtocolCriteriaFieldId> = setOf(
            GraphQLNetworkConditionProtocol.operationNameFieldId,
            GraphQLNetworkConditionProtocol.operationTypeFieldId,
        )
        val JSON_FIELDS: Set<String> = setOf(VERSION_FIELD, OPERATION_NAME_FIELD, OPERATION_TYPE_FIELD)
        val GRAPHQL_NAME: Regex = Regex("^[_A-Za-z][_0-9A-Za-z]*$")
    }
}

private fun GraphQLOperationType.token(): String = when (this) {
    GraphQLOperationType.QUERY -> "query"
    GraphQLOperationType.MUTATION -> "mutation"
    GraphQLOperationType.SUBSCRIPTION -> "subscription"
}
