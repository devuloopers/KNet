package com.devuloopers.knet.engine.certificate.util

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.LeafCertificate
import org.bouncycastle.asn1.x509.GeneralName
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reusable domain assertions for certificate testing.
 */
fun assertIsRootCa(ca: CertificateAuthority) {
    assertNotNull(ca.privateKey, "Root CA private key must not be null")
    assertNotNull(ca.certificate, "Root CA certificate must not be null")
    ca.certificate.verify(ca.certificate.publicKey)
    assertEquals(ca.certificate.issuerX500Principal, ca.certificate.subjectX500Principal)
    assertTrue(ca.certificate.basicConstraints >= 0, "Basic constraints must indicate this is a CA")
}

fun assertIsLeafCertificate(leaf: LeafCertificate, expectedHostname: String, ca: CertificateAuthority) {
    assertNotNull(leaf.keyPair, "Leaf key pair must not be null")
    assertNotNull(leaf.certificate, "Leaf certificate must not be null")
    leaf.certificate.verify(ca.certificate.publicKey)
    assertTrue(leaf.certificate.subjectX500Principal.name.contains("CN=$expectedHostname"))
    assertTrue(leaf.certificate.basicConstraints == -1, "Leaf certificate must NOT be a CA")

    val sanList = leaf.certificate.subjectAlternativeNames
    assertNotNull(sanList, "Subject Alternative Names (SAN) list must not be null")
    assertTrue(sanList.isNotEmpty(), "SAN list must not be empty")
}
