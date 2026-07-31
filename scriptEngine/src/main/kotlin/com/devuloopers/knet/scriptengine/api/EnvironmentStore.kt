package com.devuloopers.knet.scriptengine.api

import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe, lock-free environment variable store powered by atomic map references.
 * Allows safe concurrent reads, mutations, and snapshot rollbacks across script runs.
 *
 * @param initialValues Optional initial map of environment key-value pairs.
 */
class EnvironmentStore(initialValues: Map<String, String> = emptyMap()) {

    private val store = AtomicReference(initialValues.toMap())

    /**
     * Retrieves the value associated with the given key, or null if absent.
     *
     * @param key Environment variable key name.
     * @return Value string if present, or null.
     */
    operator fun get(key: String): String? {
        return store.get()[key]
    }

    /**
     * Atomically sets or updates an environment key-value pair.
     *
     * @param key Environment variable key name.
     * @param value Environment variable value string.
     */
    operator fun set(key: String, value: String) {
        while (true) {
            val current = store.get()
            val updated = current + (key to value)
            if (store.compareAndSet(current, updated)) {
                break
            }
        }
    }

    /**
     * Atomically removes an environment variable by key.
     *
     * @param key Environment variable key name.
     */
    fun remove(key: String) {
        while (true) {
            val current = store.get()
            if (!current.containsKey(key)) break
            val updated = current - key
            if (store.compareAndSet(current, updated)) {
                break
            }
        }
    }

    /**
     * Atomically clears all environment variables.
     */
    fun clear() {
        store.set(emptyMap())
    }

    /**
     * Checks if the store contains the specified environment variable key.
     *
     * @param key Environment variable key name.
     * @return True if key exists, false otherwise.
     */
    fun has(key: String): Boolean {
        return store.get().containsKey(key)
    }

    /**
     * Returns an immutable snapshot of all current environment key-value pairs.
     *
     * @return Copy of the environment map.
     */
    fun snapshot(): Map<String, String> {
        return store.get()
    }
}
