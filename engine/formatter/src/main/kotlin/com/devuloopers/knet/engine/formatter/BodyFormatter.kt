package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.model.BodyFormat

/**
 * Strategy interface for parsing and formatting a specific HTTP payload format.
 * Implementations are registered in [com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry].
 */
interface BodyFormatter {
    /**
     * Higher priority formatters are checked first during Stage 2 structural inspection fallback.
     * Range: 0 (lowest) to 100 (highest).
     */
    val priority: Int

    /**
     * Returns true if this formatter can handle the given payload.
     *
     * @param headers HTTP headers map (case-insensitive lookup recommended).
     * @param bodyText Raw body text string.
     */
    fun matches(headers: Map<String, String>, bodyText: String): Boolean

    /**
     * Formats the raw payload string into a strongly-typed [BodyFormat].
     *
     * @param headers HTTP headers map.
     * @param bodyText Raw body text string.
     * @return Strongly-typed formatting result.
     */
    fun format(headers: Map<String, String>, bodyText: String): BodyFormat
}
