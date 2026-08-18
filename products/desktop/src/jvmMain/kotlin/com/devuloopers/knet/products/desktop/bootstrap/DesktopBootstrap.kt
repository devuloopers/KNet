package com.devuloopers.knet.products.desktop.bootstrap

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.devuloopers.knet.connectivity.desktop.DesktopConnectivityRuntime
import com.devuloopers.knet.connectivity.desktop.gateway.AuthenticatedProxyGateway
import com.devuloopers.knet.connectivity.desktop.portal.DedicatedSetupPortal
import com.devuloopers.knet.connectivity.desktop.wifi.DesktopWifiSharingRuntime
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.data.desktop.inspection.DesktopSemanticInspectionRuntime
import com.devuloopers.knet.data.desktop.proxy.repository.DesktopProxyRuntimeAdapter
import com.devuloopers.knet.domain.config.AppMetadata
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.di.DesktopModules
import com.devuloopers.knet.products.desktop.lifecycle.ApplicationLifecycle
import com.devuloopers.knet.products.desktop.lifecycle.ShutdownAware
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.ui.core.foundation.resources.kNetLogoPainter
import com.devuloopers.knet.ui.desktop.app.window.MainWindow
import org.koin.core.Koin
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
        val koin = startDependencyInjection(configuration)
        registerLifecycleResources(koin)
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

    private fun startDependencyInjection(configuration: DesktopConfiguration): Koin {
        val configModule = module {
            single { configuration }
        }
        val koinApplication = startKoin {
            modules(listOf(configModule) + DesktopModules.all)
        }
        KNetLogger.info(tag = "DesktopBootstrap") {
            "Koin DI container initialized with ${DesktopModules.all.size + 1} modules."
        }
        return koinApplication.koin
    }

    /** Registers process resources in dependency order so reverse shutdown closes the proxy before storage. */
    private fun registerLifecycleResources(koin: Koin) {
        val database = koin.get<KNetDatabase>()
        ApplicationLifecycle.registerResource(object : ShutdownAware {
            override fun close() {
                database.close()
            }
        })

        val proxyRuntime = koin.get<DesktopProxyRuntimeAdapter>()
        ApplicationLifecycle.registerResource(object : ShutdownAware {
            override fun close() {
                proxyRuntime.close()
            }
        })

        val inspectionRuntime = koin.get<DesktopSemanticInspectionRuntime>()
        ApplicationLifecycle.registerResource(object : ShutdownAware {
            override fun close() {
                inspectionRuntime.close()
            }
        })

        val setupPortal = koin.get<DedicatedSetupPortal>()
        runCatching(setupPortal::start).onFailure { failure ->
            KNetLogger.warn("DesktopBootstrap") {
                "Dedicated setup portal is unavailable: ${failure::class.simpleName}"
            }
        }
        ApplicationLifecycle.registerResource(object : ShutdownAware {
            override fun close() {
                setupPortal.close()
            }
        })

        val authenticatedGateway = koin.get<AuthenticatedProxyGateway>()
        runCatching(authenticatedGateway::start).onFailure { failure ->
            KNetLogger.warn("DesktopBootstrap") {
                "Authenticated companion gateway is unavailable: ${failure::class.simpleName}"
            }
        }
        ApplicationLifecycle.registerResource(object : ShutdownAware {
            override fun close() {
                authenticatedGateway.close()
            }
        })

        val connectivityRuntime = koin.get<DesktopConnectivityRuntime>()
        ApplicationLifecycle.registerResource(object : ShutdownAware {
            override fun close() {
                connectivityRuntime.close()
            }
        })

        val wifiSharingRuntime = koin.get<DesktopWifiSharingRuntime>()
        ApplicationLifecycle.registerResource(object : ShutdownAware {
            override fun close() {
                wifiSharingRuntime.close()
            }
        })
    }

    private fun launchDesktopApplication() {
        application {
            Window(
                onCloseRequest = {
                    ApplicationLifecycle.shutdown()
                    exitApplication()
                },
                title = AppMetadata.APP_DISPLAY_TITLE,
                icon = kNetLogoPainter()
            ) {
                MainWindow()
            }
        }
    }
}
