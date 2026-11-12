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
class GraphQLDocumentParser {
    private val decoder = GraphQLHttpDecoder()

    /** Decodes source-aware HTTP facts using the same bounded parser as compatibility consumers. */
    fun decodeHttp(method: String, url: String, contentType: String = "", body: ByteArray? = null,
        bodyComplete: Boolean = true): com.devuloopers.knet.domain.graphql.GraphQLHttpDocument? =
        decoder.decode(method, url, contentType, body, bodyComplete)

    /** Parses complete JSON envelopes or existing raw GraphQL bodies without inventing missing operations. */
    fun parse(body: ByteArray): GraphQLDocument? {
        val decoded = decoder.decode("POST", "", body = body) ?: return null
        val summary = decoded.queryText.take(2_048).replace(Regex("\\s+"), " ").take(256)
        return decoded.matchableOperations.takeIf { it.isNotEmpty() }?.map {
            GraphQLOperation(it.name, it.type, summary)
        }?.let(::GraphQLDocument)
    }
}
