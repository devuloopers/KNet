package com.devuloopers.knet.testingserver.model

import java.time.Instant

/**
 * Standardized DTO for testing server error responses.
 */
data class ErrorResponse(
    val success: Boolean = false,
    val status: Int = 400,
    val error: String = "Bad Request",
    val path: String = "",
    val timestamp: String = Instant.now().toString()
)
