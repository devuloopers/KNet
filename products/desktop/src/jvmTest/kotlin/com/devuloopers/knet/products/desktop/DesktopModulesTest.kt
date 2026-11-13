package com.devuloopers.knet.products.desktop

import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.contract.inspection.CapabilityMaturity
import com.devuloopers.knet.application.contract.inspection.RuntimeCapabilityCatalog
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.di.DesktopModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

/**
 * Unit tests verifying layered DI composition, non-duplicate module aggregation, and Koin container startup.
 */
class DesktopModulesTest {

    @BeforeTest
    fun setUp() {
        stopKoin()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testShouldExposeAllModuleGroups() {
        assertNotNull(DesktopModules.platform)
        assertNotNull(DesktopModules.features)
        assertEquals(1, DesktopModules.platform.size)
        assertEquals(13, DesktopModules.features.size)
    }

    @Test
    fun testShouldCombineModulesCorrectly() {
        val expectedSize = DesktopModules.platform.size +
                DesktopModules.features.size

        assertEquals(expectedSize, DesktopModules.all.size)
    }

    @Test
    fun testShouldNotContainDuplicateModules() {
        val uniqueModules = DesktopModules.all.distinct()
        assertEquals(DesktopModules.all.size, uniqueModules.size, "DesktopModules.all must contain unique module instances")
    }

    @Test
    fun `closing the container cancels the shared policy and transform scope`() {
        val application = startKoin { modules(listOf(module { single { DesktopConfiguration.load() } }) + DesktopModules.all) }
        val scope = application.koin.get<CoroutineScope>()
        assertTrue(scope.coroutineContext.job.isActive)
        application.close()
        assertFalse(scope.coroutineContext.job.isActive)
    }

    @Test
    fun testShouldCreateKoinSuccessfully() {
        val configModule = module {
            single { DesktopConfiguration.load() }
        }
        val koinApp = startKoin {
            modules(listOf(configModule) + DesktopModules.all)
        }
        assertNotNull(koinApp.koin)
        val breakpointProtocols = koinApp.koin.get<BreakpointProtocolRegistry>().definitions
        assertEquals(
            setOf("http", "grpc", "graphql", "websocket", "graphql-websocket", "sse"),
            breakpointProtocols.map { it.protocolId.value }.toSet(),
        )
        assertEquals(6, breakpointProtocols.size)

        val conditionProtocols = koinApp.koin.get<NetworkConditionProtocolRegistry>().definitions
        assertEquals(
            listOf("transport", "http", "graphql", "grpc", "websocket", "graphql-websocket"),
            conditionProtocols.map { definition -> definition.protocolId.value },
        )

        val capabilities = koinApp.koin.get<RuntimeCapabilityCatalog>()
        assertEquals(CapabilityMaturity.EXPERIMENTAL, capabilities.get("companion.android")?.maturity)
        assertEquals(CapabilityMaturity.EXPERIMENTAL, capabilities.get("companion.android.vpn")?.maturity)
        assertEquals(CapabilityMaturity.EXPERIMENTAL, capabilities.get("companion.ios")?.maturity)
        assertEquals(CapabilityMaturity.UNAVAILABLE, capabilities.get("companion.ios.packet-tunnel")?.maturity)
        assertTrue(capabilities.get("mobile-companion") == null)
        assertTrue(capabilities.get("vpn") == null)
    }
    @Test
    fun `persisted query use cases share capture catalog and container close clears ephemeral sources`() = kotlinx.coroutines.test.runTest {
        val application = startKoin { modules(listOf(module { single { DesktopConfiguration.load() } }) + DesktopModules.all) }
        val catalog = application.koin.get<com.devuloopers.knet.application.contract.graphql.GraphQLPersistedQueryCatalog>()
        assertTrue(catalog === com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLPersistedQueryRuntime.catalog)
        val observer = application.koin.get<com.devuloopers.knet.application.usecase.graphql.ObserveGraphQLPersistedQueriesUseCase>()
        val source = "query CompositionRead { item { id } }"
        val hash = com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLQueryHashing.sha256(source)
        val endpoint = catalog.endpoint("https://composition.test/graphql")!!
        val file = java.nio.file.Files.createTempFile("knet-query-composition-", ".json")
        try {
            java.nio.file.Files.writeString(file, "{\"${hash.value}\":\"$source\"}")
            val importer = application.koin.get<com.devuloopers.knet.application.usecase.graphql.ImportGraphQLPersistedQueriesUseCase>()
            assertEquals(com.devuloopers.knet.application.contract.graphql.GraphQLManifestImportResult.Imported(1),
                importer.execute(endpoint.value, file.toString()))
            assertEquals(source, catalog.lookup(endpoint, hash)?.query)
            assertTrue(observer.execute().value > 0)
            application.close()
            assertTrue(catalog.lookup(endpoint, hash) == null)
        } finally { java.nio.file.Files.deleteIfExists(file); application.close() }
    }

}
