package com.devuloopers.knet.ui.desktop.breakpointmanager.di

import com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:breakpointManager`.
 * ViewModels inject domain UseCases provided by `:data:desktop` / `:core:domain`.
 */
public val breakpointManagerUiModule = module {
    viewModelOf(::BreakpointManagerViewModel)
}
