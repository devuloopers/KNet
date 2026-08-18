package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.core.http.config.HttpClientConfiguration
import com.devuloopers.knet.core.http.cookie.CookieStore
import com.devuloopers.knet.core.http.cookie.MemoryCookieStore
import com.devuloopers.knet.core.http.interceptor.HttpInterceptor
import com.devuloopers.knet.core.http.routing.DefaultProxyRoutingStrategy
import com.devuloopers.knet.core.http.routing.ProxyRoutingStrategy
import com.devuloopers.knet.domain.clientNetwork.decoder.BodyDecoder
import com.devuloopers.knet.domain.clientNetwork.decoder.DecodedBodyResult
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor as DomainHttpExecutor
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.sanitizeTransportHeaders
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.HttpMethod as KNetHttpMethod
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpMethod as KtorHttpMethod
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.TimeSource

/**
 * Reusable Postman-style API Request Dispatcher powered by Ktor 3.5.1 Client.
 *
 * Implements [DomainHttpExecutor] and [AutoCloseable] for Kotlin Multiplatform execution.
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
    private val customEngine: HttpClientEngine? = null,
) : DomainHttpExecutor {

    private var currentConfiguration: HttpClientConfiguration = configuration
    private val proxyHttpClients = PlatformHttpClientCache(::createHttpClient)

    private val directHttpClient: HttpClient by lazy {
        createHttpClient(targetProxyPort = null)
    }

    /**
     * Updates the active client timeout in seconds dynamically at runtime.
     *
     * @param seconds Timeout duration in seconds.
     */
    open fun updateTimeoutSeconds(seconds: Int) {
        val millis = (seconds.coerceAtLeast(1)).toLong() * 1000L
        updateTimeoutMillis(millis)
    }

    /**
     * Updates the active client timeout in milliseconds dynamically at runtime.
     *
     * @param millis Timeout duration in milliseconds.
     */
    open fun updateTimeoutMillis(millis: Long) {
        val coerced = millis.coerceAtLeast(100L)
        currentConfiguration = currentConfiguration.copy(
            timeoutMillis = coerced,
            connectTimeoutMillis = coerced
        )
    }

    /**
     * Returns the active HTTP client configuration.
     *
     * @return Current [HttpClientConfiguration] instance.
     */
    open fun getConfiguration(): HttpClientConfiguration = currentConfiguration

    /**
     * Retrieves or creates a thread-safe cached Ktor [HttpClient] instance configured
     * to route traffic through the specified local [port] proxy endpoint.
     *
     * @param port The target HTTP proxy port number.
     * @return Cached or newly created [HttpClient] configured for proxy interception.
     */
    private fun getProxyHttpClient(port: Int): HttpClient {
        return proxyHttpClients.get(port)
    }

    /**
     * Constructs a Ktor [HttpClient] configured with timeouts, retries, redirect behavior,
     * and optional proxy routing settings.
     *
     * @param targetProxyPort Optional proxy port integer; when non-null and > 0, configures the CIO engine HTTP proxy.
     * @return Configured [HttpClient] instance.
     */
    private fun createHttpClient(targetProxyPort: Int?): HttpClient {
        val block: HttpClientConfig<*>.() -> Unit = {
            install(HttpTimeout) {
                requestTimeoutMillis = currentConfiguration.timeoutMillis
                connectTimeoutMillis = currentConfiguration.connectTimeoutMillis
                socketTimeoutMillis = currentConfiguration.timeoutMillis
            }
            if (currentConfiguration.retryCount > 0) {
                install(HttpRequestRetry) {
                    retryOnExceptionOrServerErrors(maxRetries = currentConfiguration.retryCount)
                    exponentialDelay()
                }
            }
            followRedirects = currentConfiguration.followRedirects
        }

        return createPlatformHttpClient(
            targetProxyPort = targetProxyPort,
            configuration = currentConfiguration,
            customEngine = customEngine,
            block = block
        )
    }

    override suspend fun execute(
        url: String,
        method: KNetHttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
        proxyPort: Int?
    ): ExecutionResult = executeDetailed(
            url = url,
            method = method,
            headers = headers,
            body = body,
            auth = auth,
            proxyPort = proxyPort
        )

    internal suspend fun executeDetailed(
        url: String,
        method: KNetHttpMethod = KNetHttpMethod.GET,
        headers: Map<String, String> = emptyMap(),
        body: OutboundRequestBody = OutboundRequestBody.None,
        auth: ApiRequestAuth = ApiRequestAuth.None,
        proxyPort: Int? = null,
    ): ExecutionResult {
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
        val targetProxyClient =
            if (attemptProxy && effectiveProxyPort != null) getProxyHttpClient(effectiveProxyPort) else null
        val initialClient = targetProxyClient ?: directHttpClient

        val rawResult = try {
            dispatchWithClient(
                client = initialClient,
                url = currentUrl,
                method = method,
                headers = currentHeaders,
                body = currentBody,
                auth = auth,
            )
        } catch (exception: Exception) {
            currentCoroutineContext().ensureActive()

            if (attemptProxy && initialClient !== directHttpClient && routingStrategy.isProxyConnectionFailure(
                    exception,
                    effectiveProxyPort
                )
            ) {
                try {
                    dispatchWithClient(
                        client = directHttpClient,
                        url = currentUrl,
                        method = method,
                        headers = currentHeaders,
                        body = currentBody,
                        auth = auth,
                    )
                } catch (fallbackException: Exception) {
                    val reason = com.devuloopers.knet.core.http.util.NetworkExceptionClassifier.classify(
                        exception = fallbackException,
                        targetUrl = currentUrl,
                        timeoutMs = currentConfiguration.timeoutMillis
                    )
                    ExecutionResult(
                        statusCode = 0,
                        statusText = "Execution Error",
                        headers = emptyMap(),
                        responseBody = "",
                        responseSizeBytes = 0L,
                        isSuccess = false,
                        errorMessage = fallbackException.message ?: fallbackException.toString(),
                        failureReason = reason,
                        timings = ExchangeTimings(totalMillis = 0L),
                    )
                }
            } else {
                val reason = com.devuloopers.knet.core.http.util.NetworkExceptionClassifier.classify(
                    exception = exception,
                    targetUrl = currentUrl,
                    timeoutMs = currentConfiguration.timeoutMillis
                )
                ExecutionResult(
                    statusCode = 0,
                    statusText = "Execution Error",
                    headers = emptyMap(),
                    responseBody = "",
                    responseSizeBytes = 0L,
                    isSuccess = false,
                    errorMessage = exception.message ?: exception.toString(),
                    failureReason = reason,
                    timings = ExchangeTimings(totalMillis = 0L),
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
        method: KNetHttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
    ): ExecutionResult {
        val elapsedTime = TimeSource.Monotonic.markNow()
        val targetMethod = KtorHttpMethod.parse(method.token)

        val response: HttpResponse = if (body is OutboundRequestBody.Multipart) {
            client.submitFormWithBinaryData(
                url = url,
                formData = formData {
                    body.fields.forEach { field -> append(field.name, field.value) }
                }
            ) {
                this.method = targetMethod
                timeout {
                    requestTimeoutMillis = currentConfiguration.timeoutMillis
                    connectTimeoutMillis = currentConfiguration.connectTimeoutMillis
                    socketTimeoutMillis = currentConfiguration.timeoutMillis
                }
                applyHeadersAndAuth(this, headers, auth)
            }
        } else {
            client.request(url) {
                this.method = targetMethod
                timeout {
                    requestTimeoutMillis = currentConfiguration.timeoutMillis
                    connectTimeoutMillis = currentConfiguration.connectTimeoutMillis
                    socketTimeoutMillis = currentConfiguration.timeoutMillis
                }
                applyHeadersAndAuth(this, headers, auth)

                when (body) {
                    is OutboundRequestBody.Json -> {
                        contentType(ContentType.Application.Json)
                        setBody(body.content)
                    }

                    is OutboundRequestBody.Xml -> {
                        contentType(ContentType.Application.Xml)
                        setBody(body.content)
                    }

                    is OutboundRequestBody.GraphQl -> {
                        contentType(ContentType.Application.Json)
                        val trimmed = body.content.trim()
                        val formattedGraphQl = when {
                            trimmed.isBlank() -> "{}"
                            trimmed.startsWith("{") -> trimmed
                            else -> "{\"query\": \"${trimmed.replace("\"", "\\\"").replace("\n", "\\n")}\"}"
                        }
                        setBody(formattedGraphQl)
                    }

                    is OutboundRequestBody.Text -> {
                        contentType(ContentType.parse(body.mediaType))
                        setBody(body.content)
                    }

                    is OutboundRequestBody.FormUrlEncoded -> {
                        contentType(ContentType.Application.FormUrlEncoded)
                        val formParams = Parameters.build {
                            body.fields.forEach { field -> append(field.name, field.value) }
                        }
                        setBody(formParams.formUrlEncode())
                    }

                    OutboundRequestBody.None,
                    is OutboundRequestBody.Multipart -> Unit
                }
            }
        }

        val responseBytes = response.readRawBytes()
        val latency = elapsedTime.elapsedNow().inWholeMilliseconds

        val host = try {
            Url(url).host
        } catch (_: Exception) {
            ""
        }
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
                    } catch (_: Exception) {
                    }
                }
            }
        }

        val responseText = when (val decodedResult = BodyDecoder.decode(responseBytes, responseHeadersList)) {
            is DecodedBodyResult.Success -> decodedResult.bytes.decodeToString()
            is DecodedBodyResult.Identity -> decodedResult.bytes.decodeToString()
            else -> try {
                responseBytes.decodeToString()
            } catch (_: Exception) {
                ""
            }
        }

        if (host.isNotBlank()) {
            cookieStore.getCookies(host).forEach { cookie ->
                if (!responseCookiesMap.containsKey(cookie.name)) {
                    responseCookiesMap[cookie.name] = cookie.value
                }
            }
        }

        return ExecutionResult(
            statusCode = response.status.value,
            statusText = response.status.description,
            headers = responseHeadersMap,
            cookies = responseCookiesMap,
            responseBody = responseText,
            responseSizeBytes = responseBytes.size.toLong(),
            isSuccess = response.status.value in 200..299,
            timings = ExchangeTimings(totalMillis = latency, downloadMillis = latency),
        )
    }

    private fun applyHeadersAndAuth(
        builder: io.ktor.client.request.HttpRequestBuilder,
        headers: Map<String, String>,
        auth: ApiRequestAuth,
    ) {
        val sanitizedHeaders = headers.sanitizeTransportHeaders()
        sanitizedHeaders.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                builder.header(k, v)
            }
        }

        val host = try {
            Url(builder.url.buildString()).host
        } catch (_: Exception) {
            ""
        }
        if (host.isNotBlank()) {
            val storedCookies = cookieStore.getCookies(host)
            if (storedCookies.isNotEmpty() && !headers.keys.any { it.equals("cookie", ignoreCase = true) }) {
                val cookieString = storedCookies.joinToString("; ") { "${it.name}=${it.value}" }
                builder.header("Cookie", cookieString)
            }
        }

        when (auth) {
            is ApiRequestAuth.Bearer -> {
                if (auth.token.isNotBlank()) {
                    val token = if (auth.token.startsWith("Bearer ", ignoreCase = true)) {
                        auth.token
                    } else {
                        "Bearer ${auth.token}"
                    }
                    builder.header("Authorization", token)
                }
            }

            is ApiRequestAuth.Basic -> {
                val credentials = "${auth.username}:${auth.password}"
                if (credentials.isNotBlank()) {
                    val encoded = kotlin.io.encoding.Base64.encode(credentials.encodeToByteArray())
                    builder.header("Authorization", "Basic $encoded")
                }
            }

            is ApiRequestAuth.ApiKey -> {
                if (auth.name.isNotBlank() && auth.value.isNotBlank()) {
                    if (auth.location.contains("query", ignoreCase = true)) {
                        builder.parameter(auth.name, auth.value)
                    } else {
                        builder.header(auth.name, auth.value)
                    }
                }
            }

            is ApiRequestAuth.OAuth2 -> {
                if (auth.token.isNotBlank()) {
                    builder.header("Authorization", "${auth.headerPrefix} ${auth.token}".trim())
                }
            }

            is ApiRequestAuth.AwsSignature,
            ApiRequestAuth.Inherit,
            ApiRequestAuth.None -> Unit
        }
    }

    override fun close() {
        if (customEngine == null) {
            directHttpClient.close()
            proxyHttpClients.close()
        }
    }
}
