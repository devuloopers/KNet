package com.devuloopers.knet.ui.desktop.httppanel.di

import com.devuloopers.knet.domain.payload.PayloadStrategy
import com.devuloopers.knet.domain.payload.PayloadStrategyRegistry
import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.usecase.SyncBodyStateUseCase
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:httpPanel`.
 *
 * Provides payload formatters, payload strategies, [PayloadStrategyRegistry], and [SyncBodyStateUseCase].
 */
public val httpPanelModule = module {
    single { GraphQLBodyFormatter() }
    single<PayloadStrategy> { GraphQlPayloadMapper(graphQlFormatter = get()) }
    single {
        PayloadStrategyRegistry(
            strategies = listOf(
                get<PayloadStrategy>()
            )
        )
    }
    factory { SyncBodyStateUseCase(get()) }
}
