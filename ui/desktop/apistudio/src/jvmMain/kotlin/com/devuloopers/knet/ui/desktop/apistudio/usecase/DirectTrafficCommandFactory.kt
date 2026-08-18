package com.devuloopers.knet.ui.desktop.apistudio.usecase

import com.devuloopers.knet.application.port.traffic.RecordHttpExchangeCommand
import com.devuloopers.knet.application.port.traffic.TrafficBodyPayload
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import java.net.URI

/** Maps one completed direct API Studio execution to the shared canonical traffic vocabulary. */
internal object DirectTrafficCommandFactory {
    /**
     * Builds a canonical recording command without introducing another request/response DTO.
     *
     * The current executor returns decoded response text and collapsed response headers, so this
     * mapper records that available semantic representation. A future executor result
     * can supply wire chunks without changing the recording port or canonical HTTP models.
     */
    fun create(
        exchangeId: ExchangeId,
        url: String,
        method: HttpMethod,
        headers: List<Pair<String, String>>,
        requestBody: String?,
        result: ExecutionResult,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
    ): RecordHttpExchangeCommand {
        val protocol = ApplicationProtocol.fromToken(DEFAULT_PROTOCOL)
        val response = result.statusCode.takeIf { status -> status in 100..999 }?.let { status ->
            ResponseHead(
                protocol = protocol,
                status = HttpStatus(status),
                reasonPhrase = result.statusText.takeIf(String::isNotBlank),
                headers = result.headers.toHeaderFields(),
            )
        }
        val state = if (response == null || result.failureReason != null) {
            ExchangeState.FAILED
        } else {
            ExchangeState.COMPLETED
        }
        return RecordHttpExchangeCommand(
            exchangeId = exchangeId,
            request = RequestHead(
                method = method,
                target = requestTarget(url, method),
                protocol = protocol,
                headers = headers.toHeaderFields(),
            ),
            requestBody = requestBody
                ?.takeIf(String::isNotEmpty)
                ?.encodeToByteArray()
                ?.let(::TrafficBodyPayload),
            response = response,
            responseBody = result.responseBody
                .takeIf { body -> response != null && body.isNotEmpty() }
                ?.encodeToByteArray()
                ?.let(::TrafficBodyPayload),
            state = state,
            timings = ExchangeTimings(totalMillis = result.latencyMs.coerceAtLeast(0L)),
            startedAtEpochMillis = startedAtEpochMillis.coerceAtLeast(0L),
            completedAtEpochMillis = maxOf(startedAtEpochMillis, completedAtEpochMillis),
            errorCode = if (state == ExchangeState.FAILED) failureCode(result.failureReason) else null,
        )
    }

    /** Preserves ordered producer headers while rejecting only unusable blank names. */
    private fun List<Pair<String, String>>.toHeaderFields(): List<HeaderField> = mapNotNull { (name, value) ->
        name.takeIf(String::isNotBlank)?.let { HeaderField(HeaderName(it), value) }
    }

    /** Converts collapsed executor response headers to the canonical header vocabulary. */
    private fun Map<String, String>.toHeaderFields(): List<HeaderField> = entries.mapNotNull { (name, value) ->
        name.takeIf(String::isNotBlank)?.let { HeaderField(HeaderName(it), value) }
    }

    /** Maps a URL to the strongest canonical target available without rejecting failed requests. */
    private fun requestTarget(url: String, method: HttpMethod): RequestTarget {
        if (method == HttpMethod.CONNECT) {
            parseAuthority(url)?.let { return RequestTarget.AuthorityForm(it) }
        }
        if (url == "*") return RequestTarget.Asterisk
        if (url.startsWith('/')) return RequestTarget.Origin(url)
        return runCatching {
            val uri = URI(url)
            val scheme = uri.scheme?.takeIf(String::isNotBlank)
            val host = uri.host?.takeIf(String::isNotBlank)
            if (scheme != null && host != null) {
                val path = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/"
                RequestTarget.Absolute(
                    scheme = HttpScheme.fromToken(scheme),
                    authority = Authority(host, uri.port.takeIf { port -> port in 1..65_535 }),
                    pathAndQuery = uri.rawQuery?.let { query -> "$path?$query" } ?: path,
                )
            } else {
                RequestTarget.Custom(safeTarget(url))
            }
        }.getOrElse { RequestTarget.Custom(safeTarget(url)) }
    }

    /** Parses CONNECT authority form without permitting invalid ports or header line injection. */
    private fun parseAuthority(value: String): Authority? {
        if (value.isBlank() || '\r' in value || '\n' in value) return null
        val uri = runCatching { URI("authority://$value") }.getOrNull() ?: return null
        val host = uri.host?.takeIf(String::isNotBlank) ?: return null
        return Authority(host, uri.port.takeIf { port -> port in 1..65_535 })
    }

    /** Produces a non-blank line-safe target for malformed failed requests. */
    private fun safeTarget(value: String): String = value
        .replace("\r", "")
        .replace("\n", "")
        .ifBlank { "/" }

    /** Stores a stable category only; exception details and target data never become error codes. */
    private fun failureCode(failure: NetworkFailureReason?): String = when (failure) {
        is NetworkFailureReason.InvalidUrl -> "api-studio-invalid-url"
        is NetworkFailureReason.HostNotFound -> "api-studio-host-not-found"
        is NetworkFailureReason.Timeout -> "api-studio-timeout"
        is NetworkFailureReason.OfflineOrUnreachable -> "api-studio-unreachable"
        is NetworkFailureReason.ProxyFailure -> "api-studio-proxy-failure"
        is NetworkFailureReason.SslHandshakeFailed -> "api-studio-tls-failure"
        is NetworkFailureReason.TooManyRedirects -> "api-studio-redirect-limit"
        NetworkFailureReason.Cancelled -> "api-studio-cancelled"
        is NetworkFailureReason.Generic -> "api-studio-execution-failed"
        null -> "api-studio-execution-failed"
    }

    private const val DEFAULT_PROTOCOL = "HTTP/1.1"
}
