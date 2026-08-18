package com.devuloopers.knet.ui.desktop.settings.platform

/** Desktop shell operations consumed by settings presentation without exposing JVM APIs. */
interface SettingsPlatformActions {
    /** Absolute application data-directory path displayed by settings. */
    val dataDirectory: String

    /**
     * Creates and opens the application data directory in the host file browser.
     *
     * @return `true` when the host accepted the open request.
     */
    suspend fun openDataDirectory(): Boolean
}
