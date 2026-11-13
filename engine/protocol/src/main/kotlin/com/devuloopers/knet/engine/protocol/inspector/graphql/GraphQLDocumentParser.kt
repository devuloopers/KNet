package com.devuloopers.knet.engine.protocol.inspector.graphql

/** Compatibility name for the shared typed GraphQL operation category. */
typealias GraphQLOperationType = com.devuloopers.knet.domain.graphql.GraphQLOperationType

/** Compact operation fact retained by HTTP and WebSocket compatibility consumers. */
data class GraphQLOperation(
    /** Resolved document name, or null for an anonymous operation. */
    val name: String?,
    /** AST-derived operation kind. */
    val type: GraphQLOperationType,
    /** Bounded source summary. */
    val summary: String,
)

/** One or more valid selected operations from GraphQL request envelopes. */
data class GraphQLDocument(
    /** Selected operations in batch order. */
    val operations: List<GraphQLOperation>,
) {
    init { require(operations.isNotEmpty()) }
}

/** Compatibility adapter to the shared bounded HTTP envelope and AST decoder. */
class GraphQLDocumentParser(
    private val persistedQueries: GraphQLPersistedQueryResolver = GraphQLPersistedQueryResolver(),
) {
    private val decoder = GraphQLHttpDecoder()

    /** Decodes source-aware HTTP facts using the same bounded parser as compatibility consumers. */
    fun decodeHttp(method: String, url: String, contentType: String = "", body: ByteArray? = null,
        bodyComplete: Boolean = true, learnCaptured: Boolean = false,
        headers: List<Pair<String, String>> = emptyList()): com.devuloopers.knet.domain.graphql.GraphQLHttpDocument? {
        // Hash the exact decompressed GraphQL source, never compressed transport bytes. Incomplete bodies
        // cannot teach mappings and are never inflated; every encoding layer shares the existing byte budget.
        if (method.equals("GET", true) || !bodyComplete || body == null ||
            body.size > GraphQLHttpDecoder.MAXIMUM_INPUT_CHARACTERS ||
            headers.none { it.first.equals("Content-Encoding", true) })
            return persistedQueries.decode(method, url, contentType, body, bodyComplete, learnCaptured)
        val decoded = com.devuloopers.knet.domain.clientNetwork.decoder.BodyDecoder.decode(
            body, headers, GraphQLHttpDecoder.MAXIMUM_INPUT_CHARACTERS)
        val bytes = when (decoded) {
            is com.devuloopers.knet.domain.clientNetwork.decoder.DecodedBodyResult.Success -> decoded.bytes
            is com.devuloopers.knet.domain.clientNetwork.decoder.DecodedBodyResult.Identity -> decoded.bytes
            else -> {
                val hint = url.substringBefore('?').contains("graphql", true) || contentType.contains("graphql", true)
                if (!hint) return null
                return com.devuloopers.knet.domain.graphql.GraphQLHttpDocument(
                    com.devuloopers.knet.domain.graphql.GraphQLHttpSource.JSON_BODY, diagnostic =
                        if (decoded is com.devuloopers.knet.domain.clientNetwork.decoder.DecodedBodyResult.OutputLimitExceeded)
                            com.devuloopers.knet.domain.graphql.GraphQLHttpDiagnostic.LIMIT_EXCEEDED
                        else com.devuloopers.knet.domain.graphql.GraphQLHttpDiagnostic.MALFORMED_ENCODING)
            }
        }
        return persistedQueries.decode(method, url, contentType, bytes, bodyComplete, learnCaptured)
    }

    /** Parses complete JSON envelopes or existing raw GraphQL bodies without inventing missing operations. */
    fun parse(body: ByteArray): GraphQLDocument? {
        val decoded = decoder.decode("POST", "", body = body) ?: return null
        val summary = decoded.queryText.take(2_048).replace(Regex("\\s+"), " ").take(256)
        return decoded.matchableOperations.takeIf { it.isNotEmpty() }?.map {
            GraphQLOperation(it.name, it.type, summary)
        }?.let(::GraphQLDocument)
    }
}
