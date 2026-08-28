package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionCertificateInstallationArtifactSource
import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/** Downloads the platform-native CA installation artifact while keeping credential access inside the workflow. */
public class DownloadCompanionRootCertificateUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val credentials: CompanionCredentialStore,
    private val certificates: CompanionCertificateInstallationArtifactSource,
    private val nowEpochMillis: () -> Long,
) {
    /** Returns an authenticated platform-native installation artifact for the active paired desktop. */
    public suspend fun execute(): DownloadCompanionRootCertificateResult {
        val access = when (val result = readCertificateAccess(registrations, credentials, nowEpochMillis)) {
            is CertificateAccessResult.Available -> result.access
            is CertificateAccessResult.Rejected -> return DownloadCompanionRootCertificateResult.Rejected(result.failure)
        }
        return try {
            when (val result = certificates.download(access.registration, access.credential)) {
                is CompanionCertificateDownloadResult.Downloaded ->
                    DownloadCompanionRootCertificateResult.Downloaded(result.artifact)
                is CompanionCertificateDownloadResult.Failed ->
                    DownloadCompanionRootCertificateResult.Rejected(result.failure)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            DownloadCompanionRootCertificateResult.Rejected(certificateUnavailable())
        }
    }
}

/** Public certificate download result. */
public sealed interface DownloadCompanionRootCertificateResult {
    /** Platform-native certificate installation material returned by the paired desktop. */
    public data class Downloaded(public val artifact: CompanionCertificateArtifact) : DownloadCompanionRootCertificateResult

    /** Typed, presentation-safe reason the download could not complete. */
    public data class Rejected(public val failure: CompanionFailure) : DownloadCompanionRootCertificateResult
}

/** Performs a real platform TLS challenge instead of treating a Settings event as proof. */
public class VerifyCompanionCertificateTrustUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val credentials: CompanionCredentialStore,
    private val certificates: CompanionRootCertificateSource,
    private val verifier: CompanionCertificateTrustVerifier,
    private val nowEpochMillis: () -> Long,
) {
    /**
     * Verifies the active registration against fresh root material and the platform TLS policy.
     *
     * When [expectedDesktopId] is supplied, verification fails closed if selection changed before credential
     * access. This lets longer workflows bind readiness to the same desktop they intend to use.
     */
    public suspend fun execute(expectedDesktopId: CompanionDesktopId? = null): CompanionCertificateState {
        val access = when (
            val result = readCertificateAccess(registrations, credentials, nowEpochMillis, expectedDesktopId)
        ) {
            is CertificateAccessResult.Available -> result.access
            is CertificateAccessResult.Rejected -> return result.failure.toCertificateVerificationState()
        }
        val artifact = try {
            when (val result = certificates.download(access.registration, access.credential)) {
                is CompanionCertificateDownloadResult.Downloaded -> result.artifact
                is CompanionCertificateDownloadResult.Failed -> return result.failure.toCertificateVerificationState()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return CompanionCertificateState.VerificationDeferred(certificateUnavailable())
        }
        return try {
            when (val result = verifier.verify(access.registration, access.credential, artifact)) {
                is CompanionCertificateState.Rejected -> result.reason.toCertificateVerificationState()
                else -> result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            CompanionCertificateState.VerificationDeferred(certificateUnavailable())
        }
    }
}

/** Exposes platform trust-store notifications as recheck triggers without interpreting them as trust. */
public class ObserveCompanionCertificateStoreChangesUseCase(
    observer: CompanionCertificateStoreChangeObserver,
) {
    /** Notification stream; every item requires a new [VerifyCompanionCertificateTrustUseCase] execution. */
    public val changes: Flow<Unit> = observer.observeChanges()
}

private data class CertificateAccess(
    val registration: CompanionRegistration,
    val credential: String,
)

private sealed interface CertificateAccessResult {
    data class Available(val access: CertificateAccess) : CertificateAccessResult
    data class Rejected(val failure: CompanionFailure) : CertificateAccessResult
}

private suspend fun readCertificateAccess(
    registrations: CompanionRegistrationRepository,
    credentials: CompanionCredentialStore,
    nowEpochMillis: () -> Long,
    expectedDesktopId: CompanionDesktopId? = null,
): CertificateAccessResult {
    val registration = registrations.activeRegistration.value
        ?: return CertificateAccessResult.Rejected(registrationMissing())
    if (expectedDesktopId != null && registration.desktopId != expectedDesktopId) {
        return CertificateAccessResult.Rejected(registrationMissing())
    }
    if (nowEpochMillis() >= registration.credentialExpiresAtEpochMillis) {
        return CertificateAccessResult.Rejected(
            CompanionFailure(CompanionFailureCode.CREDENTIAL_EXPIRED, "Paired credential expired.", true),
        )
    }
    val credential = try {
        credentials.read(registration.credentialReference)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        return CertificateAccessResult.Rejected(
            CompanionFailure(
                CompanionFailureCode.PERSISTENCE_FAILED,
                "Unable to read the paired credential.",
                true,
            ),
        )
    } ?: return CertificateAccessResult.Rejected(
        CompanionFailure(
            CompanionFailureCode.CREDENTIAL_NOT_FOUND,
            "Paired credential is unavailable.",
            false,
        ),
    )
    return CertificateAccessResult.Available(CertificateAccess(registration, credential))
}

private fun certificateUnavailable(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.CERTIFICATE_UNAVAILABLE,
    "Unable to retrieve or verify the KNet certificate.",
    true,
)

private fun CompanionFailure.toCertificateVerificationState(): CompanionCertificateState =
    if (recoverable) {
        CompanionCertificateState.VerificationDeferred(this)
    } else {
        CompanionCertificateState.Rejected(this)
    }
