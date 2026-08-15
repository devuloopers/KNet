package com.devuloopers.knet.ui.desktop.breakpointmanager.di

import com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:breakpointManager`.
 * ViewModels inject domain UseCases provided by `:data:desktop` / `:core:domain`.
 */
public val breakpointManagerUiModule = module {
    viewModel {
        BreakpointManagerViewModel(
            getRulesUseCase = get(),
            observeGlobalInterceptionUseCase = get(),
            observeActiveInterceptionsUseCase = get(),
            saveRuleUseCase = get(),
            toggleRuleUseCase = get(),
            deleteRuleUseCase = get(),
            toggleGlobalInterceptionUseCase = get(),
            forwardInterceptedRequestUseCase = get(),
            forwardInterceptedResponseUseCase = get(),
            dropInterceptedTransactionUseCase = get(),
            clearInterceptionSessionsUseCase = get()
        )
    }
}

