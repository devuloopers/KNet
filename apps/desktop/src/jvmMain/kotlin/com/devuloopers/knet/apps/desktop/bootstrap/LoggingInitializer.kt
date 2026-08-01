package com.devuloopers.knet.apps.desktop.bootstrap

import com.devuloopers.knet.apps.desktop.config.DesktopConfiguration
import com.devuloopers.knet.core.logger.KNetLogger

/**
 * Initializes the Kermit structured logging pipeline and log output formatting.
 */
object LoggingInitializer : ApplicationInitializer {

    override val priority: Int = 100

    override fun initialize(configuration: DesktopConfiguration) {
        KNetLogger.info(tag = "LoggingInitializer") {
            "Logging pipeline initialized for environment [${configuration.environment.name}] at [${configuration.logDirectory}]"
        }
    }
}
