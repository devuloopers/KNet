package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.traffic.CapturePauseResult
import com.devuloopers.knet.application.contract.traffic.CaptureResumeResult
import com.devuloopers.knet.application.contract.traffic.CaptureSessionControl
import com.devuloopers.knet.application.contract.traffic.CaptureSessionState
import kotlinx.coroutines.flow.StateFlow

/** Pauses canonical traffic capture while leaving proxy forwarding available. */
public class PauseTrafficCaptureUseCase(
    private val captureControl: CaptureSessionControl,
) {
    /** Detaches the current capture generation. */
    public suspend fun execute(): CapturePauseResult = captureControl.pause()

    /** Pauses only the captured device or Other traffic; changing tabs cannot redirect this command. */
    public suspend fun execute(workspace: com.devuloopers.knet.domain.policy.PolicyWorkspace): CapturePauseResult =
        captureControl.pause(workspace)
}

/** Resumes traffic capture by attaching a fresh canonical generation. */
public class ResumeTrafficCaptureUseCase(
    private val captureControl: CaptureSessionControl,
) {
    /** Attaches or returns the currently active capture generation. */
    public suspend fun execute(): CaptureResumeResult = captureControl.resume()

    /** Enables one source, preparing its admission even if the shared listener is not running yet. */
    public suspend fun execute(workspace: com.devuloopers.knet.domain.policy.PolicyWorkspace): CaptureResumeResult =
        captureControl.resume(workspace)
}

/** Exposes capture attachment state independently from proxy runtime state. */
public class ObserveTrafficCaptureStateUseCase(
    private val captureControl: CaptureSessionControl,
) {
    /** Returns the hot capture state owned by the runtime adapter. */
    public fun execute(workspace: com.devuloopers.knet.domain.policy.PolicyWorkspace =
        com.devuloopers.knet.domain.policy.PolicyWorkspace.Other): StateFlow<CaptureSessionState> =
        com.devuloopers.knet.application.contract.device.ProjectedStateFlow(captureControl.workspaceCaptureState) {
            it.stateFor(workspace)
        }

    /** Returns the complete current recording selection for synchronous device-tab projection. */
    public fun executeWorkspaces(): StateFlow<com.devuloopers.knet.application.contract.traffic.WorkspaceCaptureSnapshot> =
        captureControl.workspaceCaptureState
}
