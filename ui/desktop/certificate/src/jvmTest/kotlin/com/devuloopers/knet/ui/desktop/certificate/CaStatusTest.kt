package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.ui.desktop.certificate.model.CaStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying the functionality and enum values of [CaStatus].
 *
 * This test ensures that the certificate authority status options are correctly
 * defined and available for UI state mapping.
 */
class CaStatusTest {

    /**
     * Verifies that the [CaStatus] enum contains all expected states.
     *
     * Design Intent: UI components rely on these specific enum values to represent
     * the root CA's state accurately.
     */
    @Test
    fun testCaStatusEnumValues() {
        val statuses = CaStatus.entries
        assertTrue(statuses.contains(CaStatus.AVAILABLE))
        assertTrue(statuses.contains(CaStatus.MISSING))
        assertTrue(statuses.contains(CaStatus.EXPIRED))
        assertTrue(statuses.contains(CaStatus.INVALID))
        assertTrue(statuses.contains(CaStatus.INSTALLATION_REQUIRED))
        assertEquals(5, statuses.size)
    }
}
