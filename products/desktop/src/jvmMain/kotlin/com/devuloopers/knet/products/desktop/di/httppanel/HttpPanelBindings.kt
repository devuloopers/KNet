package com.devuloopers.knet.products.desktop.di.httppanel

import com.devuloopers.knet.domain.payload.PayloadStrategy
import com.devuloopers.knet.domain.payload.PayloadStrategyRegistry
import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.usecase.SyncBodyStateUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** HTTP editor/inspector body strategies and editor-state synchronization. */
internal val httpPanelBindings: Module = module {
    single { GraphQLBodyFormatter() }
    single<PayloadStrategy> { GraphQlPayloadMapper(graphQlFormatter = get()) }
    single { PayloadStrategyRegistry(strategies = listOf(get<PayloadStrategy>())) }
    factory { SyncBodyStateUseCase(get()) }
}
