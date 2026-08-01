package com.devuloopers.knet.apps.desktop.bootstrap

import com.devuloopers.knet.apps.desktop.config.DesktopConfiguration
import com.devuloopers.knet.apps.desktop.di.DesktopModules
import com.devuloopers.knet.core.logger.KNetLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Initializes the Koin Dependency Injection container with all Desktop application modules.
 */
object KoinInitializer : ApplicationInitializer {

    override val priority: Int = 300

    override fun initialize(configuration: DesktopConfiguration) {
        val configModule = module {
            single { configuration }
        }

        startKoin {
            modules(listOf(configModule) + DesktopModules.all)
        }

        KNetLogger.info(tag = "KoinInitializer") {
            "Koin DI container initialized with ${DesktopModules.all.size + 1} modules."
        }
    }
}
