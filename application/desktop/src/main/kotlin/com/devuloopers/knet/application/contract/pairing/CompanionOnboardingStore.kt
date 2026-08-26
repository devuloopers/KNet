package com.devuloopers.knet.application.contract.pairing

import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionPairingInvitation

/**
 * Pending complete invitation protected by a distinct one-time bootstrap secret digest.
 *
 * @property id opaque lookup identity advertised in the lightweight bootstrap.
 * @property retrievalSecretDigest one-way digest of the bootstrap secret; plaintext is never stored.
 * @property expiresAtEpochMillis expiry shared by the bootstrap and complete pairing invitation.
 * @property invitation complete secret-bearing invitation returned once after authentication.
 */
public data class PendingCompanionOnboarding(
    public val id: CompanionBootstrapId,
    public val retrievalSecretDigest: String,
    public val expiresAtEpochMillis: Long,
    public val invitation: CompanionPairingInvitation,
) {
    init {
        require(retrievalSecretDigest.isNotBlank()) { "Bootstrap secret digest must not be blank." }
        require(expiresAtEpochMillis == invitation.pairing.expiresAtEpochMillis) {
            "Bootstrap and pairing invitation expiry must match."
        }
    }
}

/** Atomic, bounded storage for secret-protected companion onboarding responses. */
public interface CompanionOnboardingStore {
    /** Publishes [pending] without retaining the plaintext retrieval secret. */
    public suspend fun put(pending: PendingCompanionOnboarding)

    /**
     * Atomically consumes and returns one live invitation when [retrievalSecretDigest] matches.
     *
     * @param id opaque record identity from the bootstrap.
     * @param retrievalSecretDigest digest computed from the presented one-time secret.
     * @param nowEpochMillis current time used to reject and remove expired records.
     * @return the consumed complete invitation, or null without revealing the rejection reason.
     *
     * Invalid, expired, and previously consumed records return null without exposing which condition failed.
     */
    public suspend fun claim(
        id: CompanionBootstrapId,
        retrievalSecretDigest: String,
        nowEpochMillis: Long,
    ): CompanionPairingInvitation?
}
