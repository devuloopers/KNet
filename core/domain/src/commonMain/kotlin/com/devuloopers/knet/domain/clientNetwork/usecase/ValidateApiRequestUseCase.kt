package com.devuloopers.knet.domain.clientNetwork.usecase

/**
 * Domain UseCase that validates client HTTP API request input before execution.
 */
class ValidateApiRequestUseCase {

    /**
     * Validates an API request URL.
     *
     * @param url Raw request URL string.
     * @return Sanitized URL string or throws IllegalArgumentException.
     */
    fun execute(url: String): String {
        require(url.isNotBlank()) { "URL cannot be empty" }

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
