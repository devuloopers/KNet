package com.devuloopers.knet.domain.workspace.di

import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import org.koin.dsl.module

val workspaceDomainModule = module {
    factory { GetWorkspaceLayoutUseCase(get()) }
    factory { SaveWorkspaceLayoutUseCase(get()) }
}
