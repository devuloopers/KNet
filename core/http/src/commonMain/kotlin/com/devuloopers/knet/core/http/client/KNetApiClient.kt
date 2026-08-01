package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.core.http.config.HttpClientConfiguration
import com.devuloopers.knet.core.http.cookie.CookieStore
import com.devuloopers.knet.core.http.cookie.MemoryCookieStore
import com.devuloopers.knet.core.http.execution.HttpExecutor
import com.devuloopers.knet.core.http.interceptor.HttpInterceptor
import com.devuloopers.knet.core.http.model.ApiExecutionResult
import com.devuloopers.knet.core.http.model.AuthType
import com.devuloopers.knet.core.http.model.HttpMetrics
import com.devuloopers.knet.core.http.model.RequestBodyType
import com.devuloopers.knet.core.http.routing.DefaultProxyRoutingStrategy
import com.devuloopers.knet.core.http.routing.ProxyRoutingStrategy
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Reusable Postman-style API Request Dispatcher powered by Ktor 3.5.1 Client.
 *
 * Implements [HttpExecutor] and [AutoCloseable] for 100% pure Kotlin Multiplatform execution.
 *
 * @param proxyPort Optional proxy port to route outgoing calls through KNet's proxy engine.
 * @param routingStrategy Strategy dictating proxy routing attempt and fallback criteria.
 * @param configuration Configuration options for timeouts, retries, and redirects.
 * @param cookieStore Cookie storage instance.
 * @param customEngine Optional custom Ktor engine (e.g. MockEngine for testing).
 */
open class KNetApiClient(
    private val proxyPort: Int? = null,
    private val routingStrategy: ProxyRoutingStrategy = DefaultProxyRoutingStrategy(),
    private val configuration: HttpClientConfiguration = HttpClientConfiguration(),
    private val cookieStore: CookieStore = MemoryCookieStore(),
    private val interceptors: List<HttpInterceptor> = emptyList(),
    private val customEngine: HttpClientEngine? = null
) : HttpExecutor {

    private val directHttpClient: HttpClient by lazy {
        createHttpClient(isProxy = false)
    }

    private val proxyHttpClient: HttpClient? by lazy {
        proxyPort?.let { createHttpClient(isProxy = true) }
    }

    private fun createHttpClient(isProxy: Boolean): HttpClient {
        val engineFactory = customEngine
        val block: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
            install(HttpTimeout) {
                requestTimeoutMillis = configuration.timeoutMillis
                connectTimeoutMillis = configuration.connectTimeoutMillis
            }
            if (configuration.retryCount > 0) {
                install(HttpRequestRetry) {
                    retryOnExceptionOrServerErrors(maxRetries = configuration.retryCount)
                    exponentialDelay()
                }
            }
            followRedirects = configuration.followRedirects
        }

        return if (engineFactory != null) {
            HttpClient(engineFactory, block)
        } else {
            HttpClient(CIO) {
                if (isProxy && proxyPort != null) {
                    engine {
                        proxy = io.ktor.client.engine.ProxyBuilder.http(Url("http://127.0.0.1:$proxyPort"))
                    }
                }
                block()
            }
        }
    }

    override suspend fun execute(request: SavedApiRequest): ApiExecutionResult {
        val headersMap = request.headers.filter { it.isEnabled }.associate { it.key to it.value }
        val (authType, authToken) = mapAuth(request.auth)

        val bodyTypeEnum = when (request.body.type.lowercase()) {
            "json" -> RequestBodyType.JSON
            "xml" -> RequestBodyType.XML
            "form-data", "form" -> RequestBodyType.FORM_URLENCODED
            "multipart" -> RequestBodyType.MULTIPART
            "graphql" -> RequestBodyType.GRAPHQL
            "raw-text", "raw" -> RequestBodyType.RAW_TEXT
            else -> RequestBodyType.NONE
        }

        return execute(
            url = request.url,
            method = request.methodString,
            headers = headersMap,
            body = request.body.content,
            bodyType = bodyTypeEnum,
            formParameters = emptyMap(),
            authType = authType,
            authToken = authToken
        )
    }

    override suspend fun execute(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String,
        bodyType: RequestBodyType,
        formParameters: Map<String, String>,
        authType: AuthType,
        authToken: String
    ): ApiExecutionResult {
        currentCoroutineContext().ensureActive()

        var currentUrl = url
        var currentHeaders = headers
        var currentBody = body

        for (interceptor in interceptors) {
            val intercepted = interceptor.interceptRequest(currentUrl, currentHeaders, currentBody)
            currentUrl = intercepted.url
            currentHeaders = intercepted.headers
            currentBody = intercepted.body
        }

        val attemptProxy = routingStrategy.shouldAttemptProxy(proxyPort)
        val initialClient = if (attemptProxy) (proxyHttpClient ?: directHttpClient) else directHttpClient

        val rawResult = try {
            dispatchWithClient(
                client = initialClient,
                url = currentUrl,
                method = method,
                headers = currentHeaders,
                body = currentBody,
                bodyType = bodyType,
                formParameters = formParameters,
                authType = authType,
                authToken = authToken
            )
        } catch (exception: Exception) {
            currentCoroutineContext().ensureActive()

            if (attemptProxy && initialClient !== directHttpClient && routingStrategy.isProxyConnectionFailure(exception, proxyPort)) {
                dispatchWithClient(
                    client = directHttpClient,
                    url = currentUrl,
                    method = method,
                    headers = currentHeaders,
                    body = currentBody,
                    bodyType = bodyType,
                    formParameters = formParameters,
                    authType = authType,
                    authToken = authToken
                )
            } else {
                ApiExecutionResult(
                    statusCode = 0,
                    statusText = "Execution Error",
                    headers = emptyMap(),
                    responseBody = "",
                    latencyMs = 0L,
                    responseSizeBytes = 0L,
                    isSuccess = false,
                    errorMessage = exception.message ?: exception.toString(),
                    metrics = HttpMetrics(totalTimeMs = 0L)
                )
            }
        }

        var finalResult = rawResult
        for (interceptor in interceptors) {
            finalResult = interceptor.interceptResponse(finalResult)
        }

        return finalResult
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
        val targetMethod = HttpMethod.parse(method.uppercase())

        val response: HttpResponse = if (bodyType == RequestBodyType.MULTIPART) {
            client.submitFormWithBinaryData(
                url = url,
                formData = formData {
                    formParameters.forEach { (k, v) -> append(k, v) }
                }
            ) {
                this.method = targetMethod
                applyHeadersAndAuth(this, headers, authType, authToken)
            }
        } else {
            client.request(url) {
                this.method = targetMethod
                applyHeadersAndAuth(this, headers, authType, authToken)

                when (bodyType) {
                    RequestBodyType.JSON -> {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                    RequestBodyType.XML -> {
                        contentType(ContentType.Application.Xml)
                        setBody(body)
                    }
                    RequestBodyType.GRAPHQL -> {
                        contentType(ContentType.Application.Json)
                        val formattedGraphQl = if (!body.trim().startsWith("{")) {
                            "{\"query\": \"${body.replace("\"", "\\\"").replace("\n", "\\n")}\"}"
                        } else {
                            body
                        }
                        setBody(formattedGraphQl)
                    }
                    RequestBodyType.RAW_TEXT -> {
                        contentType(ContentType.Text.Plain)
                        setBody(body)
                    }
                    RequestBodyType.FORM_URLENCODED -> {
                        contentType(ContentType.Application.FormUrlEncoded)
                        val formParams = Parameters.build {
                            formParameters.forEach { (k, v) -> append(k, v) }
                        }
                        setBody(formParams.formUrlEncode())
                    }
                    RequestBodyType.MULTIPART, RequestBodyType.NONE -> { }
                }
            }
        }

        val latency = System.currentTimeMillis() - startTime
        val responseText = response.bodyAsText()
        val responseBytes = response.readRawBytes()

        val responseHeadersMap = mutableMapOf<String, String>()
        response.headers.forEach { key, values ->
            responseHeadersMap[key] = values.joinToString(", ")
        }

        val metrics = HttpMetrics(
            totalTimeMs = latency,
            downloadTimeMs = latency
        )

        return ApiExecutionResult(
            statusCode = response.status.value,
            statusText = response.status.description,
            headers = responseHeadersMap,
            responseBody = responseText,
            latencyMs = latency,
            responseSizeBytes = responseBytes.size.toLong(),
            isSuccess = response.status.value in 200..299,
            metrics = metrics
        )
    }

    private fun applyHeadersAndAuth(
        builder: io.ktor.client.request.HttpRequestBuilder,
        headers: Map<String, String>,
        authType: AuthType,
        authToken: String
    ) {
        headers.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                builder.header(k, v)
            }
        }

        when (authType) {
            AuthType.BEARER_TOKEN -> {
                if (authToken.isNotBlank()) {
                    val token = if (authToken.startsWith("Bearer ", ignoreCase = true)) authToken else "Bearer $authToken"
                    builder.header("Authorization", token)
                }
            }
            AuthType.BASIC_AUTH -> {
                if (authToken.isNotBlank()) {
                    val encoded = kotlin.io.encoding.Base64.encode(authToken.encodeToByteArray())
                    builder.header("Authorization", "Basic $encoded")
                }
            }
            AuthType.API_KEY -> {
                if (authToken.isNotBlank()) {
                    builder.header("X-API-Key", authToken)
                }
            }
            AuthType.NONE -> { }
        }
    }

    private fun mapAuth(auth: ApiRequestAuth): Pair<AuthType, String> {
        return when (auth) {
            is ApiRequestAuth.Bearer -> AuthType.BEARER_TOKEN to auth.token
            is ApiRequestAuth.Basic -> AuthType.BASIC_AUTH to "${auth.username}:${auth.password}"
            is ApiRequestAuth.ApiKey -> AuthType.API_KEY to auth.value
            else -> AuthType.NONE to ""
        }
    }

    override fun close() {
        if (customEngine == null) {
            directHttpClient.close()
            proxyHttpClient?.close()
        }
    }
}
