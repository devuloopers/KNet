package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.core.logger.KNetLogger

private const val TAG = "NetworkSimulatorManager"

/**
 * Thread-safe manager holding the active [NetworkProfile].
 * Performs atomic volatile reference swaps to allow instant profile hot-swapping.
 */
class NetworkSimulatorManager {

    /**
     * The currently active network simulation profile.
     * Defaults to [NetworkProfiles.NONE] (passthrough).
     */
    @Volatile
    var activeProfile: NetworkProfile = NetworkProfiles.NONE
        private set

    /**
     * Activates the given [NetworkProfile], immediately affecting all proxied connections.
     */
    fun applyProfile(profile: NetworkProfile) {
        activeProfile = profile
        KNetLogger.debug(TAG) {
            "Network profile applied: name=${profile.name}, bandwidth=${profile.bandwidthBytesPerSecond} B/s, " +
                "latency=${profile.latencyMs}ms, loss=${profile.packetLossPercent}%"
        }
    }

    /**
     * Applies a named preset profile from [NetworkProfiles].
     */
    fun applyPreset(profile: NetworkProfile) = applyProfile(profile)

    /**
     * Resets simulation to [NetworkProfiles.NONE] (passthrough).
     */
    fun reset() {
        activeProfile = NetworkProfiles.NONE
        KNetLogger.debug(TAG) { "Network simulation reset to Passthrough" }
    }
}
