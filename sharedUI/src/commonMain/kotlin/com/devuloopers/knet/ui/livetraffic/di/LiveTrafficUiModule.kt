package com.devuloopers.knet.ui.livetraffic.di

import com.devuloopers.knet.ui.livetraffic.viewmodel.LiveTrafficViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Feature-centric Koin DI module for the Live Traffic UI layer.
 */
val liveTrafficUiModule = module {
    viewModel { LiveTrafficViewModel(get(), get()) }
}
