package com.devuloopers.knet.domain.util

import com.devuloopers.knet.domain.clientNetwork.model.MimeType

/**
 * Centralized domain utility for extracting, parsing, and inferring HTTP MIME types.
 */
object MimeTypeUtils {

    /**
     * Performs case-insensitive lookup on HTTP headers and extracts strongly-typed [MimeType] enum.
     * E.g. "application/json; charset=utf-8" -> [MimeType.APPLICATION_JSON].
     *
     * @param headers Map of HTTP response headers.
     * @param defaultMime Default fallback [MimeType] if Content-Type header is absent.
     * @return Strongly-typed [MimeType] enum instance.
     */
    fun extractFromHeaders(headers: Map<String, String>, defaultMime: MimeType = MimeType.TEXT_PLAIN): MimeType {
        val rawHeader = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value
            ?: return defaultMime
        return parsePrimaryMimeType(rawHeader)
    }

    /**
     * Parses a raw Content-Type header string into a strongly-typed [MimeType] enum.
     *
     * @param rawHeader Content-Type header raw value string.
     * @return Strongly-typed [MimeType] enum instance.
     */
    fun parsePrimaryMimeType(rawHeader: String): MimeType {
        if (rawHeader.isBlank()) return MimeType.TEXT_PLAIN
        return MimeType.fromString(rawHeader)
    }

    /**
     * Checks if the strongly-typed [mimeType] or payload structure indicates JSON.
     *
     * @param mimeType Strongly-typed [MimeType] enum instance.
     * @param rawBody Raw response body payload string.
     * @return True if JSON payload; false otherwise.
     */
    fun isJson(mimeType: MimeType, rawBody: String = ""): Boolean {
        if (mimeType == MimeType.APPLICATION_JSON) return true
        val trimmed = rawBody.trim()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    /**
     * Overload accepting raw MIME type string for convenience.
     */
    fun isJson(rawMimeString: String, rawBody: String = ""): Boolean {
        return isJson(parsePrimaryMimeType(rawMimeString), rawBody)
    }

    /**
     * Checks if the strongly-typed [mimeType] or payload structure indicates XML or HTML.
     *
     * @param mimeType Strongly-typed [MimeType] enum instance.
     * @param rawBody Raw response body payload string.
     * @return True if XML/HTML payload; false otherwise.
     */
    fun isXmlOrHtml(mimeType: MimeType, rawBody: String = ""): Boolean {
        if (mimeType == MimeType.APPLICATION_XML || mimeType == MimeType.TEXT_HTML) return true
        val trimmed = rawBody.trim()
        return trimmed.startsWith("<")
    }

    /**
     * Overload accepting raw MIME type string for convenience.
     */
    fun isXmlOrHtml(rawMimeString: String, rawBody: String = ""): Boolean {
        return isXmlOrHtml(parsePrimaryMimeType(rawMimeString), rawBody)
    }
}
