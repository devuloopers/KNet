package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.port.traffic.CapturePauseResult
import com.devuloopers.knet.application.port.traffic.CaptureResumeResult
import com.devuloopers.knet.application.port.traffic.CaptureSessionControlPort
import com.devuloopers.knet.application.port.traffic.CaptureSessionState
import kotlinx.coroutines.flow.StateFlow

/** Pauses canonical traffic capture while leaving proxy forwarding available. */
public class PauseTrafficCaptureUseCase(
    private val captureControl: CaptureSessionControlPort,
) {
    /** Detaches the current capture generation. */
    public suspend fun execute(): CapturePauseResult = captureControl.pause()
}

/** Resumes traffic capture by attaching a fresh canonical generation. */
public class ResumeTrafficCaptureUseCase(
    private val captureControl: CaptureSessionControlPort,
) {
    /** Attaches or returns the currently active capture generation. */
    public suspend fun execute(): CaptureResumeResult = captureControl.resume()
}

/** Exposes capture attachment state independently from proxy runtime state. */
public class ObserveTrafficCaptureStateUseCase(
    private val captureControl: CaptureSessionControlPort,
) {
    /** Returns the hot capture state owned by the runtime adapter. */
    public fun execute(): StateFlow<CaptureSessionState> = captureControl.captureState
}
