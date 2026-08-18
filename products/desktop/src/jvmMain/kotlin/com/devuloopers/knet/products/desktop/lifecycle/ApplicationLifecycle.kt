package com.devuloopers.knet.products.desktop.lifecycle

import com.devuloopers.knet.core.logger.KNetLogger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Desktop application shutdown hooks and resource cleanup.
 * Decoupled from specific repository, proxy, or database implementations.
 */
object ApplicationLifecycle {

    private val resources = CopyOnWriteArrayList<ShutdownAware>()
    private val shutdownHookInstalled = AtomicBoolean(false)

    /**
     * Registers a [ShutdownAware] resource for cleanup during application shutdown.
     */
    fun registerResource(resource: ShutdownAware) {
        resources.add(resource)
    }

    /**
     * Installs JVM runtime shutdown hook to trigger resource cleanup when process terminates.
     */
    fun installShutdownHook() {
        if (!shutdownHookInstalled.compareAndSet(false, true)) return
        Runtime.getRuntime().addShutdownHook(
            Thread(
                { shutdown() },
                "knet-application-shutdown",
            )
        )
    }

    /**
     * Executes resource cleanup in reverse registration order.
     */
    fun shutdown() {
        val resourcesToClose = synchronized(this) {
            resources.reversed().also { resources.clear() }
        }
        if (resourcesToClose.isEmpty()) return

        KNetLogger.info(tag = "ApplicationLifecycle") { "Desktop application shutdown sequence initiated." }
        resourcesToClose.forEach { resource ->
            try {
                resource.close()
            } catch (e: Exception) {
                KNetLogger.error(tag = "ApplicationLifecycle", throwable = e) {
                    "Error closing resource during shutdown: ${e.message}"
                }
            }
        }
    }
}
