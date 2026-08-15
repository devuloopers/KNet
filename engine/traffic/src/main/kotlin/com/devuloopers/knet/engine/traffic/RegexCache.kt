package com.devuloopers.knet.engine.traffic

import java.util.concurrent.ConcurrentHashMap

/**
 * Reusable thread-safe bounded cache for compiled regex patterns.
 * Avoids repeated Regex compilation overhead inside high-frequency Netty event loops
 * while protecting against unbounded memory growth.
 */
object RegexCache {

    private const val MAX_CACHE_SIZE = 1000
    private val cache = ConcurrentHashMap<String, Regex>()

    /**
     * Obtains a compiled [Regex] instance for the given pattern string.
     * Compiles and caches on miss; returns cached instance on hit.
     *
     * @param pattern The regular expression pattern string.
     * @return A compiled [Regex] object, or null if pattern is invalid.
     */
    fun getOrNull(pattern: String): Regex? {
        if (pattern.isBlank()) return null
        val cached = cache[pattern]
        if (cached != null) return cached

        return try {
            val regex = Regex(pattern)
            if (cache.size >= MAX_CACHE_SIZE) {
                cache.clear()
            }
            cache[pattern] = regex
            regex
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clears all cached compiled regex patterns.
     */
    fun clear() {
        cache.clear()
    }
}
