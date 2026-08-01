package com.devuloopers.knet.ui.desktop.workspace.di

import com.devuloopers.knet.ui.desktop.workspace.viewmodel.WorkspaceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:workspace`.
 */
val workspaceUiModule = module {
    viewModel { WorkspaceViewModel(get(), get()) }
}
