package com.devuloopers.knet.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Strongly-typed destination routes for KNet top-level application navigation.
 */
@Serializable
sealed interface Screen {
    /** Main active traffic capture workspace & inspector. */
    @Serializable
    data object LiveTraffic : Screen

    /** Session history archives, metrics, and HAR export/import screen. */
    @Serializable
    data object Sessions : Screen

    /** Saved API requests and Postman/Insomnia collection manager. */
    @Serializable
    data object Collections : Screen

    /** Global breakpoint, rewrite, and mock interception rules console. */
    @Serializable
    data object Rules : Screen

    /** Root CA installer, SSL pinning config, and client certificate manager. */
    @Serializable
    data object Certificates : Screen

    /** Application preferences, proxy port config, and theme settings. */
    @Serializable
    data object Settings : Screen
}
