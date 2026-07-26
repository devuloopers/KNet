package com.devuloopers.knet.data.rules.di

import com.devuloopers.knet.data.rules.repository.RulesRepositoryImpl
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import org.koin.dsl.module

/**
 * Feature-centric Koin DI module for the Rules data layer.
 */
val rulesDataModule = module {
    single<RulesRepository> { RulesRepositoryImpl() }
}
