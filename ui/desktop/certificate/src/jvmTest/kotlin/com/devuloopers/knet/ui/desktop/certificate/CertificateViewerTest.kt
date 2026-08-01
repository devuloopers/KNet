package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.ui.desktop.certificate.model.CertificateSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying properties of [CertificateSummary] used by the Certificate Viewer panel.
 */
public class CertificateViewerTest {

    /**
     * Verifies that the constructor of [CertificateSummary] populates all fields correctly.
     *
     * Design Intent: Summaries of certificates are shown in UI grids/tables before expanding details.
     */
    @Test
    public fun testCertificateSummaryProperties() {
        val summary = CertificateSummary(
            alias = "dev-cert",
            subject = "CN=dev-cert, O=Devuloopers",
            expiration = "2028-01-01",
            type = "Client",
            status = "Active"
        )
        assertEquals("dev-cert", summary.alias)
        assertEquals("CN=dev-cert, O=Devuloopers", summary.subject)
        assertEquals("2028-01-01", summary.expiration)
        assertEquals("Client", summary.type)
        assertEquals("Active", summary.status)
    }
}
