package com.devuloopers.knet.engine.certificate.authority

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.util.assertIsRootCa
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CertificateAuthorityTest {

    @Test
    fun testDefaultRootCaGeneration() {
        val ca = CertificateAuthority.generate()
        assertIsRootCa(ca)
        assertTrue(ca.certificate.subjectX500Principal.name.contains("CN=${CertificateAuthority.DEFAULT_CA_CN}"))
        assertTrue(ca.certificate.subjectX500Principal.name.contains("O=${CertificateAuthority.DEFAULT_ORG}"))
    }

    @Test
    fun testCustomRootCaGeneration() {
        val customName = "Custom KNet CA"
        val customOrg = "Custom Org"
        val ca = CertificateAuthority.generate(commonName = customName, org = customOrg, validityDays = 60)

        assertIsRootCa(ca)
        assertTrue(ca.certificate.subjectX500Principal.name.contains("CN=$customName"))
        assertTrue(ca.certificate.subjectX500Principal.name.contains("O=$customOrg"))
    }

    @Test
    fun testPemSerializationRoundtrip() {
        val ca = CertificateAuthority.generate(commonName = "Serialize CA", org = "Serialize Org")

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
    fun testInvalidPemLoading() {
        assertFailsWith<IllegalArgumentException> {
            CertificateAuthority.loadFromPemStrings("INVALID_PEM_CERT", "INVALID_PEM_KEY")
        }
    }
}
