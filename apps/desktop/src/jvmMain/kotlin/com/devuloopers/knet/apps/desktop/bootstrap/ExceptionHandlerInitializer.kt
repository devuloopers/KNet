package com.devuloopers.knet.apps.desktop.bootstrap

import com.devuloopers.knet.apps.desktop.config.DesktopConfiguration
import com.devuloopers.knet.core.logger.KNetLogger

/**
 * Installs global JVM default uncaught exception handling for the application process.
 */
public object ExceptionHandlerInitializer : ApplicationInitializer {

    override val priority: Int = 200

    override fun initialize(configuration: DesktopConfiguration) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            KNetLogger.error(tag = "ExceptionHandlerInitializer", throwable = throwable) {
                "Uncaught exception on thread '${thread.name}': ${throwable.message}"
            }
        }
        KNetLogger.info(tag = "ExceptionHandlerInitializer") {
            "JVM default uncaught exception handler successfully installed."
        }
    }
}
