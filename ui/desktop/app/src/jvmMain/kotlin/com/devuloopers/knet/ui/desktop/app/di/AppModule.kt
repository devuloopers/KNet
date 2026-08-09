package com.devuloopers.knet.ui.desktop.app.di

import com.devuloopers.knet.ui.desktop.apistudio.di.apiStudioUiModule
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module aggregating desktop application UI framework dependencies.
 * Uses the recommended Koin viewModel DSL to scope TrafficViewModel to the ViewModelStoreOwner lifecycle.
 * Includes [apiStudioUiModule] which provides [ApiStudioViewModel] and its required UseCases.
 */
val desktopAppUiModule = module {
    includes(apiStudioUiModule)
    viewModel {
        TrafficViewModel(
            getLiveTrafficUseCase = get(),
            clearLiveTrafficUseCase = get(),
            startProxyEngineUseCase = get(),
            stopProxyEngineUseCase = get(),
            observeProxyEngineStateUseCase = get(),
            loadTransactionBodyUseCase = get(),
            observeLocalIpUseCase = get()
        )
    }
}
