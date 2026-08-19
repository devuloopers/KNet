package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.ui.core.foundation.time.KNetDateTime

/** Strongly typed breakpoint presentation attached separately from canonical HTTP values. */
sealed interface TrafficInterceptionUiState {
    /** The exchange has no breakpoint presentation marker. */
    data object None : TrafficInterceptionUiState

    /** The exchange is actively suspended and awaiting a user decision. */
    data class Paused(
        val pendingId: String,
        val ruleId: String,
        val phase: BreakpointPhase,
    ) : TrafficInterceptionUiState

    /** The exchange matched at least one breakpoint earlier in this process session. */
    data class Matched(
        val ruleIds: Set<String>,
        val phases: Set<BreakpointPhase>,
    ) : TrafficInterceptionUiState {
        init {
            require(ruleIds.isNotEmpty()) { "A matched interception requires at least one rule ID." }
            require(phases.isNotEmpty()) { "A matched interception requires at least one phase." }
        }
    }
}

/**
 * Bounded row metadata owned by the desktop Traffic presentation.
 *
 * Payload bytes and storage paths are intentionally absent. Selection loads canonical details
 * through the application layer under a separate preview budget.
 *
 * @property sequenceNumber One-based visible capture sequence, where the oldest retained row is
 * `1` and the newest retained row has the highest number.
 */
data class TrafficRowUiState(
    val sequenceNumber: Int,
    val transactionId: String,
    val method: String,
    val scheme: String,
    val host: String,
    val path: String,
    val status: Int,
    val statusText: String,
    val protocol: String,
    val timestamp: Long,
    val formattedTimestamp: String,
    val formattedTime: String,
    val transferredBytes: Long,
    val responseBytes: Long,
    val formattedSize: String,
    val dateGroup: String,
    val queryParams: Map<String, String>,
    val requestHeaders: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val timings: ExchangeTimings,
    val interception: TrafficInterceptionUiState = TrafficInterceptionUiState.None,
) {
    val fullUrl: String
        get() = when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            host.isBlank() -> path
            else -> "$scheme://$host$path"
        }
}

/** Builds an immediate body-free Traffic projection for a suspended canonical exchange. */
internal fun PendingBreakpoint.toTrafficRowUiState(): TrafficRowUiState {
    val exchange = HttpExchangeSnapshot(
        id = candidate.exchangeId,
        request = candidate.request,
        response = candidate.response,
        state = when (candidate.phase) {
            BreakpointPhase.REQUEST -> ExchangeState.REQUEST_COMPLETE
            BreakpointPhase.RESPONSE -> ExchangeState.RESPONSE_COMPLETE
            BreakpointPhase.BOTH -> error("A pending breakpoint cannot use the BOTH phase.")
        },
        startedAtEpochMillis = candidate.startedAtEpochMillis,
    )
    return exchange.toTrafficRowUiState().copy(
        status = 0,
        statusText = "In Progress",
        formattedTime = "-",
        interception = TrafficInterceptionUiState.Paused(
            pendingId = id,
            ruleId = ruleId,
            phase = candidate.phase,
        ),
    )
}

/** Maps one canonical exchange metadata snapshot into a body-free Traffic row. */
internal fun HttpExchangeSnapshot.toTrafficRowUiState(): TrafficRowUiState {
    val targetParts = request.head.target.toDisplayTarget(request.head.headers.firstValue("Host"))
    val requestBytes = request.body.observedBytes()
    val responseBytes = response?.body?.observedBytes() ?: 0L
    val transferredBytes = requestBytes + responseBytes
    val totalMillis = timings.totalMillis
    val responseHead = response?.head
    return TrafficRowUiState(
        sequenceNumber = 0,
        transactionId = id.value,
        method = request.head.method.token,
        scheme = targetParts.scheme,
        host = targetParts.host,
        path = targetParts.path,
        status = responseHead?.status?.code ?: 0,
        statusText = responseHead?.reasonPhrase?.takeIf { it.isNotBlank() }
            ?: when {
                responseHead != null -> responseHead.status.code.defaultReason()
                state.name == "DROPPED" -> "Dropped"
                state.name == "FAILED" -> "Failed"
                state.name == "CANCELLED" -> "Cancelled"
                else -> "Pending"
            },
        protocol = responseHead?.protocol?.token ?: request.head.protocol.token,
        timestamp = startedAtEpochMillis,
        formattedTimestamp = KNetDateTime.time(startedAtEpochMillis, includeMilliseconds = true),
        formattedTime = totalMillis?.let { "$it ms" } ?: "-",
        transferredBytes = transferredBytes,
        responseBytes = responseBytes,
        formattedSize = formatBytes(transferredBytes),
        dateGroup = KNetDateTime.dateKey(startedAtEpochMillis),
        queryParams = parseQuery(targetParts.path),
        requestHeaders = request.head.headers.toDisplayMap(),
        responseHeaders = responseHead?.headers?.toDisplayMap().orEmpty(),
        timings = timings,
    )
}

private data class DisplayTarget(val scheme: String, val host: String, val path: String)

private fun RequestTarget.toDisplayTarget(hostHeader: String?): DisplayTarget = when (this) {
    is RequestTarget.Absolute -> DisplayTarget(scheme.token, authority.displayHost(), pathAndQuery)
    is RequestTarget.Origin -> DisplayTarget(
        scheme = if (hostHeader?.substringAfterLast(':', "") == "443") "https" else "http",
        host = hostHeader.orEmpty(),
        path = pathAndQuery,
    )
    is RequestTarget.AuthorityForm -> DisplayTarget("https", authority.displayHost(), "/")
    RequestTarget.Asterisk -> DisplayTarget("http", hostHeader.orEmpty(), "*")
    is RequestTarget.Custom -> parseCustomTarget(value, hostHeader)
}

private fun parseCustomTarget(value: String, hostHeader: String?): DisplayTarget {
    val schemeDelimiter = value.indexOf("://")
    if (schemeDelimiter <= 0) return DisplayTarget("http", hostHeader.orEmpty(), value)
    val scheme = value.substring(0, schemeDelimiter)
    val authorityStart = schemeDelimiter + 3
    val pathStart = value.indexOf('/', authorityStart).let { if (it < 0) value.length else it }
    return DisplayTarget(
        scheme = scheme,
        host = value.substring(authorityStart, pathStart),
        path = value.substring(pathStart).ifBlank { "/" },
    )
}

private fun com.devuloopers.knet.traffic.model.http.Authority.displayHost(): String =
    if (port == null) host else "$host:$port"

private fun List<com.devuloopers.knet.traffic.model.http.HeaderField>.firstValue(name: String): String? =
    firstOrNull { it.name.value.equals(name, ignoreCase = true) }?.value

private fun List<com.devuloopers.knet.traffic.model.http.HeaderField>.toDisplayMap(): Map<String, String> =
    associate { it.name.value to it.value }

private fun MessageBodyRef.observedBytes(): Long = when (this) {
    MessageBodyRef.Empty -> 0L
    is MessageBodyRef.Available -> body.observedBytes
    is MessageBodyRef.Unavailable -> 0L
}

private fun parseQuery(path: String): Map<String, String> {
    val query = path.substringAfter('?', "").substringBefore('#')
    if (query.isBlank()) return emptyMap()
    return query.split('&').mapNotNull { pair ->
        val name = pair.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val value = pair.substringAfter('=', "")
        decodeQueryComponent(name) to decodeQueryComponent(value)
    }.toMap()
}

private fun decodeQueryComponent(value: String): String {
    val decoded = StringBuilder(value.length)
    val escapedBytes = mutableListOf<Byte>()

    fun flushEscapedBytes() {
        if (escapedBytes.isEmpty()) return
        decoded.append(ByteArray(escapedBytes.size) { index -> escapedBytes[index] }.decodeToString())
        escapedBytes.clear()
    }

    var index = 0
    while (index < value.length) {
        val current = value[index]
        val high = value.getOrNull(index + 1)?.digitToIntOrNull(16)
        val low = value.getOrNull(index + 2)?.digitToIntOrNull(16)
        if (current == '%' && high != null && low != null) {
            escapedBytes += ((high shl 4) or low).toByte()
            index += 3
            continue
        }
        flushEscapedBytes()
        decoded.append(if (current == '+') ' ' else current)
        index++
    }
    flushEscapedBytes()
    return decoded.toString()
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${formatDecimal(bytes / (1024.0 * 1024.0))} MB"
    bytes >= 1024L -> "${formatDecimal(bytes / 1024.0)} KB"
    else -> "$bytes B"
}

private fun formatDecimal(value: Double): String = String.format(java.util.Locale.ROOT, "%.2f", value)
    .trimEnd('0')
    .trimEnd('.')

private fun Int.defaultReason(): String = when (this) {
    100 -> "Continue"
    101 -> "Switching Protocols"
    200 -> "OK"
    201 -> "Created"
    202 -> "Accepted"
    204 -> "No Content"
    301 -> "Moved Permanently"
    302 -> "Found"
    304 -> "Not Modified"
    400 -> "Bad Request"
    401 -> "Unauthorized"
    403 -> "Forbidden"
    404 -> "Not Found"
    408 -> "Request Timeout"
    429 -> "Too Many Requests"
    500 -> "Internal Server Error"
    502 -> "Bad Gateway"
    503 -> "Service Unavailable"
    504 -> "Gateway Timeout"
    else -> "HTTP $this"
}
