package com.devuloopers.knet.engine.script.internal

/**
 * ThreadLocal container holding the active [ResultCollector] for Kotlin script execution.
 */
object ResultCollectorHolder {

    private val threadLocalCollector = ThreadLocal<ResultCollector?>()

    /**
     * Obtains the current thread's [ResultCollector].
     */
    fun get(): ResultCollector? = threadLocalCollector.get()

    /**
     * Sets the active [ResultCollector] for the current thread.
     */
    fun set(collector: ResultCollector) {
        threadLocalCollector.set(collector)
    }

    /**
     * Clears the active [ResultCollector] for the current thread.
     */
    fun clear() {
        threadLocalCollector.remove()
    }
}
