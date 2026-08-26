package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.pairing.DeviceScope

/** Decodes a versioned, lightweight QR/deep-link bootstrap payload without persisting it. */
public fun interface CompanionInvitationCodec {
    /** Decodes [payload] into a validated bootstrap reference or a presentation-safe rejection. */
    public fun decode(payload: String): InvitationDecodeResult
}

/** Strict bootstrap decoding outcome. */
public sealed interface InvitationDecodeResult {
    /** Valid bootstrap reference that still requires authenticated redemption. */
    public data class Accepted(public val bootstrap: CompanionPairingBootstrap) : InvitationDecodeResult

    /** Malformed or unsupported bootstrap reference. */
    public data class Rejected(public val failure: CompanionFailure) : InvitationDecodeResult
}

/** Retrieves a complete invitation through the TLS identity pinned by a scanned bootstrap reference. */
public fun interface CompanionInvitationResolver {
    /** Atomically redeems [bootstrap] and authenticates the response before returning it. */
    public suspend fun resolve(bootstrap: CompanionPairingBootstrap): CompanionInvitationResolutionResult
}

/** Authenticated bootstrap redemption outcome. */
public sealed interface CompanionInvitationResolutionResult {
    /** Complete invitation accepted from the pinned desktop endpoint. */
    public data class Resolved(
        public val invitation: CompanionPairingInvitation,
    ) : CompanionInvitationResolutionResult

    /** Transport, identity, expiry, replay, or response validation failure. */
    public data class Rejected(
        public val failure: CompanionFailure,
    ) : CompanionInvitationResolutionResult
}

/** Creates or restores the device proof identity from platform-protected key material. */
public fun interface CompanionDeviceIdentityProvider {
    public suspend fun getOrCreate(): CompanionDeviceIdentity
}

/** Signs one pairing transcript using the platform-protected key referenced by the public identity. */
public fun interface CompanionDeviceProofSigner {
    public suspend fun sign(identity: CompanionDeviceIdentity, message: String): String
}

/** Performs the authenticated pairing and refresh exchanges over a pinned control transport. */
public interface CompanionPairingClient {
    public suspend fun pair(
        invitation: CompanionPairingInvitation,
        identity: CompanionDeviceIdentity,
        displayName: String,
    ): CompanionPairingClientResult

    public suspend fun refresh(
        registration: CompanionRegistration,
        currentCredential: String,
    ): CompanionCredentialRefreshResult
}

/** Secret-bearing pairing reply consumed immediately by the pairing use case. */
public sealed interface CompanionPairingClientResult {
    public data class Paired(
        public val credential: String,
        public val scopes: Set<DeviceScope>,
        public val credentialExpiresAtEpochMillis: Long,
    ) : CompanionPairingClientResult

    public data class Rejected(public val failure: CompanionFailure) : CompanionPairingClientResult
}
