package com.devuloopers.knet.engine.certificate.security

import com.devuloopers.knet.engine.certificate.LeafCertificateGenerator
import com.devuloopers.knet.engine.certificate.util.TestCertificateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CertificateSecurityTest {

    private val ca = TestCertificateFactory.createTestCa()

    @Test
    fun testRootCaConstraints() {
        val rootCert = ca.certificate
        assertTrue(rootCert.basicConstraints >= 0, "Root CA must have isCA = true")
        assertTrue(rootCert.keyUsage[5], "Root CA must have keyCertSign keyUsage")
        assertTrue(rootCert.keyUsage[6], "Root CA must have cRLSign keyUsage")
    }

    @Test
    fun testLeafCertificateSecurityConstraints() {
        val leaf = LeafCertificateGenerator.generate("secure.internal", ca)
        val leafCert = leaf.certificate

        assertEquals(-1, leafCert.basicConstraints, "Leaf certificate must NOT have isCA = true")
        assertTrue(leafCert.keyUsage[0], "Leaf must have digitalSignature keyUsage")
        assertTrue(leafCert.keyUsage[2], "Leaf must have keyEncipherment keyUsage")
        assertFalse(leafCert.keyUsage[5], "Leaf MUST NOT have keyCertSign keyUsage")
    }

    @Test
    fun testUniqueSerialNumbers() {
        val leaf1 = LeafCertificateGenerator.generate("domain1.com", ca)
        val leaf2 = LeafCertificateGenerator.generate("domain1.com", ca)
        assertNotEquals(leaf1.certificate.serialNumber, leaf2.certificate.serialNumber, "Leaf certificates must have unique serial numbers")
    }
}
