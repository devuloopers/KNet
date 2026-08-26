package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateController
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailure
import kotlinx.coroutines.flow.Flow

/** Downloads public CA material through the authenticated companion control plane. */
public class DownloadCompanionRootCertificateUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val certificates: CompanionCertificateController,
) {
    public suspend fun execute(): DownloadCompanionRootCertificateResult {
        val registration = registrations.activeRegistration.value
            ?: return DownloadCompanionRootCertificateResult.Rejected(registrationMissing())
        return when (val result = certificates.download(registration)) {
            is CompanionCertificateDownloadResult.Downloaded -> DownloadCompanionRootCertificateResult.Downloaded(result.artifact)
            is CompanionCertificateDownloadResult.Failed -> DownloadCompanionRootCertificateResult.Rejected(result.failure)
        }
    }
}

/** Public certificate download result. */
public sealed interface DownloadCompanionRootCertificateResult {
    public data class Downloaded(public val artifact: CompanionCertificateArtifact) : DownloadCompanionRootCertificateResult
    public data class Rejected(public val failure: CompanionFailure) : DownloadCompanionRootCertificateResult
}

/** Performs a real TLS challenge instead of enumerating the platform certificate store. */
public class VerifyCompanionCertificateTrustUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val certificates: CompanionCertificateController,
) {
    public suspend fun execute(): CompanionCertificateState {
        val registration = registrations.activeRegistration.value
            ?: return CompanionCertificateState.Rejected(registrationMissing())
        return certificates.verifyTrust(registration)
    }

    public fun observeActive(): Flow<CompanionCertificateState>? =
        registrations.activeRegistration.value?.let(certificates::observe)
}

