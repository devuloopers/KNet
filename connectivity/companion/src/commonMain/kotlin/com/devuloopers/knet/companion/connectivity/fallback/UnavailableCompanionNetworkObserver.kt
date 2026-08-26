package com.devuloopers.knet.companion.connectivity.fallback

import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.model.CompanionNetworkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fail-closed network observer used while a native platform adapter is unavailable. */
internal class UnavailableCompanionNetworkObserver : CompanionNetworkObserver {
    private val state: StateFlow<CompanionNetworkState> =
        MutableStateFlow<CompanionNetworkState>(CompanionNetworkState.Unknown).asStateFlow()

    override fun observe(): StateFlow<CompanionNetworkState> = state
}
