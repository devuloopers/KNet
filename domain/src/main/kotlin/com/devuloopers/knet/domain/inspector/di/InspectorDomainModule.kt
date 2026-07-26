package com.devuloopers.knet.domain.inspector.di

import com.devuloopers.knet.domain.inspector.usecase.GetTransactionDetailUseCase
import org.koin.dsl.module

/**
 * Feature-centric Koin DI module for the Inspector domain layer.
 */
val inspectorDomainModule = module {
    factory { GetTransactionDetailUseCase(get()) }
}
