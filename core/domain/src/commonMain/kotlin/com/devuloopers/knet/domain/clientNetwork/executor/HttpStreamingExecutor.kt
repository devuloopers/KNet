package com.devuloopers.knet.domain.clientNetwork.executor

import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.flow.Flow


/** Owned original bytes for archival consumers; decoding/pretty-printing never replaces this representation. */
class HttpExecutionBytes(bytes: ByteArray) {
    private val content = bytes.copyOf()
    /** Independent bytes owned by the caller. */
    fun copyBytes(): ByteArray = content.copyOf()
    override fun equals(other: Any?): Boolean = other is HttpExecutionBytes && content.contentEquals(other.content)
    override fun hashCode(): Int = content.contentHashCode()
    override fun toString(): String = "HttpExecutionBytes(size=${content.size})"
}

/** Transport-observed sent request paired with the received response, including redirects and encoded entity. */
data class HttpSentRequest(
    val url: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: HttpExecutionBytes?,
)

/**
 * Immutable response metadata published as soon as the transport receives the response head.
 *
 * @property statusCode Numeric HTTP response status.
 * @property statusText Transport-provided response reason phrase when available.
 * @property headers Case-preserving response header values normalized by the transport adapter.
 * @property cookies Parsed response cookies.
 * @property protocol Actual negotiated application protocol when observable.
 */
data class HttpExecutionResponseHead(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val cookies: Map<String, String>,
    val protocol: ApplicationProtocol?,
    val orderedHeaders: List<Pair<String, String>> = headers.toList(),
    val sentRequest: HttpSentRequest? = null,
)

/** Owned response bytes whose lifetime is independent of the transport callback that produced them. */
class HttpExecutionBodyChunk(bytes: ByteArray) {
    private val content = bytes.copyOf()

    init {
        require(content.isNotEmpty()) { "An HTTP execution body chunk must not be empty." }
    }

    /** Returns a copy that the receiver may retain or mutate. */
    fun copyBytes(): ByteArray = content.copyOf()

    /** Number of bytes in this chunk. */
    val size: Int
        get() = content.size
}

/** Ordered lifecycle events for one protocol-neutral HTTP execution. */
sealed interface HttpExecutionEvent {
    /** Response headers are available while the response body may still be streaming. */
    data class ResponseHead(val value: HttpExecutionResponseHead) : HttpExecutionEvent

    /** One owned response-body segment in wire order. */
    data class BodyChunk(val value: HttpExecutionBodyChunk) : HttpExecutionEvent

    /** Terminal canonical result for a normally completed or classified failed execution. */
    data class Completed(val result: ExecutionResult) : HttpExecutionEvent
}

/**
 * Optional streaming companion to [HttpExecutor].
 *
 * Implementations publish the response head before body chunks and exactly one terminal [HttpExecutionEvent.Completed].
 * Cancellation of the collecting coroutine must cancel the underlying call and release its transport resources.
 */
interface HttpStreamingExecutor {
    /**
     * Executes one HTTP request and emits ordered response lifecycle events.
     *
     * @param url Absolute request URL.
     * @param method Canonical HTTP method.
     * @param headers Sanitized request headers.
     * @param body Strongly typed outbound body.
     * @param auth Strongly typed authentication policy.
     * @param proxyPort Optional local proxy port; null selects direct execution.
     * @param httpVersionPreference Requested wire-version policy.
     * @return Cold event flow whose cancellation must close the active transport response.
     */
    fun executeStreaming(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        headers: Map<String, String> = emptyMap(),
        body: OutboundRequestBody = OutboundRequestBody.None,
        auth: ApiRequestAuth = ApiRequestAuth.None,
        proxyPort: Int? = null,
        httpVersionPreference: HttpVersionPreference = HttpVersionPreference.AUTO,
    ): Flow<HttpExecutionEvent>
}
