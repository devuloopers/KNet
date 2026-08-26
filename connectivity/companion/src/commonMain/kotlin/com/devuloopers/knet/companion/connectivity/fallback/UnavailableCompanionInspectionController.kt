package com.devuloopers.knet.companion.connectivity.fallback

import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.model.CompanionInspectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fail-closed inspection controller used until a native packet backend is available. */
internal class UnavailableCompanionInspectionController(platformName: String) : CompanionInspectionController {
    private val failure = unavailablePlatformCapability(platformName, "inspection")
    private val mutableState = MutableStateFlow<CompanionInspectionState>(CompanionInspectionState.Stopped)

    override val state: StateFlow<CompanionInspectionState> = mutableState.asStateFlow()

    override suspend fun prepare(): CompanionInspectionPreparationResult {
        mutableState.value = CompanionInspectionState.Failed(failure)
        return CompanionInspectionPreparationResult.Failed(failure)
    }

    override suspend fun start(configuration: CompanionInspectionConfiguration): CompanionInspectionStartResult {
        mutableState.value = CompanionInspectionState.Failed(failure)
        return CompanionInspectionStartResult.Failed(failure)
    }

    override suspend fun stop() {
        mutableState.value = CompanionInspectionState.Stopped
    }
}
