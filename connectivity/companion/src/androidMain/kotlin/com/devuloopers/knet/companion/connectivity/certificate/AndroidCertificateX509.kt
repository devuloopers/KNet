package com.devuloopers.knet.companion.connectivity.certificate

import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal fun ByteArray.parseX509Certificate(): X509Certificate? = runCatching {
    CertificateFactory.getInstance("X.509").generateCertificate(inputStream()) as X509Certificate
}.getOrNull()

internal fun X509Certificate.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { byte -> "%02x".format(byte) }

internal fun X509Certificate.isValidPairingRoot(expectedSha256: String): Boolean = runCatching {
    isValidRootCertificate() && sha256Hex() == expectedSha256
}.getOrDefault(false)

/** Returns the exact valid QR-pinned CA certificate or null without creating a trust manager. */
internal fun ByteArray.validatedBootstrapRoot(expectedSha256: String): X509Certificate? =
    parseX509Certificate()?.takeIf { certificate -> certificate.isValidPairingRoot(expectedSha256) }

internal fun X509Certificate.isValidRootCertificate(): Boolean = runCatching {
    checkValidity()
    verify(publicKey)
    basicConstraints >= 0
}.getOrDefault(false)

internal fun List<X509Certificate>.matchesPinnedTransportIdentity(expectedSha256: String): Boolean {
    val leaf = firstOrNull() ?: return false
    val pinned = firstOrNull { it.sha256Hex() == expectedSha256 } ?: return false
    return leaf.sha256Hex() == pinned.sha256Hex() || leaf.isDirectlyIssuedBy(pinned)
}

internal fun List<X509Certificate>.isServedByRoot(root: X509Certificate): Boolean =
    firstOrNull()?.isDirectlyIssuedBy(root) == true

private fun X509Certificate.isDirectlyIssuedBy(issuer: X509Certificate): Boolean = runCatching {
    check(sha256Hex() != issuer.sha256Hex())
    verify(issuer.publicKey)
    issuerX500Principal == issuer.subjectX500Principal
}.getOrDefault(false)
