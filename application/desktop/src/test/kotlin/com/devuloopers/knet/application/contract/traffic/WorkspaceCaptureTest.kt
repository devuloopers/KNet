package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import kotlin.test.*

/** Source status derives from one writer and full identities without inheriting another tab's choice. */
class WorkspaceCaptureTest {
    private val first = PolicyWorkspace.Device(RegisteredDeviceId("first-device"))
    private val second = PolicyWorkspace.Device(RegisteredDeviceId("second-device"))

    @Test fun explicitSelectionDoesNotAdmitNewDevicesOrOtherTraffic() {
        val recording = CaptureSessionState.Capturing(CaptureSessionId("shared-writer"))
        val snapshot = WorkspaceCaptureSnapshot(recording, CaptureAdmissionPolicy(false, mapOf(first to true)))
        assertEquals(recording, snapshot.stateFor(first))
        assertEquals(CaptureSessionState.Paused, snapshot.stateFor(second))
        assertEquals(CaptureSessionState.Paused, snapshot.stateFor(PolicyWorkspace.Other))
        assertEquals(first, TrafficSource.Device(RegisteredDeviceId("first-device")).captureWorkspace())
        assertEquals(PolicyWorkspace.Other, TrafficSource.Other.captureWorkspace())
    }

    @Test fun sourceChoicesSurviveWriterChangesAndDoNotClaimAnInactiveWriterIsRecording() {
        val policy = CaptureAdmissionPolicy(false, mapOf(first to true, second to false, PolicyWorkspace.Other to true))
        val initial = WorkspaceCaptureSnapshot(CaptureSessionState.Starting, policy)
        assertEquals(CaptureSessionState.Starting, initial.stateFor(first))
        assertEquals(CaptureSessionState.Paused, initial.stateFor(second))
        for (state in listOf(CaptureSessionState.Inactive, CaptureSessionState.Paused, CaptureSessionState.Failed("fixture"))) {
            val stopped = initial.copy(sessionState = state)
            assertEquals(state, stopped.stateFor(first))
            assertEquals(state, stopped.stateFor(second))
            assertEquals(policy, stopped.admission)
        }
    }

    @Test fun administrativeGlobalAdmissionStillSupportsIndependentOverrides() {
        val snapshot = WorkspaceCaptureSnapshot(CaptureSessionState.Capturing(CaptureSessionId("legacy")),
            CaptureAdmissionPolicy(overrides = mapOf(first to false)))
        assertEquals(CaptureSessionState.Paused, snapshot.stateFor(first))
        assertIs<CaptureSessionState.Capturing>(snapshot.stateFor(second))
        assertIs<CaptureSessionState.Capturing>(snapshot.stateFor(PolicyWorkspace.Other))
    }
}
