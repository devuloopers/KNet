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

/** Identifies the independently owned listener that could not start. */
public enum class WifiSharingListenerKind {
    /** Exact-interface standard proxy gateway used by local-network clients. */
    LAN_PROXY_GATEWAY,

    /** Exact-interface HTTP server that delivers setup instructions and certificates. */
    SETUP_PORTAL,
}

/** Platform-neutral classification of why a Wi-Fi listener could not bind. */
public enum class WifiSharingListenerFailureReason {
    /** Another active or recently closed socket currently owns the address and port. */
    ADDRESS_IN_USE,

    /** The selected network address disappeared or cannot be assigned on this host. */
    ADDRESS_UNAVAILABLE,

    /** The operating system denied permission to create the listener. */
    PERMISSION_DENIED,

    /** Listener startup failed for a reason that the platform adapter could not classify safely. */
    UNKNOWN,
}

/**
 * Non-secret network location of a Wi-Fi listener failure.
 *
 * @property host Exact local address that KNet attempted to bind.
 * @property port TCP port that KNet attempted to bind.
 */
public data class WifiSharingListenerEndpoint(
    public val host: String,
    public val port: Int,
) {
    init {
        require(host.isNotBlank()) { "Wi-Fi listener host must not be blank." }
        require(port in 1..65_535) { "Wi-Fi listener port must be between 1 and 65535." }
    }
}

/** Typed, presentation-safe reason that automatic Wi-Fi sharing could not activate. */
public sealed interface WifiSharingFailure {
    /** No non-loopback IPv4 address is currently available for exact-interface binding. */
    public data object NetworkAddressUnavailable : WifiSharingFailure

    /** The process-owned KNet root certificate could not be loaded for setup delivery. */
    public data object CertificateUnavailable : WifiSharingFailure

    /**
     * One concrete listener could not bind.
     *
     * @property listener Listener role that failed.
     * @property endpoint Exact attempted local endpoint.
     * @property reason Platform-neutral bind-failure classification.
     */
    public data class ListenerUnavailable(
        public val listener: WifiSharingListenerKind,
        public val endpoint: WifiSharingListenerEndpoint,
        public val reason: WifiSharingListenerFailureReason,
    ) : WifiSharingFailure

    /** Activation failed outside certificate loading or listener binding. */
    public data object Unexpected : WifiSharingFailure
}

/** Immutable lifecycle state for the automatically managed Wi-Fi connectivity adapter. */
public sealed interface WifiSharingState {
    /** No LAN listener exists, normally because the proxy is stopped or no LAN address is available. */
    public data class Disabled(public val availableAddresses: List<NetworkAddress>) : WifiSharingState

    /** Exact-interface listeners are being created and are not yet published. */
    public data object Enabling : WifiSharingState

    /**
     * A recoverable activation failure is waiting for its next bounded retry.
     *
     * @property failure Typed reason from the most recent activation attempt.
     * @property attempt One-based retry attempt that will run after [retryInMillis].
     * @property retryInMillis Delay before the next attempt.
     * @property availableAddresses Current presentation-safe network candidates.
     */
    public data class Recovering(
        public val failure: WifiSharingFailure,
        public val attempt: Int,
        public val retryInMillis: Long,
        public val availableAddresses: List<NetworkAddress>,
    ) : WifiSharingState {
        init {
            require(attempt > 0) { "Wi-Fi recovery attempt must be positive." }
            require(retryInMillis > 0L) { "Wi-Fi recovery delay must be positive." }
        }
    }

    /** Wi-Fi gateway and setup page are accepting any reachable local-network client. */
    public data class Active(
        public val session: WifiSharingSession,
        public val metrics: WifiSharingMetrics,
    ) : WifiSharingState

    /** Wi-Fi resources are closing and no new clients are admitted. */
    public data object Disabling : WifiSharingState

    /**
     * Automatic activation failed after all partially opened resources were rolled back.
     *
     * @property failure Typed presentation-safe terminal failure.
     * @property recoverable Whether activation can resume automatically without user reconfiguration.
     * @property availableAddresses Current presentation-safe network candidates.
     */
    public data class Failed(
        public val failure: WifiSharingFailure,
        public val recoverable: Boolean,
        public val availableAddresses: List<NetworkAddress>,
    ) : WifiSharingState
}
