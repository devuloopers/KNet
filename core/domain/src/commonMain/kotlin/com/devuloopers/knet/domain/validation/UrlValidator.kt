package com.devuloopers.knet.domain.validation

/**
 * Pure domain validator for HTTP/HTTPS URL strings.
 */
object UrlValidator {

    /**
     * Evaluates whether the provided API request URL is non-blank and structurally valid.
     *
     * @param url Target URL string.
     * @return True if URL is non-blank and valid; false otherwise.
     */
    fun isValid(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return false

        // Environment variable syntax e.g. {{baseUrl}}/endpoint
        if (trimmed.startsWith("{{") && trimmed.contains("}}")) return true

        // Direct localhost or IP targets
        if (trimmed.startsWith("localhost", ignoreCase = true) ||
            trimmed.startsWith("127.0.0.1") ||
            trimmed.startsWith("0.0.0.0") ||
            trimmed.startsWith("http://localhost", ignoreCase = true) ||
            trimmed.startsWith("https://localhost", ignoreCase = true)
        ) {
            return true
        }

        // Standard URL scheme check or dot notation domain check
        val hasValidScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        val hasDomainDot = trimmed.contains(".")

        return hasValidScheme || hasDomainDot
    }
}
