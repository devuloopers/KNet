package com.devuloopers.knet.products.desktop

import com.devuloopers.knet.products.desktop.bootstrap.DesktopBootstrap
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Test Suite for [DesktopBootstrap] Composition Root.
 * Verifies startup orchestrator initialization and environment loading.
 */
class DesktopBootstrapTest {

    @BeforeTest
    fun setUp() {
        stopKoin()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testDesktopBootstrapObjectIsNotNull() {
        assertNotNull(DesktopBootstrap)
    }

    @Test
    fun testDesktopConfigurationLoadsSuccessfully() {
        val config = DesktopConfiguration.load()
        assertNotNull(config)
        assertNotNull(config.environment)
    }
}
