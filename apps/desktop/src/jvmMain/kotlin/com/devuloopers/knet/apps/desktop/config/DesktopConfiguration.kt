package com.devuloopers.knet.apps.desktop.config

import java.nio.file.Path

/**
 * Centralized Desktop Application Configuration.
 *
 * Holds system paths, environment definitions, and feature toggles.
 * Constructed via [DesktopConfiguration.load].
 */
data class DesktopConfiguration(
    val environment: Environment = Environment.DEVELOPMENT,
    val appDirectory: Path = Path.of(System.getProperty("user.home"), ".knet"),
    val databaseDirectory: Path = appDirectory.resolve("database"),
    val logDirectory: Path = appDirectory.resolve("logs")
) {
    companion object {
        /**
         * Loads and constructs the global [DesktopConfiguration] instance.
         */
        fun load(): DesktopConfiguration = DesktopConfiguration()
    }
}
