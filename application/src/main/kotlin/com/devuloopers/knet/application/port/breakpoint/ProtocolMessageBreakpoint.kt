package com.devuloopers.knet.application.port.breakpoint

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import kotlinx.coroutines.flow.StateFlow

/** One complete, bounded framed message offered to the application breakpoint gate. */
public data class ProtocolMessageBreakpointCandidate(
    public val exchangeId: ExchangeId,
    public val messageId: ProtocolMessageId,
    public val protocolId: BreakpointProtocolId,
    public val request: HttpRequestSnapshot,
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
    public val candidate: ProtocolMessageBreakpointCandidate,
)

/** Engine-facing gate for framed application-protocol messages. */
public interface ProtocolMessageBreakpointGate {
    /** Cheap request-head prefilter used before a protocol adapter buffers complete messages. */
    public fun mayInterceptMessage(
        request: HttpRequestSnapshot,
        protocolId: BreakpointProtocolId,
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
