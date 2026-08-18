package com.devuloopers.knet.testingserver.model

import kotlin.time.Clock

/**
 * Standardized DTO for testing server error responses.
 */
data class ErrorResponse(
    val success: Boolean = false,
    val status: Int = 400,
    val error: String = "Bad Request",
    val path: String = "",
    val timestamp: String = Clock.System.now().toString()
)
