package com.devuloopers.knet.ui.desktop.apistudio.di

import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:apistudio`.
 */
public val apiStudioUiModule = module {
    factory { ExecuteClientApiRequestUseCase(get()) }
    factory { FormatResponseBodyUseCase() }
    viewModel { ApiStudioViewModel(get(), get()) }
}
