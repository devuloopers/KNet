package com.devuloopers.knet.application.contract.graphql

import com.devuloopers.knet.domain.graphql.GraphQLQueryHash
import com.devuloopers.knet.domain.graphql.GraphQLQueryProvenance
import kotlinx.coroutines.flow.StateFlow

/** Canonical scheme/authority/path scope; query parameters and credentials are excluded. */
@JvmInline
public value class GraphQLEndpoint(public val value: String) {
    init { require(value.isNotBlank() && value.length <= 8_192) }
}

/** Verified source-only lookup result. Variables, headers and response data are never retained here. */
public data class GraphQLQueryMapping(public val query: String, public val provenance: GraphQLQueryProvenance)

/** Bounded manifest import failure categories with no source text or sensitive path in messages. */
public enum class GraphQLManifestFailure(public val description: String) {
    INVALID_ENDPOINT("Choose an absolute HTTP or HTTPS GraphQL endpoint before importing."),
    UNREADABLE_FILE("The manifest could not be read."),
    LIMIT_EXCEEDED("The manifest exceeds the query catalog limits."),
    INVALID_FORMAT("The manifest format or operation metadata is invalid."),
    HASH_MISMATCH("A manifest document does not match its SHA-256 identifier."),
    INVALID_QUERY("A manifest contains an invalid or ambiguous GraphQL document."),
}

/** Atomic import outcome; failure leaves the catalog unchanged. */
public sealed interface GraphQLManifestImportResult {
    /** Number of distinct verified documents imported for the selected endpoint. */
    public data class Imported(public val count: Int) : GraphQLManifestImportResult
    /** Actionable failure category without query content. */
    public data class Rejected(public val reason: GraphQLManifestFailure) : GraphQLManifestImportResult
}

/** Process-local bounded mapping catalog implemented by the GraphQL protocol extension. */
public interface GraphQLPersistedQueryCatalog {
    /** Revision changes when lookup results can change; no payloads are emitted. */
    public val revision: StateFlow<Long>
    /** Canonicalizes an absolute HTTP(S) endpoint or rejects an unscoped/invalid URL. */
    public fun endpoint(url: String): GraphQLEndpoint?
    /** Looks up only within [endpoint], applying the catalog's lifetime and allocation bounds. */
    public fun lookup(endpoint: GraphQLEndpoint, hash: GraphQLQueryHash): GraphQLQueryMapping?
    /** Verifies exact source hashing before retaining a complete captured document. */
    public fun rememberCaptured(endpoint: GraphQLEndpoint, hash: GraphQLQueryHash, query: String): Boolean
    /** Imports a bounded SHA-256 manifest atomically into one explicit endpoint scope. */
    public fun importManifest(endpoint: GraphQLEndpoint, bytes: ByteArray): GraphQLManifestImportResult
    /** Clears all mappings without modifying saved requests, captures or server state. */
    public fun clear()
}

/** Product-owned local file access, keeping filesystem paths out of protocol parsing and presentation. */
public fun interface GraphQLManifestReader {
    /** Reads at most [maximumBytes] plus one overflow marker byte, or throws, without loading an unbounded file. */
    public suspend fun read(path: String, maximumBytes: Int): ByteArray
}
