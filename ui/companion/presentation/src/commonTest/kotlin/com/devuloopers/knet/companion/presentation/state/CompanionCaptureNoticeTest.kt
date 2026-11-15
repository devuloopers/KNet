package com.devuloopers.knet.companion.presentation.state

import com.devuloopers.knet.companion.model.*
import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import kotlin.test.*

/** Capture prompts follow authenticated desktop state, not heartbeats or unrelated peers. */
class CompanionCaptureNoticeTest {
    private val registration = CompanionRegistration(CompanionDesktopId("desktop"), CompanionDesktopDisplayName("Mac"),
        RegisteredDeviceId("phone"), CompanionServiceEndpoint("localhost", 8183, CompanionEndpointScheme.HTTPS),
        CompanionServiceEndpoint("localhost", 8184, CompanionEndpointScheme.HTTPS), Sha256Fingerprint("a".repeat(64)),
        Sha256Fingerprint("b".repeat(64)), CompanionRootCertificate(byteArrayOf(1)), CompanionCredentialReference("secret"),
        setOf(DeviceScope.PROXY_STREAM), 1000, 5000)
    private fun observation(status: CompanionDesktopCaptureState) =
        CompanionDesktopCaptureObservation(registration.desktopId, status)

    @Test fun stoppedAndPausedPromptOnceThenCaptureDismisses() {
        for (status in listOf(CompanionDesktopCaptureState.STOPPED, CompanionDesktopCaptureState.PAUSED)) {
            val first = CompanionUiState(activeRegistration = registration).withCaptureObservation(observation(status))
            assertEquals(status, first.desktopCaptureNotice)
            val dismissed = first.copy(desktopCaptureNotice = null).withCaptureObservation(observation(status))
            assertNull(dismissed.desktopCaptureNotice)
            assertNull(first.withCaptureObservation(observation(CompanionDesktopCaptureState.CAPTURING)).desktopCaptureNotice)
            val reconnected = dismissed.withCaptureObservation(null)
                .withCaptureObservation(observation(status))
            assertEquals(status, reconnected.desktopCaptureNotice)
        }
    }
    @Test fun oldDesktopAndDisconnectedFramesCannotClaimRecording() {
        val initial = CompanionUiState(activeRegistration = registration)
        val foreign = observation(CompanionDesktopCaptureState.STOPPED).copy(desktopId = CompanionDesktopId("other"))
        assertNull(initial.withCaptureObservation(foreign).desktopCaptureNotice)
        assertNull(initial.withCaptureObservation(null).desktopCaptureNotice)
    }
    @Test fun preflightPromptSurvivesNoSessionUntilFreshCapturingEvidence() {
        val initial = CompanionUiState(activeRegistration = registration, captureNoticeFromPreflight = true,
            desktopCaptureNotice = CompanionDesktopCaptureState.STOPPED)
        assertEquals(initial.desktopCaptureNotice, initial.withCaptureObservation(null).desktopCaptureNotice)
        val running = initial.withCaptureObservation(observation(CompanionDesktopCaptureState.CAPTURING))
        assertNull(running.desktopCaptureNotice); assertFalse(running.captureNoticeFromPreflight)
    }
}
