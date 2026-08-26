package com.devuloopers.knet.application.usecase.pairing

import com.devuloopers.knet.application.contract.pairing.CompanionOnboardingStore
import com.devuloopers.knet.application.contract.pairing.PairingCryptography
import com.devuloopers.knet.companion.model.CompanionBootstrapRedemptionRequest
import com.devuloopers.knet.companion.model.CompanionPairingInvitation

/** Atomically exchanges a valid one-time bootstrap secret for the complete pairing invitation. */
public class RedeemPairingOnboardingUseCase(
    private val cryptography: PairingCryptography,
    private val onboardingStore: CompanionOnboardingStore,
    private val nowEpochMillis: () -> Long,
) {
    /** Returns one complete invitation or a non-descriptive rejection for invalid, expired, or replayed input. */
    public suspend fun execute(request: CompanionBootstrapRedemptionRequest): PairingOnboardingRedemptionResult {
        val invitation = onboardingStore.claim(
            id = request.id,
            retrievalSecretDigest = cryptography.digest(request.retrievalSecret.value),
            nowEpochMillis = nowEpochMillis(),
        ) ?: return PairingOnboardingRedemptionResult.Rejected
        return PairingOnboardingRedemptionResult.Redeemed(invitation)
    }
}

/** Bootstrap redemption outcome that deliberately does not reveal the rejection reason. */
public sealed interface PairingOnboardingRedemptionResult {
    /** Complete secret-bearing invitation returned once. */
    public data class Redeemed(
        public val invitation: CompanionPairingInvitation,
    ) : PairingOnboardingRedemptionResult

    /** Invalid, expired, or already consumed bootstrap reference. */
    public data object Rejected : PairingOnboardingRedemptionResult
}
