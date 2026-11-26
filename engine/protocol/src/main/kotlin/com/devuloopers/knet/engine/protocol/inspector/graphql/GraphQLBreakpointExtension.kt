package com.devuloopers.knet.engine.protocol.inspector.graphql

import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolExtension
import com.devuloopers.knet.application.contract.breakpoint.BreakpointRuleSuggestionInput
import com.devuloopers.knet.application.contract.breakpoint.CompiledProtocolCriteria
import com.devuloopers.knet.application.contract.breakpoint.GraphQLBreakpointMatch
import com.devuloopers.knet.application.contract.breakpoint.GraphQLBreakpointScope
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaOption
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.breakpoint.ProtocolInspectionInput
import com.devuloopers.knet.application.contract.breakpoint.ProtocolObservation
import com.devuloopers.knet.domain.graphql.GraphQLQueryHash
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Public identities used by GraphQL breakpoint criteria and extension registration. */
object GraphQLBreakpointProtocol {
    /** Stable GraphQL extension identity. */
    val id: BreakpointProtocolId = GraphQLBreakpointMatch.protocolId

    /** Optional operation-name editor field. */
    val operationNameFieldId: ProtocolCriteriaFieldId = GraphQLBreakpointMatch.operationNameField
}

/** Compact GraphQL request facts retained across request and response breakpoint phases. */
private data class GraphQLBreakpointObservation(
    val verifiedNames: List<String?>,
    val hash: GraphQLQueryHash?,
    val requestOperationName: String?,
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
        criteriaVersion = EXACT_CRITERIA_VERSION,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Choice(
                id = GraphQLBreakpointMatch.scopeField,
                label = "Match",
                options = GraphQLBreakpointScope.entries.map { ProtocolCriteriaOption(it.token, it.label) },
                defaultValue = GraphQLBreakpointScope.CHOOSE.token,
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = GraphQLBreakpointProtocol.operationNameFieldId,
                label = "Operation Name",
                placeholder = "e.g. GetUserProfile or UpdateCart",
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = GraphQLBreakpointMatch.hashField,
                label = "SHA-256",
            ),
            ProtocolCriteriaFieldDefinition.Text(
                id = GraphQLBreakpointMatch.requestOperationField,
                label = "Operation name from request",
            ),
        ),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? {
        if (criteria.protocolId != GraphQLBreakpointProtocol.id) return null
        val decoded = decodeCriteria(criteria.encodedPayload) ?: return null
        return GraphQLCompiledCriteria(decoded)
    }

    override fun inspect(input: ProtocolInspectionInput): ProtocolObservation? {
        val retained = input.requestObservation as? GraphQLBreakpointObservation
        if (input.candidate.phase == BreakpointPhase.RESPONSE && retained != null) return retained

        return inspectRequest(
            request = input.candidate.request,
            bodyBytes = input.candidate.requestBody?.takeIf { it.size.toLong() >= input.candidate.requestObservedBodyBytes }?.copyBytes(),
        )
    }

    override fun suggestCriteria(input: BreakpointRuleSuggestionInput): ProtocolMatchCriteria? {
        val observation = inspectRequest(
            request = input.request,
            bodyBytes = input.requestBody
                ?.takeIf { input.requestBodyComplete }
                ?.copyBytes(),
        ) ?: return null
        val verifiedName = observation.verifiedNames.singleOrNull()
        val hash = observation.hash
        return when {
            hash != null -> encodeExact(hash, observation.requestOperationName, verifiedName)
            verifiedName != null -> encodeLegacy(verifiedName)
            // The draft remains GraphQL, but its placeholder cannot match any request.
            else -> encode(buildJsonObject { put(VERSION_FIELD, EXACT_CRITERIA_VERSION) })
        }
    }

    private fun inspectRequest(
        request: HttpRequestSnapshot,
        bodyBytes: ByteArray?,
    ): GraphQLBreakpointObservation? {
        val absoluteUrl = request.absoluteUrl()
        val contentType = request.head.headers.firstOrNull {
            it.name.value.equals(CONTENT_TYPE_HEADER, ignoreCase = true)
        }?.value.orEmpty()
        val document = parser.decodeHttp(request.head.method.token, absoluteUrl, contentType, bodyBytes, learnCaptured = true, headers = request.head.headers.map { it.name.value to it.value }) ?: return null
        return GraphQLBreakpointObservation(
            verifiedNames = document.matchableOperations.map { it.name },
            hash = document.persistedQuery?.hash?.takeIf { document.diagnostic == null },
            requestOperationName = document.suppliedOperationName?.takeIf(String::isNotBlank),
        )
    }

    override fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> {
        if (criteria.protocolId != GraphQLBreakpointProtocol.id) return emptyList()
        val decoded = decodeCriteria(criteria.encodedPayload) ?: return emptyList()
        val name = ProtocolCriteriaValue(GraphQLBreakpointMatch.operationNameField, decoded.operationName.orEmpty())
        return when (decoded.scope) {
            GraphQLBreakpointScope.EXACT -> listOf(
                ProtocolCriteriaValue(GraphQLBreakpointMatch.scopeField, decoded.scope.token),
                ProtocolCriteriaValue(GraphQLBreakpointMatch.hashField, checkNotNull(decoded.hash).value),
                ProtocolCriteriaValue(GraphQLBreakpointMatch.requestOperationField, decoded.requestOperationName.orEmpty()),
                name,
            )
            GraphQLBreakpointScope.CHOOSE -> listOf(ProtocolCriteriaValue(GraphQLBreakpointMatch.scopeField, decoded.scope.token))
            else -> listOf(name)
        }
    }

    override fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria? {
        val entered = values.associate { it.fieldId to it.value.trim() }
        if (entered.size != values.size || entered.keys.any { id -> definition.fields.none { it.id == id } }) return null
        val operation = entered[GraphQLBreakpointMatch.operationNameField]?.takeIf(String::isNotEmpty)
        val scopeToken = entered[GraphQLBreakpointMatch.scopeField]
        if (scopeToken == null) {
            // Preserve the programmatic v1 editor contract and older saved rule semantics.
            if (entered.keys.any { it != GraphQLBreakpointMatch.operationNameField } ||
                operation != null && !GraphQLBreakpointMatch.isOperationName(operation)) return null
            return encodeLegacy(operation)
        }
        return when (GraphQLBreakpointScope.entries.firstOrNull { it.token == scopeToken }) {
            GraphQLBreakpointScope.ENDPOINT -> encodeLegacy(null)
            GraphQLBreakpointScope.OPERATION -> operation?.takeIf(GraphQLBreakpointMatch::isOperationName)?.let(::encodeLegacy)
            GraphQLBreakpointScope.EXACT -> {
                val hash = entered[GraphQLBreakpointMatch.hashField]?.let(GraphQLBreakpointMatch::parseHash) ?: return null
                val requestName = entered[GraphQLBreakpointMatch.requestOperationField]?.takeIf(String::isNotEmpty)
                if (requestName != null && !GraphQLBreakpointMatch.isOperationName(requestName)) return null
                encodeExact(hash, requestName, operation?.takeIf(GraphQLBreakpointMatch::isOperationName))
            }
            GraphQLBreakpointScope.CHOOSE, null -> null
        }
    }

    private fun encodeLegacy(operation: String?): ProtocolMatchCriteria = encode(buildJsonObject {
        put(VERSION_FIELD, LEGACY_CRITERIA_VERSION)
        put(OPERATION_NAME_FIELD, operation?.let(::JsonPrimitive) ?: JsonNull)
    })

    private fun encodeExact(hash: GraphQLQueryHash, requestName: String?, operation: String?): ProtocolMatchCriteria =
        encode(buildJsonObject {
            put(VERSION_FIELD, EXACT_CRITERIA_VERSION)
            put(HASH_FIELD, hash.value)
            if (requestName != null) put(REQUEST_OPERATION_FIELD, requestName)
            if (operation != null) put(OPERATION_NAME_FIELD, operation)
        })

    private fun encode(payload: JsonObject): ProtocolMatchCriteria =
        ProtocolMatchCriteria(GraphQLBreakpointProtocol.id, payload.toString())

    private fun decodeCriteria(payload: String): GraphQLCriteria? {
        if (payload.isBlank() || payload.length > MAXIMUM_CRITERIA_CHARACTERS) return null
        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return null
        return runCatching {
            when ((root[VERSION_FIELD] as? JsonPrimitive)?.intOrNull) {
                LEGACY_CRITERIA_VERSION -> {
                    require(root.keys.all { it in LEGACY_FIELDS })
                    val operation = root.operationName(OPERATION_NAME_FIELD)
                    GraphQLCriteria(if (operation == null) GraphQLBreakpointScope.ENDPOINT else GraphQLBreakpointScope.OPERATION,
                        operationName = operation)
                }
                EXACT_CRITERIA_VERSION -> {
                    require(root.keys.all { it in EXACT_FIELDS })
                    if (root.keys == setOf(VERSION_FIELD)) GraphQLCriteria(GraphQLBreakpointScope.CHOOSE)
                    else {
                        val hash = root[HASH_FIELD] as? JsonPrimitive ?: return null
                        require(hash.isString)
                        GraphQLCriteria(GraphQLBreakpointScope.EXACT, GraphQLQueryHash(hash.content),
                            root.operationName(REQUEST_OPERATION_FIELD), root.operationName(OPERATION_NAME_FIELD))
                    }
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun JsonObject.operationName(field: String): String? {
        val value = this[field] ?: return null
        if (value == JsonNull) return null
        require(value is JsonPrimitive && value.isString)
        return value.content.trim().also { require(GraphQLBreakpointMatch.isOperationName(it)) }
    }

    private data class GraphQLCriteria(
        val scope: GraphQLBreakpointScope,
        val hash: GraphQLQueryHash? = null,
        val requestOperationName: String? = null,
        val operationName: String? = null,
    )

    private class GraphQLCompiledCriteria(private val criteria: GraphQLCriteria) : CompiledProtocolCriteria {
        override val protocolId: BreakpointProtocolId = GraphQLBreakpointProtocol.id
        override val requiresExactEndpoint: Boolean = criteria.scope == GraphQLBreakpointScope.EXACT

        override fun matches(observation: ProtocolObservation?): Boolean {
            val graphQL = observation as? GraphQLBreakpointObservation ?: return false
            return when (criteria.scope) {
                GraphQLBreakpointScope.CHOOSE -> false
                GraphQLBreakpointScope.ENDPOINT -> true
                GraphQLBreakpointScope.OPERATION -> criteria.operationName in graphQL.verifiedNames
                GraphQLBreakpointScope.EXACT -> graphQL.hash == criteria.hash &&
                    (criteria.requestOperationName == null || graphQL.requestOperationName == criteria.requestOperationName)
            }
        }
    }

    private companion object {
        const val LEGACY_CRITERIA_VERSION = 1
        const val EXACT_CRITERIA_VERSION = 2
        const val VERSION_FIELD = "version"
        const val OPERATION_NAME_FIELD = "operationName"
        const val HASH_FIELD = "sha256"
        const val REQUEST_OPERATION_FIELD = "requestOperationName"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val MAXIMUM_CRITERIA_CHARACTERS = 4_096
        val LEGACY_FIELDS = setOf(VERSION_FIELD, OPERATION_NAME_FIELD)
        val EXACT_FIELDS = LEGACY_FIELDS + setOf(HASH_FIELD, REQUEST_OPERATION_FIELD)
    }
}
