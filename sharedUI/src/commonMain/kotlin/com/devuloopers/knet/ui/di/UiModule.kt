package com.devuloopers.knet.ui.di

import com.devuloopers.knet.data.di.dataModule
import com.devuloopers.knet.domain.di.domainModule
import com.devuloopers.knet.ui.inspector.di.inspectorUiModule
import com.devuloopers.knet.ui.livetraffic.di.liveTrafficUiModule
import com.devuloopers.knet.ui.rules.di.rulesUiModule
import com.devuloopers.knet.ui.workspace.di.workspaceUiModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Global Koin DI registry for the UI module.
 * Aggregates all feature-specific UI modules.
 */
val uiModule = module {
    includes(liveTrafficUiModule, inspectorUiModule, rulesUiModule, workspaceUiModule)
}

/**
 * Initializes Koin Dependency Injection engine combining all multiplatform modules.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(domainModule, dataModule, uiModule)
}
