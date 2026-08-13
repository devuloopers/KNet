package com.devuloopers.knet.apps.desktop.di

import com.devuloopers.knet.data.desktop.di.DesktopDataModule
import com.devuloopers.knet.ui.desktop.app.di.desktopAppUiModule
import com.devuloopers.knet.ui.desktop.httppanel.di.httpPanelModule
import org.koin.core.module.Module

/**
 * Layered Desktop DI Composition Root module registry.
 *
 * Organizes application Koin modules cleanly by architectural layer.
 */
public object DesktopModules {

    public val core: List<Module> = emptyList()

    public val storage: List<Module> = emptyList()

    public val data: List<Module> = DesktopDataModule.all

    public val engine: List<Module> = emptyList()

    public val ui: List<Module> = listOf(desktopAppUiModule, httpPanelModule)

    /**
     * Complete aggregated Koin module list for Desktop application assembly.
     */
    public val all: List<Module> = core + storage + data + engine + ui
}
