package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.application.contract.certificate.CertificateAuthorityStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying the application-owned certificate authority statuses.
 *
 * This test ensures that the certificate authority status options are correctly
 * defined and available for UI state mapping.
 */
class CaStatusTest {

    /**
     * Verifies that the status enum contains all expected states.
     *
     * Design Intent: UI components rely on these specific enum values to represent
     * the root CA's state accurately.
     */
    @Test
    fun testCaStatusEnumValues() {
        val statuses = CertificateAuthorityStatus.entries
        assertTrue(statuses.contains(CertificateAuthorityStatus.AVAILABLE))
        assertTrue(statuses.contains(CertificateAuthorityStatus.MISSING))
        assertTrue(statuses.contains(CertificateAuthorityStatus.EXPIRED))
        assertTrue(statuses.contains(CertificateAuthorityStatus.INVALID))
        assertEquals(4, statuses.size)
    }
}
