package com.devuloopers.knet.products.desktop.lifecycle

import com.devuloopers.knet.core.logger.KNetLogger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.TimeSource

/**
 * Manages Desktop application shutdown hooks and resource cleanup.
 * Decoupled from specific repository, proxy, or database implementations.
 */
object ApplicationLifecycle {

    private val resources = CopyOnWriteArrayList<RegisteredShutdownResource>()
    private val shutdownHookInstalled = AtomicBoolean(false)

    /**
     * Registers a [ShutdownAware] resource for cleanup during application shutdown.
     */
    fun registerResource(resource: ShutdownAware) {
        registerResource(resource::class.simpleName ?: "anonymous-resource", resource)
    }

    /** Registers a named resource so slow shutdown stages remain observable in production logs. */
    fun registerResource(name: String, resource: ShutdownAware) {
        require(name.isNotBlank()) { "Shutdown resource name must not be blank." }
        resources.add(RegisteredShutdownResource(name, resource))
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
        closeResources(takeResources())
    }

    /**
     * Transfers cleanup to a non-daemon worker so the Compose event thread can close immediately.
     *
     * A JVM shutdown hook racing this worker sees an empty resource registry, preserving exactly-once
     * cleanup without requiring an unsafe forced process exit.
     */
    fun shutdownAsync() {
        val resourcesToClose = takeResources()
        if (resourcesToClose.isEmpty()) return
        thread(name = ASYNC_SHUTDOWN_THREAD_NAME, isDaemon = false) {
            closeResources(resourcesToClose)
        }
    }

    private fun takeResources(): List<RegisteredShutdownResource> = synchronized(this) {
        resources.reversed().also { resources.clear() }
    }

    private fun closeResources(resourcesToClose: List<RegisteredShutdownResource>) {
        if (resourcesToClose.isEmpty()) return
        val shutdownMark = TimeSource.Monotonic.markNow()
        KNetLogger.info(tag = "ApplicationLifecycle") { "Desktop application shutdown sequence initiated." }
        resourcesToClose.forEach { registered ->
            val resourceMark = TimeSource.Monotonic.markNow()
            try {
                registered.resource.close()
            } catch (e: Exception) {
                KNetLogger.error(tag = "ApplicationLifecycle", throwable = e) {
                    "Error closing shutdown resource '${registered.name}': ${e.message}"
                }
            } finally {
                KNetLogger.debug(tag = "ApplicationLifecycle") {
                    "application_event=shutdown_resource_closed resource=${registered.name} " +
                        "duration_ms=${resourceMark.elapsedNow().inWholeMilliseconds}"
                }
            }
        }
        KNetLogger.info(tag = "ApplicationLifecycle") {
            "application_event=shutdown_completed duration_ms=${shutdownMark.elapsedNow().inWholeMilliseconds}"
        }
    }

    private data class RegisteredShutdownResource(
        val name: String,
        val resource: ShutdownAware,
    )

    private const val ASYNC_SHUTDOWN_THREAD_NAME: String = "knet-application-shutdown-worker"
}
