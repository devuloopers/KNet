package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.application.contract.certificate.CertificateAuthoritySummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying the canonical certificate-authority summary.
 */
class CertificateAuthoritySummaryTest {

    /**
     * Verifies that the canonical summary has safe defaults before backend loading.
     *
     * Design Intent: Ensures fallback values exist for CA details before they are populated from the backend.
     */
    @Test
    fun testDefaultProperties() {
        val details = CertificateAuthoritySummary()
        assertEquals("", details.subject)
        assertEquals("", details.issuer)
        assertEquals("", details.serialNumber)
        assertEquals("", details.signatureAlgorithm)
        assertEquals("", details.validFrom)
        assertEquals("", details.validUntil)
        assertEquals("", details.sha1Fingerprint)
        assertEquals("", details.sha256Fingerprint)
    }

    /**
     * Verifies custom X.509 summary fields are assigned correctly.
     *
     * Design Intent: Verifies standard fields representing X509 attributes are correctly mapped.
     */
    @Test
    fun testCustomValues() {
        val details = CertificateAuthoritySummary(
            subject = "CN=KNet Intercepting Root CA",
            issuer = "CN=KNet Intercepting Root CA",
            serialNumber = "DE:AD:BE:EF:12:34:56:78",
            signatureAlgorithm = "SHA256withRSA",
            validFrom = "2026-08-01",
            validUntil = "2036-08-01",
            sha1Fingerprint = "7A:B2:D5:E8:C2:59:71:0F:7D:F8:8C:BE:1C:2B:E9:9A:8F:B1:01:C2",
            sha256Fingerprint = "F5:C2:17:8D:2D:E8:C9:F0:A1:2B:3C:4D:5E:6F:7A:8B:9C:0D:1E:2F:3A:4B:5C:6D:7E:8F:90:A1:B2:C3"
        )
        assertEquals("CN=KNet Intercepting Root CA", details.subject)
        assertEquals("CN=KNet Intercepting Root CA", details.issuer)
        assertEquals("DE:AD:BE:EF:12:34:56:78", details.serialNumber)
        assertEquals("SHA256withRSA", details.signatureAlgorithm)
        assertEquals("2026-08-01", details.validFrom)
        assertEquals("2036-08-01", details.validUntil)
        assertEquals("7A:B2:D5:E8:C2:59:71:0F:7D:F8:8C:BE:1C:2B:E9:9A:8F:B1:01:C2", details.sha1Fingerprint)
        assertEquals(
            "F5:C2:17:8D:2D:E8:C9:F0:A1:2B:3C:4D:5E:6F:7A:8B:9C:0D:1E:2F:3A:4B:5C:6D:7E:8F:90:A1:B2:C3",
            details.sha256Fingerprint
        )
    }
}
