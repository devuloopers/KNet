package com.devuloopers.knet.engine.simulator

/**
 * Immutable configuration describing simulated network conditions to be applied
 * to all proxied connections by [KNetNetworkSimulatorHandler].
 *
 * @property name Descriptive profile label (e.g., "3G UMTS", "Custom", "Offline").
 * @property bandwidthBytesPerSecond Maximum byte throughput per channel per second (null = unlimited).
 * @property latencyMs Fixed artificial delay added to inbound requests and outbound responses, in milliseconds.
 * @property packetLossPercent Percentage (0 to 100) of inbound requests randomly dropped without forwarding.
 */
data class NetworkProfile(
    val name: String = "Custom",
    val bandwidthBytesPerSecond: Long? = null,
    val latencyMs: Long = 0L,
    val packetLossPercent: Int = 0
) {
    init {
        require(packetLossPercent in 0..100) {
            "packetLossPercent must be between 0 and 100, got $packetLossPercent"
        }
        require(latencyMs >= 0) {
            "latencyMs must be non-negative, got $latencyMs"
        }
        bandwidthBytesPerSecond?.let {
            require(it > 0) { "bandwidthBytesPerSecond must be positive if set, got $it" }
        }
    }

    /** Returns true if any simulation axis is active. */
    val isActive: Boolean
        get() = bandwidthBytesPerSecond != null || latencyMs > 0L || packetLossPercent > 0
}
