package com.devuloopers.knet.application.usecase.device

import com.devuloopers.knet.application.contract.traffic.CaptureSessionControl
import com.devuloopers.knet.application.contract.traffic.CaptureSessionState
import com.devuloopers.knet.companion.model.CompanionDesktopCaptureState
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** Projects canonical recording admission for one authenticated Companion without depending on UI selection. */
public class ObserveCompanionCaptureStatusUseCase(private val capture: CaptureSessionControl) {
    /** Current coherent writer/source projection, also used by the authenticated HTTPS readiness route. */
    public fun current(id: RegisteredDeviceId): CompanionDesktopCaptureState = project(capture.workspaceCaptureState.value.stateFor(PolicyWorkspace.Device(id)))
    /** Emits only changes affecting this identity; another phone's capture choice is immaterial. */
    public fun observe(id: RegisteredDeviceId): Flow<CompanionDesktopCaptureState> = capture.workspaceCaptureState
        .map { project(it.stateFor(PolicyWorkspace.Device(id))) }.distinctUntilChanged()

    private fun project(state: CaptureSessionState): CompanionDesktopCaptureState = when (state) {
        CaptureSessionState.Inactive, is CaptureSessionState.Failed -> CompanionDesktopCaptureState.STOPPED
        CaptureSessionState.Starting -> CompanionDesktopCaptureState.STARTING
        CaptureSessionState.Paused -> CompanionDesktopCaptureState.PAUSED
        is CaptureSessionState.Capturing -> CompanionDesktopCaptureState.CAPTURING
    }
}
