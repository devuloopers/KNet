package com.devuloopers.knet.domain.clientNetwork.usecase

import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.HttpMethod

/**
 * Domain UseCase that handles executing outbound client HTTP/HTTPS API requests using strongly-typed contracts.
 *
 * @param httpExecutor Low-level HTTP execution engine.
 * @param validateUseCase Request validation UseCase.
 */
class ExecuteClientApiRequestUseCase(
    private val httpExecutor: HttpExecutor,
    private val validateUseCase: ValidateApiRequestUseCase = ValidateApiRequestUseCase()
) {

    /**
     * Executes an API request and returns domain [ExecutionResult].
     *
     * @param url Request URL.
     * @param method Strongly-typed HTTP Method enum (GET, POST, PUT, DELETE, etc.).
     * @param customMethod Optional custom HTTP method string if method == HttpMethod.CUSTOM.
     * @param headers Map of header key-values.
     * @param queryParams Map of query parameter key-values to append to URL.
     * @param cookies Map of request cookies to send.
     * @param body Request body payload string.
     * @param bodyType Strongly-typed body type enum (NONE, JSON, XML, FORM_DATA, etc.).
     * @param auth Strongly-typed polymorphic authorization configuration (None, Bearer, Basic, ApiKey).
     * @param proxyPort Optional proxy port (routes through proxy when non-null; direct when null).
     * @return [ExecutionResult] containing response details.
     */
    suspend operator fun invoke(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        customMethod: String? = null,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        body: String = "",
        bodyType: RequestBodyType = RequestBodyType.NONE,
        auth: ApiRequestAuth = ApiRequestAuth.None,
        proxyPort: Int? = null
    ): ExecutionResult {
        val sanitizedUrl = try {
            validateUseCase.execute(url, method, customMethod)
        } catch (e: Exception) {
            return ExecutionResult(
                statusCode = 0,
                statusText = "Validation Error",
                errorMessage = e.message ?: "Invalid request parameters"
            )
        }

        // Build target URL with query parameters appended
        val finalUrl = if (queryParams.isNotEmpty()) {
            val queryString = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            if (sanitizedUrl.contains("?")) "$sanitizedUrl&$queryString" else "$sanitizedUrl?$queryString"
        } else {
            sanitizedUrl
        }

        // Merge cookies into request headers if specified
        val mergedHeaders = headers.toMutableMap()
        if (cookies.isNotEmpty() && !mergedHeaders.keys.any { it.equals("cookie", ignoreCase = true) }) {
            mergedHeaders["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        return try {
            httpExecutor.execute(
                url = finalUrl,
                method = method,
                customMethod = customMethod,
                headers = mergedHeaders,
                body = body,
                bodyType = bodyType,
                auth = auth,
                proxyPort = proxyPort
            )
        } catch (e: Exception) {
            ExecutionResult(
                statusCode = 0,
                statusText = "Execution Error",
                errorMessage = e.message ?: e.toString()
            )
        }
    }
}
