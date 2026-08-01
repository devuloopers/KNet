package com.devuloopers.knet.ui.desktop.scripting.di

import com.devuloopers.knet.ui.desktop.scripting.viewmodel.ScriptingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:scripting`.
 */
public val scriptingUiModule = module {
    viewModel { ScriptingViewModel() }
}
