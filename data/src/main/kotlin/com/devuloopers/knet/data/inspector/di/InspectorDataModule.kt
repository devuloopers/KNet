package com.devuloopers.knet.data.inspector.di

import com.devuloopers.knet.data.inspector.repository.InspectorRepositoryImpl
import com.devuloopers.knet.domain.inspector.repository.InspectorRepository
import org.koin.dsl.module

/**
 * Feature-centric Koin DI module for the Inspector data layer.
 */
val inspectorDataModule = module {
    single<InspectorRepository> { InspectorRepositoryImpl(get()) }
}
