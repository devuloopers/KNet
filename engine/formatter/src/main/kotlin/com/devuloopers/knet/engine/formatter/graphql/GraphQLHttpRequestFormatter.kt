package com.devuloopers.knet.engine.formatter.graphql

import com.devuloopers.knet.traffic.model.http.RequestTarget

import com.devuloopers.knet.domain.graphql.GraphQLHttpDocument
import com.devuloopers.knet.domain.payload.StructuredPayloadState
import com.devuloopers.knet.domain.graphql.GraphQLHttpParameter
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLHttpDecoder

/** Presentation-facing adapter to protocol-owned decoding. UI callers prepare results off-thread. */
object GraphQLHttpRequestFormatter {
    private val decoder = GraphQLHttpDecoder()

    /** Extracts bounded facts without rewriting [url], [body] or their original encoding. */
    fun inspect(method: String, url: String, contentType: String = "", body: ByteArray? = null,
        bodyComplete: Boolean = true): GraphQLHttpDocument? = decoder.decode(method, url, contentType, body, bodyComplete)

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
        if (previous.queryText != current.queryText) put(GraphQLHttpParameter.QUERY, current.queryText)
        if (previous.operationName != current.operationName) put(GraphQLHttpParameter.OPERATION_NAME, current.operationName.takeIf(String::isNotEmpty))
        if (previous.variablesText != current.variablesText) put(GraphQLHttpParameter.VARIABLES, current.variablesText.takeIf(String::isNotBlank))
        if (previous.extensionsText != current.extensionsText) put(GraphQLHttpParameter.EXTENSIONS, current.extensionsText.takeIf(String::isNotBlank))
    }

    /** Formats a standalone document through the shared bounded AST parser. */
    fun formatQuery(query: String): String = decoder.formatQuery(query)
}
