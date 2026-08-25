package com.devuloopers.knet.application.port.apistudio

import com.devuloopers.knet.domain.request.descriptor.RequestKindId

/** Cardinality of an authored RPC-style operation, independent of its wire protocol. */
public enum class ApiStudioOperationShape {
    UNARY,
    SERVER_STREAMING,
    CLIENT_STREAMING,
    BIDIRECTIONAL_STREAMING,
}

/** Presentation-safe operation exposed by a protocol authoring contribution. */
public data class ApiStudioProtocolOperation(
    public val id: String,
    public val displayName: String,
    public val requestType: String,
    public val responseType: String,
    public val shape: ApiStudioOperationShape,
) {
    init {
        require(id.isNotBlank()) { "Protocol operation ID must not be blank." }
        require(displayName.isNotBlank()) { "Protocol operation name must not be blank." }
    }
}

/** Ordered metadata authored without exposing a protocol library's metadata type. */
public data class ApiStudioProtocolMetadataEntry(
    public val name: String,
    public val value: String,
    public val enabled: Boolean = true,
)

/** Protocol-neutral input shared by RPC-like API Studio contributions. */
public data class ApiStudioProtocolDraft(
    public val id: String,
    public val name: String,
    public val targetHost: String,
    public val targetPort: Int,
    public val useTls: Boolean,
    public val operationId: String,
    public val deadlineMillis: Long,
    public val metadata: List<ApiStudioProtocolMetadataEntry>,
    public val outboundMessages: List<String>,
    public val schemaSourceId: String?,
)

/** Safe result of importing a schema owned by one protocol contribution. */
public data class ApiStudioProtocolSchemaImport(
    public val sourceId: String,
    public val fileCount: Int,
    public val operationCount: Int,
)

/**
 * Authoring boundary for an API Studio protocol contribution.
 *
 * The UI receives only safe strings and cardinality facts. Protobuf, reflection, generated stubs,
 * and protocol-specific draft DTOs remain inside the owning engine module.
 */
public interface ApiStudioProtocolAuthoringPort {
    public val kind: RequestKindId

    public fun importSchema(sourceId: String, bytes: ByteArray): Result<ApiStudioProtocolSchemaImport>

    public fun operations(): List<ApiStudioProtocolOperation>

    public fun createDocument(draft: ApiStudioProtocolDraft): Result<ApiStudioProtocolDocument>

    /** Restores presentation-safe authoring state from this protocol's opaque document. */
    public fun readDocument(document: ApiStudioProtocolDocument): Result<ApiStudioProtocolDraft>
}

/** Immutable authoring registry assembled by the product composition root. */
public class ApiStudioProtocolAuthoringRegistry(
    contributions: List<ApiStudioProtocolAuthoringPort> = emptyList(),
) {
    private val byKind = contributions.associateBy(ApiStudioProtocolAuthoringPort::kind).also { indexed ->
        require(indexed.size == contributions.size) { "API Studio authoring kinds must be unique." }
    }

    public fun importSchema(
        kind: RequestKindId,
        sourceId: String,
        bytes: ByteArray,
    ): Result<ApiStudioProtocolSchemaImport> = contribution(kind).importSchema(sourceId, bytes)

    public fun operations(kind: RequestKindId): List<ApiStudioProtocolOperation> = contribution(kind).operations()

    public fun createDocument(
        kind: RequestKindId,
        draft: ApiStudioProtocolDraft,
    ): Result<ApiStudioProtocolDocument> = contribution(kind).createDocument(draft)

    public fun readDocument(document: ApiStudioProtocolDocument): Result<ApiStudioProtocolDraft> =
        contribution(document.kind).readDocument(document)

    private fun contribution(kind: RequestKindId): ApiStudioProtocolAuthoringPort =
        requireNotNull(byKind[kind]) { "No API Studio authoring contribution is registered for '${kind.value}'." }
}
