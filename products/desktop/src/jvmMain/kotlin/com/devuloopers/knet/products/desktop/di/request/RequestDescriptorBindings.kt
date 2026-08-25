package com.devuloopers.knet.products.desktop.di.request

import com.devuloopers.knet.domain.request.descriptor.HttpRequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.engine.formatter.descriptor.GraphQlRequestDescriptorStrategy
import com.devuloopers.knet.engine.grpc.GrpcRequestDescriptorStrategy
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Shared authored/captured request presentation strategies and their ordered resolver. */
internal val requestDescriptorBindings: Module = module {
    factory { GraphQlRequestDescriptorStrategy() } bind RequestDescriptorStrategy::class
    factory { GrpcRequestDescriptorStrategy() } bind RequestDescriptorStrategy::class
    factory { HttpRequestDescriptorStrategy() } bind RequestDescriptorStrategy::class
    factory { DescribeRequestUseCase(strategies = getAll<RequestDescriptorStrategy>()) }
}
