package com.devuloopers.knet.ui.desktop.app.navigation

/**
 * Sealed interface representing all primary desktop navigation destinations in the application.
 */
sealed interface DesktopDestination {

    /**
     * Live Traffic Explorer screen hosting real-time transaction tables and filters.
     */
    data object Traffic : DesktopDestination

    /**
     * Stock-phone onboarding, approval, and active Wi-Fi access management.
     */
    data object ConnectDevice : DesktopDestination

    /**
     * HTTP Transaction Inspector panel for header and payload details.
     */
    data object Inspector : DesktopDestination

    /**
     * API request authoring studio client.
     */
    data object ApiStudio : DesktopDestination

    /**
     * PKI root certificates and CA trust manager dashboard.
     */
    data object Certificate : DesktopDestination

    /**
     * Breakpoint interception rules and in-flight traffic manager.
     */
    data object Breakpoints : DesktopDestination

    /**
     * General application proxy defaults configuration screen.
     */
    data object Settings : DesktopDestination
}
