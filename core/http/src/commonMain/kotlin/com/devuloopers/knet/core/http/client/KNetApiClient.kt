package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.core.http.config.HttpClientConfiguration
import com.devuloopers.knet.core.http.cookie.CookieStore
import com.devuloopers.knet.core.http.cookie.MemoryCookieStore
import com.devuloopers.knet.core.http.execution.HttpExecutor as CoreHttpExecutor
import com.devuloopers.knet.core.http.interceptor.HttpInterceptor
import com.devuloopers.knet.core.http.model.ApiExecutionResult
import com.devuloopers.knet.core.http.model.AuthType
import com.devuloopers.knet.core.http.model.HttpMetrics
import com.devuloopers.knet.core.http.model.RequestBodyType
import com.devuloopers.knet.core.http.routing.DefaultProxyRoutingStrategy
import com.devuloopers.knet.core.http.routing.ProxyRoutingStrategy
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor as DomainHttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.clientNetwork.model.KNetHeaders
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.clientNetwork.decoder.BodyDecoder
import com.devuloopers.knet.domain.clientNetwork.decoder.DecodedBodyResult
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.sanitizeTransportHeaders
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
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
 * Implements [DomainHttpExecutor] and [AutoCloseable] for 100% pure Kotlin Multiplatform execution.
 *
 * @param proxyPort Optional proxy port to route outgoing calls through KNet's proxy engine.
 * @param routingStrategy Strategy dictating proxy routing attempt and fallback criteria.
 * @param configuration Configuration options for timeouts, retries, and redirects.
 * @param cookieStore Cookie storage instance.
 * @param customEngine Optional custom Ktor engine (e.g. MockEngine for testing).
 * @param proxyTrafficListener Optional listener to manually capture proxy traffic failures.
 */
open class KNetApiClient(
    private val proxyPort: Int? = null,
    private val routingStrategy: ProxyRoutingStrategy = DefaultProxyRoutingStrategy(),
    private val configuration: HttpClientConfiguration = HttpClientConfiguration(),
    private val cookieStore: CookieStore = MemoryCookieStore(),
    private val interceptors: List<HttpInterceptor> = emptyList(),
    private val customEngine: HttpClientEngine? = null,
    private val proxyTrafficListener: ProxyTrafficListener? = null
) : CoreHttpExecutor, DomainHttpExecutor {

    private val proxyHttpClients = java.util.concurrent.ConcurrentHashMap<Int, HttpClient>()

    private val directHttpClient: HttpClient by lazy {
        createHttpClient(targetProxyPort = null)
    }

    private val proxyHttpClient: HttpClient? by lazy {
        proxyPort?.let { getProxyHttpClient(it) }
    }

    /**
     * Retrieves or creates a thread-safe cached Ktor [HttpClient] instance configured
     * to route traffic through the specified local [port] proxy endpoint.
     *
     * @param port The target HTTP proxy port number.
     * @return Cached or newly created [HttpClient] configured for proxy interception.
     */
    private fun getProxyHttpClient(port: Int): HttpClient {
        return proxyHttpClients.getOrPut(port) {
            createHttpClient(targetProxyPort = port)
        }
    }

    /**
     * Constructs a Ktor [HttpClient] configured with timeouts, retries, redirect behavior,
     * and optional proxy routing settings.
     *
     * @param targetProxyPort Optional proxy port integer; when non-null and > 0, configures the CIO engine HTTP proxy.
     * @return Configured [HttpClient] instance.
     */
    private fun createHttpClient(targetProxyPort: Int?): HttpClient {
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
            HttpClient(getKNetHttpEngine()) {
                if (targetProxyPort != null && targetProxyPort > 0) {
                    engine {
                        proxy = io.ktor.client.engine.ProxyBuilder.http(Url("http://127.0.0.1:$targetProxyPort"))
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
        method: com.devuloopers.knet.domain.collection.model.HttpMethod,
        customMethod: String?,
        headers: Map<String, String>,
        body: String,
        bodyType: com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType,
        formParameters: Map<String, String>,
        auth: ApiRequestAuth,
        proxyPort: Int?
    ): com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult {
        val methodString = if (method == com.devuloopers.knet.domain.collection.model.HttpMethod.CUSTOM && !customMethod.isNullOrBlank()) {
            customMethod
        } else {
            method.name
        }

        val requestBodyType = when (bodyType) {
            com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.JSON -> RequestBodyType.JSON
            com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.XML -> RequestBodyType.XML
            com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.FORM_DATA,
            com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.X_WWW_FORM_URLENCODED -> RequestBodyType.FORM_URLENCODED
            com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.MULTIPART -> RequestBodyType.MULTIPART
            com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.GRAPHQL -> RequestBodyType.GRAPHQL
            com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.RAW_TEXT -> RequestBodyType.RAW_TEXT
            com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.NONE -> RequestBodyType.NONE
        }

        val (mappedAuthType, authToken) = when (auth) {
            is ApiRequestAuth.Bearer -> AuthType.BEARER_TOKEN to auth.token
            is ApiRequestAuth.Basic -> AuthType.BASIC_AUTH to "${auth.username}:${auth.password}"
            is ApiRequestAuth.ApiKey -> AuthType.API_KEY to auth.value
            is ApiRequestAuth.OAuth2 -> AuthType.BEARER_TOKEN to auth.token
            else -> AuthType.NONE to ""
        }

        val result = execute(
            url = url,
            method = methodString,
            headers = headers,
            body = body,
            bodyType = requestBodyType,
            formParameters = formParameters,
            authType = mappedAuthType,
            authToken = authToken,
            proxyPort = proxyPort
        )

        return com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult(
            statusCode = result.statusCode,
            statusText = result.statusText,
            headers = result.headers,
            cookies = result.cookies,
            responseBody = result.responseBody,
            latencyMs = result.latencyMs,
            responseSizeBytes = result.responseSizeBytes,
            isSuccess = result.isSuccess,
            errorMessage = result.errorMessage,
            failureReason = result.failureReason
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
        authToken: String,
        proxyPort: Int?
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

        val effectiveProxyPort = proxyPort ?: this.proxyPort
        val attemptProxy = routingStrategy.shouldAttemptProxy(effectiveProxyPort)
        val targetProxyClient = if (attemptProxy && effectiveProxyPort != null) getProxyHttpClient(effectiveProxyPort) else null
        val initialClient = targetProxyClient ?: directHttpClient

        var currentTransactionId: String? = null
        if (attemptProxy && targetProxyClient != null && proxyTrafficListener != null) {
            currentTransactionId = io.ktor.util.generateNonce()
            val pendingHttpRequest = com.devuloopers.knet.domain.clientNetwork.model.HttpRequest(
                id = currentTransactionId,
                method = method,
                url = currentUrl,
                protocol = "HTTP/1.1",
                headers = currentHeaders.map { it.key to it.value },
                body = if (currentBody.isNotEmpty()) currentBody.encodeToByteArray() else null,
                timestamp = System.currentTimeMillis()
            )
            proxyTrafficListener.onRequestCaptured(pendingHttpRequest)

            val headersWithId = currentHeaders.toMutableMap()
            headersWithId[KNetHeaders.HEADER_TRANSACTION_ID] = currentTransactionId
            currentHeaders = headersWithId
        }

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

            if (attemptProxy && initialClient !== directHttpClient && routingStrategy.isProxyConnectionFailure(exception, effectiveProxyPort)) {
                try {
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
                } catch (fallbackException: Exception) {
                    if (attemptProxy && proxyTrafficListener != null && effectiveProxyPort != null) {
                        recordSyntheticProxyFailure(currentTransactionId, currentUrl, method, currentHeaders, currentBody, fallbackException)
                    }
                    val reason = com.devuloopers.knet.core.http.util.NetworkExceptionClassifier.classify(
                        exception = fallbackException,
                        targetUrl = currentUrl,
                        timeoutMs = configuration.timeoutMillis
                    )
                    ApiExecutionResult(
                        statusCode = 0,
                        statusText = "Execution Error",
                        headers = emptyMap(),
                        responseBody = "",
                        latencyMs = 0L,
                        responseSizeBytes = 0L,
                        isSuccess = false,
                        errorMessage = fallbackException.message ?: fallbackException.toString(),
                        failureReason = reason,
                        metrics = HttpMetrics(totalTimeMs = 0L)
                    )
                }
            } else {
                if (attemptProxy && initialClient !== directHttpClient && proxyTrafficListener != null && effectiveProxyPort != null) {
                    recordSyntheticProxyFailure(currentTransactionId, currentUrl, method, currentHeaders, currentBody, exception)
                }
                val reason = com.devuloopers.knet.core.http.util.NetworkExceptionClassifier.classify(
                    exception = exception,
                    targetUrl = currentUrl,
                    timeoutMs = configuration.timeoutMillis
                )
                ApiExecutionResult(
                    statusCode = 0,
                    statusText = "Execution Error",
                    headers = emptyMap(),
                    responseBody = "",
                    latencyMs = 0L,
                    responseSizeBytes = 0L,
                    isSuccess = false,
                    errorMessage = exception.message ?: exception.toString(),
                    failureReason = reason,
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
                        val trimmed = body.trim()
                        val formattedGraphQl = when {
                            trimmed.isBlank() -> "{}"
                            trimmed.startsWith("{") -> trimmed
                            else -> "{\"query\": \"${trimmed.replace("\"", "\\\"").replace("\n", "\\n")}\"}"
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
                    RequestBodyType.NONE -> { }
                }
            }
        }

        val latency = System.currentTimeMillis() - startTime
        val responseBytes = response.readRawBytes()

        val host = try { Url(url).host } catch (_: Exception) { "" }
        val responseHeadersMap = mutableMapOf<String, String>()
        val responseHeadersList = mutableListOf<Pair<String, String>>()
        val responseCookiesMap = mutableMapOf<String, String>()

        response.headers.forEach { key, values ->
            val valueString = values.joinToString(", ")
            responseHeadersMap[key] = valueString
            responseHeadersList.add(key to valueString)
            if (key.equals("set-cookie", ignoreCase = true)) {
                values.forEach { setCookieRaw ->
                    try {
                        val cookie = io.ktor.http.parseServerSetCookieHeader(setCookieRaw)
                        if (host.isNotBlank()) {
                            cookieStore.storeCookie(host, cookie)
                            responseCookiesMap[cookie.name] = cookie.value
                        }
                    } catch (_: Exception) { }
                }
            }
        }

        val decodedResult = BodyDecoder.decode(responseBytes, responseHeadersList)
        val responseText = when (decodedResult) {
            is DecodedBodyResult.Success -> decodedResult.bytes.decodeToString()
            is DecodedBodyResult.Identity -> decodedResult.bytes.decodeToString()
            else -> try { responseBytes.decodeToString() } catch (_: Exception) { "" }
        }

        if (host.isNotBlank()) {
            cookieStore.getCookies(host).forEach { cookie ->
                if (!responseCookiesMap.containsKey(cookie.name)) {
                    responseCookiesMap[cookie.name] = cookie.value
                }
            }
        }

        val metrics = HttpMetrics(
            totalTimeMs = latency,
            downloadTimeMs = latency
        )

        return ApiExecutionResult(
            statusCode = response.status.value,
            statusText = response.status.description,
            headers = responseHeadersMap,
            cookies = responseCookiesMap,
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
        val sanitizedHeaders = headers.sanitizeTransportHeaders()
        sanitizedHeaders.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                builder.header(k, v)
            }
        }

        val host = try { Url(builder.url.buildString()).host } catch (_: Exception) { "" }
        if (host.isNotBlank()) {
            val storedCookies = cookieStore.getCookies(host)
            if (storedCookies.isNotEmpty() && !headers.keys.any { it.equals("cookie", ignoreCase = true) }) {
                val cookieString = storedCookies.joinToString("; ") { "${it.name}=${it.value}" }
                builder.header("Cookie", cookieString)
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

    private fun recordSyntheticProxyFailure(
        transactionId: String?,
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String,
        exception: Exception
    ) {
        val finalTransactionId = transactionId ?: io.ktor.util.generateNonce()
        val timestamp = System.currentTimeMillis()
        val httpRequest = com.devuloopers.knet.domain.clientNetwork.model.HttpRequest(
            id = finalTransactionId,
            method = method,
            url = url,
            protocol = "HTTP/1.1",
            headers = headers.map { it.key to it.value },
            body = if (body.isNotEmpty()) body.encodeToByteArray() else null,
            timestamp = timestamp
        )
        val httpResponse = com.devuloopers.knet.domain.clientNetwork.model.HttpResponse(
            statusCode = 502,
            statusText = "Bad Gateway",
            headers = listOf(KNetHeaders.HEADER_PROXY_ERROR to (exception.message ?: "Unknown Error")),
            body = ("Proxy Connection Failed: " + (exception.message ?: "Unknown Error")).encodeToByteArray(),
            timestamp = timestamp
        )
        val transaction = com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction(
            id = finalTransactionId,
            request = httpRequest,
            response = httpResponse,
            requestBodyPath = null,
            responseBodyPath = null,
            requestBodySize = httpRequest.body?.size?.toLong() ?: 0L,
            responseBodySize = httpResponse.body?.size?.toLong() ?: 0L,
            durationMs = 0L,
            timestamp = timestamp,
            timings = com.devuloopers.knet.domain.clientNetwork.model.HttpTimings(
                dnsMs = 0L,
                tcpMs = 0L,
                tlsMs = 0L,
                ttfbMs = 0L,
                downloadMs = 0L,
                isReusedConnection = false
            )
        )
        proxyTrafficListener?.onTransactionCaptured(transaction)
    }

    override fun close() {
        if (customEngine == null) {
            directHttpClient.close()
            proxyHttpClient?.close()
            proxyHttpClients.values.forEach { it.close() }
            proxyHttpClients.clear()
        }
    }
}
