package com.devuloopers.knet.application.port.breakpoint

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import kotlinx.coroutines.flow.StateFlow

/** One complete, bounded framed message offered to the application breakpoint gate. */
public data class ProtocolMessageBreakpointCandidate(
    public val exchangeId: ExchangeId,
    public val messageId: ProtocolMessageId,
    /** Ordered semantic-to-transport protocol identities that may inspect this one wire message. */
    public val protocolRoute: List<BreakpointProtocolId>,
    public val kind: ProtocolMessageKind,
    public val request: HttpRequestSnapshot,
    /** Server-selected application subprotocol, when the framed transport negotiated one. */
    public val negotiatedSubprotocol: String? = null,
    public val direction: TrafficDirection,
    public val sequence: Long,
    public val declaredBytes: Long,
    public val compressed: Boolean,
    public val compressionEncoding: String?,
    public val body: BreakpointBody,
    public val retainedTransportBytes: Long = 0L,
    public val startedAtEpochMillis: Long,
) {
    init {
        require(protocolRoute.isNotEmpty()) { "A protocol message route must not be empty." }
        require(protocolRoute.distinct().size == protocolRoute.size) {
            "A protocol message route must not contain duplicate identities."
        }
        require(sequence > 0L) { "Protocol message sequence must be positive." }
        require(declaredBytes >= 0L) { "Declared protocol message bytes must not be negative." }
        require(retainedTransportBytes >= 0L) { "Retained protocol transport bytes must not be negative." }
    }

    public val phase: BreakpointPhase
        get() = when (direction) {
            TrafficDirection.CLIENT_TO_SERVER -> BreakpointPhase.REQUEST
            TrafficDirection.SERVER_TO_CLIENT -> BreakpointPhase.RESPONSE
        }

    public val retainedBytes: Long
        get() = body.size.toLong() + retainedTransportBytes

    /** Most-specific protocol identity offered by the transport adapter. */
    public val primaryProtocolId: BreakpointProtocolId
        get() = protocolRoute.first()
}

/** Decision returned to a framed-message transport adapter. */
public sealed interface ProtocolMessageBreakpointDecision {
    public data object ContinueUnchanged : ProtocolMessageBreakpointDecision
    public data class Replace(public val body: BreakpointBody) : ProtocolMessageBreakpointDecision
    public data object DropStream : ProtocolMessageBreakpointDecision
}

/** Immutable framed-message pause exposed to authorized presentation. */
public data class PendingProtocolMessageBreakpoint(
    public val id: String,
    public val ruleId: String,
    /** Protocol layer whose rule won deterministic evaluation for this message. */
    public val matchedProtocolId: BreakpointProtocolId,
    public val candidate: ProtocolMessageBreakpointCandidate,
)

/** Engine-facing gate for framed application-protocol messages. */
public interface ProtocolMessageBreakpointGate {
    /** Cheap request-head prefilter used before a protocol adapter buffers complete messages. */
    public fun mayInterceptMessage(
        request: HttpRequestSnapshot,
        protocolRoute: List<BreakpointProtocolId>,
        direction: TrafficDirection,
    ): Boolean

    public suspend fun interceptMessage(
        candidate: ProtocolMessageBreakpointCandidate,
    ): ProtocolMessageBreakpointDecision

    /** Cancels pending framed-message decisions owned by one terminated parent exchange. */
    public fun cancelProtocolMessages(exchangeId: ExchangeId)
}

/** Presentation/control boundary for framed-message pauses. */
public interface ProtocolMessageBreakpointControlPort {
    public val pendingProtocolMessages: StateFlow<List<PendingProtocolMessageBreakpoint>>
    public suspend fun resolveProtocolMessage(
        pendingId: String,
        decision: ProtocolMessageBreakpointDecision,
    ): Boolean
}
