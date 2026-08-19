package com.devuloopers.knet.domain.clientNetwork.usecase

import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import kotlinx.coroutines.CancellationException

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
     * @param method Extension-safe HTTP method shared by all request-producing features.
     * @param headers Map of header key-values.
     * @param queryParams Ordered query parameter pairs used to rebuild the URL query exactly once.
     * @param cookies Map of request cookies to send.
     * @param body Self-contained strongly typed request body.
     * @param auth Strongly-typed polymorphic authorization configuration (None, Bearer, Basic, ApiKey).
     * @param proxyPort Optional proxy port (routes through proxy when non-null; direct when null).
     * @return [ExecutionResult] containing response details.
     */
    suspend operator fun invoke(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        headers: Map<String, String> = emptyMap(),
        queryParams: List<Pair<String, String>> = emptyList(),
        cookies: Map<String, String> = emptyMap(),
        body: OutboundRequestBody = OutboundRequestBody.None,
        auth: ApiRequestAuth = ApiRequestAuth.None,
        proxyPort: Int? = null
    ): ExecutionResult {
        val sanitizedUrl = try {
            validateUseCase.execute(url)
        } catch (e: Exception) {
            return ExecutionResult(
                statusCode = 0,
                statusText = "Validation Error",
                errorMessage = e.message ?: "Invalid request parameters"
            )
        }

        // Rebuild instead of append because editor and capture contracts also retain a complete URL.
        val finalUrl = if (queryParams.isNotEmpty()) {
            UrlQueryStringParser.rebuildUrlWithQueryParams(sanitizedUrl, queryParams)
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
                headers = mergedHeaders,
                body = body,
                auth = auth,
                proxyPort = proxyPort
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            ExecutionResult(
                statusCode = 0,
                statusText = "Execution Error",
                errorMessage = e.message ?: e.toString()
            )
        }
    }
}
