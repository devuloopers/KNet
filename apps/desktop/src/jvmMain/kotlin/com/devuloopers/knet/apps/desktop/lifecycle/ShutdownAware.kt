package com.devuloopers.knet.apps.desktop.lifecycle

/**
 * Interface contract for application resources requiring explicit cleanup during shutdown.
 * Standardizes cleanup naming with Kotlin/Java [AutoCloseable] conventions.
 */
public interface ShutdownAware {

    /**
     * Closes and releases resources associated with this instance during application shutdown.
     */
    public fun close()
}
