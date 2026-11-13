package com.devuloopers.knet.application.usecase.graphql

import com.devuloopers.knet.application.contract.graphql.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/** Imports explicitly selected local mappings for the active endpoint, with no network registry requests. */
public class ImportGraphQLPersistedQueriesUseCase(
    private val catalog: GraphQLPersistedQueryCatalog,
    private val reader: GraphQLManifestReader,
) {
    /** Reads a bounded local [path] and atomically validates its contents under [endpointUrl]. */
    public suspend fun execute(endpointUrl: String, path: String): GraphQLManifestImportResult {
        val endpoint = catalog.endpoint(endpointUrl) ?: return GraphQLManifestImportResult.Rejected(GraphQLManifestFailure.INVALID_ENDPOINT)
        val bytes = try { reader.read(path, MAXIMUM_MANIFEST_BYTES) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return GraphQLManifestImportResult.Rejected(GraphQLManifestFailure.UNREADABLE_FILE) }
        if (bytes.size > MAXIMUM_MANIFEST_BYTES) return GraphQLManifestImportResult.Rejected(GraphQLManifestFailure.LIMIT_EXCEEDED)
        return catalog.importManifest(endpoint, bytes)
    }
    /** File allocation bound shared by product readers and import validation. */
    public companion object { public const val MAXIMUM_MANIFEST_BYTES: Int = 2_097_152 }
}

/** Allows presentation to invalidate derived views when verified mappings change. */
public class ObserveGraphQLPersistedQueriesUseCase(private val catalog: GraphQLPersistedQueryCatalog) {
    /** Returns a payload-free revision stream; unchanged mappings do not trigger refreshes. */
    public fun execute(): StateFlow<Long> = catalog.revision
}
