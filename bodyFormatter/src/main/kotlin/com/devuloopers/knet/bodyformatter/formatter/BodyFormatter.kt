package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat

/**
 * Strategy interface for parsing and formatting a specific HTTP payload format.
 */
interface BodyFormatter {
    /** Higher priority formatters are checked first during fallback inspection. */
    val priority: Int

    /** Returns true if this formatter handles the given payload. */
    fun matches(headers: Map<String, String>, bodyText: String): Boolean

    /** Formats the raw payload string into a strongly-typed [BodyFormat]. */
    fun format(headers: Map<String, String>, bodyText: String): BodyFormat
}
