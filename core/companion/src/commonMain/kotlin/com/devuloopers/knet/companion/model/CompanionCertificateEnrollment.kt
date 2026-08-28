package com.devuloopers.knet.companion.model

/**
 * Durable proof that certificate onboarding was completed for one exact desktop root.
 *
 * This is intentionally separate from the live [CompanionCertificateState]. The enrollment decides whether the
 * onboarding UI has already been completed, while a fresh trust verification still decides whether inspection may
 * start. Rotating a desktop root invalidates the enrollment because [rootCertificateSha256] no longer matches.
 */
public data class CompanionCertificateEnrollment(
    public val desktopId: CompanionDesktopId,
    public val rootCertificateSha256: Sha256Fingerprint,
    public val completedAtEpochMillis: Long,
) {
    init {
        require(completedAtEpochMillis >= 0L) { "Certificate enrollment completion time must not be negative." }
    }

    /** Returns true only for the registration and root that were explicitly completed. */
    public fun matches(registration: CompanionRegistration): Boolean =
        desktopId == registration.desktopId && rootCertificateSha256 == registration.rootCertificateSha256
}
