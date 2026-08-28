package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionCertificateEnrollmentRepository
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.model.CompanionCertificateEnrollment
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import kotlinx.coroutines.flow.StateFlow

/** Exposes durable certificate-onboarding completion independently from live platform trust. */
public class ObserveCompanionCertificateEnrollmentsUseCase(
    repository: CompanionCertificateEnrollmentRepository,
) {
    public val enrollments: StateFlow<List<CompanionCertificateEnrollment>> = repository.enrollments
}

/** Re-verifies trust and durably completes onboarding for the active desktop's exact root. */
public class CompleteCompanionCertificateEnrollmentUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val enrollments: CompanionCertificateEnrollmentRepository,
    private val verifyTrust: VerifyCompanionCertificateTrustUseCase,
    private val nowEpochMillis: () -> Long,
) {
    public suspend fun execute(expectedDesktopId: CompanionDesktopId): CompleteCompanionCertificateEnrollmentResult {
        val registration = registrations.activeRegistration.value
            ?.takeIf { it.desktopId == expectedDesktopId }
            ?: return CompleteCompanionCertificateEnrollmentResult.Rejected(
                CompanionFailure(
                    CompanionFailureCode.REGISTRATION_NOT_FOUND,
                    "The paired desktop is no longer selected.",
                    true,
                ),
            )
        return when (val trust = verifyTrust.execute(expectedDesktopId)) {
            is CompanionCertificateState.Trusted -> {
                if (trust.rootCertificateSha256 != registration.rootCertificateSha256) {
                    CompleteCompanionCertificateEnrollmentResult.Rejected(certificateNotTrusted())
                } else {
                    val enrollment = CompanionCertificateEnrollment(
                        desktopId = registration.desktopId,
                        rootCertificateSha256 = registration.rootCertificateSha256,
                        completedAtEpochMillis = nowEpochMillis(),
                    )
                    if (enrollments.complete(enrollment)) {
                        CompleteCompanionCertificateEnrollmentResult.Completed(enrollment, trust)
                    } else {
                        CompleteCompanionCertificateEnrollmentResult.Rejected(
                            CompanionFailure(
                                CompanionFailureCode.PERSISTENCE_FAILED,
                                "Unable to save certificate setup completion.",
                                true,
                            ),
                        )
                    }
                }
            }

            is CompanionCertificateState.Rejected ->
                CompleteCompanionCertificateEnrollmentResult.Rejected(trust.reason)
            is CompanionCertificateState.VerificationDeferred ->
                CompleteCompanionCertificateEnrollmentResult.Rejected(trust.reason)
            CompanionCertificateState.InstallationRequired,
            CompanionCertificateState.Unknown,
            CompanionCertificateState.Verifying,
            -> CompleteCompanionCertificateEnrollmentResult.Rejected(certificateNotTrusted())
        }
    }

    private fun certificateNotTrusted(): CompanionFailure = CompanionFailure(
        CompanionFailureCode.CERTIFICATE_NOT_TRUSTED,
        "The KNet certificate is not trusted on this device.",
        true,
    )
}

public sealed interface CompleteCompanionCertificateEnrollmentResult {
    public data class Completed(
        public val enrollment: CompanionCertificateEnrollment,
        public val trust: CompanionCertificateState.Trusted,
    ) : CompleteCompanionCertificateEnrollmentResult

    public data class Rejected(
        public val failure: CompanionFailure,
    ) : CompleteCompanionCertificateEnrollmentResult
}
