package com.devuloopers.knet.ui.workspace.di

import com.devuloopers.knet.ui.workspace.viewmodel.WorkspaceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val workspaceUiModule = module {
    viewModel { WorkspaceViewModel(get(), get()) }
}
