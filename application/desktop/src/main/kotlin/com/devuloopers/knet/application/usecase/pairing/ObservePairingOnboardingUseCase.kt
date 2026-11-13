package com.devuloopers.knet.application.usecase.pairing

import com.devuloopers.knet.application.contract.pairing.CompanionOnboardingStore
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Whether one exact QR bootstrap can still be redeemed; absence never implies completed device setup. */
public enum class PairingOnboardingAvailability {
    /** The store still contains the one-time invitation. Presentation also checks its deadline. */
    AVAILABLE,
    /** The invitation was consumed, pruned or is otherwise absent and must no longer be displayed. */
    UNAVAILABLE,
}

/** Exposes replayable, non-secret availability without giving presentation access to onboarding storage. */
public class ObservePairingOnboardingUseCase(private val onboardingStore: CompanionOnboardingStore) {
    /**
     * Observes [id] independently of other invitations, including redemption before collection begins.
     * @return distinct availability updates; no secret, device credential or polling request is emitted.
     */
    public fun execute(id: CompanionBootstrapId): Flow<PairingOnboardingAvailability> =
        onboardingStore.pendingIds.map { ids ->
            if (id in ids) PairingOnboardingAvailability.AVAILABLE else PairingOnboardingAvailability.UNAVAILABLE
        }.distinctUntilChanged()
}
