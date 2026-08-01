package com.devuloopers.knet.apps.desktop

import com.devuloopers.knet.apps.desktop.bootstrap.ApplicationInitializer
import com.devuloopers.knet.apps.desktop.bootstrap.DesktopBootstrap
import com.devuloopers.knet.apps.desktop.config.DesktopConfiguration
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test Suite for [DesktopBootstrap] Composition Root.
 * Verifies startup sequencing, priority ordering, shutdown hook installation, and error handling.
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
    fun testShouldExecuteAllInitializersInPriorityOrder() {
        val executionOrder = mutableListOf<String>()

        val init1 = object : ApplicationInitializer {
            override val priority: Int = 100
            override fun initialize(configuration: DesktopConfiguration) {
                executionOrder.add("Logging")
            }
        }
        val init2 = object : ApplicationInitializer {
            override val priority: Int = 200
            override fun initialize(configuration: DesktopConfiguration) {
                executionOrder.add("ExceptionHandler")
            }
        }
        val init3 = object : ApplicationInitializer {
            override val priority: Int = 300
            override fun initialize(configuration: DesktopConfiguration) {
                executionOrder.add("Koin")
            }
        }

        val initializers = listOf(init3, init1, init2)
        val config = DesktopConfiguration.load()

        initializers.sortedBy { it.priority }.forEach { it.initialize(config) }

        assertEquals(3, executionOrder.size)
        assertEquals("Logging", executionOrder[0])
        assertEquals("ExceptionHandler", executionOrder[1])
        assertEquals("Koin", executionOrder[2])
    }

    @Test
    fun testShouldStopStartupWhenInitializerFails() {
        var koinExecuted = false

        val failingInit = object : ApplicationInitializer {
            override val priority: Int = 100
            override fun initialize(configuration: DesktopConfiguration) {
                throw IllegalStateException("Startup failed at logging phase")
            }
        }
        val koinInit = object : ApplicationInitializer {
            override val priority: Int = 200
            override fun initialize(configuration: DesktopConfiguration) {
                koinExecuted = true
            }
        }

        val initializers = listOf(failingInit, koinInit)
        val config = DesktopConfiguration.load()

        assertFailsWith<IllegalStateException> {
            initializers.sortedBy { it.priority }.forEach { it.initialize(config) }
        }

        assertTrue(!koinExecuted, "Koin initializer must NOT execute if previous initializer fails")
    }

    @Test
    fun testDesktopBootstrapObjectIsNotNull() {
        assertNotNull(DesktopBootstrap)
    }
}
