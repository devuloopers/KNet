package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat

/**
 * Fallback strategy formatter for plain unformatted text payloads.
 */
class PlainTextBodyFormatter : BodyFormatter {
    override val priority: Int = 0

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean = true

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        return BodyFormat.RawText(bodyText)
    }
}
