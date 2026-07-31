package com.devuloopers.knet.scriptengine.kotlin.runtime

import com.devuloopers.knet.scriptengine.core.ResultCollector

/**
 * Thread-local context holder providing dynamic resolution of the active [ResultCollector]
 * during compiled Kotlin script execution.
 *
 * Prevents stale closure capture when [javax.script.CompiledScript] instances are cached
 * and evaluated across multiple execution runs.
 */
object ResultCollectorHolder {

    private val threadCollector = ThreadLocal<ResultCollector?>()

    /**
     * Binds the [collector] instance to the current execution thread.
     */
    fun set(collector: ResultCollector) {
        threadCollector.set(collector)
    }

    /**
     * Retrieves the active [ResultCollector] bound to the current thread.
     */
    fun get(): ResultCollector? = threadCollector.get()

    /**
     * Clears the thread-local binding to prevent thread pool memory leaks.
     */
    fun clear() {
        threadCollector.remove()
    }
}
