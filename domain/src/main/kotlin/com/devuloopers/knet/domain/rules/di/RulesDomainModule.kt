package com.devuloopers.knet.domain.rules.di

import com.devuloopers.knet.domain.rules.usecase.GetRulesUseCase
import com.devuloopers.knet.domain.rules.usecase.SaveRuleUseCase
import com.devuloopers.knet.domain.rules.usecase.ToggleRuleUseCase
import org.koin.dsl.module

/**
 * Feature-centric Koin DI module for the Rules domain layer.
 */
val rulesDomainModule = module {
    factory { GetRulesUseCase(get()) }
    factory { ToggleRuleUseCase(get()) }
    factory { SaveRuleUseCase(get()) }
}
