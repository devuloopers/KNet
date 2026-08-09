package com.devuloopers.knet.ui.desktop.traffic.di

import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:traffic`.
 */
public val trafficModule = module {
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
