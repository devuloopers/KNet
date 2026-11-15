package com.devuloopers.knet.companion.presentation.state

import com.devuloopers.knet.companion.application.contract.CompanionDesktopCaptureObservation
import com.devuloopers.knet.companion.model.CompanionDesktopCaptureState

/** Reduces live readiness without re-prompting on heartbeats or accepting another desktop's late frame. */
internal fun CompanionUiState.withCaptureObservation(observation: CompanionDesktopCaptureObservation?): CompanionUiState {
    val status = observation?.takeIf { it.desktopId == activeRegistration?.desktopId }
        ?.status ?: CompanionDesktopCaptureState.UNKNOWN
    if (status == CompanionDesktopCaptureState.UNKNOWN) return copy(desktopCaptureStatus = status,
        desktopCaptureNotice = if (captureNoticeFromPreflight) desktopCaptureNotice else null)
    if (status == CompanionDesktopCaptureState.CAPTURING) return copy(desktopCaptureStatus = status,
        desktopCaptureNotice = null, captureNoticeFromPreflight = false)
    val needsNotice = status == CompanionDesktopCaptureState.PAUSED || status == CompanionDesktopCaptureState.STOPPED
    return copy(desktopCaptureStatus = status,
        desktopCaptureNotice = if (!needsNotice) null else if (desktopCaptureStatus != status) status else desktopCaptureNotice,
        captureNoticeFromPreflight = false)
}
