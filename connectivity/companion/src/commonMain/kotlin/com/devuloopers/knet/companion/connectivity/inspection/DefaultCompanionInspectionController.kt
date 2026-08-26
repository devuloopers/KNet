package com.devuloopers.knet.companion.connectivity.inspection

import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Platform-neutral inspection lifecycle reducer used behind native connectivity adapters.
 *
 * Native code owns permission checks and packet resources through the injected operations. This reducer owns
 * serialization, idempotency, state transitions, cancellation recovery, and failure containment.
 */
internal class DefaultCompanionInspectionController(
    private val preparePlatform: suspend () -> CompanionInspectionPreparationResult,
    private val startPlatform: suspend (CompanionInspectionConfiguration) -> CompanionInspectionStartResult,
    private val stopPlatform: suspend () -> Unit,
    private val unexpectedStartFailure: () -> CompanionFailure,
    private val nowEpochMillis: () -> Long,
) : CompanionInspectionController {
    private val lifecycleLock: Mutex = Mutex()
    private val mutableState: MutableStateFlow<CompanionInspectionState> =
        MutableStateFlow(CompanionInspectionState.Stopped)

    override val state: StateFlow<CompanionInspectionState> = mutableState.asStateFlow()

    override suspend fun prepare(): CompanionInspectionPreparationResult = lifecycleLock.withLock {
        val result = preparePlatform()
        when (result) {
            CompanionInspectionPreparationResult.Ready -> {
                if (mutableState.value !is CompanionInspectionState.Running) {
                    mutableState.value = CompanionInspectionState.Stopped
                }
            }

            CompanionInspectionPreparationResult.ConsentRequired -> {
                mutableState.value = CompanionInspectionState.AwaitingVpnConsent
            }

            is CompanionInspectionPreparationResult.Failed -> {
                mutableState.value = CompanionInspectionState.Failed(result.failure)
            }
        }
        result
    }

    override suspend fun start(configuration: CompanionInspectionConfiguration): CompanionInspectionStartResult =
        lifecycleLock.withLock {
            if (mutableState.value is CompanionInspectionState.Running) {
                return@withLock CompanionInspectionStartResult.Started
            }
            mutableState.value = CompanionInspectionState.Preparing
            return try {
                val result = startPlatform(configuration)
                when (result) {
                    CompanionInspectionStartResult.Started -> {
                        mutableState.value = CompanionInspectionState.Running(
                            mode = configuration.mode,
                            startedAtEpochMillis = nowEpochMillis(),
                            fullHttpsInspection = configuration.fullHttpsInspection,
                        )
                    }

                    is CompanionInspectionStartResult.Failed -> {
                        mutableState.value = if (result.failure.code == CompanionFailureCode.VPN_PERMISSION_DENIED) {
                            CompanionInspectionState.AwaitingVpnConsent
                        } else {
                            CompanionInspectionState.Failed(result.failure)
                        }
                    }
                }
                result
            } catch (cancelled: CancellationException) {
                mutableState.value = CompanionInspectionState.Stopped
                throw cancelled
            } catch (_: Exception) {
                val failure = unexpectedStartFailure()
                mutableState.value = CompanionInspectionState.Failed(failure)
                CompanionInspectionStartResult.Failed(failure)
            }
        }

    override suspend fun stop(): Unit = lifecycleLock.withLock {
        if (mutableState.value == CompanionInspectionState.Stopped) return@withLock
        mutableState.value = CompanionInspectionState.Stopping
        try {
            stopPlatform()
        } finally {
            mutableState.value = CompanionInspectionState.Stopped
        }
    }
}
