package com.devuloopers.knet.domain.di

import com.devuloopers.knet.domain.inspector.di.inspectorDomainModule
import com.devuloopers.knet.domain.livetraffic.di.liveTrafficDomainModule
import com.devuloopers.knet.domain.rules.di.rulesDomainModule
import org.koin.dsl.module

/**
 * Global Koin DI registry for the domain module.
 * Aggregates all feature-specific domain modules.
 */
val domainModule = module {
    includes(liveTrafficDomainModule, inspectorDomainModule, rulesDomainModule)
}
