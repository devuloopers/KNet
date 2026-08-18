package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Represents the current status of the KNet Root Certificate Authority (CA).
 */
enum class CaStatus {
    AVAILABLE,
    MISSING,
    EXPIRED,
    INVALID,
    INSTALLATION_REQUIRED
}
