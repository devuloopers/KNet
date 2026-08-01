package com.devuloopers.knet.apps.desktop.lifecycle

import com.devuloopers.knet.core.logger.KNetLogger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages Desktop application shutdown hooks and resource cleanup.
 * Decoupled from specific repository, proxy, or database implementations.
 */
public object ApplicationLifecycle {

    private val resources = CopyOnWriteArrayList<ShutdownAware>()

    /**
     * Registers a [ShutdownAware] resource for cleanup during application shutdown.
     */
    public fun registerResource(resource: ShutdownAware) {
        resources.add(resource)
    }

    /**
     * Installs JVM runtime shutdown hook to trigger resource cleanup when process terminates.
     */
    public fun installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown()
        })
    }

    /**
     * Executes resource cleanup in reverse registration order.
     */
    public fun shutdown() {
        KNetLogger.info(tag = "ApplicationLifecycle") { "Desktop application shutdown sequence initiated." }
        resources.reversed().forEach { resource ->
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
