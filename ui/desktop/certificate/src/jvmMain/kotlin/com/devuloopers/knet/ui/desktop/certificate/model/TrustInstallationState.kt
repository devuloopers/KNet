package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Trust installation progress status enum.
 */
enum class TrustInstallationState {
    CHECKING,
    IDLE,
    INSTALLING,
    INSTALLED,
    MANUAL_ACTION_REQUIRED,
    FAILED
}
