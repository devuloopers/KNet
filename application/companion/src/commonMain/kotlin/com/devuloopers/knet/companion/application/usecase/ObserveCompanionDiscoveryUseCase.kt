package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery
import com.devuloopers.knet.companion.model.CompanionDiscoveryState
import kotlinx.coroutines.flow.StateFlow

/** Provides native discovery lifecycle state without exposing platform service objects. */
public class ObserveCompanionDiscoveryUseCase(discovery: CompanionDesktopDiscovery) {
    public val state: StateFlow<CompanionDiscoveryState> = discovery.state
}
