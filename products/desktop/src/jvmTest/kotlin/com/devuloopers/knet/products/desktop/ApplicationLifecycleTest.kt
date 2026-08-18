package com.devuloopers.knet.products.desktop

import com.devuloopers.knet.products.desktop.lifecycle.ApplicationLifecycle
import com.devuloopers.knet.products.desktop.lifecycle.ShutdownAware
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit tests verifying [ApplicationLifecycle] resource registration and reverse shutdown execution.
 */
class ApplicationLifecycleTest {

    @Test
    fun testShouldRegisterResources() {
        var closed = false
        val resource = object : ShutdownAware {
            override fun close() {
                closed = true
            }
        }
        ApplicationLifecycle.registerResource(resource)
        ApplicationLifecycle.shutdown()
        assertTrue(closed, "Registered resource must be closed on shutdown")
    }

    @Test
    fun testShouldShutdownResourcesInReverseOrder() {
        val closeOrder = mutableListOf<String>()

        val resourceA = object : ShutdownAware {
            override fun close() {
                closeOrder.add("A")
            }
        }
        val resourceB = object : ShutdownAware {
            override fun close() {
                closeOrder.add("B")
            }
        }
        val resourceC = object : ShutdownAware {
            override fun close() {
                closeOrder.add("C")
            }
        }

        ApplicationLifecycle.registerResource(resourceA)
        ApplicationLifecycle.registerResource(resourceB)
        ApplicationLifecycle.registerResource(resourceC)

        ApplicationLifecycle.shutdown()

        val idxA = closeOrder.lastIndexOf("A")
        val idxB = closeOrder.lastIndexOf("B")
        val idxC = closeOrder.lastIndexOf("C")

        assertTrue(idxC < idxB, "Resource C registered after B must close before B")
        assertTrue(idxB < idxA, "Resource B registered after A must close before A")
    }

    @Test
    fun testShouldContinueShutdownAfterException() {
        var aClosed = false
        val failingResourceB = object : ShutdownAware {
            override fun close() {
                throw RuntimeException("Resource B forced failure during test")
            }
        }
        val resourceA = object : ShutdownAware {
            override fun close() {
                aClosed = true
            }
        }

        ApplicationLifecycle.registerResource(resourceA)
        ApplicationLifecycle.registerResource(failingResourceB)

        // Must not propagate exception and must continue to close resourceA
        ApplicationLifecycle.shutdown()

        assertTrue(aClosed, "Resource A must close even if Resource B threw an exception during shutdown")
    }

    @Test
    fun testShouldInstallJvmShutdownHookWithoutError() {
        // Must complete without throwing an exception
        ApplicationLifecycle.installShutdownHook()
    }

    @Test
    fun testShouldIgnoreEmptyLifecycle() {
        // Must complete cleanly without errors when no resources are registered
        ApplicationLifecycle.shutdown()
    }
}
