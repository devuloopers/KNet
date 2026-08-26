package com.devuloopers.knet.companion.connectivity.android

import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionInspectionMode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AndroidCompanionInspectionControllerTest {
    @Test
    fun consentIsRequiredBeforeBackendStarts() = runTest {
        val backend = FakeBackend()
        val controller = AndroidCompanionInspectionController(AndroidVpnConsent { false }, backend) { 1_000L }

        assertIs<CompanionInspectionPreparationResult.ConsentRequired>(controller.prepare())
        assertIs<CompanionInspectionStartResult.Failed>(controller.start(configuration()))
        assertEquals(0, backend.startCalls)
        assertIs<CompanionInspectionState.AwaitingVpnConsent>(controller.state.value)
    }

    @Test
    fun successfulBackendPublishesRunningAndThenStopped() = runTest {
        val backend = FakeBackend()
        val controller = AndroidCompanionInspectionController(AndroidVpnConsent { true }, backend) { 1_000L }

        assertIs<CompanionInspectionPreparationResult.Ready>(controller.prepare())
        assertIs<CompanionInspectionState.Stopped>(controller.state.value)
        assertIs<CompanionInspectionStartResult.Started>(controller.start(configuration()))
        assertEquals(1, backend.startCalls)
        assertEquals(1_000L, assertIs<CompanionInspectionState.Running>(controller.state.value).startedAtEpochMillis)

        controller.stop()
        assertEquals(1, backend.stopCalls)
        assertIs<CompanionInspectionState.Stopped>(controller.state.value)
    }

    @Test
    fun repeatedStartIsIdempotentWhileInspectionIsRunning() = runTest {
        val backend = FakeBackend()
        val controller = AndroidCompanionInspectionController(AndroidVpnConsent { true }, backend) { 1_000L }

        assertIs<CompanionInspectionStartResult.Started>(controller.start(configuration()))
        assertIs<CompanionInspectionStartResult.Started>(controller.start(configuration()))

        assertEquals(1, backend.startCalls)
    }

    private class FakeBackend : AndroidInspectionBackend {
        var startCalls = 0
        var stopCalls = 0
        override suspend fun start(configuration: CompanionInspectionConfiguration): AndroidInspectionBackendResult {
            startCalls += 1
            return AndroidInspectionBackendResult.Started
        }
        override suspend fun stop() {
            stopCalls += 1
        }
    }

    private fun configuration(): CompanionInspectionConfiguration = CompanionInspectionConfiguration(
        registration = CompanionRegistration(
            desktopId = CompanionDesktopId("desktop-1"),
            desktopDisplayName = "Development Mac",
            deviceId = RegisteredDeviceId("device-1"),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, true),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, true),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
            credentialReference = CompanionCredentialReference("credential-reference"),
            scopes = setOf(DeviceScope.PROXY_STREAM),
            pairedAtEpochMillis = 1_000L,
            credentialExpiresAtEpochMillis = 2_000L,
        ),
        mode = CompanionInspectionMode.DEVICE_VPN,
        unsupportedTrafficPolicy = UnsupportedTrafficPolicy.REJECT,
        fullHttpsInspection = false,
    )
}
