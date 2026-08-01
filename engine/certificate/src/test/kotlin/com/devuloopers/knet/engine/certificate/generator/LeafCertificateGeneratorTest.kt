package com.devuloopers.knet.engine.certificate.generator

import com.devuloopers.knet.engine.certificate.LeafCertificateGenerator
import com.devuloopers.knet.engine.certificate.util.TestCertificateFactory
import com.devuloopers.knet.engine.certificate.util.assertIsLeafCertificate
import org.bouncycastle.asn1.x509.GeneralName
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LeafCertificateGeneratorTest {

    private val ca = TestCertificateFactory.createTestCa()

    @Test
    fun testStandardHostnameLeafGeneration() {
        val domain = "api.github.com"
        val leaf = LeafCertificateGenerator.generate(domain, ca)
        assertIsLeafCertificate(leaf, domain, ca)
    }

    @Test
    fun testWildcardHostnameLeafGeneration() {
        val wildcard = "*.google.com"
        val leaf = LeafCertificateGenerator.generate(wildcard, ca)
        assertIsLeafCertificate(leaf, wildcard, ca)
    }

    @Test
    fun testIpAddressLeafGeneration() {
        val ipv4 = "192.168.1.1"
        val leaf = LeafCertificateGenerator.generate(ipv4, ca)

        assertNotNull(leaf.certificate)
        leaf.certificate.verify(ca.certificate.publicKey)

        val sanList = leaf.certificate.subjectAlternativeNames
        assertNotNull(sanList)
        assertTrue(sanList.isNotEmpty())

        var foundIpSan = false
        for (san in sanList) {
            val type = san[0] as Int
            val value = san[1] as String
            if (type == GeneralName.iPAddress && value == ipv4) {
                foundIpSan = true
                break
            }
        }
        assertTrue(foundIpSan, "IP Address SAN must match '$ipv4'")
    }
}
