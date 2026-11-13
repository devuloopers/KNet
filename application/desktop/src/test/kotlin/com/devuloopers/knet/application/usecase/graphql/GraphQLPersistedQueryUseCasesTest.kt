package com.devuloopers.knet.application.usecase.graphql

import com.devuloopers.knet.application.contract.graphql.*
import com.devuloopers.knet.domain.graphql.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Import coordinates bounded local IO and leaves protocol validation inside the catalog. */
class GraphQLPersistedQueryUseCasesTest {
    private class Catalog : GraphQLPersistedQueryCatalog {
        override val revision = MutableStateFlow(0L)
        var received: ByteArray? = null
        override fun endpoint(url: String): GraphQLEndpoint? = GraphQLEndpoint("https://fixture.test:443/graphql").takeIf { url.startsWith("https://") }
        override fun lookup(endpoint: GraphQLEndpoint, hash: GraphQLQueryHash): GraphQLQueryMapping? = null
        override fun rememberCaptured(endpoint: GraphQLEndpoint, hash: GraphQLQueryHash, query: String) = false
        override fun importManifest(endpoint: GraphQLEndpoint, bytes: ByteArray): GraphQLManifestImportResult {
            received = bytes; revision.value++; return GraphQLManifestImportResult.Imported(1)
        }
        override fun clear() = Unit
    }
    @Test fun `valid scoped import forwards bounded bytes and exposes payload free revision`() = runTest {
        val catalog = Catalog()
        val useCase = ImportGraphQLPersistedQueriesUseCase(catalog, GraphQLManifestReader { path, bound ->
            assertEquals("fixture.json", path); assertEquals(2_097_152, bound); "{}".encodeToByteArray()
        })
        assertEquals(GraphQLManifestImportResult.Imported(1), useCase.execute("https://fixture.test/graphql", "fixture.json"))
        assertContentEquals("{}".encodeToByteArray(), catalog.received)
        assertEquals(1L, ObserveGraphQLPersistedQueriesUseCase(catalog).execute().value)
    }
    @Test fun `invalid endpoint avoids any file IO`() = runTest {
        val useCase = ImportGraphQLPersistedQueriesUseCase(Catalog(), GraphQLManifestReader { _, _ -> fail("Unexpected IO") })
        assertEquals(GraphQLManifestFailure.INVALID_ENDPOINT, assertIs<GraphQLManifestImportResult.Rejected>(useCase.execute("/graphql", "fixture.json")).reason)
    }
    @Test fun `read failure and oversized file reject without invoking importer`() = runTest {
        for ((reader, reason) in listOf(
            GraphQLManifestReader { _, _ -> throw java.io.IOException("sensitive path") } to GraphQLManifestFailure.UNREADABLE_FILE,
            GraphQLManifestReader { _, bound -> ByteArray(bound + 1) } to GraphQLManifestFailure.LIMIT_EXCEEDED,
        )) {
            val catalog = Catalog()
            assertEquals(reason, assertIs<GraphQLManifestImportResult.Rejected>(ImportGraphQLPersistedQueriesUseCase(catalog, reader)
                .execute("https://fixture.test/graphql", "fixture.json")).reason)
            assertNull(catalog.received)
        }
    }
    @Test fun `cancellation propagates without being presented as import failure`() = runTest {
        assertFailsWith<CancellationException> {
            ImportGraphQLPersistedQueriesUseCase(Catalog(), GraphQLManifestReader { _, _ -> throw CancellationException("cancelled") })
                .execute("https://fixture.test/graphql", "fixture.json")
        }
    }
}
