package com.devuloopers.knet.ui.desktop.httppanel.di

import com.devuloopers.knet.domain.payload.PayloadMapper
import com.devuloopers.knet.domain.payload.PayloadMapperRegistry
import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.usecase.SyncBodyStateUseCase
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:httpPanel`.
 *
 * Provides payload formatters, payload mappers, [PayloadMapperRegistry], and [SyncBodyStateUseCase].
 */
public val httpPanelModule = module {
    single { GraphQLBodyFormatter() }
    single<PayloadMapper<*>> { GraphQlPayloadMapper(graphQlFormatter = get()) }
    single {
        PayloadMapperRegistry(
            mappers = listOf(
                get<PayloadMapper<*>>()
            )
        )
    }
    factory { SyncBodyStateUseCase(get()) }
}
