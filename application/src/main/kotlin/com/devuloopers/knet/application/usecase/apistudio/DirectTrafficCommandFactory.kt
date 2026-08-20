package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.port.traffic.RecordHttpExchangeCommand
import com.devuloopers.knet.application.port.traffic.TrafficBodyPayload
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.*
import java.net.URI

/** Maps one completed direct API Studio execution to canonical Traffic values. */
internal object DirectTrafficCommandFactory {
    fun create(
        exchangeId: ExchangeId,
        url: String,
        method: HttpMethod,
        headers: List<Pair<String, String>>,
        requestBody: String?,
        result: ExecutionResult,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long
    ): RecordHttpExchangeCommand {
        val protocol = ApplicationProtocol.fromToken(DEFAULT_PROTOCOL)
        val response = result.statusCode.takeIf { it in 100..999 }?.let { status ->
            ResponseHead(
                protocol = protocol,
                status = HttpStatus(status),
                reasonPhrase = result.statusText.takeIf(String::isNotBlank),
                headers = result.headers.toHeaderFields()
            )
        }
        val state = if (response == null || result.failureReason != null) {
            ExchangeState.FAILED
        } else {
            ExchangeState.COMPLETED
        }
        return RecordHttpExchangeCommand(
            exchangeId = exchangeId,
            request = RequestHead(method, requestTarget(url, method), protocol, headers.toHeaderFields()),
            requestBody = requestBody?.takeIf(String::isNotEmpty)?.encodeToByteArray()?.let(::TrafficBodyPayload),
            response = response,
            responseBody = result.responseBody
                .takeIf { response != null && it.isNotEmpty() }
                ?.encodeToByteArray()
                ?.let(::TrafficBodyPayload),
            state = state,
            timings = ExchangeTimings(totalMillis = result.latencyMs.coerceAtLeast(0L)),
            startedAtEpochMillis = startedAtEpochMillis.coerceAtLeast(0L),
            completedAtEpochMillis = maxOf(startedAtEpochMillis, completedAtEpochMillis),
            errorCode = if (state == ExchangeState.FAILED) failureCode(result.failureReason) else null
        )
    }

    private fun List<Pair<String, String>>.toHeaderFields(): List<HeaderField> = mapNotNull { (name, value) ->
        name.takeIf(String::isNotBlank)?.let { HeaderField(HeaderName(it), value) }
    }

    private fun Map<String, String>.toHeaderFields(): List<HeaderField> = entries.mapNotNull { (name, value) ->
        name.takeIf(String::isNotBlank)?.let { HeaderField(HeaderName(it), value) }
    }

    private fun requestTarget(url: String, method: HttpMethod): RequestTarget {
        if (method == HttpMethod.CONNECT) parseAuthority(url)?.let { return RequestTarget.AuthorityForm(it) }
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
                    authority = Authority(host, uri.port.takeIf { it in 1..65_535 }),
                    pathAndQuery = uri.rawQuery?.let { query -> "$path?$query" } ?: path
                )
            } else {
                RequestTarget.Custom(safeTarget(url))
            }
        }.getOrElse { RequestTarget.Custom(safeTarget(url)) }
    }

    private fun parseAuthority(value: String): Authority? {
        if (value.isBlank() || '\r' in value || '\n' in value) return null
        val uri = runCatching { URI("authority://$value") }.getOrNull() ?: return null
        val host = uri.host?.takeIf(String::isNotBlank) ?: return null
        return Authority(host, uri.port.takeIf { it in 1..65_535 })
    }

    private fun safeTarget(value: String): String = value
        .replace("\r", "")
        .replace("\n", "")
        .ifBlank { "/" }

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

    private const val DEFAULT_PROTOCOL: String = "HTTP/1.1"
}
