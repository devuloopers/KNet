package com.devuloopers.knet.products.desktop

import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.di.DesktopModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertEquals(11, DesktopModules.features.size)
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
    }
}
