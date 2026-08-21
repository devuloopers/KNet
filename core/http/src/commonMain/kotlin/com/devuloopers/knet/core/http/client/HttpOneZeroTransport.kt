package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.core.http.config.HttpClientConfiguration
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpMethod

/** Complete, engine-neutral input for the exact HTTP/1.0 JVM wire adapter. */
internal data class HttpOneZeroTransportRequest(
    val url: String,
    val method: HttpMethod,
    val headers: Map<String, String>,
    val body: OutboundRequestBody,
    val proxyPort: Int?,
    val configuration: HttpClientConfiguration,
    val localProxyTlsTrust: LocalProxyTlsTrust?,
)

/** Complete, engine-neutral input for the exact HTTP/2 JVM transport adapter. */
internal data class HttpTwoTransportRequest(
    val url: String,
    val method: HttpMethod,
    val headers: Map<String, String>,
    val body: OutboundRequestBody,
    val proxyPort: Int?,
    val configuration: HttpClientConfiguration,
    val localProxyTlsTrust: LocalProxyTlsTrust?,
    /** When true, an HTTP/1.1 negotiation result is a request failure rather than a valid fallback. */
    val requireHttpTwo: Boolean,
)

/** Raw response returned before shared decoding, cookie storage, and presentation mapping. */
internal data class HttpTransportResponse(
    val statusCode: Int,
    val reasonPhrase: String,
    val protocol: ApplicationProtocol,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpTransportResponse

        if (statusCode != other.statusCode) return false
        if (reasonPhrase != other.reasonPhrase) return false
        if (protocol != other.protocol) return false
        if (headers != other.headers) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + reasonPhrase.hashCode()
        result = 31 * result + protocol.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

/** Emits a real HTTP/1.0 request line on the current platform. */
internal expect suspend fun executeHttpOneZero(
    request: HttpOneZeroTransportRequest,
): HttpTransportResponse

/** Reusable HTTP/2 transport owner with bounded platform-client pooling. */
internal expect class HttpTwoTransport() {
    /** Prefers HTTP/2 and optionally fails if the peer or proxy path downgrades the exchange. */
    suspend fun execute(request: HttpTwoTransportRequest): HttpTransportResponse

    /** Cancels active requests and releases cached client ownership. */
    fun close()
}
