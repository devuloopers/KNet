package com.devuloopers.knet.core.http.execution

import com.devuloopers.knet.core.http.model.ApiExecutionResult
import com.devuloopers.knet.core.http.model.AuthType
import com.devuloopers.knet.core.http.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.SavedApiRequest

/**
 * Interface contract defining universal outbound HTTP execution.
 * Decouples consumer applications and ViewModels from underlying HTTP engine implementations.
 */
interface HttpExecutor : AutoCloseable {

    /**
     * Executes an HTTP call defined by a domain [SavedApiRequest] model.
     *
     * @param request Immutable domain request model.
     * @return [ApiExecutionResult] containing response details and timing metrics.
     */
    suspend fun execute(request: SavedApiRequest): ApiExecutionResult

    /**
     * Executes an HTTP call with fine-grained parameter specification.
     *
     * @param url Target HTTP/HTTPS URL string.
     * @param method HTTP method name string (GET, POST, PUT, DELETE, etc.).
     * @param headers Map of HTTP request header key-value pairs.
     * @param body Request body payload content string.
     * @param bodyType Strongly-typed request body format enum.
     * @param formParameters Key-value form parameters for form requests.
     * @param authType Strongly-typed authentication scheme enum.
     * @param authToken Authentication credential/token string.
     * @param proxyPort Optional proxy port for local HTTP proxy interception.
     * @return [ApiExecutionResult] containing response data, headers, cookies, timing metrics, and status code.
     */
    suspend fun execute(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        bodyType: RequestBodyType = RequestBodyType.NONE,
        formParameters: Map<String, String> = emptyMap(),
        authType: AuthType = AuthType.NONE,
        authToken: String = "",
        proxyPort: Int? = null
    ): ApiExecutionResult
}
