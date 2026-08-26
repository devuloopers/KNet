package com.devuloopers.knet.companion.android

import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.contract.CompanionTransportResult
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Fail-closed data-plane adapter used until the authenticated Android carrier is registered. */
internal class UnavailableCompanionTransport : CompanionTransport {
    private val mutableState = MutableStateFlow<CompanionConnectionState>(CompanionConnectionState.Disconnected)

    override val state: StateFlow<CompanionConnectionState> = mutableState

    override suspend fun connect(
        registration: CompanionRegistration,
        credential: String,
    ): CompanionTransportResult = CompanionTransportResult.Rejected(unavailableFailure())

    override suspend fun disconnect() {
        mutableState.value = CompanionConnectionState.Disconnected
    }
}

private fun unavailableFailure(): CompanionFailure = CompanionFailure(
    code = CompanionFailureCode.TRANSPORT_UNAVAILABLE,
    message = "The companion inspection data plane is not available in this build.",
    recoverable = true,
)
