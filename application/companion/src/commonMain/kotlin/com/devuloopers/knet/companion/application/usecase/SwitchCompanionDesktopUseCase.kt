package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ordered progress of a handoff; no new capture can start before the previous capture stops. */
public enum class CompanionDesktopSwitchPhase {
    STOPPING_INSPECTION,
    VERIFYING_DESKTOP,
    RESUMING_INSPECTION,
}

/** Result retains the actual selected desktop even if target verification or capture resumption fails. */
public sealed interface CompanionDesktopSwitchResult {
    /**
     * Target selected; certificate setup or a new native consent prompt may still be required.
     * @property consentRequired whether resuming capture requires an explicit native consent prompt.
     */
    public data class Selected(public val consentRequired: Boolean = false) : CompanionDesktopSwitchResult

    /** A sanitized reason for the failed handoff. Saved pairings are never removed. */
    public data class Failed(public val failure: CompanionFailure) : CompanionDesktopSwitchResult
}

/**
 * Serializes desktop handoffs across capture, authenticated endpoint discovery, selection and optional restart.
 *
 * An unreachable target leaves the previous selection intact, with capture stopped. Selecting a desktop whose
 * root has not completed onboarding enters certificate setup instead of silently starting limited inspection.
 */
public class SwitchCompanionDesktopUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val enrollments: CompanionCertificateEnrollmentRepository,
    private val inspection: CompanionInspectionController,
    private val stop: StopCompanionInspectionUseCase,
    private val resolver: CompanionEndpointResolver,
    private val verify: VerifyCompanionCertificateTrustUseCase,
    private val start: StartCompanionInspectionUseCase,
) {
    private val mutex: Mutex = Mutex()

    /**
     * Selects [desktopId], reporting ordered [onPhase] updates. [resumeInspection] is used after re-pairing has
     * already stopped an active session; otherwise the current runtime determines whether to restart.
     */
    public suspend fun execute(
        desktopId: CompanionDesktopId,
        resumeInspection: Boolean = false,
        onPhase: (CompanionDesktopSwitchPhase) -> Unit = {},
    ): CompanionDesktopSwitchResult = mutex.withLock {
        val target = registrations.registrations.value.firstOrNull { it.desktopId == desktopId }
            ?: return@withLock CompanionDesktopSwitchResult.Failed(missingDesktop())
        if (registrations.activeRegistration.value?.desktopId == desktopId && !resumeInspection) {
            return@withLock CompanionDesktopSwitchResult.Selected()
        }
        val resume = resumeInspection || inspection.state.value is CompanionInspectionState.Running
        try {
            onPhase(CompanionDesktopSwitchPhase.STOPPING_INSPECTION)
            stop.execute()
            onPhase(CompanionDesktopSwitchPhase.VERIFYING_DESKTOP)
            val verified = when (val endpoint = resolver.resolve(target)) {
                is CompanionEndpointRecoveryResult.Recovered -> endpoint.registration
                is CompanionEndpointRecoveryResult.Rejected ->
                    return@withLock CompanionDesktopSwitchResult.Failed(endpoint.failure)
            }
            if (!registrations.setActive(verified.desktopId)) {
                return@withLock CompanionDesktopSwitchResult.Failed(missingDesktop())
            }
            val trust = verify.execute(verified.desktopId)
            val enrolled = enrollments.enrollments.value.any { it.matches(verified) }
            if (resume && enrolled && trust is CompanionCertificateState.Trusted &&
                trust.rootCertificateSha256 == verified.rootCertificateSha256
            ) {
                onPhase(CompanionDesktopSwitchPhase.RESUMING_INSPECTION)
                when (val started = start.execute()) {
                    is StartCompanionInspectionResult.Started -> CompanionDesktopSwitchResult.Selected()
                    StartCompanionInspectionResult.VpnConsentRequired ->
                        CompanionDesktopSwitchResult.Selected(consentRequired = true)
                    is StartCompanionInspectionResult.Rejected -> CompanionDesktopSwitchResult.Failed(started.failure)
                }
            } else {
                CompanionDesktopSwitchResult.Selected()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CompanionDesktopSwitchResult.Failed(
                CompanionFailure(CompanionFailureCode.TRANSPORT_UNAVAILABLE, "Unable to switch desktops. Try again.", true),
            )
        }
    }

    private fun missingDesktop(): CompanionFailure = CompanionFailure(
        CompanionFailureCode.REGISTRATION_NOT_FOUND, "The selected desktop is no longer registered.", true,
    )
}
