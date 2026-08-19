package com.devuloopers.knet.connectivity.model

/** Stable non-secret identifier for one automatically managed Wi-Fi sharing lifecycle. */
@JvmInline
public value class WifiSharingSessionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "WifiSharingSessionId must not be blank." }
    }
}

/** Presentation-safe description of the active open Wi-Fi proxy gateway and setup page. */
public data class WifiSharingSession(
    public val id: WifiSharingSessionId,
    public val networkAddress: NetworkAddress,
    public val proxyEndpoint: ProxyEndpoint,
    public val setupUrl: String,
    public val certificateSha256: String,
    public val networkVersion: Long,
    public val startedAtEpochMillis: Long,
) {
    init {
        require(proxyEndpoint.scope == ProxyEndpointScope.LAN) { "Wi-Fi sharing must publish a LAN endpoint." }
        require(proxyEndpoint.accessRequirement == ProxyAccessRequirement.OPEN_LAN_CLIENT) {
            "Wi-Fi sharing must advertise open LAN client access."
        }
        require(setupUrl.startsWith("http://") || setupUrl.startsWith("https://")) {
            "Wi-Fi setup URL must use HTTP or HTTPS."
        }
        require(certificateSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Wi-Fi certificate fingerprint must be a lowercase SHA-256 value."
        }
        require(networkVersion >= 0L) { "Wi-Fi network version must not be negative." }
        require(startedAtEpochMillis >= 0L) { "Wi-Fi session start time must not be negative." }
    }
}

/** Safe aggregate counters for the active Wi-Fi gateway. */
public data class WifiSharingMetrics(
    public val activeConnections: Int = 0,
    public val acceptedConnections: Long = 0L,
    public val rejectedConnections: Long = 0L,
) {
    init {
        require(activeConnections >= 0) { "Active Wi-Fi connections must not be negative." }
        require(acceptedConnections >= 0L) { "Accepted Wi-Fi connections must not be negative." }
        require(rejectedConnections >= 0L) { "Rejected Wi-Fi connections must not be negative." }
    }
}

/** Immutable lifecycle state for the automatically managed Wi-Fi connectivity adapter. */
public sealed interface WifiSharingState {
    /** No LAN listener exists, normally because the proxy is stopped or no LAN address is available. */
    public data class Disabled(public val availableAddresses: List<NetworkAddress>) : WifiSharingState

    /** Exact-interface listeners are being created and are not yet published. */
    public data object Enabling : WifiSharingState

    /** Wi-Fi gateway and setup page are accepting any reachable local-network client. */
    public data class Active(
        public val session: WifiSharingSession,
        public val metrics: WifiSharingMetrics,
    ) : WifiSharingState

    /** Wi-Fi resources are closing and no new clients are admitted. */
    public data object Disabling : WifiSharingState

    /** Automatic activation failed after all partially opened resources were rolled back. */
    public data class Failed(
        public val code: String,
        public val recoverable: Boolean,
        public val availableAddresses: List<NetworkAddress>,
    ) : WifiSharingState {
        init {
            require(code.isNotBlank()) { "Wi-Fi failure code must not be blank." }
        }
    }
}
