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

/** Raw response returned before shared decoding, cookie storage, and presentation mapping. */
internal data class HttpTransportResponse(
    val statusCode: Int,
    val reasonPhrase: String,
    val protocol: ApplicationProtocol,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
)

/** Emits a real HTTP/1.0 request line on the current platform. */
internal expect suspend fun executeHttpOneZero(
    request: HttpOneZeroTransportRequest,
): HttpTransportResponse
