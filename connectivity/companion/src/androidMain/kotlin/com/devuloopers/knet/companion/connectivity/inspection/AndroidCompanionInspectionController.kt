package com.devuloopers.knet.companion.connectivity.inspection

import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Deterministic Android implementation of the shared inspection lifecycle contract.
 *
 * @param consent native VPN-consent query that retains no Android intent.
 * @param backend native packet backend used only after consent succeeds.
 * @param nowEpochMillis clock used to timestamp a successful running state.
 */
internal class AndroidCompanionInspectionController(
    private val consent: AndroidVpnConsent,
    private val backend: AndroidInspectionBackend,
    private val nowEpochMillis: () -> Long,
) : CompanionInspectionController {
    private val delegate: CompanionInspectionController = DefaultCompanionInspectionController(
        preparePlatform = {
            if (consent.isGranted()) {
                CompanionInspectionPreparationResult.Ready
            } else {
                CompanionInspectionPreparationResult.ConsentRequired
            }
        },
        startPlatform = { configuration ->
            if (!consent.isGranted()) {
                CompanionInspectionStartResult.Failed(
                    CompanionFailure(
                        CompanionFailureCode.VPN_PERMISSION_DENIED,
                        "Android VPN permission is required.",
                        true,
                    ),
                )
            } else {
                when (val result = backend.start(configuration)) {
                    AndroidInspectionBackendResult.Started -> CompanionInspectionStartResult.Started
                    is AndroidInspectionBackendResult.Failed -> CompanionInspectionStartResult.Failed(result.failure)
                }
            }
        },
        stopPlatform = backend::stop,
        unexpectedStartFailure = {
            CompanionFailure(
                CompanionFailureCode.VPN_START_FAILED,
                "Android VPN could not be started.",
                true,
            )
        },
        nowEpochMillis = nowEpochMillis,
    )

    /** Shared, serialized inspection state exposed through the portable application contract. */
    override val state: StateFlow<CompanionInspectionState> = delegate.state

    /** Checks Android VPN consent without acquiring packet resources. */
    override suspend fun prepare(): CompanionInspectionPreparationResult = delegate.prepare()

    /** Starts the injected Android backend with [configuration] through the shared lifecycle reducer. */
    override suspend fun start(
        configuration: CompanionInspectionConfiguration,
    ): CompanionInspectionStartResult = delegate.start(configuration)

    /** Stops the injected Android backend through the shared lifecycle reducer. */
    override suspend fun stop(): Unit = delegate.stop()
}
