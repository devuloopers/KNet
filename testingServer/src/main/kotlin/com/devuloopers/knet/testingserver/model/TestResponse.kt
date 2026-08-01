package com.devuloopers.knet.testingserver.model

import java.time.Instant

/**
 * Standardized DTO for testing server HTTP responses.
 */
data class TestResponse(
    val success: Boolean = true,
    val status: Int = 200,
    val method: String = "GET",
    val path: String = "",
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val body: Any? = null,
    val timestamp: String = Instant.now().toString()
)
