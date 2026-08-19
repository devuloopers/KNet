package com.devuloopers.knet.application.port.traffic

import com.devuloopers.knet.traffic.id.CaptureSessionId
import kotlinx.coroutines.flow.StateFlow

/** Outcome of preparing capture ownership for a destructive traffic-history clear. */
public enum class CaptureClearPreparation {
    /** A running canonical capture session was replaced before its closed history was cleared. */
    CANONICAL_SESSION_ROTATED,

    /** No canonical session was active, so no replacement was required. */
    CANONICAL_SESSION_INACTIVE,
}

/** Current attachment state of canonical proxy traffic capture. */
public sealed interface CaptureSessionState {
    /** No proxy runtime currently owns a capture target. */
    public data object Inactive : CaptureSessionState

    /** A canonical writer is being prepared for a starting proxy runtime. */
    public data object Starting : CaptureSessionState

    /** Proxy exchanges are being recorded into [sessionId]. */
    public data class Capturing(
        public val sessionId: CaptureSessionId,
    ) : CaptureSessionState

    /** The proxy remains available, but exchanges pass through without being recorded. */
    public data object Paused : CaptureSessionState

    /** Capture attachment failed independently of any previously retained traffic. */
    public data class Failed(
        public val code: String,
    ) : CaptureSessionState
}

/** Typed result of detaching capture from a persistent proxy listener. */
public enum class CapturePauseResult {
    /** Capture was detached and the previous writer was scheduled for retirement. */
    PAUSED,

    /** Capture was already detached from a running proxy. */
    ALREADY_PAUSED,

    /** No running proxy was available to pause. */
    PROXY_INACTIVE,
}

/** Typed result of attaching a new capture generation to a persistent proxy listener. */
public sealed interface CaptureResumeResult {
    /** A new canonical session is accepting subsequent proxy exchanges. */
    public data class Capturing(
        public val sessionId: CaptureSessionId,
    ) : CaptureResumeResult

    /** The existing canonical session was already accepting exchanges. */
    public data class AlreadyCapturing(
        public val sessionId: CaptureSessionId,
    ) : CaptureResumeResult

    /** No running proxy was available to receive a capture target. */
    public data object ProxyInactive : CaptureResumeResult

    /** A new canonical writer could not be created. */
    public data class Failed(
        public val code: String,
    ) : CaptureResumeResult
}

/**
 * Application boundary for changing capture-session ownership before traffic history is deleted.
 *
 * Implementations serialize this operation with proxy start/stop and ensure that callbacks have a
 * new writer before the previous canonical session becomes eligible for deletion. Transport connections
 * must remain open; only their capture side output may move to the replacement writer.
 */
public interface CaptureSessionControlPort {
    /** Hot state of capture attachment, independent from proxy listener lifecycle. */
    public val captureState: StateFlow<CaptureSessionState>

    /** Detaches capture without stopping forwarding or closing client connections. */
    public suspend fun pause(): CapturePauseResult

    /** Attaches a new canonical session without restarting the proxy listener. */
    public suspend fun resume(): CaptureResumeResult

    /**
     * Rotates active capture ownership for a clear operation.
     *
     * @return Typed preparation outcome used for diagnostics and tests.
     */
    public suspend fun rotateForTrafficClear(): CaptureClearPreparation
}

/** Application boundary for deleting terminal traffic metadata and owned body objects. */
public interface TrafficMaintenancePort {
    /** Clears terminal canonical sessions while preserving any active session. */
    public suspend fun clearTerminalTraffic()
}
