package com.devuloopers.knet.products.desktop

import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.config.Environment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test Suite for [DesktopConfiguration].
 * Verifies default configuration loading, path resolution, and immutability.
 */
class DesktopConfigurationTest {

    @Test
    fun testShouldLoadDefaultConfiguration() {
        val config = DesktopConfiguration.load()
        assertNotNull(config.appDirectory)
        assertNotNull(config.databaseDirectory)
        assertNotNull(config.logDirectory)
        assertEquals(Environment.DEVELOPMENT, config.environment)
    }

    @Test
    fun testShouldResolveDatabasePath() {
        val config = DesktopConfiguration.load()
        val expectedDatabasePath = config.appDirectory.resolve("database")
        assertEquals(expectedDatabasePath, config.databaseDirectory)
    }

    @Test
    fun testShouldResolveLogDirectory() {
        val config = DesktopConfiguration.load()
        val expectedLogPath = config.appDirectory.resolve("logs")
        assertEquals(expectedLogPath, config.logDirectory)
    }

    @Test
    fun testShouldProduceImmutableConfiguration() {
        val config1 = DesktopConfiguration.load()
        val config2 = config1.copy(environment = Environment.PRODUCTION)
        assertEquals(Environment.DEVELOPMENT, config1.environment)
        assertEquals(Environment.PRODUCTION, config2.environment)
    }
}
