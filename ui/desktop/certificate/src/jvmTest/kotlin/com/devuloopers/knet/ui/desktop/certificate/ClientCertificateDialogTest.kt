package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.application.contract.certificate.ClientCertificateSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying properties of the canonical client-certificate summary.
 */
class ClientCertificateDialogTest {

    /**
     * Verifies that the canonical constructor assigns all fields correctly.
     *
     * Design Intent: UI dialogs bind these properties to text inputs for client certificate registration.
     */
    @Test
    fun testClientCertificateProperties() {
        val cert = ClientCertificateSummary(
            alias = "my-client-cert",
            subject = "CN=my-client-cert, O=Devuloopers",
            host = "*.api.example.com",
            expiration = "2030-12-31",
            enabled = true
        )
        assertEquals("my-client-cert", cert.alias)
        assertEquals("CN=my-client-cert, O=Devuloopers", cert.subject)
        assertEquals("*.api.example.com", cert.host)
        assertEquals("2030-12-31", cert.expiration)
        assertTrue(cert.enabled)
    }
}
