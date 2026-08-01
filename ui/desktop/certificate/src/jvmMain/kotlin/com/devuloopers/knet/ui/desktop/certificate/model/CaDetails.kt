package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Presentation model for detailed X.509 Certificate Authority attributes.
 */
public data class CaDetails(
    val subject: String = "",
    val issuer: String = "",
    val serialNumber: String = "",
    val signatureAlgorithm: String = "",
    val validFrom: String = "",
    val validUntil: String = "",
    val sha1Fingerprint: String = "",
    val sha256Fingerprint: String = ""
)
