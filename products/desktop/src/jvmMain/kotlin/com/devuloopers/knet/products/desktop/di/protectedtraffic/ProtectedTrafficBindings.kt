package com.devuloopers.knet.products.desktop.di.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.ProtectedTrafficRepository
import com.devuloopers.knet.application.usecase.protectedtraffic.DeleteProtectedTrafficRuleUseCase
import com.devuloopers.knet.application.usecase.protectedtraffic.ObserveProtectedTrafficConfigurationUseCase
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
import kotlinx.coroutines.CoroutineScope

/** Protected-traffic policy persistence, use cases, and desktop proxy adapter composition. */
internal val protectedTrafficBindings: Module = module {
    single<com.devuloopers.knet.application.contract.protectedtraffic.AutomaticTlsFallback> {
        com.devuloopers.knet.data.desktop.protectedtraffic.InMemoryAutomaticTlsFallback(get<CoroutineScope>())
    }
    single { RoomProtectedTrafficRepository(get<KNetDatabase>().protectedTrafficDao(), get<CoroutineScope>()) } bind
        ProtectedTrafficRepository::class
    single {
        val repository = get<ProtectedTrafficRepository>()
        val certificates = get<com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository>()
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(certificates.rootCertificateDer())
            .joinToString("") { "%02x".format(it) }
        ProtectedTrafficTlsInterceptionPolicy(configuration = { repository.configuration.value }, automaticFallback = get(),
            inspectionAuthorityIdentity = { fingerprint.takeIf { certificates.inspectionAuthorityValid() } })
    } bind TlsInterceptionPolicy::class
    factory { ObserveProtectedTrafficConfigurationUseCase(get()) }
    factory { SetProtectedTrafficDefaultActionUseCase(get(), get()) }
    factory { UpsertProtectedTrafficRuleUseCase(get(), get()) }
    factory { DeleteProtectedTrafficRuleUseCase(get(), get()) }
    factory { SetProtectedTrafficFlowActionUseCase(get<TrafficQuery>(), get(), get()) }
    factory { com.devuloopers.knet.application.usecase.protectedtraffic.ObserveProtectedTrafficFlowHandlingUseCase(get(), get(), get()) }
    factory { com.devuloopers.knet.application.usecase.protectedtraffic.ResetProtectedTrafficFlowActionUseCase(get(), get(), get()) }
    viewModel {
        ProtectedTrafficViewModel(observeConfiguration = get(), setDefaultAction = get(), upsertRule = get(),
            deleteRule = get())
    }
}
