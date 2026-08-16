package com.devuloopers.knet.apps.desktop.di

import com.devuloopers.knet.data.desktop.di.DesktopDataModule
import com.devuloopers.knet.ui.desktop.app.di.desktopAppUiModule
import com.devuloopers.knet.ui.desktop.breakpointmanager.di.breakpointManagerUiModule
import com.devuloopers.knet.ui.desktop.httppanel.di.httpPanelModule
import org.koin.core.module.Module

/**
 * Layered Desktop DI Composition Root module registry.
 *
 * Organizes application Koin modules cleanly by architectural layer.
 */
object DesktopModules {

    val core: List<Module> = emptyList()

    val storage: List<Module> = emptyList()

    val data: List<Module> = DesktopDataModule.all

    val engine: List<Module> = emptyList()

    val ui: List<Module> = listOf(desktopAppUiModule, httpPanelModule, breakpointManagerUiModule)

    /**
     * Complete aggregated Koin module list for Desktop application assembly.
     */
    val all: List<Module> = core + storage + data + engine + ui
}
