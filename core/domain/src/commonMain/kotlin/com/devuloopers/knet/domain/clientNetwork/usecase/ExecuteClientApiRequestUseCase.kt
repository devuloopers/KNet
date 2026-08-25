package com.devuloopers.knet.domain.clientNetwork.usecase

import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionEvent
import com.devuloopers.knet.domain.clientNetwork.executor.HttpStreamingExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
     * Executes the same validated request through a streaming transport when available.
     *
     * Executors that only implement the terminal contract remain compatible and produce one completed event.
     */
    fun stream(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        headers: Map<String, String> = emptyMap(),
        queryParams: List<Pair<String, String>> = emptyList(),
        cookies: Map<String, String> = emptyMap(),
        body: OutboundRequestBody = OutboundRequestBody.None,
        auth: ApiRequestAuth = ApiRequestAuth.None,
        proxyPort: Int? = null,
        httpVersionPreference: HttpVersionPreference = HttpVersionPreference.AUTO,
    ): Flow<HttpExecutionEvent> = flow {
        val prepared = prepare(url, headers, queryParams, cookies)
        if (prepared == null) {
            emit(HttpExecutionEvent.Completed(validationFailure(url)))
            return@flow
        }
        val streamingExecutor = httpExecutor as? HttpStreamingExecutor
        if (streamingExecutor == null) {
            emit(
                HttpExecutionEvent.Completed(
                    invoke(
                        url = url,
                        method = method,
                        headers = headers,
                        queryParams = queryParams,
                        cookies = cookies,
                        body = body,
                        auth = auth,
                        proxyPort = proxyPort,
                        httpVersionPreference = httpVersionPreference,
                    ),
                ),
            )
        } else {
            streamingExecutor.executeStreaming(
                url = prepared.url,
                method = method,
                headers = prepared.headers,
                body = body,
                auth = auth,
                proxyPort = proxyPort,
                httpVersionPreference = httpVersionPreference,
            ).collect(::emit)
        }
    }

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
     * @param httpVersionPreference Requested wire-version policy for this execution.
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
        proxyPort: Int? = null,
        httpVersionPreference: HttpVersionPreference = HttpVersionPreference.AUTO,
    ): ExecutionResult {
        val prepared = prepare(url, headers, queryParams, cookies) ?: return validationFailure(url)

        return try {
            httpExecutor.execute(
                url = prepared.url,
                method = method,
                headers = prepared.headers,
                body = body,
                auth = auth,
                proxyPort = proxyPort,
                httpVersionPreference = httpVersionPreference,
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

    private fun prepare(
        url: String,
        headers: Map<String, String>,
        queryParams: List<Pair<String, String>>,
        cookies: Map<String, String>,
    ): PreparedRequest? {
        val sanitizedUrl = runCatching { validateUseCase.execute(url) }.getOrNull() ?: return null
        val finalUrl = if (queryParams.isNotEmpty()) {
            UrlQueryStringParser.rebuildUrlWithQueryParams(sanitizedUrl, queryParams)
        } else {
            sanitizedUrl
        }
        val mergedHeaders = headers.toMutableMap()
        if (cookies.isNotEmpty() && !mergedHeaders.keys.any { it.equals("cookie", ignoreCase = true) }) {
            mergedHeaders["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
        return PreparedRequest(finalUrl, mergedHeaders)
    }

    private fun validationFailure(url: String): ExecutionResult {
        val message = runCatching { validateUseCase.execute(url) }
            .exceptionOrNull()
            ?.message
            ?: "Invalid request parameters"
        return ExecutionResult(
            statusCode = 0,
            statusText = "Validation Error",
            errorMessage = message,
        )
    }

    private data class PreparedRequest(
        val url: String,
        val headers: Map<String, String>,
    )
}
