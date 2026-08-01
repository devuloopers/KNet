package com.devuloopers.knet.ui.desktop.inspector.di

import com.devuloopers.knet.ui.desktop.inspector.viewmodel.InspectorViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:inspector`.
 */
public val inspectorUiModule = module {
    viewModel { InspectorViewModel() }
}
