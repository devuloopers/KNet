package com.devuloopers.knet.companion.sharedui

import com.devuloopers.knet.companion.model.*
import com.devuloopers.knet.companion.presentation.state.*
import com.devuloopers.knet.companion.sharedui.screen.certificate.*
import com.devuloopers.knet.website.*
import kotlin.test.Test

/** The actual Companion application root, never an Android installation, camera or VPN service. */
class WebsiteCompanionArtworkTest {
    /** Pairing and connected screenshots share the production mobile theme, resources and navigation. */
    @Test fun renderCompanion() {
        val registration = certificateTestRegistration().copy(desktopDisplayName = CompanionDesktopDisplayName("MacBook Pro"))
        val connected = CompanionUiState(registrations = listOf(registration), activeRegistration = registration,
            certificate = CompanionCertificateState.Trusted(registration.rootCertificateSha256, 1_000L),
            certificateEnrollment = CompanionCertificateEnrollment(registration.desktopId, registration.rootCertificateSha256, 1_000L),
            desktopAvailability = CompanionDesktopAvailability.Available(registration.desktopId, 1_000L),
            network = CompanionNetworkState.Available(false),
            inspection = CompanionInspectionState.Running(CompanionInspectionMode.DEVICE_VPN, 1_000L, true))
        for ((scene, state) in listOf(ArtworkScene.COMPANION_PAIRING to CompanionUiState(network = CompanionNetworkState.Available(false)),
            ArtworkScene.COMPANION_CONNECTED to connected)) {
            renderArtwork(scene, listOf(if (scene == ArtworkScene.COMPANION_PAIRING) "Connect your desktop" else "MacBook Pro")) {
                KNetCompanionApp(state = state, onAction = {}, onExitRequested = {},
                    certificateInstallationGuidance = CertificateInstallationGuidance(platform = CertificateInstallationPlatform.ANDROID,
                        steps = listOf("Install the KNet certificate, then return to Companion.")))
            }
        }
    }
}
