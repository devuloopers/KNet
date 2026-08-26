package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.pairing.DeviceScope

/** Decodes a versioned, secret-bearing QR/deep-link payload without persisting it. */
public fun interface CompanionInvitationCodec {
    public fun decode(payload: String): InvitationDecodeResult
}

/** Strict invitation decoding outcome. */
public sealed interface InvitationDecodeResult {
    public data class Accepted(public val invitation: CompanionPairingInvitation) : InvitationDecodeResult
    public data class Rejected(public val failure: CompanionFailure) : InvitationDecodeResult
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
