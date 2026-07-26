package com.devuloopers.knet.domain.livetraffic.di

import com.devuloopers.knet.domain.livetraffic.usecase.ClearLiveTrafficUseCase
import com.devuloopers.knet.domain.livetraffic.usecase.GetLiveTrafficUseCase
import org.koin.dsl.module

/**
 * Feature-centric Koin DI module for the Live Traffic domain layer.
 */
val liveTrafficDomainModule = module {
    factory { GetLiveTrafficUseCase(get()) }
    factory { ClearLiveTrafficUseCase(get()) }
}
