package com.devuloopers.knet.domain.payload

/**
 * Closed, strongly-typed sealed domain hierarchy representing all structured request body payloads.
 *
 * Provides a polymorphic, type-safe representation of protocol-specific payload structures
 * (such as GraphQL, Protobuf, Avro, and raw text) without untyped generic erasures or casts.
 */
sealed interface StructuredPayloadState {

    /**
     * Raw transport string representation suitable for wire transport.
     */
    val rawText: String

    /**
     * Strongly-typed state representation for GraphQL operations.
     *
     * @property queryText The GraphQL query document AST string.
     * @property variablesText JSON-formatted variables map string.
     * @property operationName Optional target operation name within multi-operation documents.
     * @property extensionsText JSON-formatted extensions map string.
     */
    data class GraphQL(
        val queryText: String = "",
        val variablesText: String = "{}",
        val operationName: String = "",
        val extensionsText: String = "{}"
    ) : StructuredPayloadState {
        override val rawText: String get() = queryText
    }

    /**
     * Strongly-typed state representation for unformatted plain/raw body text.
     *
     * @property content Raw unformatted payload string.
     */
    data class RawText(
        val content: String = ""
    ) : StructuredPayloadState {
        override val rawText: String get() = content
    }
}
