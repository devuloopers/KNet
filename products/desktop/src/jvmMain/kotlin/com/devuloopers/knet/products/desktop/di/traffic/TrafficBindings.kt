package com.devuloopers.knet.products.desktop.di.traffic

import com.devuloopers.knet.application.contract.traffic.TrafficMaintenance
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.contract.traffic.ProtocolMessageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficSessionCatalog
import com.devuloopers.knet.application.usecase.traffic.ClearTrafficHistoryUseCase
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveLatestTrafficSessionUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficGenerationsUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveProtocolMessageChangesUseCase
import com.devuloopers.knet.application.usecase.traffic.QueryProtocolMessagesUseCase
import com.devuloopers.knet.application.usecase.traffic.LoadProtocolMessageBodyUseCase
import com.devuloopers.knet.application.usecase.traffic.PrepareTrafficRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.PrepareCapturedNetworkRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.PauseTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.QueryTrafficPageUseCase
import com.devuloopers.knet.application.usecase.traffic.ResumeTrafficCaptureUseCase
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficMaintenanceAdapter
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficQueryAdapter
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Canonical traffic maintenance/query adapters and traffic application workflows. */
internal val trafficBindings: Module = module {
    single {
        DesktopTrafficMaintenanceAdapter(
            database = get(),
            canonicalBodyStore = get(),
        )
    }
    single<TrafficMaintenance> { get<DesktopTrafficMaintenanceAdapter>() }
    single {
        DesktopTrafficQueryAdapter(
            dao = get<KNetDatabase>().canonicalCaptureDao(),
            bodyStore = get(),
        )
    }
    single<TrafficQuery> { get<DesktopTrafficQueryAdapter>() }
    single<ProtocolMessageQuery> { get<DesktopTrafficQueryAdapter>() }
    single<TrafficSessionCatalog> { get<DesktopTrafficQueryAdapter>() }

    single { ClearTrafficHistoryUseCase(get(), get()) }
    factory { LoadTrafficExchangeDetailsUseCase(get()) }
    factory { ObserveLatestTrafficSessionUseCase(get()) }
    factory { QueryTrafficPageUseCase(get()) }
    factory { ObserveTrafficGenerationsUseCase(get()) }
    factory { ObserveProtocolMessageChangesUseCase(get()) }
    factory { QueryProtocolMessagesUseCase(get()) }
    factory { LoadProtocolMessageBodyUseCase(get(), get()) }
    factory { PrepareTrafficRequestUseCase(get()) }
    factory { PrepareCapturedNetworkRequestUseCase(get()) }
    factory { PauseTrafficCaptureUseCase(get()) }
    factory { ResumeTrafficCaptureUseCase(get()) }

    viewModel {
        TrafficViewModel(
            observeLatestTrafficSessionUseCase = get(),
            queryTrafficPageUseCase = get(),
            observeTrafficGenerationsUseCase = get(),
            observeProtocolMessageChangesUseCase = get(),
            queryProtocolMessagesUseCase = get(),
            loadProtocolMessageBodyUseCase = get(),
            clearTrafficHistoryUseCase = get(),
            startLoopbackProxyUseCase = get(),
            stopProxyRuntimeUseCase = get(),
            observeProxyRuntimeStateUseCase = get(),
            pauseTrafficCaptureUseCase = get(),
            resumeTrafficCaptureUseCase = get(),
            observeTrafficCaptureStateUseCase = get(),
            loadTrafficExchangeDetailsUseCase = get(),
            observeLocalIpUseCase = get(),
            observeApplicationSettingsUseCase = get(),
            prepareCapturedNetworkRequestUseCase = get(),
            observeInspectionAnnotationsUseCase = get(),
            describeRequestUseCase = get(),
            prepareBreakpointRuleDraftUseCase = get(),
            observeRulesUseCase = get(),
            observePendingBreakpointsUseCase = get(),
            observePendingProtocolMessageBreakpointsUseCase = get(),
            getWorkspaceLayoutUseCase = get(),
            updateWorkspaceLayoutUseCase = get(),
        )
    }
}
