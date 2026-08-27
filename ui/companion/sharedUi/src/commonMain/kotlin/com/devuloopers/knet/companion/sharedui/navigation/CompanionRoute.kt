package com.devuloopers.knet.companion.sharedui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Serializable root destinations supported by the companion setup and readiness flow. */
@Serializable
internal sealed interface CompanionRoute : NavKey {
    /** Modern QR-only desktop connection flow with its inline scanner. */
    @Serializable
    data object ConnectDesktop : CompanionRoute

    /** Certificate installation and authoritative trust verification. */
    @Serializable
    data object CertificateSetup : CompanionRoute

    /** Operational home shown only after the user explicitly continues beyond verified setup. */
    @Serializable
    data object InspectionHome : CompanionRoute
}
