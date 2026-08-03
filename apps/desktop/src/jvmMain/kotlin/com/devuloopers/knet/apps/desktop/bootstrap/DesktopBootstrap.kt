package com.devuloopers.knet.apps.desktop.bootstrap

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.devuloopers.knet.apps.desktop.config.DesktopConfiguration
import com.devuloopers.knet.apps.desktop.di.DesktopModules
import com.devuloopers.knet.apps.desktop.lifecycle.ApplicationLifecycle
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.ui.desktop.app.window.MainWindow
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Desktop Application Bootstrap Orchestrator.
 *
 * Sequentially executes explicit configuration loading, logging startup, global exception handling,
 * Koin dependency injection container creation, JVM shutdown hook registration, and Compose Desktop UI launch.
 */
object DesktopBootstrap {

    /**
     * Bootstraps the Desktop application with the specified [configuration].
     */
    fun start(configuration: DesktopConfiguration = DesktopConfiguration.load()) {
        configureLogging(configuration)
        installExceptionHandler()
        startDependencyInjection(configuration)
        ApplicationLifecycle.installShutdownHook()
        launchDesktopApplication()
    }

    private fun configureLogging(configuration: DesktopConfiguration) {
        KNetLogger.info(tag = "DesktopBootstrap") {
            "Logging pipeline initialized for environment [${configuration.environment.name}] at [${configuration.logDirectory}]"
        }
    }

    private fun installExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            KNetLogger.error(tag = "DesktopBootstrap", throwable = throwable) {
                "Uncaught process exception on thread '${thread.name}': ${throwable.message}"
            }
        }
    }

    private fun startDependencyInjection(configuration: DesktopConfiguration) {
        val configModule = module {
            single { configuration }
        }
        startKoin {
            modules(listOf(configModule) + DesktopModules.all)
        }
        KNetLogger.info(tag = "DesktopBootstrap") {
            "Koin DI container initialized with ${DesktopModules.all.size + 1} modules."
        }
    }

    private fun launchDesktopApplication() {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "KNet Network Inspector & API Studio"
            ) {
                MainWindow()
            }
        }
    }
}
