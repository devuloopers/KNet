package com.devuloopers.knet.data.desktop.certificate

/** Desktop operating-system trust boundary used by the application certificate adapter. */
interface DesktopRootTrustController {
    fun installRootCertificate(): InstallationResult

    fun isRootCertificateTrusted(): Boolean
}
