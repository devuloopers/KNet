package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.ui.desktop.certificate.model.ClientCertificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests verifying properties and behavior of the [ClientCertificate] presentation model.
 */
public class ClientCertificateDialogTest {

    /**
     * Verifies that the [ClientCertificate] constructor assigns all fields correctly.
     *
     * Design Intent: UI dialogs bind these properties to text inputs for client certificate registration.
     */
    @Test
    public fun testClientCertificateProperties() {
        val cert = ClientCertificate(
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
