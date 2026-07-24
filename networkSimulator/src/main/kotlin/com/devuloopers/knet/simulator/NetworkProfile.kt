package com.devuloopers.knet.simulator

/**
 * Immutable configuration describing simulated network conditions to be applied
 * to all proxied connections by [KNetNetworkSimulatorHandler].
 *
 * All three simulation axes are independent and can be combined freely.
 *
 * @property bandwidthBytesPerSecond Maximum byte throughput per channel per second.
 *   When `null`, no bandwidth limit is applied.
 * @property latencyMs Fixed artificial delay added to every inbound request and outbound response,
 *   in milliseconds. Defaults to 0 (no delay).
 * @property packetLossPercent Integer from 0 to 100 representing the percentage of inbound
 *   requests that are randomly dropped without forwarding. Defaults to 0 (no loss).
 */
data class NetworkProfile(
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

    companion object {
        /**
         * No simulation applied — full passthrough, no throttling, no delay, no loss.
         */
        val NONE = NetworkProfile()

        /**
         * Simulates a slow 2G GPRS mobile data connection (~40 KB/s, 500ms RTT).
         */
        val MOBILE_2G = NetworkProfile(
            bandwidthBytesPerSecond = 40_000,
            latencyMs = 500
        )

        /**
         * Simulates a typical 3G mobile data connection (~50 KB/s, 300ms RTT).
         */
        val MOBILE_3G = NetworkProfile(
            bandwidthBytesPerSecond = 50_000,
            latencyMs = 300
        )

        /**
         * Simulates a typical 4G LTE mobile connection (~5 MB/s, 50ms RTT).
         */
        val MOBILE_4G = NetworkProfile(
            bandwidthBytesPerSecond = 5_000_000,
            latencyMs = 50
        )

        /**
         * Simulates a high-latency satellite internet connection (~1 MB/s, 600ms RTT).
         */
        val SATELLITE = NetworkProfile(
            bandwidthBytesPerSecond = 1_000_000,
            latencyMs = 600
        )

        /**
         * Simulates an unstable network with significant packet loss and moderate latency.
         * Useful for testing client-side retry logic and error handling.
         */
        val LOSSY = NetworkProfile(
            latencyMs = 200,
            packetLossPercent = 20
        )

        /**
         * Simulates an offline / completely unreachable network (100% packet loss).
         */
        val OFFLINE = NetworkProfile(packetLossPercent = 100)
    }

    /** Returns true if any simulation axis is active. */
    val isActive: Boolean
        get() = bandwidthBytesPerSecond != null || latencyMs > 0L || packetLossPercent > 0
}
