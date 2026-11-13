package com.devuloopers.knet.domain.graphql

/** Location of GraphQL data, independent of validity. @property label Human-readable source label. */
enum class GraphQLHttpSource(val label: String) {
    /** A POST application/json envelope. */
    JSON_BODY("JSON body"),
    /** Form-encoded parameters of a GET request. */
    URL_PARAMETERS("URL parameters"),
    /** Existing application/graphql compatibility path. */
    RAW_DOCUMENT("Raw document"),
    /** Existing array-of-envelopes compatibility path. */
    JSON_BATCH("JSON batch"),
}

/** Selected executable operation category. @property label Human-readable operation kind. */
enum class GraphQLOperationType(val label: String) {
    /** Read operation. */
    QUERY("Query"),
    /** Write operation. */
    MUTATION("Mutation"),
    /** Long-lived event operation. */
    SUBSCRIPTION("Subscription"),
}

/** Extraction diagnostics never change forwarding policy. @property description Actionable explanation. */
enum class GraphQLHttpDiagnostic(val description: String) {
    /** The document was not supplied. */
    MISSING_QUERY("No query document was supplied. Persisted-query metadata cannot reconstruct it."),
    /** Persisted-query metadata is not a supported SHA-256 version-1 envelope. */
    INVALID_PERSISTED_QUERY("Persisted-query metadata requires version 1 and a 64-character SHA-256 hash."),
    /** The supplied query does not match its hash; forwarding remains unchanged. */
    PERSISTED_QUERY_HASH_MISMATCH("The supplied query does not match its persisted-query SHA-256 hash."),
    /** Envelope fields have invalid types or JSON. */
    MALFORMED_ENVELOPE("GraphQL parameters have invalid JSON or field types."),
    /** UTF-8 form encoding was invalid. */
    MALFORMED_ENCODING("URL parameters are not valid UTF-8 form encoding."),
    /** Recognized URL parameters were repeated. */
    AMBIGUOUS_PARAMETERS("Repeated GraphQL parameters make the request ambiguous."),
    /** The document could not be parsed. */
    INVALID_DOCUMENT("The query document has invalid GraphQL syntax."),
    /** The document does not select a unique operation. */
    AMBIGUOUS_OPERATION("Select one operation using operationName; the document is ambiguous."),
    /** The supplied selector was not found. */
    UNKNOWN_OPERATION("operationName does not select an operation in this document."),
    /** The operation is incompatible with GET. */
    GET_OPERATION_NOT_QUERY("GET supports query operations only; this request is preserved as captured."),
    /** An inspection resource limit was reached. */
    LIMIT_EXCEEDED("GraphQL inspection limits were exceeded; original data remains available."),
    /** Only a prefix of the body is available. */
    TRUNCATED("The retained request body is incomplete; operation matching is unavailable."),
}

/** Compact selected operation; full documents and variables never enter rule criteria. */
data class GraphQLHttpOperation(
    /** Actual document name, null for an anonymous operation. */
    val name: String?,
    /** Actual AST operation kind. */
    val type: GraphQLOperationType,
)

/**
 * Bounded, derived HTTP GraphQL data. Original method/target/body remain owned by the request.
 * [selectedOperation] is absent when selection or envelope validation fails. A query remains viewable
 * with a diagnostic, but [matchableOperations] excludes incompatible or invalid requests.
 */
data class GraphQLHttpDocument(
    /** Authoritative source selected by the HTTP method. */
    val source: GraphQLHttpSource,
    /** Unescaped query source, retained even for invalid operation selection. */
    val queryText: String = "",
    /** AST-formatted query, or source text if parsing was unsuccessful. */
    val formattedQuery: String = queryText,
    /** Variables JSON text; malformed source is retained when a diagnostic is present. */
    val variablesJson: String = "",
    /** Extensions JSON text; malformed source is retained when a diagnostic is present. */
    val extensionsJson: String = "",
    /** Explicit request selector; distinct from a document-derived operation name. */
    val suppliedOperationName: String? = null,
    /** Resolved operation, absent for invalid or ambiguous selection. */
    val selectedOperation: GraphQLHttpOperation? = null,
    /** Selected operations for valid single or batch envelopes. */
    val matchableOperations: List<GraphQLHttpOperation> = emptyList(),
    /** Explanation for incomplete or invalid derived facts. */
    val diagnostic: GraphQLHttpDiagnostic? = null,
    /** Validated persisted-query protocol metadata; absent for ordinary requests or invalid metadata. */
    val persistedQuery: GraphQLPersistedQuery? = null,
    /** Where the displayed document came from; never changes the canonical request source. */
    val queryProvenance: GraphQLQueryProvenance = if (queryText.isBlank()) GraphQLQueryProvenance.UNAVAILABLE else GraphQLQueryProvenance.REQUEST,
    /** Lookup-only source kept separate from the captured query field. */
    val resolvedQueryText: String? = null,
    /** Bounded individual batch envelopes used for lookup and conservative operation matching. */
    val batchEntries: List<GraphQLHttpDocument> = emptyList(),
) {
    /** True for a legitimate hash-only request whose source has not been observed or imported. */
    val isPersistedQueryUnresolved: Boolean
        get() = persistedQuery != null && queryText.isBlank() && resolvedQueryText == null

    /** Conservative derived-data cache weight, excluding the canonical request. */
    val estimatedRetainedBytes: Long
        get() = (queryText.length.toLong() + formattedQuery.length + variablesJson.length +
            extensionsJson.length + suppliedOperationName.orEmpty().length + resolvedQueryText.orEmpty().length) * 2L +
            matchableOperations.size * 512L + batchEntries.sumOf { it.estimatedRetainedBytes }
}

/** Closed HTTP envelope field set. @property wireName Case-sensitive transport parameter name. */
enum class GraphQLHttpParameter(val wireName: String) {
    /** Executable document source. */
    QUERY("query"),
    /** JSON variable values. */
    VARIABLES("variables"),
    /** Selected operation name. */
    OPERATION_NAME("operationName"),
    /** Implementer-scoped JSON metadata. */
    EXTENSIONS("extensions"),
}

/** Validated lowercase SHA-256 identity, independent of a server-specific operation name. */
@kotlin.jvm.JvmInline
value class GraphQLQueryHash(val value: String) {
    init { require(value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) }
}

/** Recognized Apollo APQ/persisted-query version-1 metadata. */
data class GraphQLPersistedQuery(val hash: GraphQLQueryHash, val version: Int = 1) {
    init { require(version == 1) }
}

/** Provenance of a derived display document; lookup data never enters request editing buffers. */
enum class GraphQLQueryProvenance(val label: String) {
    /** Document text is present in this request. */
    REQUEST("Request"),
    /** Exact hash-to-source mapping was verified from complete captured traffic. */
    CAPTURED_TRAFFIC("Captured traffic"),
    /** Exact mapping was explicitly supplied in a validated local manifest. */
    IMPORTED_MANIFEST("Imported manifest"),
    /** No matching document has been observed or imported. */
    UNAVAILABLE("Query source unavailable"),
}
