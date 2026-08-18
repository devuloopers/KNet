package com.devuloopers.knet.domain.clientNetwork.model

/**
 * Strongly-typed domain representation of common HTTP MIME content types.
 *
 * @property value Standard HTTP Content-Type header string representation.
 */
enum class MimeType(val value: String) {
    APPLICATION_JSON("application/json"),
    APPLICATION_XML("application/xml"),
    APPLICATION_FORM_URLENCODED("application/x-www-form-urlencoded"),
    MULTIPART_FORM_DATA("multipart/form-data"),
    APPLICATION_GRAPHQL("application/graphql"),
    TEXT_PLAIN("text/plain"),
    TEXT_HTML("text/html"),
    APPLICATION_OCTET_STREAM("application/octet-stream"),
    UNKNOWN("");

    companion object {
        /**
         * Resolves a raw MIME type string or Content-Type header value into a strongly-typed [MimeType] enum.
         *
         * @param rawMime Content-Type header or raw MIME string (e.g. "application/json; charset=utf-8").
         * @return Resolved [MimeType] enum instance.
         */
        fun fromString(rawMime: String): MimeType {
            val clean = rawMime.substringBefore(";").trim().lowercase()
            return when {
                clean.contains("json") -> APPLICATION_JSON
                clean.contains("xml") -> APPLICATION_XML
                clean.contains("x-www-form-urlencoded") -> APPLICATION_FORM_URLENCODED
                clean.contains("form-data") || clean.contains("multipart") -> MULTIPART_FORM_DATA
                clean.contains("graphql") -> APPLICATION_GRAPHQL
                clean.contains("html") -> TEXT_HTML
                clean.contains("text/plain") || clean.startsWith("text/") -> TEXT_PLAIN
                clean.contains("octet-stream") -> APPLICATION_OCTET_STREAM
                clean.isBlank() -> UNKNOWN
                else -> UNKNOWN
            }
        }
    }
}
