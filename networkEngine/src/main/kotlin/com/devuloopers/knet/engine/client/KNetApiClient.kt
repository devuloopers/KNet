package com.devuloopers.knet.engine.client

import com.devuloopers.knet.engine.client.model.ApiExecutionResult
import com.devuloopers.knet.engine.client.model.AuthType
import com.devuloopers.knet.engine.client.model.RequestBodyType
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.Closeable
import kotlin.io.encoding.Base64

/**
 * Reusable Postman-style API Request Dispatcher powered by Ktor 3.5.1 Client (CIO engine).
 *
 * Supports:
 * - All standard HTTP methods (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS).
 * - All Postman body modes: JSON, XML, Form URL-encoded, Multipart form-data, GraphQL, Raw Text.
 * - Authentication modes: Bearer token, Basic auth, API Key header/param.
 * - Custom headers and request timeouts.
 * - Dynamic dual-mode routing: Uses local proxy (`127.0.0.1:8888`) when running, and automatically
 *   falls back to direct HTTP execution when proxy engine is stopped or unreachable.
 *
 * @param proxyPort Optional proxy port to route outgoing calls through KNet's proxy engine.
 * @param routingStrategy Strategy dictating proxy routing attempt and fallback criteria.
 */
open class KNetApiClient(
    private val proxyPort: Int? = null,
    private val routingStrategy: ProxyRoutingStrategy = DefaultProxyRoutingStrategy()
) : Closeable {

    private val directHttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
            }
        }
    }

    private val proxyHttpClient by lazy {
        proxyPort?.let { port ->
            HttpClient(CIO) {
                engine {
                    proxy = io.ktor.client.engine.ProxyBuilder.http(Url("http://127.0.0.1:$port"))
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 10_000
                }
            }
        }
    }

    /**
     * Executes an outbound API call with full Postman body and header dispatching.
     * Automatically attempts proxy routing if active, and falls back to direct execution if proxy is unreachable.
     *
     * @param url Target endpoint URL.
     * @param method HTTP method string (e.g. "GET", "POST", "PUT").
     * @param headers Map of request HTTP headers.
     * @param body Raw body text (for JSON, XML, Raw Text).
     * @param bodyType Postman-style [RequestBodyType].
     * @param formParameters Key-value pairs for form-urlencoded or multipart requests.
     * @param authType [AuthType] specifying authentication method.
     * @param authToken Token string for Bearer/Basic auth.
     * @return [ApiExecutionResult] containing status code, latency, headers, and body.
     */
    open suspend fun execute(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        bodyType: RequestBodyType = RequestBodyType.NONE,
        formParameters: Map<String, String> = emptyMap(),
        authType: AuthType = AuthType.NONE,
        authToken: String = ""
    ): ApiExecutionResult {
        val attemptProxy = routingStrategy.shouldAttemptProxy(proxyPort)
        val initialClient = if (attemptProxy) (proxyHttpClient ?: directHttpClient) else directHttpClient

        return try {
            dispatchWithClient(
                client = initialClient,
                url = url,
                method = method,
                headers = headers,
                body = body,
                bodyType = bodyType,
                formParameters = formParameters,
                authType = authType,
                authToken = authToken
            )
        } catch (exception: Exception) {
            if (attemptProxy && initialClient !== directHttpClient && routingStrategy.isProxyConnectionFailure(exception, proxyPort)) {
                // Local Proxy is stopped/unreachable — fallback gracefully to direct HTTP execution
                try {
                    dispatchWithClient(
                        client = directHttpClient,
                        url = url,
                        method = method,
                        headers = headers,
                        body = body,
                        bodyType = bodyType,
                        formParameters = formParameters,
                        authType = authType,
                        authToken = authToken
                    )
                } catch (fallbackException: Exception) {
                    buildErrorResult(fallbackException)
                }
            } else {
                buildErrorResult(exception)
            }
        }
    }

    private suspend fun dispatchWithClient(
        client: HttpClient,
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String,
        bodyType: RequestBodyType,
        formParameters: Map<String, String>,
        authType: AuthType,
        authToken: String
    ): ApiExecutionResult {
        val startTime = System.currentTimeMillis()
        val response = client.request(url) {
            this.method = HttpMethod.parse(method.uppercase())

            // 1. Headers & Auth
            headers.forEach { (key, value) ->
                header(key, value)
            }

            when (authType) {
                AuthType.BEARER_TOKEN -> {
                    if (authToken.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer $authToken")
                    }
                }

                AuthType.BASIC_AUTH -> {
                    if (authToken.isNotBlank()) {
                        val encoded = Base64.encode(authToken.encodeToByteArray())
                        header(HttpHeaders.Authorization, "Basic $encoded")
                    }
                }

                AuthType.API_KEY -> {
                    if (authToken.isNotBlank()) {
                        header("X-API-Key", authToken)
                    }
                }

                AuthType.NONE -> { /* No auth header added */ }
            }

            // 2. Request Body Dispatching
            if (this.method != HttpMethod.Get && this.method != HttpMethod.Head) {
                when (bodyType) {
                    RequestBodyType.JSON -> {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }

                    RequestBodyType.XML -> {
                        contentType(ContentType.Application.Xml)
                        setBody(body)
                    }

                    RequestBodyType.FORM_URLENCODED -> {
                        contentType(ContentType.Application.FormUrlEncoded)
                        val encodedForm = formParameters.entries.joinToString("&") { (k, v) -> "$k=$v" }
                        setBody(encodedForm)
                    }

                    RequestBodyType.MULTIPART -> {
                        setBody(MultiPartFormDataContent(formData {
                            formParameters.forEach { (k, v) ->
                                append(k, v)
                            }
                        }))
                    }

                    RequestBodyType.GRAPHQL -> {
                        contentType(ContentType.Application.Json)
                        val gqlJson = "{\"query\": ${escapeJsonString(body)}}"
                        setBody(gqlJson)
                    }

                    RequestBodyType.RAW_TEXT -> {
                        contentType(ContentType.Text.Plain)
                        setBody(body)
                    }

                    RequestBodyType.NONE -> {
                        if (body.isNotBlank()) {
                            setBody(body)
                        }
                    }
                }
            }
        }

        val endTime = System.currentTimeMillis()
        val responseText = response.bodyAsText()
        val headerMap = response.headers.entries().associate { it.key to it.value.joinToString(", ") }

        return ApiExecutionResult(
            statusCode = response.status.value,
            statusText = response.status.description,
            headers = headerMap,
            responseBody = responseText,
            latencyMs = (endTime - startTime).coerceAtLeast(1L),
            responseSizeBytes = responseText.toByteArray().size.toLong()
        )
    }

    private fun buildErrorResult(exception: Exception): ApiExecutionResult {
        return ApiExecutionResult(
            statusCode = 0,
            statusText = "Execution Error",
            headers = emptyMap(),
            responseBody = "",
            latencyMs = 1L,
            responseSizeBytes = 0L,
            isSuccess = false,
            errorMessage = exception.message ?: exception.toString()
        )
    }

    private fun escapeJsonString(input: String): String {
        return "\"" + input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""
    }

    // ── Dedicated Helper Methods for HTTP Verbs ─────────────────────────────

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        authType: AuthType = AuthType.NONE,
        authToken: String = ""
    ): ApiExecutionResult = execute(
        url = url,
        method = "GET",
        headers = headers,
        authType = authType,
        authToken = authToken
    )

    suspend fun post(
        url: String,
        body: String = "",
        bodyType: RequestBodyType = RequestBodyType.JSON,
        headers: Map<String, String> = emptyMap(),
        formParameters: Map<String, String> = emptyMap(),
        authType: AuthType = AuthType.NONE,
        authToken: String = ""
    ): ApiExecutionResult = execute(
        url = url,
        method = "POST",
        body = body,
        bodyType = bodyType,
        headers = headers,
        formParameters = formParameters,
        authType = authType,
        authToken = authToken
    )

    suspend fun put(
        url: String,
        body: String = "",
        bodyType: RequestBodyType = RequestBodyType.JSON,
        headers: Map<String, String> = emptyMap(),
        formParameters: Map<String, String> = emptyMap(),
        authType: AuthType = AuthType.NONE,
        authToken: String = ""
    ): ApiExecutionResult = execute(
        url = url,
        method = "PUT",
        body = body,
        bodyType = bodyType,
        headers = headers,
        formParameters = formParameters,
        authType = authType,
        authToken = authToken
    )

    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
        authType: AuthType = AuthType.NONE,
        authToken: String = ""
    ): ApiExecutionResult = execute(
        url = url,
        method = "DELETE",
        headers = headers,
        authType = authType,
        authToken = authToken
    )

    suspend fun patch(
        url: String,
        body: String = "",
        bodyType: RequestBodyType = RequestBodyType.JSON,
        headers: Map<String, String> = emptyMap(),
        authType: AuthType = AuthType.NONE,
        authToken: String = ""
    ): ApiExecutionResult = execute(
        url = url,
        method = "PATCH",
        body = body,
        bodyType = bodyType,
        headers = headers,
        authType = authType,
        authToken = authToken
    )

    override fun close() {
        runCatching { directHttpClient.close() }
        runCatching { proxyHttpClient?.close() }
    }
}
