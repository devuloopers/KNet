package com.devuloopers.knet.ui.desktop.app.navigation

/**
 * Sealed interface representing all primary desktop navigation destinations in the application.
 */
public sealed interface DesktopDestination {

    /**
     * Live Traffic Explorer screen hosting real-time transaction tables and filters.
     */
    public data object Traffic : DesktopDestination

    /**
     * HTTP Transaction Inspector panel for header and payload details.
     */
    public data object Inspector : DesktopDestination

    /**
     * API request authoring studio client.
     */
    public data object ApiStudio : DesktopDestination

    /**
     * PKI root certificates and CA trust manager dashboard.
     */
    public data object Certificate : DesktopDestination

    /**
     * Breakpoint interception rules and in-flight traffic manager.
     */
    public data object Breakpoints : DesktopDestination

    /**
     * General application proxy defaults configuration screen.
     */
    public data object Settings : DesktopDestination
}
