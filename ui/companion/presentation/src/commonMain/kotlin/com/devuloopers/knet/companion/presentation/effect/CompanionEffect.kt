package com.devuloopers.knet.companion.presentation.effect

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.model.CompanionDesktopId

/** One-shot native work containing portable values and no Android or Apple framework types. */
public sealed interface CompanionEffect {
    /** Requests the platform VPN authorization surface. */
    public data object RequestVpnConsent : CompanionEffect

    /** Hands a public root certificate to the platform-owned user-visible file exporter. */
    public data class ExportCertificate(
        public val desktopId: CompanionDesktopId,
        public val artifact: CompanionCertificateArtifact,
    ) : CompanionEffect

    /** Opens platform guidance for enabling trust after installation. */
    public data object OpenCertificateTrustSettings : CompanionEffect
}
