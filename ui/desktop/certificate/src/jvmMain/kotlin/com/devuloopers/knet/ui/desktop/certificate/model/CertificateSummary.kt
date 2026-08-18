package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Lightweight summary model representing certificate lines in tables and views.
 */
data class CertificateSummary(
    val alias: String,
    val subject: String,
    val expiration: String,
    val type: String,
    val status: String
)
