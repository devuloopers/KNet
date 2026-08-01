package com.devuloopers.knet.apps.desktop

import com.devuloopers.knet.apps.desktop.bootstrap.ApplicationInitializer
import com.devuloopers.knet.apps.desktop.bootstrap.ExceptionHandlerInitializer
import com.devuloopers.knet.apps.desktop.bootstrap.KoinInitializer
import com.devuloopers.knet.apps.desktop.bootstrap.LoggingInitializer
import com.devuloopers.knet.apps.desktop.config.DesktopConfiguration
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Test Suite for Bootstrap Integration & Sequencing.
 * Verifies priority-ordered initializer execution, dependency graph integrity, and error propagation.
 */
class BootstrapInitializationTest {

    @BeforeTest
    fun setUp() {
        stopKoin()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testShouldInitializeCompleteStartupSequenceInPriorityOrder() {
        val executionOrder = mutableListOf<String>()

        val logging = object : ApplicationInitializer {
            override val priority: Int = LoggingInitializer.priority
            override fun initialize(configuration: DesktopConfiguration) {
                executionOrder.add("Logging")
            }
        }

        val exceptionHandler = object : ApplicationInitializer {
            override val priority: Int = ExceptionHandlerInitializer.priority
            override fun initialize(configuration: DesktopConfiguration) {
                executionOrder.add("ExceptionHandler")
            }
        }

        val koinInit = object : ApplicationInitializer {
            override val priority: Int = KoinInitializer.priority
            override fun initialize(configuration: DesktopConfiguration) {
                executionOrder.add("Koin")
            }
        }

        val initializers = listOf(koinInit, logging, exceptionHandler)
        val config = DesktopConfiguration.load()

        initializers.sortedBy { it.priority }.forEach { it.initialize(config) }

        assertEquals(listOf("Logging", "ExceptionHandler", "Koin"), executionOrder)
    }

    @Test
    fun testShouldCreateDependencyGraph() {
        val config = DesktopConfiguration.load()
        KoinInitializer.initialize(config)
        // Verify Koin container started without dependency definition errors
        assertTrue(org.koin.core.context.GlobalContext.getOrNull() != null)
    }

    @Test
    fun testShouldStopStartupWhenInitializerFails() {
        var secondExecuted = false

        val failingInit = object : ApplicationInitializer {
            override val priority: Int = 100
            override fun initialize(configuration: DesktopConfiguration) {
                throw IllegalStateException("Initializer forced failure")
            }
        }

        val secondInit = object : ApplicationInitializer {
            override val priority: Int = 200
            override fun initialize(configuration: DesktopConfiguration) {
                secondExecuted = true
            }
        }

        val initializers = listOf(failingInit, secondInit)
        val config = DesktopConfiguration.load()

        assertFailsWith<IllegalStateException> {
            initializers.sortedBy { it.priority }.forEach { it.initialize(config) }
        }

        assertTrue(!secondExecuted, "Second initializer must NOT execute when a previous initializer fails")
    }
}
