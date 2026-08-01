package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Presentation model representing imported Client certificates for mutual TLS.
 */
public data class ClientCertificate(
    val alias: String,
    val subject: String,
    val host: String,
    val expiration: String,
    val enabled: Boolean = true
)
