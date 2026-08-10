package com.devuloopers.knet.ui.desktop.app.di

import com.devuloopers.knet.ui.desktop.apistudio.di.apiStudioUiModule
import com.devuloopers.knet.ui.desktop.certificate.di.certificateUiModule
import com.devuloopers.knet.ui.desktop.settings.di.settingsUiModule
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module aggregating desktop application UI framework dependencies.
 * Uses the recommended Koin viewModel DSL to scope TrafficViewModel to the ViewModelStoreOwner lifecycle.
 * Includes [apiStudioUiModule], [certificateUiModule], and [settingsUiModule].
 */
val desktopAppUiModule = module {
    includes(apiStudioUiModule, certificateUiModule, settingsUiModule)
    viewModel {
        TrafficViewModel(
            getLiveTrafficUseCase = get(),
            clearLiveTrafficUseCase = get(),
            startProxyEngineUseCase = get(),
            stopProxyEngineUseCase = get(),
            observeProxyEngineStateUseCase = get(),
            loadTransactionBodyUseCase = get(),
            observeLocalIpUseCase = get(),
            widgetPreferencesRepository = get()
        )
    }
}
