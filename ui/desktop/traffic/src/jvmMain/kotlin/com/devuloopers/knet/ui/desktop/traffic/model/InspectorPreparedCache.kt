package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Main-thread-confined LRU cache bounded by both prepared inspector count and estimated bytes.
 *
 * Body previews and formatter results dominate the retained weight. A state larger than the
 * complete byte budget is deliberately not cached.
 */
internal class InspectorPreparedCache(
    private val maximumEntries: Int,
    private val maximumRetainedBytes: Long,
) {
    private val entries = LinkedHashMap<String, InspectorPreparedState>(maximumEntries, 0.75f, true)
    private var retainedBytes = 0L

    init {
        require(maximumEntries > 0) { "Inspector cache entry limit must be positive." }
        require(maximumRetainedBytes > 0L) { "Inspector cache byte limit must be positive." }
    }

    /** Returns the cached state and promotes it to most recently used. */
    operator fun get(transactionId: String): InspectorPreparedState? = entries[transactionId]

    /** Adds or replaces one state and evicts least-recently-used entries until both limits hold. */
    fun put(state: InspectorPreparedState) {
        require(state.transactionId.isNotBlank()) { "A cached inspector state requires a transaction ID." }
        entries.remove(state.transactionId)?.let { previous ->
            retainedBytes -= previous.estimatedRetainedBytes
        }

        val weight = state.estimatedRetainedBytes
        if (weight > maximumRetainedBytes) return

        entries[state.transactionId] = state
        retainedBytes += weight
        while (entries.size > maximumEntries || retainedBytes > maximumRetainedBytes) {
            val eldest = entries.entries.firstOrNull() ?: break
            retainedBytes -= eldest.value.estimatedRetainedBytes
            entries.remove(eldest.key)
        }
    }

    /** Removes every prepared detail and releases its retained preview references. */
    fun clear() {
        entries.clear()
        retainedBytes = 0L
    }
}
