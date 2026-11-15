package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Prefers VPN-owned WSS readiness. A running inspection with no app-visible readiness session uses bounded
 * pinned HTTPS reads (the Apple extension owns its socket in a separate process). Idle apps do not poll.
 * Recording evidence never fabricates an authenticated WELCOME or renews the packet-policy lease.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class ObserveCompanionDesktopCaptureUseCase(
    private val session: StateFlow<CompanionSessionObservation?>,
    private val registrations: CompanionRegistrationRepository,
    private val inspection: CompanionInspectionController,
    private val fetch: FetchCompanionCaptureStatusUseCase,
) {
    /** Foreign/stale frames are discarded; stopped inspection and source changes cancel outstanding reads. */
    public fun execute(): Flow<CompanionDesktopCaptureObservation?> = combine(session, registrations.activeRegistration, inspection.state) { value, registration, native ->
        Triple(value, registration, native)
    }.transformLatest { (value, registration, native) ->
        if (registration == null || native !is CompanionInspectionState.Running) { emit(null); return@transformLatest }
        val owned = value?.takeIf { it.desktopId == registration.desktopId }
        if (owned?.phase == CompanionSessionPhase.CONNECTED && owned.captureStatus != CompanionDesktopCaptureState.UNKNOWN) {
            emit(CompanionDesktopCaptureObservation(registration.desktopId, owned.captureStatus)); return@transformLatest
        }
        if (owned != null && owned.phase != CompanionSessionPhase.CONNECTED)
            emit(CompanionDesktopCaptureObservation(registration.desktopId, CompanionDesktopCaptureState.UNKNOWN))
        // Allow normal WSS startup to deliver its snapshot before opening a redundant HTTPS request.
        delay(2_000)
        while (currentCoroutineContext().isActive && registrations.activeRegistration.value == registration && inspection.state.value is CompanionInspectionState.Running) {
            val status = fetch.execute(registration)
            currentCoroutineContext().ensureActive()
            if (registrations.activeRegistration.value != registration) return@transformLatest
            emit(CompanionDesktopCaptureObservation(registration.desktopId, status))
            delay(if (status == CompanionDesktopCaptureState.UNKNOWN) 30_000 else 5_000)
        }
    }.distinctUntilChanged()
}
