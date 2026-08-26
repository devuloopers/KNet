package com.devuloopers.knet.companion.connectivity.inspection

import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.connectivity.testing.companionInspectionConfigurationFixture
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CompanionInspectionLifecycleTest {
    @Test
    fun successfulPlatformLifecycleIsSerializedAndIdempotent() = runTest {
        var startCalls = 0
        var stopCalls = 0
        val controller = DefaultCompanionInspectionController(
            preparePlatform = { CompanionInspectionPreparationResult.Ready },
            startPlatform = {
                startCalls += 1
                CompanionInspectionStartResult.Started
            },
            stopPlatform = { stopCalls += 1 },
            unexpectedStartFailure = ::unexpectedFailure,
            nowEpochMillis = { 1_000L },
        )

        assertIs<CompanionInspectionPreparationResult.Ready>(controller.prepare())
        assertIs<CompanionInspectionStartResult.Started>(controller.start(companionInspectionConfigurationFixture()))
        assertIs<CompanionInspectionStartResult.Started>(controller.start(companionInspectionConfigurationFixture()))
        assertEquals(1, startCalls)
        assertEquals(1_000L, assertIs<CompanionInspectionState.Running>(controller.state.value).startedAtEpochMillis)

        controller.stop()
        assertEquals(1, stopCalls)
        assertIs<CompanionInspectionState.Stopped>(controller.state.value)
    }

    @Test
    fun permissionFailureReturnsToAwaitingConsent() = runTest {
        val failure = CompanionFailure(
            CompanionFailureCode.VPN_PERMISSION_DENIED,
            "Platform VPN permission is required.",
            true,
        )
        val controller = DefaultCompanionInspectionController(
            preparePlatform = { CompanionInspectionPreparationResult.ConsentRequired },
            startPlatform = { CompanionInspectionStartResult.Failed(failure) },
            stopPlatform = {},
            unexpectedStartFailure = ::unexpectedFailure,
            nowEpochMillis = { 1_000L },
        )

        assertIs<CompanionInspectionPreparationResult.ConsentRequired>(controller.prepare())
        assertIs<CompanionInspectionStartResult.Failed>(controller.start(companionInspectionConfigurationFixture()))
        assertIs<CompanionInspectionState.AwaitingVpnConsent>(controller.state.value)
    }

    @Test
    fun concurrentStartsAcquireThePlatformBackendOnce() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var startCalls = 0
        val controller = DefaultCompanionInspectionController(
            preparePlatform = { CompanionInspectionPreparationResult.Ready },
            startPlatform = {
                startCalls += 1
                entered.complete(Unit)
                release.await()
                CompanionInspectionStartResult.Started
            },
            stopPlatform = {},
            unexpectedStartFailure = ::unexpectedFailure,
            nowEpochMillis = { 1_000L },
        )

        val first = async { controller.start(companionInspectionConfigurationFixture()) }
        entered.await()
        val second = async { controller.start(companionInspectionConfigurationFixture()) }
        release.complete(Unit)

        assertIs<CompanionInspectionStartResult.Started>(first.await())
        assertIs<CompanionInspectionStartResult.Started>(second.await())
        assertEquals(1, startCalls)
    }

    @Test
    fun cancellationRestoresStoppedState() = runTest {
        val controller = DefaultCompanionInspectionController(
            preparePlatform = { CompanionInspectionPreparationResult.Ready },
            startPlatform = { throw CancellationException("cancelled") },
            stopPlatform = {},
            unexpectedStartFailure = ::unexpectedFailure,
            nowEpochMillis = { 1_000L },
        )

        assertFailsWith<CancellationException> {
            controller.start(companionInspectionConfigurationFixture())
        }
        assertIs<CompanionInspectionState.Stopped>(controller.state.value)
    }

    @Test
    fun unexpectedPlatformExceptionBecomesTypedFailure() = runTest {
        val controller = DefaultCompanionInspectionController(
            preparePlatform = { CompanionInspectionPreparationResult.Ready },
            startPlatform = { error("unexpected") },
            stopPlatform = {},
            unexpectedStartFailure = ::unexpectedFailure,
            nowEpochMillis = { 1_000L },
        )

        val result = assertIs<CompanionInspectionStartResult.Failed>(
            controller.start(companionInspectionConfigurationFixture()),
        )

        assertEquals(CompanionFailureCode.VPN_START_FAILED, result.failure.code)
        assertIs<CompanionInspectionState.Failed>(controller.state.value)
    }

    @Test
    fun prepareWhileRunningDoesNotReplaceRunningState() = runTest {
        val controller = DefaultCompanionInspectionController(
            preparePlatform = { CompanionInspectionPreparationResult.Ready },
            startPlatform = { CompanionInspectionStartResult.Started },
            stopPlatform = {},
            unexpectedStartFailure = ::unexpectedFailure,
            nowEpochMillis = { 1_000L },
        )

        controller.start(companionInspectionConfigurationFixture())
        assertIs<CompanionInspectionPreparationResult.Ready>(controller.prepare())

        assertIs<CompanionInspectionState.Running>(controller.state.value)
    }

    @Test
    fun stopFailureStillRestoresStoppedState() = runTest {
        val controller = DefaultCompanionInspectionController(
            preparePlatform = { CompanionInspectionPreparationResult.Ready },
            startPlatform = { CompanionInspectionStartResult.Started },
            stopPlatform = { error("stop failed") },
            unexpectedStartFailure = ::unexpectedFailure,
            nowEpochMillis = { 1_000L },
        )
        controller.start(companionInspectionConfigurationFixture())

        assertFailsWith<IllegalStateException> { controller.stop() }
        assertIs<CompanionInspectionState.Stopped>(controller.state.value)
    }

    private fun unexpectedFailure(): CompanionFailure = CompanionFailure(
        CompanionFailureCode.VPN_START_FAILED,
        "Platform inspection could not start.",
        true,
    )
}
