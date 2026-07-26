package com.devuloopers.knet.ui.rules.di

import com.devuloopers.knet.ui.rules.viewmodel.RulesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Feature-centric Koin DI module for the Rules UI layer.
 */
val rulesUiModule = module {
    viewModel { RulesViewModel(get(), get(), get()) }
}
