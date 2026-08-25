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
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionBodyChunk
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionEvent
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.domain.clientNetwork.executor.HttpStreamingExecutor
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor as DomainHttpExecutor
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.sanitizeTransportHeaders
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficAttributionHeader
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlin.time.TimeSource

/**
 * Reusable Postman-style API Request Dispatcher powered by Ktor 3.5.1 Client.
 *
 * Implements [DomainHttpExecutor] and [AutoCloseable] for Kotlin Multiplatform execution.
 *
 * @param proxyPort Optional proxy port to route outgoing calls through KNet's proxy engine.
 * @param localProxyTlsTrust Optional certificate authority trusted only for proxy-routed TLS.
 * @param captureOrigin Optional origin attached only while routing through KNet's local proxy.
 * @param routingStrategy Strategy dictating proxy routing attempt and fallback criteria.
 * @param configuration Configuration options for timeouts, retries, and redirects.
 * @param cookieStore Cookie storage instance.
 * @param customEngine Optional custom Ktor engine (e.g. MockEngine for testing).
 */
open class KNetApiClient(
    private val proxyPort: Int? = null,
    private val localProxyTlsTrust: LocalProxyTlsTrust? = null,
    private val captureOrigin: TrafficOrigin? = null,
    private val routingStrategy: ProxyRoutingStrategy = DefaultProxyRoutingStrategy(),
    private val configuration: HttpClientConfiguration = HttpClientConfiguration(),
    private val cookieStore: CookieStore = MemoryCookieStore(),
    private val interceptors: List<HttpInterceptor> = emptyList(),
    private val customEngine: HttpClientEngine? = null,
) : DomainHttpExecutor, HttpStreamingExecutor {

    private var currentConfiguration: HttpClientConfiguration = configuration
    private val httpTwoTransport = HttpTwoTransport()
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
            localProxyTlsTrust = localProxyTlsTrust.takeIf { targetProxyPort?.let { it > 0 } == true },
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
        proxyPort: Int?,
        httpVersionPreference: HttpVersionPreference,
    ): ExecutionResult = executeDetailed(
            url = url,
            method = method,
            headers = headers,
            body = body,
            auth = auth,
            proxyPort = proxyPort,
            httpVersionPreference = httpVersionPreference,
        )

    override fun executeStreaming(
        url: String,
        method: KNetHttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
        proxyPort: Int?,
        httpVersionPreference: HttpVersionPreference,
    ): Flow<HttpExecutionEvent> = flow {
        if (httpVersionPreference == HttpVersionPreference.HTTP_1_0) {
            emit(HttpExecutionEvent.Completed(executeDetailed(url, method, headers, body, auth, proxyPort, httpVersionPreference)))
            return@flow
        }

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
        currentHeaders = currentHeaders.withoutCaptureAttribution()
        val effectiveProxyPort = proxyPort ?: this@KNetApiClient.proxyPort
        val attemptProxy = routingStrategy.shouldAttemptProxy(effectiveProxyPort)
        var responseStarted = false

        suspend fun dispatch(targetProxyPort: Int?): ExecutionResult {
            val attributedHeaders = currentHeaders.withCaptureAttribution(targetProxyPort)
            return when (httpVersionPreference) {
                HttpVersionPreference.HTTP_2 -> dispatchHttpTwoStreaming(
                    url = currentUrl,
                    method = method,
                    headers = attributedHeaders,
                    body = currentBody,
                    auth = auth,
                    targetProxyPort = targetProxyPort,
                    requireHttpTwo = true,
                    onEvent = { event ->
                        if (event is HttpExecutionEvent.ResponseHead) responseStarted = true
                        emit(event)
                    },
                )
                HttpVersionPreference.AUTO -> if (customEngine == null) {
                    dispatchHttpTwoStreaming(
                        url = currentUrl,
                        method = method,
                        headers = attributedHeaders,
                        body = currentBody,
                        auth = auth,
                        targetProxyPort = targetProxyPort,
                        requireHttpTwo = false,
                        onEvent = { event ->
                            if (event is HttpExecutionEvent.ResponseHead) responseStarted = true
                            emit(event)
                        },
                    )
                } else {
                    dispatchWithClientStreaming(
                        client = targetProxyPort?.let(::getProxyHttpClient) ?: directHttpClient,
                        url = currentUrl,
                        method = method,
                        headers = attributedHeaders,
                        body = currentBody,
                        auth = auth,
                        onEvent = { event ->
                            if (event is HttpExecutionEvent.ResponseHead) responseStarted = true
                            emit(event)
                        },
                    )
                }
                HttpVersionPreference.HTTP_1_1 -> dispatchWithClientStreaming(
                    client = targetProxyPort?.let(::getProxyHttpClient) ?: directHttpClient,
                    url = currentUrl,
                    method = method,
                    headers = attributedHeaders,
                    body = currentBody,
                    auth = auth,
                    onEvent = { event ->
                        if (event is HttpExecutionEvent.ResponseHead) responseStarted = true
                        emit(event)
                    },
                )
                HttpVersionPreference.HTTP_1_0 -> error("HTTP/1.0 is handled by the terminal compatibility path.")
            }
        }

        val initialProxyPort = effectiveProxyPort.takeIf { attemptProxy }
        val rawResult = try {
            dispatch(initialProxyPort)
        } catch (exception: Exception) {
            currentCoroutineContext().ensureActive()
            if (!responseStarted && initialProxyPort != null &&
                routingStrategy.isProxyConnectionFailure(exception, effectiveProxyPort)
            ) {
                try {
                    dispatch(null)
                } catch (fallbackException: Exception) {
                    currentCoroutineContext().ensureActive()
                    failureResult(fallbackException, currentUrl)
                }
            } else {
                failureResult(exception, currentUrl)
            }
        }
        var finalResult = rawResult
        for (interceptor in interceptors) finalResult = interceptor.interceptResponse(finalResult)
        emit(HttpExecutionEvent.Completed(finalResult))
    }

    internal suspend fun executeDetailed(
        url: String,
        method: KNetHttpMethod = KNetHttpMethod.GET,
        headers: Map<String, String> = emptyMap(),
        body: OutboundRequestBody = OutboundRequestBody.None,
        auth: ApiRequestAuth = ApiRequestAuth.None,
        proxyPort: Int? = null,
        httpVersionPreference: HttpVersionPreference = HttpVersionPreference.AUTO,
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
        currentHeaders = currentHeaders.withoutCaptureAttribution()

        val effectiveProxyPort = proxyPort ?: this.proxyPort
        val attemptProxy = routingStrategy.shouldAttemptProxy(effectiveProxyPort)

        suspend fun dispatch(targetProxyPort: Int?): ExecutionResult {
            val attributedHeaders = currentHeaders.withCaptureAttribution(targetProxyPort)
            return when (httpVersionPreference) {
                HttpVersionPreference.HTTP_1_0 -> dispatchHttpOneZero(
                    url = currentUrl,
                    method = method,
                    headers = attributedHeaders,
                    body = currentBody,
                    auth = auth,
                    targetProxyPort = targetProxyPort,
                )
                HttpVersionPreference.HTTP_2 -> dispatchHttpTwo(
                    url = currentUrl,
                    method = method,
                    headers = attributedHeaders,
                    body = currentBody,
                    auth = auth,
                    targetProxyPort = targetProxyPort,
                    requireHttpTwo = true,
                )
                HttpVersionPreference.AUTO -> if (customEngine == null) {
                    dispatchHttpTwo(
                        url = currentUrl,
                        method = method,
                        headers = attributedHeaders,
                        body = currentBody,
                        auth = auth,
                        targetProxyPort = targetProxyPort,
                        requireHttpTwo = false,
                    )
                } else {
                    dispatchWithClient(
                        client = targetProxyPort?.let(::getProxyHttpClient) ?: directHttpClient,
                        url = currentUrl,
                        method = method,
                        headers = attributedHeaders,
                        body = currentBody,
                        auth = auth,
                    )
                }
                HttpVersionPreference.HTTP_1_1 -> dispatchWithClient(
                    client = targetProxyPort?.let(::getProxyHttpClient) ?: directHttpClient,
                    url = currentUrl,
                    method = method,
                    headers = attributedHeaders,
                    body = currentBody,
                    auth = auth,
                )
            }
        }

        val initialProxyPort = effectiveProxyPort.takeIf { attemptProxy }

        val rawResult = try {
            dispatch(initialProxyPort)
        } catch (exception: Exception) {
            currentCoroutineContext().ensureActive()

            if (initialProxyPort != null && routingStrategy.isProxyConnectionFailure(
                    exception,
                    effectiveProxyPort
                )
            ) {
                try {
                    dispatch(null)
                } catch (fallbackException: Exception) {
                    currentCoroutineContext().ensureActive()
                    failureResult(fallbackException, currentUrl)
                }
            } else {
                failureResult(exception, currentUrl)
            }
        }

        var finalResult = rawResult
        for (interceptor in interceptors) {
            finalResult = interceptor.interceptResponse(finalResult)
        }

        return finalResult
    }

    private suspend fun dispatchHttpTwo(
        url: String,
        method: KNetHttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
        targetProxyPort: Int?,
        requireHttpTwo: Boolean,
    ): ExecutionResult {
        val elapsedTime = TimeSource.Monotonic.markNow()
        val prepared = prepareExactTransportRequest(url, headers, auth)
        val response = httpTwoTransport.execute(
            HttpTwoTransportRequest(
                url = prepared.url,
                method = method,
                headers = prepared.headers,
                body = body,
                proxyPort = targetProxyPort,
                configuration = currentConfiguration,
                localProxyTlsTrust = localProxyTlsTrust.takeIf { targetProxyPort != null },
                requireHttpTwo = requireHttpTwo,
            ),
        )
        return response.toExecutionResult(
            requestUrl = prepared.url,
            latencyMillis = elapsedTime.elapsedNow().inWholeMilliseconds,
        )
    }

    private suspend fun dispatchHttpTwoStreaming(
        url: String,
        method: KNetHttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
        targetProxyPort: Int?,
        requireHttpTwo: Boolean,
        onEvent: suspend (HttpExecutionEvent) -> Unit,
    ): ExecutionResult {
        val elapsedTime = TimeSource.Monotonic.markNow()
        val prepared = prepareExactTransportRequest(url, headers, auth)
        val response = httpTwoTransport.executeStreaming(
            request = HttpTwoTransportRequest(
                url = prepared.url,
                method = method,
                headers = prepared.headers,
                body = body,
                proxyPort = targetProxyPort,
                configuration = currentConfiguration,
                localProxyTlsTrust = localProxyTlsTrust.takeIf { targetProxyPort != null },
                requireHttpTwo = requireHttpTwo,
            ),
            onResponseHead = { head ->
                onEvent(HttpExecutionEvent.ResponseHead(head.toDomainHead(prepared.url)))
            },
            onBodyChunk = { bytes ->
                onEvent(HttpExecutionEvent.BodyChunk(HttpExecutionBodyChunk(bytes)))
            },
        )
        return response.toExecutionResult(
            requestUrl = prepared.url,
            latencyMillis = elapsedTime.elapsedNow().inWholeMilliseconds,
        )
    }

    /** Adds attribution only to the local proxy hop; direct requests can never leak this field. */
    private fun Map<String, String>.withCaptureAttribution(targetProxyPort: Int?): Map<String, String> {
        val origin = captureOrigin ?: return this
        if (targetProxyPort == null) return this
        return this + (TrafficAttributionHeader.NAME to origin.token)
    }

    /** User-authored or interceptor-provided values cannot spoof KNet's internal attribution. */
    private fun Map<String, String>.withoutCaptureAttribution(): Map<String, String> =
        filterKeys { name -> !name.equals(TrafficAttributionHeader.NAME, ignoreCase = true) }

    private suspend fun dispatchHttpOneZero(
        url: String,
        method: KNetHttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
        targetProxyPort: Int?,
    ): ExecutionResult {
        val elapsedTime = TimeSource.Monotonic.markNow()
        val prepared = prepareExactTransportRequest(url, headers, auth)
        val response = executeHttpOneZero(
            HttpOneZeroTransportRequest(
                url = prepared.url,
                method = method,
                headers = prepared.headers,
                body = body,
                proxyPort = targetProxyPort,
                configuration = currentConfiguration,
                localProxyTlsTrust = localProxyTlsTrust.takeIf { targetProxyPort != null },
            ),
        )
        return response.toExecutionResult(
            requestUrl = prepared.url,
            latencyMillis = elapsedTime.elapsedNow().inWholeMilliseconds,
        )
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
        val response = openResponseWithClient(client, url, method, headers, body, auth)
        val responseBytes = response.readRawBytes()
        return response.toExecutionResult(url, responseBytes, elapsedTime.elapsedNow().inWholeMilliseconds)
    }

    private suspend fun dispatchWithClientStreaming(
        client: HttpClient,
        url: String,
        method: KNetHttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
        onEvent: suspend (HttpExecutionEvent) -> Unit,
    ): ExecutionResult {
        val elapsedTime = TimeSource.Monotonic.markNow()
        val statement = prepareRequestWithClient(client, url, method, headers, body, auth)
        return statement.execute { response ->
            val head = response.toDomainHead(url)
            onEvent(HttpExecutionEvent.ResponseHead(head))
            val eventStream = head.headers.isIdentityEventStreamHeaders()
            val retainedChunks = mutableListOf<ByteArray>()
            var retainedBytes = 0
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(STREAM_CHUNK_BYTES)
            try {
                while (!channel.isClosedForRead) {
                    currentCoroutineContext().ensureActive()
                    val read = channel.readAvailable(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    val chunk = buffer.copyOf(read)
                    onEvent(HttpExecutionEvent.BodyChunk(HttpExecutionBodyChunk(chunk)))
                    if (!eventStream) {
                        check(retainedBytes <= MAXIMUM_TERMINAL_BODY_BYTES - read) {
                            "HTTP response exceeds the bounded API Studio body limit."
                        }
                        retainedChunks += chunk
                        retainedBytes += read
                    }
                }
            } catch (cancellation: CancellationException) {
                channel.cancel(cancellation)
                response.cancel("Streaming response collection was cancelled.", cancellation)
                throw cancellation
            }
            val responseBytes = ByteArray(retainedBytes)
            var offset = 0
            retainedChunks.forEach { chunk ->
                chunk.copyInto(responseBytes, destinationOffset = offset)
                offset += chunk.size
            }
            response.toExecutionResult(url, responseBytes, elapsedTime.elapsedNow().inWholeMilliseconds)
        }
    }

    private suspend fun prepareRequestWithClient(
        client: HttpClient,
        url: String,
        method: KNetHttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
    ): HttpStatement {
        val targetMethod = KtorHttpMethod.parse(method.token)
        val configure: HttpRequestBuilder.() -> Unit = {
            this.method = targetMethod
            timeout {
                requestTimeoutMillis = currentConfiguration.timeoutMillis
                connectTimeoutMillis = currentConfiguration.connectTimeoutMillis
                socketTimeoutMillis = currentConfiguration.timeoutMillis
            }
            applyHeadersAndAuth(this, headers, auth)
        }
        return if (body is OutboundRequestBody.Multipart) {
            client.prepareFormWithBinaryData(
                url = url,
                formData = formData {
                    body.fields.forEach { field -> append(field.name, field.value) }
                },
                block = configure,
            )
        } else {
            client.prepareRequest(url) {
                configure()
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
    }

    private suspend fun openResponseWithClient(
        client: HttpClient,
        url: String,
        method: KNetHttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
    ): HttpResponse = prepareRequestWithClient(client, url, method, headers, body, auth).execute()

    private fun HttpResponse.toDomainHead(requestUrl: String): HttpExecutionResponseHead {
        val (responseHeadersMap, responseCookiesMap) = collectHeadersAndCookies(requestUrl, headers)
        return HttpExecutionResponseHead(
            statusCode = status.value,
            statusText = status.description,
            headers = responseHeadersMap,
            cookies = responseCookiesMap,
            protocol = ApplicationProtocol.fromToken(version.toString()),
        )
    }

    private fun HttpResponse.toExecutionResult(
        requestUrl: String,
        responseBytes: ByteArray,
        latency: Long,
    ): ExecutionResult {
        val (responseHeadersMap, responseCookiesMap) = collectHeadersAndCookies(requestUrl, headers)
        val responseHeadersList = responseHeadersMap.map { (name, value) -> name to value }
        val responseText = decodeResponseBody(responseBytes, responseHeadersList)
        return ExecutionResult(
            statusCode = status.value,
            statusText = status.description,
            headers = responseHeadersMap,
            cookies = responseCookiesMap,
            responseBody = responseText,
            responseSizeBytes = responseBytes.size.toLong(),
            isSuccess = status.value in 200..299,
            timings = ExchangeTimings(totalMillis = latency, downloadMillis = latency),
            protocol = ApplicationProtocol.fromToken(version.toString()),
        )
    }

    private fun collectHeadersAndCookies(
        requestUrl: String,
        responseHeaders: Headers,
    ): Pair<Map<String, String>, Map<String, String>> {
        val host = try {
            Url(requestUrl).host
        } catch (_: Exception) {
            ""
        }
        val responseHeadersMap = mutableMapOf<String, String>()
        val responseCookiesMap = mutableMapOf<String, String>()

        responseHeaders.forEach { key, values ->
            val valueString = values.joinToString(", ")
            responseHeadersMap[key] = valueString
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

        if (host.isNotBlank()) {
            cookieStore.getCookies(host).forEach { cookie ->
                if (!responseCookiesMap.containsKey(cookie.name)) {
                    responseCookiesMap[cookie.name] = cookie.value
                }
            }
        }

        return responseHeadersMap to responseCookiesMap
    }

    private fun HttpTransportResponse.toExecutionResult(
        requestUrl: String,
        latencyMillis: Long,
    ): ExecutionResult {
        val host = runCatching { Url(requestUrl).host }.getOrDefault("")
        val responseHeadersMap = linkedMapOf<String, String>()
        val responseCookiesMap = linkedMapOf<String, String>()
        headers.groupBy({ it.first }, { it.second }).forEach { (name, values) ->
            responseHeadersMap[name] = values.joinToString(", ")
        }
        headers.filter { (name, _) -> name.equals("set-cookie", ignoreCase = true) }
            .forEach { (_, rawCookie) ->
                runCatching { parseServerSetCookieHeader(rawCookie) }.getOrNull()?.let { cookie ->
                    if (host.isNotBlank()) cookieStore.storeCookie(host, cookie)
                    responseCookiesMap[cookie.name] = cookie.value
                }
            }
        if (host.isNotBlank()) {
            cookieStore.getCookies(host).forEach { cookie -> responseCookiesMap.putIfAbsent(cookie.name, cookie.value) }
        }
        val responseText = decodeResponseBody(body, headers)
        return ExecutionResult(
            statusCode = statusCode,
            statusText = reasonPhrase,
            headers = responseHeadersMap,
            cookies = responseCookiesMap,
            responseBody = responseText,
            responseSizeBytes = body.size.toLong(),
            isSuccess = statusCode in 200..299,
            timings = ExchangeTimings(totalMillis = latencyMillis, downloadMillis = latencyMillis),
            protocol = protocol,
        )
    }

    private fun HttpTransportResponseHead.toDomainHead(requestUrl: String): HttpExecutionResponseHead {
        val host = runCatching { Url(requestUrl).host }.getOrDefault("")
        val responseHeadersMap = linkedMapOf<String, String>()
        val responseCookiesMap = linkedMapOf<String, String>()
        headers.groupBy({ it.first }, { it.second }).forEach { (name, values) ->
            responseHeadersMap[name] = values.joinToString(", ")
        }
        headers.filter { (name, _) -> name.equals("set-cookie", ignoreCase = true) }
            .forEach { (_, rawCookie) ->
                runCatching { parseServerSetCookieHeader(rawCookie) }.getOrNull()?.let { cookie ->
                    if (host.isNotBlank()) cookieStore.storeCookie(host, cookie)
                    responseCookiesMap[cookie.name] = cookie.value
                }
            }
        return HttpExecutionResponseHead(
            statusCode = statusCode,
            statusText = reasonPhrase,
            headers = responseHeadersMap,
            cookies = responseCookiesMap,
            protocol = protocol,
        )
    }

    private fun prepareExactTransportRequest(
        url: String,
        headers: Map<String, String>,
        auth: ApiRequestAuth,
    ): PreparedExactTransportRequest {
        val preparedHeaders = headers.sanitizeTransportHeaders().toMutableMap()
        var preparedUrl = url
        val host = runCatching { Url(url).host }.getOrDefault("")
        if (host.isNotBlank() && preparedHeaders.keys.none { it.equals("cookie", ignoreCase = true) }) {
            cookieStore.getCookies(host).takeIf { it.isNotEmpty() }?.let { cookies ->
                preparedHeaders["Cookie"] = cookies.joinToString("; ") { "${it.name}=${it.value}" }
            }
        }
        when (auth) {
            is ApiRequestAuth.Bearer -> auth.token.takeIf(String::isNotBlank)?.let { token ->
                preparedHeaders["Authorization"] = if (token.startsWith("Bearer ", ignoreCase = true)) {
                    token
                } else {
                    "Bearer $token"
                }
            }
            is ApiRequestAuth.Basic -> {
                val credentials = "${auth.username}:${auth.password}"
                preparedHeaders["Authorization"] =
                    "Basic ${kotlin.io.encoding.Base64.encode(credentials.encodeToByteArray())}"
            }
            is ApiRequestAuth.ApiKey -> if (auth.name.isNotBlank() && auth.value.isNotBlank()) {
                if (auth.location.contains("query", ignoreCase = true)) {
                    preparedUrl = URLBuilder(preparedUrl).apply {
                        parameters.append(auth.name, auth.value)
                    }.buildString()
                } else {
                    preparedHeaders[auth.name] = auth.value
                }
            }
            is ApiRequestAuth.OAuth2 -> auth.token.takeIf(String::isNotBlank)?.let { token ->
                preparedHeaders["Authorization"] = "${auth.headerPrefix} $token".trim()
            }
            is ApiRequestAuth.AwsSignature,
            ApiRequestAuth.Inherit,
            ApiRequestAuth.None -> Unit
        }
        return PreparedExactTransportRequest(preparedUrl, preparedHeaders)
    }

    private fun decodeResponseBody(
        bytes: ByteArray,
        headers: List<Pair<String, String>>,
    ): String = when (val decodedResult = BodyDecoder.decode(bytes, headers)) {
        is DecodedBodyResult.Success -> decodedResult.bytes.decodeToString()
        is DecodedBodyResult.Identity -> decodedResult.bytes.decodeToString()
        is DecodedBodyResult.OutputLimitExceeded ->
            "[Decoded payload exceeds ${decodedResult.maximumOutputBytes} bytes]"
        else -> runCatching { bytes.decodeToString() }.getOrDefault("")
    }

    private fun failureResult(exception: Exception, targetUrl: String): ExecutionResult {
        val reason = com.devuloopers.knet.core.http.util.NetworkExceptionClassifier.classify(
            exception = exception,
            targetUrl = targetUrl,
            timeoutMs = currentConfiguration.timeoutMillis,
        )
        return ExecutionResult(
            statusCode = 0,
            statusText = "Execution Error",
            isSuccess = false,
            errorMessage = exception.message ?: exception.toString(),
            failureReason = reason,
            timings = ExchangeTimings(totalMillis = 0L),
        )
    }

    private fun Map<String, String>.isIdentityEventStreamHeaders(): Boolean {
        val eventStream = entries.any { (name, value) ->
            name.equals("content-type", ignoreCase = true) &&
                value.substringBefore(';').trim().equals("text/event-stream", ignoreCase = true)
        }
        val contentEncoding = entries.firstOrNull { (name, _) ->
            name.equals("content-encoding", ignoreCase = true)
        }?.value?.trim()?.lowercase()
        return eventStream && (contentEncoding.isNullOrEmpty() || contentEncoding == "identity")
    }

    private data class PreparedExactTransportRequest(
        val url: String,
        val headers: Map<String, String>,
    )

    private companion object {
        const val STREAM_CHUNK_BYTES: Int = 8 * 1_024
        const val MAXIMUM_TERMINAL_BODY_BYTES: Int = 16 * 1_024 * 1_024
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
        httpTwoTransport.close()
        if (customEngine == null) {
            directHttpClient.close()
            proxyHttpClients.close()
        }
    }
}
