package com.devuloopers.knet.testingserver.model

/**
 * Standardized DTO for WebFlux testing server responses.
 */
data class TestServerResponse(
    val status: Int,
    val message: String,
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val body: String? = null,
    val data: Any? = null
)
