package com.devuloopers.knet.products.desktop.di.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.application.usecase.protectedtraffic.DeleteProtectedTrafficRuleUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.ObserveProtectedTrafficConfigurationUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.SetProtectedServiceGroupEnabledUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.SetProtectedTrafficDefaultActionUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.UpsertProtectedTrafficRuleUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.SetProtectedTrafficFlowActionUseCase
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.data.desktop.protectedtraffic.ProtectedTrafficTlsInterceptionPolicy
import com.devuloopers.knet.data.desktop.protectedtraffic.RoomProtectedTrafficRepository
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionPolicy
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel.ProtectedTrafficViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Protected-traffic policy persistence, use cases, and desktop proxy adapter composition. */
internal val protectedTrafficBindings: Module = module {
    single { RoomProtectedTrafficRepository(get<KNetDatabase>().protectedTrafficDao()) } bind
        ProtectedTrafficRepository::class
    single {
        val repository = get<ProtectedTrafficRepository>()
        ProtectedTrafficTlsInterceptionPolicy(
            configuration = { repository.configuration.value },
        )
    } bind TlsInterceptionPolicy::class
    factory { ObserveProtectedTrafficConfigurationUseCase(get()) }
    factory { SetProtectedTrafficDefaultActionUseCase(get()) }
    factory { UpsertProtectedTrafficRuleUseCase(get()) }
    factory { DeleteProtectedTrafficRuleUseCase(get()) }
    factory { SetProtectedServiceGroupEnabledUseCase(get()) }
    factory { SetProtectedTrafficFlowActionUseCase(get<TrafficQuery>(), get()) }
    viewModel { ProtectedTrafficViewModel(get(), get(), get(), get(), get(), get()) }
}
