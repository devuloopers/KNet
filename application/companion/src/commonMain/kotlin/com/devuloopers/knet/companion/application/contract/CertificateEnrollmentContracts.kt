package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionCertificateEnrollment
import com.devuloopers.knet.companion.model.CompanionDesktopId
import kotlinx.coroutines.flow.StateFlow

/** Durable certificate-onboarding completion state, keyed by desktop identity and exact root fingerprint. */
public interface CompanionCertificateEnrollmentRepository {
    public val enrollments: StateFlow<List<CompanionCertificateEnrollment>>

    /** Persists [enrollment] only when it still matches a registered desktop. */
    public suspend fun complete(enrollment: CompanionCertificateEnrollment): Boolean

    /** Removes any certificate-onboarding completion associated with [desktopId]. */
    public suspend fun removeEnrollment(desktopId: CompanionDesktopId): Boolean
}
