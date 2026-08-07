package com.devuloopers.knet.ui.desktop.apistudio.di

import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:apistudio`.
 *
 * Provides [ApiStudioViewModel] wired with [ExecuteClientApiRequestUseCase],
 * [FormatResponseBodyUseCase], and [ObserveProxyEngineStateUseCase].
 * Traffic recording is handled exclusively by the proxy pipeline — API Studio
 * has no direct Room DB exposure.
 */
public val apiStudioUiModule = module {
    factory { ExecuteClientApiRequestUseCase(get()) }
    factory { FormatResponseBodyUseCase() }
    viewModel { ApiStudioViewModel(get(), get(), get()) }
}


