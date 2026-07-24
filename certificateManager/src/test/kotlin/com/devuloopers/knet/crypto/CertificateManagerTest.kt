package com.devuloopers.knet.crypto

import org.bouncycastle.asn1.x509.GeneralName
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CertificateManagerTest {

    @Test
    fun testRootCaGeneration() {
        val ca = CertificateAuthority.generate(
            commonName = "Test KNet CA",
            org = "Test Org",
            validityDays = 30
        )

        assertNotNull(ca.privateKey)
        assertNotNull(ca.certificate)

        // Verify it is self-signed (verifying the cert's signature with its own public key)
        ca.certificate.verify(ca.certificate.publicKey)

        // Verify issuer matches subject
        assertEquals(ca.certificate.issuerX500Principal, ca.certificate.subjectX500Principal)
        assertTrue(ca.certificate.subjectX500Principal.name.contains("CN=Test KNet CA"))
        assertTrue(ca.certificate.subjectX500Principal.name.contains("O=Test Org"))

        // Verify Basic Constraints (CA must be true)
        assertTrue(ca.certificate.basicConstraints >= 0, "Basic Constraints must indicate this is a CA")
    }

    @Test
    fun testPemSerialization() {
        val ca = CertificateAuthority.generate(
            commonName = "Serialize CA",
            org = "Serialize Org",
            validityDays = 10
        )

        val (certPem, keyPem) = ca.saveToPemStrings()
        assertTrue(certPem.contains("-----BEGIN CERTIFICATE-----"))
        assertTrue(certPem.contains("-----END CERTIFICATE-----"))
        assertTrue(keyPem.contains("-----BEGIN PRIVATE KEY-----") || keyPem.contains("-----BEGIN RSA PRIVATE KEY-----"))

        val loadedCa = CertificateAuthority.loadFromPemStrings(certPem, keyPem)
        assertEquals(ca.certificate.serialNumber, loadedCa.certificate.serialNumber)
        assertEquals(ca.certificate.publicKey, loadedCa.certificate.publicKey)
        assertEquals(ca.certificate.subjectX500Principal, loadedCa.certificate.subjectX500Principal)
    }

    @Test
    fun testLeafGeneration() {
        val ca = CertificateAuthority.generate(
            commonName = "Signer CA",
            org = "Signer Org",
            validityDays = 15
        )

        val domain = "api.github.com"
        val leaf = LeafCertificateGenerator.generate(domain, ca, validityDays = 5)

        assertNotNull(leaf.keyPair)
        assertNotNull(leaf.certificate)

        // Verify leaf is signed by CA
        leaf.certificate.verify(ca.certificate.publicKey)

        // Verify Subject CN contains the domain
        assertTrue(leaf.certificate.subjectX500Principal.name.contains("CN=$domain"))

        // Verify SAN (Subject Alternative Name) is present and correct
        val sanList = leaf.certificate.subjectAlternativeNames
        assertNotNull(sanList)
        assertTrue(sanList.isNotEmpty())

        var foundSan = false
        for (san in sanList) {
            // Index 0 contains the GeneralName type constant, Index 1 contains the value (String or byte[])
            val type = san[0] as Int
            val value = san[1] as String
            if (type == GeneralName.dNSName && value == domain) {
                foundSan = true
                break
            }
        }
        assertTrue(foundSan, "Subject Alternative Name (SAN) must contain DNS Name '$domain'")
    }

    @Test
    fun testCertificateCache() {
        val ca = CertificateAuthority.generate(
            commonName = "Cache CA",
            org = "Cache Org",
            validityDays = 5
        )

        val cache = CertificateCache()
        assertEquals(0, cache.size())

        val leaf1 = cache.get("google.com", ca)
        assertEquals(1, cache.size())

        // Fetch again, should return the cached instance
        val leaf2 = cache.get("google.com", ca)
        assertSame(leaf1, leaf2, "Cached instance should be returned on consecutive queries")
        assertEquals(1, cache.size())

        // Fetch a different domain, should generate a new one
        val leaf3 = cache.get("yahoo.com", ca)
        assertNotEquals(leaf1.certificate.serialNumber, leaf3.certificate.serialNumber)
        assertEquals(2, cache.size())

        // Clear cache
        cache.clear()
        assertEquals(0, cache.size())

        // Fetch google.com again, should be a new instance
        val leaf4 = cache.get("google.com", ca)
        assertNotEquals(leaf1.certificate.serialNumber, leaf4.certificate.serialNumber)
        assertEquals(1, cache.size())
    }
}
