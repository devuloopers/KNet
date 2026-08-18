package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.application.port.certificate.ClientCertificateSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying list management operations on canonical certificate summaries.
 */
class ClientCertificateListTest {

    /**
     * Verifies filtering of enabled/disabled client certificates in a list.
     *
     * Design Intent: UI panels filter client certificates for validation and list presentation.
     */
    @Test
    fun testClientCertificateListFiltering() {
        val certs = listOf(
            ClientCertificateSummary("alias1", "subject1", "host1", "exp1", enabled = true),
            ClientCertificateSummary("alias2", "subject2", "host2", "exp2", enabled = false),
            ClientCertificateSummary("alias3", "subject3", "host3", "exp3", enabled = true)
        )

        val enabledCerts = certs.filter { it.enabled }
        assertEquals(2, enabledCerts.size)
        assertTrue(enabledCerts.any { it.alias == "alias1" })
        assertTrue(enabledCerts.any { it.alias == "alias3" })
    }
}
