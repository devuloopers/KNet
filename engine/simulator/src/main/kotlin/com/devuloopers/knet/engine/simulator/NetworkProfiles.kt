package com.devuloopers.knet.engine.simulator

/**
 * Built-in preset catalog for common mobile, wireless, satellite, lossy, and offline network environments.
 */
object NetworkProfiles {

    /** Passthrough — no throttling, delay, or packet loss. */
    val NONE = NetworkProfile(name = "Passthrough")

    /** Offline / Unreachable network (100% packet loss). */
    val OFFLINE = NetworkProfile(name = "Offline", packetLossPercent = 100)

    /** Slow 2G GPRS (~40 KB/s, 500ms RTT). */
    val MOBILE_2G = NetworkProfile(
        name = "2G GPRS",
        bandwidthBytesPerSecond = 40_000,
        latencyMs = 500
    )

    /** Typical 3G UMTS (~50 KB/s, 300ms RTT). */
    val MOBILE_3G = NetworkProfile(
        name = "3G UMTS",
        bandwidthBytesPerSecond = 50_000,
        latencyMs = 300
    )

    /** Typical 4G LTE (~5 MB/s, 50ms RTT). */
    val MOBILE_4G = NetworkProfile(
        name = "4G LTE",
        bandwidthBytesPerSecond = 5_000_000,
        latencyMs = 50
    )

    /** Fast 5G NR (~50 MB/s, 15ms RTT). */
    val MOBILE_5G = NetworkProfile(
        name = "5G NR",
        bandwidthBytesPerSecond = 50_000_000,
        latencyMs = 15
    )

    /** Broadband Wi-Fi (~15 MB/s, 10ms RTT). */
    val WIFI = NetworkProfile(
        name = "Wi-Fi",
        bandwidthBytesPerSecond = 15_000_000,
        latencyMs = 10
    )

    /** High-latency Satellite internet (~1 MB/s, 600ms RTT). */
    val SATELLITE = NetworkProfile(
        name = "Satellite",
        bandwidthBytesPerSecond = 1_000_000,
        latencyMs = 600
    )

    /** Unstable lossy network (200ms latency, 20% packet loss). */
    val LOSSY = NetworkProfile(
        name = "Lossy Network",
        latencyMs = 200,
        packetLossPercent = 20
    )
}
