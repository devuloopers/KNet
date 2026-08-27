package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.application.contract.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.contract.breakpoint.PendingProtocolMessageBreakpoint
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptor
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.StandardHttpScheme
import com.devuloopers.knet.ui.core.foundation.time.KNetDateTime
import kotlin.math.roundToLong

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
 * @property sequenceNumber One-based ordinal within the currently retained traffic history. Zero
 * is reserved for a provisional breakpoint row that has not reached canonical storage yet.
 * @property method Actual HTTP transport method retained for filters and request behavior.
 * @property displayMethod Protocol-aware method identity rendered in the table, such as `POST` or `GQL`.
 * @property requestKind Semantic request kind used for stable presentation styling.
 */
data class TrafficRowUiState(
    val sequenceNumber: Long,
    val transactionId: String,
    val method: String,
    val displayMethod: String = method,
    val requestKind: RequestKindId = RequestKindId.HTTP,
    val scheme: HttpScheme,
    val host: String,
    val path: String,
    val status: Int,
    val statusText: String,
    val protocol: ApplicationProtocol,
    val clientProtocol: ApplicationProtocol = protocol,
    val upstreamProtocol: ApplicationProtocol? = null,
    val connectionId: String? = null,
    val streamId: Long? = null,
    val origin: TrafficOrigin = TrafficOrigin.ProxyClient,
    val timestamp: Long,
    val formattedTimestamp: String,
    val formattedTime: String,
    val transferredBytes: Long,
    val responseBytes: Long,
    val formattedSize: String,
    val dateGroup: String,
    val contentType: String?,
    val timings: ExchangeTimings,
    val terminalOutcome: ExchangeTerminalOutcome? = null,
    val interception: TrafficInterceptionUiState = TrafficInterceptionUiState.None,
) {
    val fullUrl: String
        get() = when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            host.isBlank() -> path
            else -> "${scheme.token}://$host$path"
        }

    /** Returns a copy decorated by the unified semantic request descriptor. */
    fun withDescriptor(descriptor: RequestDescriptor): TrafficRowUiState = copy(
        displayMethod = descriptor.badgeLabel,
        requestKind = descriptor.kind,
    )
}

/** Builds an immediate body-free Traffic projection for a suspended canonical exchange. */
internal fun PendingBreakpoint.toTrafficRowUiState(
    descriptor: RequestDescriptor? = null,
): TrafficRowUiState {
    val exchange = toTrafficExchangeSnapshot()
    val row = exchange.toTrafficRowUiState().copy(
        status = 0,
        statusText = "In Progress",
        formattedTime = "-",
        interception = TrafficInterceptionUiState.Paused(
            pendingId = id,
            ruleId = ruleId,
            phase = candidate.phase,
        ),
    )
    return descriptor?.let(row::withDescriptor) ?: row
}

/** Builds the canonical metadata available while a breakpoint is still suspended. */
internal fun PendingBreakpoint.toTrafficExchangeSnapshot(): HttpExchangeSnapshot =
    HttpExchangeSnapshot(
        id = candidate.exchangeId,
        request = candidate.request,
        response = candidate.response,
        origin = candidate.origin,
        state = when (candidate.phase) {
            BreakpointPhase.REQUEST -> ExchangeState.REQUEST_COMPLETE
            BreakpointPhase.RESPONSE -> ExchangeState.RESPONSE_COMPLETE
            BreakpointPhase.BOTH -> error("A pending breakpoint cannot use the BOTH phase.")
        },
        startedAtEpochMillis = candidate.startedAtEpochMillis,
    )

/** Builds the canonical parent metadata available while one framed message is suspended. */
internal fun PendingProtocolMessageBreakpoint.toTrafficExchangeSnapshot(): HttpExchangeSnapshot =
    HttpExchangeSnapshot(
        id = candidate.exchangeId,
        request = candidate.request,
        origin = TrafficOrigin.ProxyClient,
        state = ExchangeState.REQUEST_COMPLETE,
        startedAtEpochMillis = candidate.startedAtEpochMillis,
    )

/** Builds an immediate body-free parent row for a suspended framed protocol message. */
internal fun PendingProtocolMessageBreakpoint.toTrafficRowUiState(
    descriptor: RequestDescriptor,
): TrafficRowUiState = toTrafficExchangeSnapshot()
    .toTrafficRowUiState()
    .copy(
        status = 0,
        statusText = "In Progress",
        formattedTime = "-",
        interception = TrafficInterceptionUiState.Paused(
            pendingId = id,
            ruleId = ruleId,
            phase = candidate.phase,
        ),
    )
    .withDescriptor(descriptor)

/** Maps one canonical exchange metadata snapshot into a body-free Traffic row. */
internal fun HttpExchangeSnapshot.toTrafficRowUiState(sequenceNumber: Long = 0L): TrafficRowUiState {
    val targetParts = request.head.target.toDisplayTarget(request.head.headers.firstValue("Host"))
    val requestBytes = request.body.observedBytes()
    val responseBytes = response?.body?.observedBytes() ?: 0L
    val transferredBytes = requestBytes + responseBytes
    val totalMillis = timings.totalMillis
    val responseHead = response?.head
    return TrafficRowUiState(
        sequenceNumber = sequenceNumber,
        transactionId = id.value,
        method = request.head.method.token,
        scheme = targetParts.scheme,
        host = targetParts.host,
        path = targetParts.path,
        status = responseHead?.status?.code ?: 0,
        statusText = responseHead?.reasonPhrase?.takeIf { it.isNotBlank() }
            ?: responseHead?.status?.code?.defaultReason()
            ?: terminalOutcome.toTrafficStatusLabel(),
        protocol = responseHead?.protocol ?: request.head.protocol,
        clientProtocol = request.head.protocol,
        upstreamProtocol = responseHead?.protocol,
        connectionId = connectionId?.value,
        streamId = streamId?.value,
        origin = origin,
        timestamp = startedAtEpochMillis,
        formattedTimestamp = KNetDateTime.time(startedAtEpochMillis, includeMilliseconds = true),
        formattedTime = totalMillis?.let { "$it ms" } ?: "-",
        transferredBytes = transferredBytes,
        responseBytes = responseBytes,
        formattedSize = formatBytes(transferredBytes),
        dateGroup = KNetDateTime.dateKey(startedAtEpochMillis),
        contentType = responseHead?.headers?.firstValue("Content-Type")
            ?: request.head.headers.firstValue("Content-Type"),
        timings = timings,
        terminalOutcome = terminalOutcome,
    )
}

/** Stable short label for a terminal outcome; new reasons extend here without changing table code. */
internal fun ExchangeTerminalOutcome?.toTrafficStatusLabel(): String = when (this) {
    ExchangeTerminalOutcome.Completed -> "Completed"
    is ExchangeTerminalOutcome.Dropped -> "Dropped"
    is ExchangeTerminalOutcome.Cancelled -> when (reason) {
        TrafficTerminationReason.Lifecycle.PROXY_STOPPED -> "Proxy Stopped"
        TrafficTerminationReason.Lifecycle.CAPTURE_TARGET_ROTATED -> "Capture Cleared"
        else -> "Cancelled"
    }
    is ExchangeTerminalOutcome.Failed -> when (reason) {
        TrafficTerminationReason.Lifecycle.PROCESS_INTERRUPTED -> "Interrupted"
        TrafficTerminationReason.Lifecycle.PROXY_ENGINE_FAILED -> "Proxy Failed"
        TrafficTerminationReason.Transport.READ_TIMED_OUT,
        TrafficTerminationReason.Transport.WRITE_TIMED_OUT,
        -> "Timed Out"
        else -> "Failed"
    }
    null -> "Pending"
}

private data class DisplayTarget(val scheme: HttpScheme, val host: String, val path: String)

private fun RequestTarget.toDisplayTarget(hostHeader: String?): DisplayTarget = when (this) {
    is RequestTarget.Absolute -> DisplayTarget(scheme, authority.displayHost(), pathAndQuery)
    is RequestTarget.Origin -> DisplayTarget(
        scheme = HttpScheme.fromToken(
            if (hostHeader?.substringAfterLast(':', "") == "443") "https" else "http",
        ),
        host = hostHeader.orEmpty(),
        path = pathAndQuery,
    )
    is RequestTarget.AuthorityForm -> DisplayTarget(HttpScheme.fromToken("https"), authority.displayHost(), "/")
    RequestTarget.Asterisk -> DisplayTarget(HttpScheme.fromToken("http"), hostHeader.orEmpty(), "*")
    is RequestTarget.Custom -> parseCustomTarget(value, hostHeader)
}

private fun parseCustomTarget(value: String, hostHeader: String?): DisplayTarget {
    val schemeDelimiter = value.indexOf("://")
    if (schemeDelimiter <= 0) {
        return DisplayTarget(HttpScheme.fromToken("http"), hostHeader.orEmpty(), value)
    }
    val scheme = value.substring(0, schemeDelimiter)
    val authorityStart = schemeDelimiter + 3
    val pathStart = value.indexOf('/', authorityStart).let { if (it < 0) value.length else it }
    return DisplayTarget(
        scheme = HttpScheme.fromToken(scheme),
        host = value.substring(authorityStart, pathStart),
        path = value.substring(pathStart).ifBlank { "/" },
    )
}

private fun com.devuloopers.knet.traffic.model.http.Authority.displayHost(): String {
    val formattedHost = if (host.contains(':')) "[$host]" else host
    return port?.let { value -> "$formattedHost:$value" } ?: formattedHost
}

/**
 * Returns the compact authority label used by the Traffic table's Host column.
 *
 * Protocol-default ports are redundant in the table and are omitted. Non-default ports and the
 * complete canonical authority remain available everywhere else.
 */
internal fun String.toTrafficHostLabel(scheme: HttpScheme): String {
    val defaultPort = when (scheme) {
        is HttpScheme.Standard -> when (scheme.value) {
            StandardHttpScheme.HTTP -> 80
            StandardHttpScheme.HTTPS -> 443
        }
        is HttpScheme.Custom -> return this
    }
    val portSuffix = ":$defaultPort"
    return when {
        startsWith('[') && endsWith("]$portSuffix") -> removeSuffix(portSuffix)
        count { character -> character == ':' } == 1 && endsWith(portSuffix) -> removeSuffix(portSuffix)
        else -> this
    }
}

private fun List<com.devuloopers.knet.traffic.model.http.HeaderField>.firstValue(name: String): String? =
    firstOrNull { it.name.value.equals(name, ignoreCase = true) }?.value

private fun MessageBodyRef.observedBytes(): Long = when (this) {
    MessageBodyRef.Empty -> 0L
    is MessageBodyRef.Available -> body.observedBytes
    is MessageBodyRef.Unavailable -> 0L
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${formatDecimal(bytes / (1024.0 * 1024.0))} MB"
    bytes >= 1024L -> "${formatDecimal(bytes / 1024.0)} KB"
    else -> "$bytes B"
}

private fun formatDecimal(value: Double): String {
    val hundredths = (value * 100.0).roundToLong()
    val whole = hundredths / 100L
    val fraction = hundredths % 100L
    return when {
        fraction == 0L -> whole.toString()
        fraction % 10L == 0L -> "$whole.${fraction / 10L}"
        else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
}

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
