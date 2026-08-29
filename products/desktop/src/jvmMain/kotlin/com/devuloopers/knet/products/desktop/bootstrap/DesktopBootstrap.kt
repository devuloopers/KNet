package com.devuloopers.knet.products.desktop.bootstrap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.devuloopers.knet.connectivity.desktop.DesktopConnectivityRuntime
import com.devuloopers.knet.connectivity.desktop.gateway.AuthenticatedProxyGateway
import com.devuloopers.knet.connectivity.desktop.gateway.CompanionControlGatewayRuntime
import com.devuloopers.knet.connectivity.desktop.discovery.CompanionDiscoveryPublisher
import com.devuloopers.knet.connectivity.desktop.portal.DedicatedSetupPortal
import com.devuloopers.knet.connectivity.desktop.wifi.DesktopWifiSharingRuntime
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.data.desktop.inspection.DesktopSemanticInspectionRuntime
import com.devuloopers.knet.data.desktop.rules.repository.RulesRepositoryImpl
import com.devuloopers.knet.data.desktop.proxy.repository.DesktopProxyRuntimeAdapter
import com.devuloopers.knet.domain.config.AppMetadata
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.di.DesktopModules
import com.devuloopers.knet.products.desktop.lifecycle.ApplicationLifecycle
import com.devuloopers.knet.products.desktop.lifecycle.ShutdownAware
import com.devuloopers.knet.products.desktop.runtime.ApplicationSettingsRuntimeSynchronizer
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.ui.core.foundation.resources.kNetLogoPainter
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode
import com.devuloopers.knet.ui.desktop.app.window.MainWindow
import com.devuloopers.knet.application.usecase.traffic.ClearTrafficHistoryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        startStartupPolicies(koin)
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
        ApplicationLifecycle.registerResource("database", object : ShutdownAware {
            override fun close() {
                database.close()
            }
        })

        val rulesRepository = koin.get<RulesRepositoryImpl>()
        ApplicationLifecycle.registerResource("rules-repository", object : ShutdownAware {
            override fun close() {
                rulesRepository.close()
            }
        })

        val proxyRuntime = koin.get<DesktopProxyRuntimeAdapter>()
        ApplicationLifecycle.registerResource("proxy-runtime", object : ShutdownAware {
            override fun close() {
                proxyRuntime.close()
            }
        })

        val inspectionRuntime = koin.get<DesktopSemanticInspectionRuntime>()
        ApplicationLifecycle.registerResource("inspection-runtime", object : ShutdownAware {
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
        ApplicationLifecycle.registerResource("setup-portal", object : ShutdownAware {
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
        ApplicationLifecycle.registerResource("authenticated-proxy-gateway", object : ShutdownAware {
            override fun close() {
                authenticatedGateway.close()
            }
        })

        val companionControlGateway = koin.get<CompanionControlGatewayRuntime>()
        runCatching(companionControlGateway::start).onFailure { failure ->
            KNetLogger.warn("DesktopBootstrap") {
                "Companion control gateway runtime is unavailable: ${failure::class.simpleName}"
            }
        }
        ApplicationLifecycle.registerResource("companion-control-gateway", object : ShutdownAware {
            override fun close() {
                companionControlGateway.close()
            }
        })

        val companionDiscovery = koin.get<CompanionDiscoveryPublisher>()
        runCatching(companionDiscovery::start).onFailure { failure ->
            KNetLogger.warn("DesktopBootstrap") {
                "Companion discovery advertisement is unavailable: ${failure::class.simpleName}"
            }
        }
        ApplicationLifecycle.registerResource("companion-discovery", object : ShutdownAware {
            override fun close() {
                companionDiscovery.close()
            }
        })

        val connectivityRuntime = koin.get<DesktopConnectivityRuntime>()
        ApplicationLifecycle.registerResource("connectivity-runtime", object : ShutdownAware {
            override fun close() {
                connectivityRuntime.close()
            }
        })

        val wifiSharingRuntime = koin.get<DesktopWifiSharingRuntime>()
        ApplicationLifecycle.registerResource("wifi-sharing-runtime", object : ShutdownAware {
            override fun close() {
                wifiSharingRuntime.close()
            }
        })
    }

    /** Starts process-owned policies once, independently from whether a feature screen is composed. */
    private fun startStartupPolicies(koin: Koin) {
        val policyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ApplicationLifecycle.registerResource("startup-policy-scope", object : ShutdownAware {
            override fun close() {
                policyScope.cancel()
            }
        })
        koin.get<ApplicationSettingsRuntimeSynchronizer>().start(policyScope)
        policyScope.launch {
            try {
                val settings = koin.get<ObserveApplicationSettingsUseCase>().execute().first()
                if (settings.autoClearTrafficOnStartup) {
                    koin.get<ClearTrafficHistoryUseCase>().execute()
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                KNetLogger.error("DesktopBootstrap", failure) {
                    "Startup traffic-history policy failed."
                }
            }
        }
    }

    private fun launchDesktopApplication() {
        application {
            val windowState = rememberWindowState()
            Window(
                onCloseRequest = {
                    ApplicationLifecycle.shutdownAsync()
                    exitApplication()
                },
                state = windowState,
                title = AppMetadata.APP_DISPLAY_TITLE,
                icon = kNetLogoPainter()
            ) {
                KNetTheme(themeMode = ThemeMode.System) {
                    WindowBackgroundFlashingWorkaround(themeBackground = KNetTheme.colors.background)
                    MainWindow()
                }
            }
        }
    }

    /**
     * Synchronizes the native AWT window and contentPane background colors with the active Compose
     * [themeBackground] to eliminate white flashes and unpainted areas during live window resizing.
     *
     * @param themeBackground The active theme background color.
     */
    @Composable
    private fun FrameWindowScope.WindowBackgroundFlashingWorkaround(themeBackground: Color) {
        val awtColor = remember(themeBackground) { java.awt.Color(themeBackground.toArgb()) }
        LaunchedEffect(window, awtColor) {
            window.background = awtColor
            window.contentPane.background = awtColor
        }
    }
}
