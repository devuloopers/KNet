package com.devuloopers.knet.engine.traffic

/**
 * Utility determining whether an HTTP payload is safe for text replacement
 * to prevent corrupting binary media buffers (images, video, compressed streams).
 */
object MimeTypeUtils {

    private val TEXTUAL_MIME_PREFIXES = listOf(
        "text/",
        "application/json",
        "application/xml",
        "application/javascript",
        "application/graphql",
        "application/x-www-form-urlencoded"
    )

    /**
     * Determines whether the given Content-Type header string represents a textual payload safe for string replacement.
     *
     * @param contentType The Content-Type header value (e.g. "application/json; charset=utf-8").
     * @return True if the payload is textual, false if it is binary or null.
     */
    fun isTextualPayload(contentType: String?): Boolean {
        if (contentType == null) return true // Default assume text if unassigned
        val cleanMime = contentType.split(";").first().trim().lowercase()
        return TEXTUAL_MIME_PREFIXES.any { cleanMime.startsWith(it) }
    }
}
