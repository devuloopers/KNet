package com.devuloopers.knet.engine.certificate

/** Engine-owned Root CA metadata, free of UI/application-layer types. */
data class CertificateAuthorityDetails(
    val status: CertificateAuthorityStatus,
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val signatureAlgorithm: String,
    val validFrom: String,
    val validUntil: String,
    val sha1Fingerprint: String,
    val sha256Fingerprint: String,
)

enum class CertificateAuthorityStatus {
    AVAILABLE,
    EXPIRED,
    INVALID,
}
