package com.devuloopers.knet.apps.desktop

import com.devuloopers.knet.apps.desktop.config.DesktopConfiguration
import com.devuloopers.knet.apps.desktop.di.DesktopModules
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
        assertNotNull(DesktopModules.core)
        assertNotNull(DesktopModules.storage)
        assertNotNull(DesktopModules.data)
        assertNotNull(DesktopModules.engine)
        assertNotNull(DesktopModules.ui)
    }

    @Test
    fun testShouldCombineModulesCorrectly() {
        val expectedSize = DesktopModules.core.size +
                DesktopModules.storage.size +
                DesktopModules.data.size +
                DesktopModules.engine.size +
                DesktopModules.ui.size

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
    }
}
