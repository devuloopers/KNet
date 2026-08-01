package com.devuloopers.knet.ui.desktop.apistudio.di

import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:apistudio`.
 */
public val apiStudioUiModule = module {
    viewModel { ApiStudioViewModel(get()) }
}
