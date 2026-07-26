package com.devuloopers.knet.data.livetraffic.di

import com.devuloopers.knet.data.livetraffic.repository.LiveTrafficRepositoryImpl
import com.devuloopers.knet.domain.livetraffic.repository.LiveTrafficRepository
import org.koin.dsl.module

/**
 * Feature-centric Koin DI module for the Live Traffic data layer.
 */
val liveTrafficDataModule = module {
    single<LiveTrafficRepository> { LiveTrafficRepositoryImpl(get()) }
}
