package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Presentation model representing imported Client certificates for mutual TLS.
 */
public data class ClientCertificate(
    val alias: String,
    val subject: String,
    val host: String,
    val expiration: String,
    val enabled: Boolean = true,
    val format: CertificateFormat = CertificateFormat.PKCS12,
    val daysUntilExpiration: Int = 365,
    val subjectDn: String = "",
    val issuerDn: String = "",
    val serialNumber: String = "",
    val sanList: List<String> = emptyList(),
    val publicKeyAlgorithm: String = "RSA 2048-bit",
    val sha256Fingerprint: String = ""
)
