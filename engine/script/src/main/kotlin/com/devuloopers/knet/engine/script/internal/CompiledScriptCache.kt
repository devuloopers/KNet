package com.devuloopers.knet.engine.script.internal

import java.util.concurrent.ConcurrentHashMap
import javax.script.CompiledScript

/**
 * Thread-safe cache storing compiled JSR-223 [CompiledScript] instances.
 */
class CompiledScriptCache {

    private val cache = ConcurrentHashMap<String, CompiledScript>()

    /**
     * Retrieves a compiled script by key if present.
     */
    fun get(key: String): CompiledScript? = cache[key]

    /**
     * Stores a compiled script in the cache under key.
     */
    fun put(key: String, script: CompiledScript) {
        cache[key] = script
    }

    /**
     * Clears all cached compiled scripts.
     */
    fun clear() {
        cache.clear()
    }
}
