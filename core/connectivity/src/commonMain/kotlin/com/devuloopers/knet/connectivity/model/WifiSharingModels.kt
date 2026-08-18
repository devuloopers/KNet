package com.devuloopers.knet.connectivity.model

/** Stable non-secret identifier for one explicitly activated Wi-Fi sharing session. */
@JvmInline
public value class WifiSharingSessionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "WifiSharingSessionId must not be blank." }
    }
}

/** Opaque identifier for one short-lived Wi-Fi onboarding invitation. */
@JvmInline
public value class WifiInvitationId(public val value: String) {
    init {
        require(value.isNotBlank()) { "WifiInvitationId must not be blank." }
    }
}

/** Opaque identifier for a phone awaiting desktop approval. */
@JvmInline
public value class WifiClientCandidateId(public val value: String) {
    init {
        require(value.isNotBlank()) { "WifiClientCandidateId must not be blank." }
    }
}

/** Stable non-secret identity assigned to an approved phone for one sharing session. */
@JvmInline
public value class WifiClientId(public val value: String) {
    init {
        require(value.isNotBlank()) { "WifiClientId must not be blank." }
    }
}

/** User-selected exact network address and listener ports for one activation attempt. */
public data class WifiSharingConfiguration(
    public val networkAddress: NetworkAddress,
    public val proxyPort: Int,
    public val setupPort: Int = DEFAULT_SETUP_PORT,
) {
    init {
        require(!networkAddress.loopback) { "Wi-Fi sharing requires a non-loopback address." }
        require(proxyPort in 1..65_535) { "Wi-Fi proxy port must be between 1 and 65535." }
        require(setupPort in 1..65_535) { "Wi-Fi setup port must be between 1 and 65535." }
        require(proxyPort != setupPort) { "Wi-Fi proxy and setup ports must be different." }
    }

    public companion object {
        /** Default port for the independently bound Wi-Fi setup endpoint. */
        public const val DEFAULT_SETUP_PORT: Int = 8_181
    }
}

/** Presentation-safe description of one active Wi-Fi sharing session. */
public data class WifiSharingSession(
    public val id: WifiSharingSessionId,
    public val networkAddress: NetworkAddress,
    public val proxyEndpoint: ProxyEndpoint,
    public val setupBaseUrl: String,
    public val certificateSha256: String,
    public val networkVersion: Long,
    public val startedAtEpochMillis: Long,
) {
    init {
        require(proxyEndpoint.scope == ProxyEndpointScope.LAN) { "Wi-Fi sharing must publish a LAN endpoint." }
        require(setupBaseUrl.startsWith("http://") || setupBaseUrl.startsWith("https://")) {
            "Wi-Fi setup URL must use HTTP or HTTPS."
        }
        require(certificateSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Wi-Fi certificate fingerprint must be a lowercase SHA-256 value."
        }
        require(networkVersion >= 0L) { "Wi-Fi network version must not be negative." }
        require(startedAtEpochMillis >= 0L) { "Wi-Fi session start time must not be negative." }
    }
}

/** Short-lived invitation returned only when the user asks to connect another phone. */
public data class WifiInvitation(
    public val id: WifiInvitationId,
    public val setupUrl: String,
    public val expiresAtEpochMillis: Long,
) {
    init {
        require(setupUrl.startsWith("http://") || setupUrl.startsWith("https://")) {
            "Wi-Fi invitation URL must use HTTP or HTTPS."
        }
        require(expiresAtEpochMillis >= 0L) { "Wi-Fi invitation expiry must not be negative." }
    }
}

/** Phone observed through an invitation but not yet authorized to use the LAN proxy. */
public data class WifiPendingClient(
    public val id: WifiClientCandidateId,
    public val sourceAddress: String,
    public val requestedAtEpochMillis: Long,
    public val expiresAtEpochMillis: Long,
    public val confirmationCode: String,
) {
    init {
        require(sourceAddress.isNotBlank()) { "Pending Wi-Fi client address must not be blank." }
        require(requestedAtEpochMillis >= 0L) { "Pending Wi-Fi client request time must not be negative." }
        require(expiresAtEpochMillis >= requestedAtEpochMillis) { "Pending Wi-Fi client expiry precedes its request." }
        require(confirmationCode.matches(Regex("[0-9]{6}"))) { "Wi-Fi confirmation code must contain six digits." }
    }
}

/** Phone approved for the current Wi-Fi sharing session. */
public data class WifiApprovedClient(
    public val id: WifiClientId,
    public val displayName: String,
    public val sourceAddress: String,
    public val approvedAtEpochMillis: Long,
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= 80) {
            "Approved Wi-Fi client display name must contain 1 to 80 characters."
        }
        require(sourceAddress.isNotBlank()) { "Approved Wi-Fi client address must not be blank." }
        require(approvedAtEpochMillis >= 0L) { "Approved Wi-Fi client time must not be negative." }
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

/** Typed reason that an active session needs explicit user action before it can resume. */
public enum class WifiSharingActionReason {
    NETWORK_CHANGED,
    ADDRESS_REMOVED,
    PROXY_STOPPED,
}

/** Serialized, immutable Wi-Fi sharing state observed by application and future presentation code. */
public sealed interface WifiSharingState {
    /** No LAN listener exists. */
    public data class Disabled(public val availableAddresses: List<NetworkAddress>) : WifiSharingState

    /** Exact-interface listeners are being created and are not yet published. */
    public data object Enabling : WifiSharingState

    /** Wi-Fi gateway and setup endpoint are accepting traffic. */
    public data class Active(
        public val session: WifiSharingSession,
        public val pendingClients: List<WifiPendingClient>,
        public val approvedClients: List<WifiApprovedClient>,
        public val metrics: WifiSharingMetrics,
    ) : WifiSharingState

    /** Active resources were closed after an invalidating runtime transition. */
    public data class NeedsUserAction(
        public val reason: WifiSharingActionReason,
        public val availableAddresses: List<NetworkAddress>,
    ) : WifiSharingState

    /** Wi-Fi resources are closing and no new clients are admitted. */
    public data object Disabling : WifiSharingState

    /** Activation or shutdown failed after cleanup was attempted. */
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
