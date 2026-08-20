package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.application.port.certificate.CertificateAuthorityStatus
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateState
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests verifying default values and copy constructors of [CertificateState].
 */
class CertificateStateTest {

    /**
     * Verifies that the default properties of [CertificateState] are initialized correctly.
     *
     * Design Intent: Assures UI widgets load with reasonable empty/default states before asynchronous loading.
     */
    @Test
    fun testCertificateStateDefaultValues() {
        val state = CertificateState()
        assertEquals(CertificateAuthorityStatus.MISSING, state.caStatus)
        assertEquals(TrustInstallationState.CHECKING, state.trustState)
        assertTrue(state.clientCertificates.isEmpty())
        assertTrue(state.mtlsRules.isEmpty())
        assertNull(state.selectedCertificate)
        assertFalse(state.isImportDialogVisible)
        assertFalse(state.isExportDialogVisible)
        assertFalse(state.isRuleDialogVisible)
        assertFalse(state.isTrustInstructionsVisible)
        assertNull(state.manualTrustInstructions)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }
}
