package com.devuloopers.knet.domain.validation

/**
 * Pure domain validator for environment variables and substitution keys.
 */
object EnvironmentValidator {

    /**
     * Validates an environment variable key name (e.g., "baseUrl", "authToken").
     */
    fun isValidVariableKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return false
        // Key should only contain alphanumeric characters, underscores, or hyphens
        return trimmed.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }
}
