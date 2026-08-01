package com.devuloopers.knet.domain.validation

/**
 * Pure domain validator for HTTP request and response header fields.
 */
object HeaderValidator {

    /**
     * Validates an HTTP header key string according to HTTP spec.
     */
    fun isValidHeaderKey(key: String): Boolean {
        if (key.isBlank()) return false
        // Control characters or whitespace are invalid in header names
        return key.none { it.isISOControl() || it.isWhitespace() }
    }

    /**
     * Validates an HTTP header value string.
     */
    fun isValidHeaderValue(value: String): Boolean {
        return value.none { it.isISOControl() && it != '\t' }
    }
}
