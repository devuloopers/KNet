package com.devuloopers.knet.ui.inspector.di

import com.devuloopers.knet.ui.inspector.viewmodel.InspectorViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Feature-centric Koin DI module for the Inspector UI layer.
 */
val inspectorUiModule = module {
    viewModel { InspectorViewModel(get()) }
}
