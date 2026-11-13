package com.devuloopers.knet.engine.formatter.graphql

import com.devuloopers.knet.traffic.model.http.RequestTarget

import com.devuloopers.knet.domain.graphql.GraphQLHttpDocument
import com.devuloopers.knet.domain.payload.StructuredPayloadState
import com.devuloopers.knet.domain.graphql.GraphQLHttpParameter
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLHttpDecoder
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLPersistedQueryResolver
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLPersistedQueryRuntime
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLQueryHashing
import com.devuloopers.knet.application.contract.graphql.GraphQLPersistedQueryCatalog
import kotlinx.serialization.json.*

/** Presentation-facing adapter to protocol-owned decoding. UI callers prepare results off-thread. */
object GraphQLHttpRequestFormatter {
    private val decoder = GraphQLHttpDecoder()
    private val persistedQueries = GraphQLPersistedQueryResolver()
    /** Shared bounded catalog exposed through its application contract for use cases and product assembly. */
    val persistedQueryCatalog: GraphQLPersistedQueryCatalog get() = GraphQLPersistedQueryRuntime.catalog

    /** Extracts bounded facts without rewriting [url], [body] or their original encoding. */
    fun inspect(method: String, url: String, contentType: String = "", body: ByteArray? = null,
        bodyComplete: Boolean = true, learnCaptured: Boolean = false): GraphQLHttpDocument? =
        persistedQueries.decode(method, url, contentType, body, bodyComplete, learnCaptured)

    /** Prepares an indented read-only projection off-thread; original envelope/URL/editor text stays intact. */
    fun format(document: GraphQLHttpDocument): BodyFormat.GraphQL = BodyFormat.GraphQL(
        operationType = document.selectedOperation?.type?.label.orEmpty(),
        operationName = document.selectedOperation?.name,
        queryText = document.formattedQuery,
        variablesJson = JsonBodyFormatter.prettyPrintJsonForInspection(document.variablesJson),
        extensionsJson = JsonBodyFormatter.prettyPrintJsonForInspection(document.extensionsJson),
        source = document.source,
        diagnostic = document.diagnostic,
        suppliedOperationName = document.suppliedOperationName,
        persistedQuery = document.persistedQuery,
        queryProvenance = document.queryProvenance,
    )

    /** Replaces an origin/absolute request target while retaining its authority and HTTP source. */
    fun updateTarget(target: RequestTarget,
        changes: Map<GraphQLHttpParameter, String?>): Result<RequestTarget> {
        return when (target) {
            is RequestTarget.Absolute -> updateUrl(target.pathAndQuery, changes).map { target.copy(pathAndQuery = it) }
            is RequestTarget.Origin -> updateUrl(target.pathAndQuery, changes).map { target.copy(pathAndQuery = it) }
            else -> Result.failure(IllegalArgumentException("GraphQL editing requires an HTTP request target"))
        }
    }

    /** Updates explicitly edited URL fields once, retaining untouched raw parameter pairs. */
    fun updateUrl(url: String, changes: Map<GraphQLHttpParameter, String?>): Result<String> =
        GraphQLHttpDecoder.updateUrlParameters(url, changes)

    /** Preserves unknown JSON envelope fields and updates only edited GraphQL data. Invalid JSON text
     * is retained as a scalar so validation can report it rather than silently dropping user input. */
    fun updateJsonBody(body: String, changes: Map<GraphQLHttpParameter, String?>): Result<String> =
        GraphQLHttpDecoder.updateJsonEnvelope(body, changes)

    /** Computes only changed fields so changing variables cannot rewrite an untouched query or selector. */
    fun changedFields(previous: StructuredPayloadState.GraphQL, current: StructuredPayloadState.GraphQL): Map<GraphQLHttpParameter, String?> = buildMap {
        if (previous.queryText != current.queryText) {
            put(GraphQLHttpParameter.QUERY, current.queryText.takeIf(String::isNotBlank))
            if (previous.extensionsText == current.extensionsText && current.queryText.isNotBlank()) {
                refreshedHashExtensions(current.extensionsText, current.queryText)?.let { put(GraphQLHttpParameter.EXTENSIONS, it) }
            }
        }
        if (previous.operationName != current.operationName) put(GraphQLHttpParameter.OPERATION_NAME, current.operationName.takeIf(String::isNotEmpty))
        if (previous.variablesText != current.variablesText) put(GraphQLHttpParameter.VARIABLES, current.variablesText.takeIf(String::isNotBlank))
        if (previous.extensionsText != current.extensionsText) put(GraphQLHttpParameter.EXTENSIONS, current.extensionsText.takeIf(String::isNotBlank))
    }

    /** Synchronizes an explicitly edited source with its existing APQ metadata, retaining other extension fields. */
    private fun refreshedHashExtensions(extensions: String, query: String): String? = runCatching {
        if (extensions.length > GraphQLHttpDecoder.MAXIMUM_INPUT_CHARACTERS || query.length > GraphQLHttpDecoder.MAXIMUM_INPUT_CHARACTERS ||
            !GraphQLHttpDecoder.withinJsonDepth(extensions)) return null
        val root = Json.parseToJsonElement(extensions) as? JsonObject ?: return null
        val metadata = root["persistedQuery"] as? JsonObject ?: return null
        if ((metadata["version"] as? JsonPrimitive)?.let { !it.isString && it.intOrNull == 1 } != true ||
            (metadata["sha256Hash"] as? JsonPrimitive)?.let { it.isString && Regex("[a-fA-F0-9]{64}").matches(it.content) } != true) return null
        JsonObject(root.toMutableMap().apply { put("persistedQuery", JsonObject(metadata.toMutableMap().apply {
            put("sha256Hash", JsonPrimitive(GraphQLQueryHashing.sha256(query).value))
        })) }).toString()
    }.getOrNull()

    /** Applies the decoder's allocation and depth bounds before a presentation JSON tree is allocated. */
    fun isWithinJsonBounds(text: String): Boolean = text.length <= GraphQLHttpDecoder.MAXIMUM_INPUT_CHARACTERS &&
        GraphQLHttpDecoder.withinJsonDepth(text)

    /** Formats a standalone document through the shared bounded AST parser. */
    fun formatQuery(query: String): String = decoder.formatQuery(query)
}
