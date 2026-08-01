package com.devuloopers.knet.apps.desktop.di

import org.koin.core.module.Module

/**
 * Desktop Application composition root module registry.
 * Exports all Koin DI modules required by the desktop application.
 */
val desktopKoinModules: List<Module> = DesktopModules.all
