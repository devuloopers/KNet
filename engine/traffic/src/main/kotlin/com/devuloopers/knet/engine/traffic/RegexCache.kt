package com.devuloopers.knet.engine.traffic

/**
 * Reusable thread-safe bounded LRU cache for compiled regex patterns.
 * Avoids repeated Regex compilation overhead inside high-frequency Netty event loops
 * while evicting least-recently-used entries to prevent unbounded memory growth.
 */
object RegexCache {

    private const val MAX_CACHE_SIZE = 1000
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, Regex>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    /**
     * Obtains a compiled [Regex] instance for the given pattern string.
     * Compiles and caches on miss; returns cached instance on hit.
     *
     * @param pattern The regular expression pattern string.
     * @return A compiled [Regex] object, or null if pattern is invalid.
     */
    fun getOrNull(pattern: String): Regex? {
        if (pattern.isBlank()) return null
        synchronized(lock) {
            val cached = cache[pattern]
            if (cached != null) return cached
        }

        return try {
            val regex = Regex(pattern)
            synchronized(lock) {
                cache[pattern] = regex
            }
            regex
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clears all cached compiled regex patterns.
     */
    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }
}
