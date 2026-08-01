package com.devuloopers.knet.apps.desktop.bootstrap

import androidx.compose.ui.window.application
import com.devuloopers.knet.apps.desktop.config.DesktopConfiguration
import com.devuloopers.knet.apps.desktop.lifecycle.ApplicationLifecycle
import com.devuloopers.knet.ui.desktop.app.window.MainWindow

/**
 * Desktop Application Bootstrap Orchestrator.
 *
 * Sequentially executes priority-ordered initializers, installs JVM shutdown hooks,
 * and launches the Compose Desktop UI frame.
 */
object DesktopBootstrap {

    private val initializers: List<ApplicationInitializer> = listOf(
        LoggingInitializer,
        ExceptionHandlerInitializer,
        KoinInitializer
    )

    /**
     * Bootstraps the Desktop application.
     */
    fun start(configuration: DesktopConfiguration = DesktopConfiguration.load()) {
        initializers.sortedBy { it.priority }.forEach { initializer ->
            initializer.initialize(configuration)
        }

        ApplicationLifecycle.installShutdownHook()

        application {
            MainWindow()
        }
    }
}
