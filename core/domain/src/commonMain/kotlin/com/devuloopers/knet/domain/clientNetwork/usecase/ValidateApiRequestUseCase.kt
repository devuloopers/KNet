package com.devuloopers.knet.domain.clientNetwork.usecase

import com.devuloopers.knet.domain.collection.model.HttpMethod

/**
 * Domain UseCase that validates client HTTP API request input before execution.
 */
class ValidateApiRequestUseCase {

    /**
     * Validates an API request URL and HTTP method enum.
     *
     * @param url Raw request URL string.
     * @param method Strongly-typed HTTP method enum.
     * @param customMethod Optional custom method name string if method == HttpMethod.CUSTOM.
     * @return Sanitized URL string or throws IllegalArgumentException.
     */
    fun execute(url: String, method: HttpMethod, customMethod: String? = null): String {
        require(url.isNotBlank()) { "URL cannot be empty" }
        if (method == HttpMethod.CUSTOM) {
            require(!customMethod.isNullOrBlank()) { "Custom HTTP method name cannot be empty" }
        }

        val trimmedUrl = url.trim()
        val sanitizedUrl = when {
            trimmedUrl.startsWith("http://", ignoreCase = true) || trimmedUrl.startsWith(
                "https://",
                ignoreCase = true
            ) -> trimmedUrl

            else -> "http://$trimmedUrl"
        }

        return sanitizedUrl
    }
}
