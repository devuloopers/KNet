package com.devuloopers.knet.traffic.model

import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.StandardHttpScheme

/**
 * Monotonic lifecycle state of one logical request/response exchange.
 */
public enum class ExchangeState {
    ADMITTED,
    REQUEST_HEADERS,
    REQUEST_STREAMING,
    REQUEST_COMPLETE,
    WAITING_FOR_RESPONSE,
    RESPONSE_HEADERS,
    RESPONSE_STREAMING,
    RESPONSE_COMPLETE,
    COMPLETED,
    FAILED,
    DROPPED,
    CANCELLED,
}

/**
 * Network timing values observed for one exchange.
 *
 * All values are optional because a failure or reused connection can make a phase inapplicable.
 *
 * @property dnsMillis DNS resolution duration.
 * @property connectMillis upstream connection duration.
 * @property tlsMillis upstream TLS negotiation duration.
 * @property firstByteMillis elapsed duration to the first response byte.
 * @property downloadMillis response streaming duration.
 * @property totalMillis total exchange duration.
 * @property connectionReused whether the exchange reused an existing upstream connection.
 */
public data class ExchangeTimings(
    public val dnsMillis: Long? = null,
    public val connectMillis: Long? = null,
    public val tlsMillis: Long? = null,
    public val firstByteMillis: Long? = null,
    public val downloadMillis: Long? = null,
    public val totalMillis: Long? = null,
    public val connectionReused: Boolean = false,
) {
    init {
        val values = listOf(dnsMillis, connectMillis, tlsMillis, firstByteMillis, downloadMillis, totalMillis)
        require(values.all { it == null || it >= 0L }) { "Exchange timing values must not be negative." }
    }
}

/**
 * Canonical immutable HTTP request shared by API Studio, Traffic, Breakpoints, replay,
 * collections, inspectors, and export.
 *
 * @property head Request method, target, protocol, and ordered headers.
 * @property body Body relationship represented without embedded payload bytes.
 * @property trailers Ordered request trailers observed after the body.
 */
public data class HttpRequestSnapshot(
    public val head: RequestHead,
    public val body: MessageBodyRef = MessageBodyRef.Empty,
    public val trailers: List<HeaderField> = emptyList(),
)

/** Renders the request target as the most complete URL/authority form available. */
public fun HttpRequestSnapshot.absoluteUrl(): String = when (val target = head.target) {
    is RequestTarget.Absolute -> {
        val port = target.authority.port?.let { ":$it" }.orEmpty()
        "${target.scheme.token}://${target.authority.urlHost()}$port${target.pathAndQuery}"
    }
    is RequestTarget.Origin -> target.pathAndQuery
    is RequestTarget.AuthorityForm -> {
        val port = target.authority.port?.let { ":$it" }.orEmpty()
        "${target.authority.urlHost()}$port"
    }
    RequestTarget.Asterisk -> "*"
    is RequestTarget.Custom -> target.value
}

/**
 * Renders a user-facing URL while retaining every non-default destination port.
 *
 * HTTP `:80` and HTTPS `:443` are redundant presentation details. This formatter removes only
 * those matching scheme defaults; the canonical request target and its port remain unchanged.
 */
public fun HttpRequestSnapshot.displayUrl(): String = when (val target = head.target) {
    is RequestTarget.Absolute -> {
        val defaultPort = when (val scheme = target.scheme) {
            is HttpScheme.Standard -> when (scheme.value) {
                StandardHttpScheme.HTTP -> 80
                StandardHttpScheme.HTTPS -> 443
            }
            is HttpScheme.Custom -> null
        }
        val port = target.authority.port
            ?.takeUnless { it == defaultPort }
            ?.let { ":$it" }
            .orEmpty()
        "${target.scheme.token}://${target.authority.urlHost()}$port${target.pathAndQuery}"
    }
    else -> absoluteUrl()
}

/** Renders the same canonical request target without an authority port for independent port matching. */
public fun HttpRequestSnapshot.absoluteUrlWithoutPort(): String = when (val target = head.target) {
    is RequestTarget.Absolute -> "${target.scheme.token}://${target.authority.urlHost()}${target.pathAndQuery}"
    is RequestTarget.Origin -> target.pathAndQuery
    is RequestTarget.AuthorityForm -> target.authority.urlHost()
    RequestTarget.Asterisk -> "*"
    is RequestTarget.Custom -> target.value
}

/** Returns the destination port retained by the typed request target, when one is available. */
public fun HttpRequestSnapshot.destinationPort(): Int? = when (val target = head.target) {
    is RequestTarget.Absolute -> target.authority.port
    is RequestTarget.AuthorityForm -> target.authority.port
    is RequestTarget.Origin,
    RequestTarget.Asterisk,
    is RequestTarget.Custom,
    -> null
}

private fun com.devuloopers.knet.traffic.model.http.Authority.urlHost(): String =
    if (host.contains(':')) "[$host]" else host

/**
 * Canonical immutable HTTP response shared by API Studio, Traffic, Breakpoints, inspectors,
 * and export.
 *
 * @property head Response protocol, status, reason, and ordered headers.
 * @property body Body relationship represented without embedded payload bytes.
 * @property trailers Ordered response trailers observed after the body.
 */
public data class HttpResponseSnapshot(
    public val head: ResponseHead,
    public val body: MessageBodyRef = MessageBodyRef.Empty,
    public val trailers: List<HeaderField> = emptyList(),
)

/**
 * Canonical read-only snapshot of one HTTP exchange.
 *
 * The snapshot is a feature-facing semantic value, not a Netty message, Room entity, editor
 * draft, or mutable breakpoint session.
 *
 * @property id Stable exchange identifier.
 * @property connectionId Optional connection identifier when the source has a transport connection.
 * @property streamId Optional multiplexed protocol stream identifier.
 * @property request Canonical request snapshot.
 * @property response Canonical response snapshot once observed.
 * @property origin Feature or client that initiated the captured exchange.
 * @property state Monotonic exchange lifecycle state.
 * @property terminalOutcome Typed terminal result when [state] is terminal.
 * @property timings Observed exchange timing values.
 * @property startedAtEpochMillis Wall-clock start time used for display and persistence ordering.
 */
public data class HttpExchangeSnapshot(
    public val id: ExchangeId,
    public val connectionId: ConnectionId? = null,
    public val streamId: StreamId? = null,
    public val request: HttpRequestSnapshot,
    public val response: HttpResponseSnapshot? = null,
    public val origin: TrafficOrigin = TrafficOrigin.ProxyClient,
    public val state: ExchangeState,
    public val terminalOutcome: ExchangeTerminalOutcome? = ExchangeTerminalOutcome.fromPersisted(state, null),
    public val timings: ExchangeTimings = ExchangeTimings(),
    public val startedAtEpochMillis: Long,
) {
    init {
        require(startedAtEpochMillis >= 0L) { "Exchange start timestamp must not be negative." }
        require(state.isTerminal == (terminalOutcome != null)) {
            "Only terminal exchange states may carry a terminal outcome."
        }
        require(terminalOutcome == null || terminalOutcome.state == state) {
            "Exchange terminal outcome must match its lifecycle state."
        }
    }
}
