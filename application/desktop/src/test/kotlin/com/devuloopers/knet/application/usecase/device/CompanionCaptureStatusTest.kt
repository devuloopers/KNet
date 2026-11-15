package com.devuloopers.knet.application.usecase.device

import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.companion.model.CompanionDesktopCaptureState
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Readiness derives from authenticated admission and physical lifecycle, independently of active tabs. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompanionCaptureStatusTest {
    @Test fun `shared writer projects independent devices without sibling emissions`() = runTest {
        val snapshot = MutableStateFlow(WorkspaceCaptureSnapshot())
        val control = object : CaptureSessionControl {
            override val captureState = MutableStateFlow<CaptureSessionState>(CaptureSessionState.Inactive)
            override val workspaceCaptureState = snapshot
            override suspend fun pause() = error("read-only")
            override suspend fun resume() = error("read-only")
            override suspend fun rotateForTrafficClear() = error("read-only")
        }
        val useCase = ObserveCompanionCaptureStatusUseCase(control)
        val a = RegisteredDeviceId("a"); val b = RegisteredDeviceId("b")
        val states = mutableListOf<CompanionDesktopCaptureState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase.observe(a).collect { states += it } }
        assertEquals(CompanionDesktopCaptureState.STOPPED, useCase.current(a))
        snapshot.value = WorkspaceCaptureSnapshot(CaptureSessionState.Capturing(CaptureSessionId("session")), CaptureAdmissionPolicy(false, mapOf(PolicyWorkspace.Device(a) to true)))
        assertEquals(CompanionDesktopCaptureState.CAPTURING, useCase.current(a))
        assertEquals(CompanionDesktopCaptureState.PAUSED, useCase.current(b))
        snapshot.value = snapshot.value.copy(admission = snapshot.value.admission.copy(overrides = snapshot.value.admission.overrides + (PolicyWorkspace.Device(b) to true)))
        assertEquals(listOf(CompanionDesktopCaptureState.STOPPED, CompanionDesktopCaptureState.CAPTURING), states)
        for ((source, expected) in listOf(CaptureSessionState.Starting to CompanionDesktopCaptureState.STARTING,
            CaptureSessionState.Paused to CompanionDesktopCaptureState.PAUSED, CaptureSessionState.Failed("closed") to CompanionDesktopCaptureState.STOPPED)) {
            snapshot.value = snapshot.value.copy(sessionState = source)
            assertEquals(expected, useCase.current(a))
        }
    }
}
