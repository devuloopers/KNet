package com.devuloopers.knet.companion.model

/** Transport selected below companion application workflows. */
public enum class CompanionTransportKind {
    DIRECT_LAN,
    RELAY,
}

/** Explicit behavior for traffic the current companion cannot inspect. */
public enum class UnsupportedTrafficPolicy {
    REJECT,
    BYPASS,
}

/** Requested device acquisition mode. */
public enum class CompanionInspectionMode {
    DEVICE_VPN,
    LOCAL_PROXY,
}

/** Current network reachability as observed by a platform adapter. */
public sealed interface CompanionNetworkState {
    public data object Unknown : CompanionNetworkState
    public data object Unavailable : CompanionNetworkState
    public data class Available(public val metered: Boolean) : CompanionNetworkState
}

/** Authenticated transport lifecycle shared by direct and future relay implementations. */
public sealed interface CompanionConnectionState {
    public data object Disconnected : CompanionConnectionState

    public data class Connecting(
        public val desktopId: CompanionDesktopId,
        public val attempt: Int,
    ) : CompanionConnectionState

    public data class Connected(
        public val desktopId: CompanionDesktopId,
        public val transport: CompanionTransportKind,
        public val connectedAtEpochMillis: Long,
    ) : CompanionConnectionState

    public data class Reconnecting(
        public val desktopId: CompanionDesktopId,
        public val attempt: Int,
    ) : CompanionConnectionState

    public data class Failed(public val failure: CompanionFailure) : CompanionConnectionState
}

/** Platform capture lifecycle. A connected transport does not imply that capture is active. */
public sealed interface CompanionInspectionState {
    public data object Stopped : CompanionInspectionState
    public data object Preparing : CompanionInspectionState
    public data object AwaitingVpnConsent : CompanionInspectionState
    public data class Running(
        public val mode: CompanionInspectionMode,
        public val startedAtEpochMillis: Long,
        public val fullHttpsInspection: Boolean,
    ) : CompanionInspectionState

    public data object Stopping : CompanionInspectionState
    public data class Failed(public val failure: CompanionFailure) : CompanionInspectionState
}

/** Stable failure categories; presentation never parses exception text. */
public enum class CompanionFailureCode {
    INVITATION_INVALID,
    INVITATION_EXPIRED,
    INVITATION_RETRIEVAL_FAILED,
    PAIRING_REJECTED,
    REGISTRATION_NOT_FOUND,
    CREDENTIAL_NOT_FOUND,
    CREDENTIAL_EXPIRED,
    NETWORK_UNAVAILABLE,
    TRANSPORT_UNAVAILABLE,
    TRANSPORT_IDENTITY_MISMATCH,
    DESKTOP_IDENTITY_CONFLICT,
    CERTIFICATE_UNAVAILABLE,
    CERTIFICATE_NOT_TRUSTED,
    VPN_PERMISSION_DENIED,
    VPN_START_FAILED,
    PLATFORM_ADAPTER_UNAVAILABLE,
    PERSISTENCE_FAILED,
    CANCELLED,
    UNKNOWN,
}

/** Sanitized, platform-neutral failure suitable for durable state and UI. */
public data class CompanionFailure(
    public val code: CompanionFailureCode,
    public val message: String,
    public val recoverable: Boolean,
) {
    init {
        require(message.isNotBlank() && message.length <= 512)
        require(message.none { character -> character.code in 0..31 || character.code == 127 })
    }
}
