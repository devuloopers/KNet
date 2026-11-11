package com.devuloopers.knet.traffic.model

/** Connection side supported by the first observed close evidence, not the application's business outcome. */
public enum class OpaqueFlowCloseInitiator {
    /** EOF received on the downstream peer connection. */
    DEVICE,
    /** EOF received on the upstream peer connection. */
    SERVER,
    /** An explicit local close or timeout initiated by KNet. */
    KNET,
    /** No attributable signal, an I/O error, or indistinguishable peer-close observations. */
    UNKNOWN,
}

/** Safe transport evidence; generic exceptions never prove a TCP reset or a remote application cause. */
public enum class OpaqueFlowCloseReason {
    /** An incoming TCP end-of-input notification. */
    PEER_EOF,
    /** Opposite peer EOFs observed within the same clock tick; origin is ambiguous. */
    CONCURRENT_PEER_EOF,
    /** Configured connection inactivity limit expired. */
    INACTIVITY_TIMEOUT,
    /** An outstanding transport write exceeded its deadline. */
    WRITE_TIMEOUT,
    /** KNet explicitly closed this socket. */
    LOCAL_CLOSE,
    /** Proxy shutdown explicitly closed the admitted connection. */
    PROXY_STOPPED,
    /** KNet explicitly reset the active connection set. */
    CONNECTIONS_RESET,
    /** Transport error whose initiating side cannot be proven. */
    IO_ERROR,
    /** Channel ended without an attributable close signal. */
    UNKNOWN,
}

/**
 * Bounded first-observed closure evidence, independent of final delivery success or failure.
 * @property initiator Observed peer side or explicit local owner; UNKNOWN when evidence is inconclusive.
 * @property reason Typed observation, never inferred from elapsed duration or decrypted content.
 * @property observedAtEpochMillis Unix time when the close signal was observed, potentially before final delivery.
 */
public data class OpaqueFlowClosure(
    public val initiator: OpaqueFlowCloseInitiator,
    public val reason: OpaqueFlowCloseReason,
    public val observedAtEpochMillis: Long,
) {
    init {
        require(observedAtEpochMillis >= 0L) { "Close observation time must not be negative." }
        require(when (reason) {
            OpaqueFlowCloseReason.PEER_EOF -> initiator == OpaqueFlowCloseInitiator.DEVICE || initiator == OpaqueFlowCloseInitiator.SERVER
            OpaqueFlowCloseReason.CONCURRENT_PEER_EOF, OpaqueFlowCloseReason.IO_ERROR, OpaqueFlowCloseReason.UNKNOWN ->
                initiator == OpaqueFlowCloseInitiator.UNKNOWN
            else -> initiator == OpaqueFlowCloseInitiator.KNET
        }) { "Close initiator must be supported by its recorded evidence." }
    }
}
