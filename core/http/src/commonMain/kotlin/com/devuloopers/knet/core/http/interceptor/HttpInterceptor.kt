package com.devuloopers.knet.core.http.interceptor

import com.devuloopers.knet.core.http.model.ApiExecutionResult

/**
 * Pipeline interceptor interface for request pre-processing and response post-processing.
 */
interface HttpInterceptor {
    /**
     * Intercepts and transforms outbound request parameters before dispatch.
     */
    suspend fun interceptRequest(
        url: String,
        headers: Map<String, String>,
        body: String
    ): InterceptedRequestData = InterceptedRequestData(url, headers, body)

    /**
     * Intercepts and transforms response results after execution.
     */
    suspend fun interceptResponse(result: ApiExecutionResult): ApiExecutionResult = result
}

/**
 * Data class representing request data modified by interceptors.
 */
data class InterceptedRequestData(
    val url: String,
    val headers: Map<String, String>,
    val body: String
)
