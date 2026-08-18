package com.devuloopers.knet.products.desktop.lifecycle

/**
 * Interface contract for application resources requiring explicit cleanup during shutdown.
 * Standardizes cleanup naming with Kotlin/Java [AutoCloseable] conventions.
 */
interface ShutdownAware {

    /**
     * Closes and releases resources associated with this instance during application shutdown.
     */
    fun close()
}
