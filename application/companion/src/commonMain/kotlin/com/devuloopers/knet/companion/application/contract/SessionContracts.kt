package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionSessionMessage
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.identity.RegisteredDeviceId

/** Minimal non-secret session access suitable for a lean packet-tunnel extension without pairing storage. */
public data class CompanionSessionAccess(
    /** Already paired secure control endpoint. */
    public val endpoint: CompanionServiceEndpoint,
    /** Registered device identity bound to the authorization token. */
    public val deviceId: RegisteredDeviceId,
    /** Validated public root for the pinned native TLS policy. */
    public val root: CompanionRootCertificate,
    /** Exact paired root fingerprint. */
    public val rootFingerprint: Sha256Fingerprint,
    /** Exact paired transport identity fingerprint. */
    public val transportFingerprint: Sha256Fingerprint,
) {
    init { require(endpoint.scheme == com.devuloopers.knet.companion.model.CompanionEndpointScheme.HTTPS) }

    /** Factories for already authenticated enrollment metadata. */
    public companion object {
        /** Extracts only connection metadata from durable enrollment, excluding credential references and UI state. */
        public fun fromRegistration(registration: CompanionRegistration): CompanionSessionAccess = CompanionSessionAccess(
            registration.controlEndpoint, registration.deviceId, registration.rootCertificate,
            registration.rootCertificateSha256, registration.transportIdentitySha256)
    }
}

/** One pinned, authenticated control connection owned by the running VPN lifecycle. */
public interface CompanionSessionConnection {
    /** Sends one bounded control envelope; application traffic never enters this connection. */
    public suspend fun send(message: CompanionSessionMessage)
    /** Receives one envelope or throws a sanitized transport/protocol failure. */
    public suspend fun receive(): CompanionSessionMessage
    /** Releases the persistent client and native TLS resources. Repeated calls must be safe. */
    public suspend fun close()
}

/** Native TLS adapter; credentials may be sent only after the paired desktop pin has been verified. */
public fun interface CompanionSessionTransport {
    /** Opens a bounded handshake to the current registered control endpoint. */
    public suspend fun connect(access: CompanionSessionAccess, credential: String): CompanionSessionConnection
}

/** Expected connection failure classes used without leaking credentials or platform exception details. */
public enum class CompanionSessionFailure { UNSUPPORTED, UNAUTHORIZED, UNAVAILABLE, INVALID_MESSAGE }

/** Typed transport failure. Unsupported peers retain the existing authenticated HTTPS policy workflow. */
public class CompanionSessionException(
    /** Closed reason suitable for workflow decisions. */
    public val reason: CompanionSessionFailure,
) : Exception("Companion control session failed: $reason")

/** Authenticated control-channel health, independent of whether the native VPN interface remains enabled. */
public enum class CompanionSessionPhase { CONNECTING, CONNECTED, RECONNECTING, REJECTED }

/** One VPN run's observation; run identity fences late cleanup from a replaced control owner. */
public data class CompanionSessionObservation(
    /** Paired desktop whose control channel is observed. */
    public val desktopId: com.devuloopers.knet.companion.model.CompanionDesktopId,
    /** Opaque VPN ownership token, never an authenticator. */
    public val runId: String,
    /** Last confirmed lifecycle phase. Connected requires an authenticated WELCOME. */
    public val phase: CompanionSessionPhase,
)
