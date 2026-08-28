package com.devuloopers.knet.companion.connectivity.transport

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import kotlin.time.Duration.Companion.days
import kotlin.time.Clock

internal data class TestCertificateIdentity(
    val rootCertificate: X509Certificate,
    val leafCertificate: X509Certificate,
    val leafPrivateKey: PrivateKey,
)

internal fun testCertificateIdentity(hostName: String): TestCertificateIdentity {
    val random = SecureRandom()
    val rootKeys = generateRsaKeyPair()
    val leafKeys = generateRsaKeyPair()
    val now = Clock.System.now()
    val rootName = X500Name("CN=KNet Test Root,O=KNet Tests,C=US")
    val rootCertificate = JcaX509v3CertificateBuilder(
        rootName,
        BigInteger(160, random),
        Date((now - 1.days).toEpochMilliseconds()),
        Date((now + 365.days).toEpochMilliseconds()),
        rootName,
        rootKeys.public,
    ).apply {
        addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
    }.toCertificate(rootKeys.private)
    val leafCertificate = JcaX509v3CertificateBuilder(
        rootName,
        BigInteger(160, random),
        Date((now - 1.days).toEpochMilliseconds()),
        Date((now + 365.days).toEpochMilliseconds()),
        X500Name("CN=$hostName,O=KNet Tests,C=US"),
        leafKeys.public,
    ).apply {
        addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
        )
        addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth),
        )
        addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(GeneralName(GeneralName.dNSName, hostName)),
        )
    }.toCertificate(rootKeys.private)
    return TestCertificateIdentity(rootCertificate, leafCertificate, leafKeys.private)
}

private fun JcaX509v3CertificateBuilder.toCertificate(signingKey: PrivateKey): X509Certificate =
    JcaX509CertificateConverter().getCertificate(
        build(JcaContentSignerBuilder("SHA256withRSA").build(signingKey)),
    )

private fun generateRsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply {
    initialize(2_048)
}.generateKeyPair()
