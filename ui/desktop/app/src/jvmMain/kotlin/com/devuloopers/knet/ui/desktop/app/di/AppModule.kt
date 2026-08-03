package com.devuloopers.knet.ui.desktop.app.di

import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module aggregating desktop application UI framework dependencies.
 * Uses the recommended Koin viewModel DSL to scope TrafficViewModel to the ViewModelStoreOwner lifecycle.
 */
public val desktopAppUiModule = module {
    viewModel {
        TrafficViewModel(
            getLiveTrafficUseCase = get(),
            clearLiveTrafficUseCase = get(),
            startProxyEngineUseCase = get(),
            stopProxyEngineUseCase = get(),
            observeProxyEngineStateUseCase = get(),
            loadTransactionBodyUseCase = get()
        )
    }
}
