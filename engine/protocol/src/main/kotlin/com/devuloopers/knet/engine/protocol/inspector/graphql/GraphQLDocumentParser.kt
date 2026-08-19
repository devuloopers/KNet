package com.devuloopers.knet.engine.protocol.inspector.graphql

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** GraphQL operation category extracted from an HTTP request document. */
enum class GraphQLOperationType {
    QUERY,
    MUTATION,
    SUBSCRIPTION,
}

/** One parsed operation from a single or batched GraphQL request body. */
data class GraphQLOperation(
    /** Explicit or document-derived operation name, or null for an anonymous operation. */
    val name: String?,
    /** Parsed operation category. */
    val type: GraphQLOperationType,
    /** Bounded normalized query summary suitable for presentation. */
    val summary: String,
)

/** Immutable parsed GraphQL body containing one or more batched operations. */
data class GraphQLDocument(
    /** Operations found in request order. */
    val operations: List<GraphQLOperation>,
) {
    init {
        require(operations.isNotEmpty()) { "A GraphQL document requires at least one operation." }
    }
}

/**
 * Kotlin serialization based parser shared by live breakpoint and asynchronous GraphQL inspection.
 *
 * Parsing is bounded by the body owner before this class is invoked. Invalid JSON and ordinary
 * JSON documents return null rather than being classified as GraphQL.
 */
class GraphQLDocumentParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Parses a GraphQL JSON object or batched JSON array from [body]. */
    fun parse(body: ByteArray): GraphQLDocument? {
        if (body.isEmpty()) return null
        val text = runCatching { body.decodeToString().trim() }.getOrNull()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
        val objects = when (root) {
            is JsonObject -> listOf(root)
            is JsonArray -> root.filterIsInstance<JsonObject>()
            else -> emptyList()
        }
        val operations = objects.asSequence()
            .take(MAXIMUM_BATCH_OPERATIONS)
            .mapNotNull(::parseOperation)
            .toList()
        return operations.takeIf(List<GraphQLOperation>::isNotEmpty)?.let(::GraphQLDocument)
    }

    private fun parseOperation(root: JsonObject): GraphQLOperation? {
        val query = (root[QUERY_FIELD] as? JsonPrimitive)?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val explicitName = (root[OPERATION_NAME_FIELD] as? JsonPrimitive)?.contentOrNull
            ?.normalizeOperationName()
        if (query == null && explicitName == null) return null

        val declaration = query?.let(OPERATION_DECLARATION::find)
        val type = when (declaration?.groups?.get(1)?.value?.lowercase()) {
            MUTATION_TOKEN -> GraphQLOperationType.MUTATION
            SUBSCRIPTION_TOKEN -> GraphQLOperationType.SUBSCRIPTION
            else -> GraphQLOperationType.QUERY
        }
        val documentName = declaration?.groups?.get(2)?.value?.normalizeOperationName()
        val summary = query.orEmpty()
            .take(MAXIMUM_SUMMARY_SOURCE_CHARACTERS)
            .replace(WHITESPACE, " ")
            .take(MAXIMUM_SUMMARY_CHARACTERS)
        return GraphQLOperation(
            name = explicitName ?: documentName,
            type = type,
            summary = summary,
        )
    }

    private fun String.normalizeOperationName(): String? = trim()
        .takeIf { it.length <= MAXIMUM_OPERATION_NAME_CHARACTERS }
        ?.takeIf(GRAPHQL_NAME::matches)

    private companion object {
        const val QUERY_FIELD = "query"
        const val OPERATION_NAME_FIELD = "operationName"
        const val MUTATION_TOKEN = "mutation"
        const val SUBSCRIPTION_TOKEN = "subscription"
        const val MAXIMUM_SUMMARY_CHARACTERS = 256
        const val MAXIMUM_SUMMARY_SOURCE_CHARACTERS = 2_048
        const val MAXIMUM_OPERATION_NAME_CHARACTERS = 256
        const val MAXIMUM_BATCH_OPERATIONS = 256
        val OPERATION_DECLARATION = Regex(
            """^\s*(query|mutation|subscription)(?:\s+([_A-Za-z][_0-9A-Za-z]*))?""",
            RegexOption.IGNORE_CASE,
        )
        val WHITESPACE = Regex("\\s+")
        val GRAPHQL_NAME = Regex("^[_A-Za-z][_0-9A-Za-z]*$")
    }
}
