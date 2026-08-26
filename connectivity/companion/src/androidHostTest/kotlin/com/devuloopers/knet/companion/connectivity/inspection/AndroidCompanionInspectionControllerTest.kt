package com.devuloopers.knet.companion.connectivity.inspection

import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.connectivity.testing.companionInspectionConfigurationFixture
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AndroidCompanionInspectionControllerTest {
    @Test
    fun consentIsRequiredBeforeBackendStarts() = runTest {
        val backend = FakeBackend()
        val controller = AndroidCompanionInspectionController({ false }, backend) { 1_000L }

        assertIs<CompanionInspectionPreparationResult.ConsentRequired>(controller.prepare())
        assertIs<CompanionInspectionStartResult.Failed>(controller.start(companionInspectionConfigurationFixture()))
        assertEquals(0, backend.startCalls)
        assertIs<CompanionInspectionState.AwaitingVpnConsent>(controller.state.value)
    }

    @Test
    fun successfulBackendPublishesRunningAndThenStopped() = runTest {
        val backend = FakeBackend()
        val controller = AndroidCompanionInspectionController({ true }, backend) { 1_000L }

        assertIs<CompanionInspectionPreparationResult.Ready>(controller.prepare())
        assertIs<CompanionInspectionState.Stopped>(controller.state.value)
        assertIs<CompanionInspectionStartResult.Started>(controller.start(companionInspectionConfigurationFixture()))
        assertEquals(1, backend.startCalls)
        assertEquals(1_000L, assertIs<CompanionInspectionState.Running>(controller.state.value).startedAtEpochMillis)

        controller.stop()
        assertEquals(1, backend.stopCalls)
        assertIs<CompanionInspectionState.Stopped>(controller.state.value)
    }

    @Test
    fun backendFailureIsPreservedByTheAndroidAdapter() = runTest {
        val failure = CompanionFailure(CompanionFailureCode.VPN_START_FAILED, "Backend failed.", true)
        val backend = FakeBackend(AndroidInspectionBackendResult.Failed(failure))
        val controller = AndroidCompanionInspectionController({ true }, backend) { 1_000L }

        val result = assertIs<CompanionInspectionStartResult.Failed>(
            controller.start(companionInspectionConfigurationFixture()),
        )

        assertEquals(failure, result.failure)
        assertEquals(1, backend.startCalls)
    }

    private class FakeBackend(
        private val result: AndroidInspectionBackendResult = AndroidInspectionBackendResult.Started,
    ) : AndroidInspectionBackend {
        var startCalls = 0
        var stopCalls = 0
        override suspend fun start(configuration: CompanionInspectionConfiguration): AndroidInspectionBackendResult {
            startCalls += 1
            return result
        }

        override suspend fun stop() {
            stopCalls += 1
        }
    }

}
