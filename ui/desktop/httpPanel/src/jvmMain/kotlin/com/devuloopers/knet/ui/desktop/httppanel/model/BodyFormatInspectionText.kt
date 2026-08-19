package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.engine.formatter.model.BodyFormat

/**
 * Resolves the formatted text projection suitable for a read-only inspection surface.
 *
 * A JSON stream remains a distinct multi-record transport shape, but each record reuses normal JSON
 * formatting and the existing JSON editor language. Blank lines preserve visible record boundaries.
 *
 * @receiver Resolved payload format, or `null` when detection was unavailable.
 * @param rawBody Original decoded payload used when no formatted text projection exists.
 * @return Formatted inspection text or [rawBody].
 */
internal fun BodyFormat?.toInspectionText(rawBody: String): String {
    return when (this) {
        is BodyFormat.JsonStream -> frames.joinToString("\n\n").ifEmpty { rawBody }
        is BodyFormat.HasTextContent -> textContent.ifEmpty { rawBody }
        else -> rawBody
    }
}
