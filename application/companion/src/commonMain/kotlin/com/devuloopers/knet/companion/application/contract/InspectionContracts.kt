package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionInspectionMode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import kotlinx.coroutines.flow.StateFlow

/** Inputs passed to a platform VPN/local-proxy adapter without exposing platform handles. */
public data class CompanionInspectionConfiguration(
    public val registration: CompanionRegistration,
    public val mode: CompanionInspectionMode,
    public val unsupportedTrafficPolicy: UnsupportedTrafficPolicy,
    public val fullHttpsInspection: Boolean,
)

/**
 * Platform capture controller. Android implements this with VpnService; iOS later uses Network Extension.
 * Preparation must not acquire long-lived packet resources, and repeated start/stop calls must be safe.
 */
public interface CompanionInspectionController {
    public val state: StateFlow<CompanionInspectionState>
    public suspend fun prepare(): CompanionInspectionPreparationResult
    public suspend fun start(configuration: CompanionInspectionConfiguration): CompanionInspectionStartResult
    public suspend fun stop()
}

/** Whether the product may start capture or must request native user consent first. */
public sealed interface CompanionInspectionPreparationResult {
    public data object Ready : CompanionInspectionPreparationResult
    public data object ConsentRequired : CompanionInspectionPreparationResult
    public data class Failed(public val failure: CompanionFailure) : CompanionInspectionPreparationResult
}

/** Capture-start outcome after any required platform consent has completed. */
public sealed interface CompanionInspectionStartResult {
    public data object Started : CompanionInspectionStartResult
    public data class Failed(public val failure: CompanionFailure) : CompanionInspectionStartResult
}
