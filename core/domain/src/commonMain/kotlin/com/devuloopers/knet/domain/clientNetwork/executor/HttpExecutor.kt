package com.devuloopers.knet.domain.clientNetwork.executor

import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.HttpMethod

/**
 * Domain interface contract defining universal outbound HTTP execution.
 * Decouples domain UseCases and ViewModels from underlying HTTP engine implementations.
 */
interface HttpExecutor : AutoCloseable {

    /**
     * Executes an HTTP call with fine-grained strongly-typed parameter specification.
     *
     * @param url Target HTTP/HTTPS URL string.
     * @param method Strongly-typed HTTP method enum (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, CUSTOM).
     * @param customMethod Optional custom method name string when method == HttpMethod.CUSTOM.
     * @param headers Map of header key-value pairs.
     * @param body Request body payload string.
     * @param bodyType Strongly-typed body format enum (NONE, JSON, XML, FORM_DATA, etc.).
     * @param formParameters Key-value form parameters.
     * @param auth Strongly-typed polymorphic authorization configuration (None, Bearer, Basic, ApiKey).
     * @param proxyPort Optional proxy port (routes through proxy when non-null; direct when null).
     * @return [ExecutionResult] containing response details.
     */
    suspend fun execute(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        customMethod: String? = null,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        bodyType: RequestBodyType = RequestBodyType.NONE,
        formParameters: Map<String, String> = emptyMap(),
        auth: ApiRequestAuth = ApiRequestAuth.None,
        proxyPort: Int? = null
    ): ExecutionResult
}
