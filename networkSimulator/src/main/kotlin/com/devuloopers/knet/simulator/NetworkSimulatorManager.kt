package com.devuloopers.knet.simulator

import com.devuloopers.knet.logger.KNetLogger

private const val TAG = "NetworkSimulatorManager"

/**
 * Thread-safe manager that holds the currently active [NetworkProfile].
 *
 * Uses `@Volatile` to ensure that writes from any thread are immediately visible
 * to Netty event loop threads reading [activeProfile] in [KNetNetworkSimulatorHandler].
 * No locking is required because [NetworkProfile] is immutable — a full atomic reference
 * swap is performed on each [applyProfile] call.
 */
class NetworkSimulatorManager {

    /**
     * The currently active network simulation profile.
     * Defaults to [NetworkProfile.NONE] (no simulation).
     *
     * All Netty channel handlers read this field on every request/response — it must be `@Volatile`.
     */
    @Volatile
    var activeProfile: NetworkProfile = NetworkProfile.NONE
        private set

    /**
     * Activates the given [NetworkProfile], immediately affecting all subsequent
     * connections handled by [KNetNetworkSimulatorHandler].
     *
     * @param profile The new simulation profile to apply.
     */
    fun applyProfile(profile: NetworkProfile) {
        activeProfile = profile
        KNetLogger.debug(TAG) {
            "Network profile applied: bandwidth=${profile.bandwidthBytesPerSecond} B/s, " +
                "latency=${profile.latencyMs}ms, loss=${profile.packetLossPercent}%"
        }
    }

    /**
     * Applies a named preset profile from [NetworkProfile.Companion].
     *
     * @param profile A preset such as [NetworkProfile.MOBILE_3G] or [NetworkProfile.LOSSY].
     */
    fun applyPreset(profile: NetworkProfile) = applyProfile(profile)

    /**
     * Resets simulation to [NetworkProfile.NONE], disabling all throttling, latency, and packet loss.
     */
    fun reset() {
        activeProfile = NetworkProfile.NONE
        KNetLogger.debug(TAG) { "Network simulation reset to NONE (passthrough)" }
    }
}
