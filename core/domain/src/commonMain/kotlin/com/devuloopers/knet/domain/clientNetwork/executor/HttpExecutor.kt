package com.devuloopers.knet.domain.clientNetwork.executor

import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.traffic.model.http.HttpMethod

/**
 * Domain interface contract defining universal outbound HTTP execution.
 * Decouples domain UseCases and ViewModels from underlying HTTP engine implementations.
 */
interface HttpExecutor : AutoCloseable {

    /**
     * Executes an HTTP call with fine-grained strongly-typed parameter specification.
     *
     * @param url Target HTTP/HTTPS URL string.
     * @param method Extension-safe HTTP method shared by API Studio, traffic, and breakpoints.
     * @param headers Map of header key-value pairs.
     * @param body Self-contained strongly typed request body.
     * @param auth Strongly-typed polymorphic authorization configuration (None, Bearer, Basic, ApiKey).
     * @param proxyPort Optional proxy port (routes through proxy when non-null; direct when null).
     * @return [ExecutionResult] containing response details.
     */
    suspend fun execute(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        headers: Map<String, String> = emptyMap(),
        body: OutboundRequestBody = OutboundRequestBody.None,
        auth: ApiRequestAuth = ApiRequestAuth.None,
        proxyPort: Int? = null
    ): ExecutionResult
}
