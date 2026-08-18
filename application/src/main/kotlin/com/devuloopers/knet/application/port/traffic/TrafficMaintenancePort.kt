package com.devuloopers.knet.application.port.traffic

/** Outcome of preparing capture ownership for a destructive traffic-history clear. */
public enum class CaptureClearPreparation {
    /** A running canonical capture session was replaced before its closed history was cleared. */
    CANONICAL_SESSION_ROTATED,

    /** No canonical session was active, so no replacement was required. */
    CANONICAL_SESSION_INACTIVE,
}

/**
 * Application boundary for changing capture-session ownership before traffic history is deleted.
 *
 * Implementations serialize this operation with proxy start/stop and ensure that callbacks have a
 * new writer before the previous canonical session becomes eligible for deletion.
 */
public interface CaptureSessionControlPort {
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
