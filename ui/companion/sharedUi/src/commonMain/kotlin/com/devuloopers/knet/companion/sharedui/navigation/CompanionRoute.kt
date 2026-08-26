package com.devuloopers.knet.companion.sharedui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Serializable root destinations supported by the companion setup and readiness flow. */
@Serializable
internal sealed interface CompanionRoute : NavKey {
    /** Invitation entry and existing-desktop selection. */
    @Serializable
    data object ConnectDesktop : CompanionRoute

    /** In-app camera scanner for a desktop invitation QR code. */
    @Serializable
    data object ScanInvitation : CompanionRoute

    /** Confirmation of a validated, still in-memory invitation. */
    @Serializable
    data object ConfirmDesktop : CompanionRoute

    /** Certificate installation and authoritative trust verification. */
    @Serializable
    data object CertificateSetup : CompanionRoute

    /** Explanation shown before requesting the native VPN authorization surface. */
    @Serializable
    data object InspectionPermission : CompanionRoute

    /** Paired connection and inspection controls. */
    @Serializable
    data object Home : CompanionRoute
}
