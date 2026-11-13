package com.devuloopers.knet.application.contract.device

import com.devuloopers.knet.application.contract.traffic.TrafficSource

/** Cumulative actual socket bytes, including HTTP/TLS framing, excluding KNet control-channel traffic. */
public data class SourceTrafficSnapshot(
    /** Timestamp from the process monotonic clock, never wall-clock epoch time. */
    public val sampledAtNanos: Long,
    /** Device-to-server bytes observed at the proxy admission boundary. */
    public val uploadedBytes: Long,
    /** Server-to-device bytes successfully written to the downstream transport. */
    public val downloadedBytes: Long,
    /** Currently admitted transport connections for this source. */
    public val activeConnections: Int,
)

/** Read-only source telemetry. Reading samples never creates a polling job or publishes a per-chunk event. */
public fun interface SourceTrafficTelemetry {
    /** Returns a timestamped cumulative snapshot for one authenticated source. */
    public fun snapshot(source: TrafficSource): SourceTrafficSnapshot
}
