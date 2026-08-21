package com.devuloopers.knet.products.desktop.di.traffic

import com.devuloopers.knet.application.port.traffic.TrafficMaintenancePort
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.application.port.traffic.TrafficSessionCatalogPort
import com.devuloopers.knet.application.usecase.traffic.ClearTrafficHistoryUseCase
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveLatestTrafficSessionUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficGenerationsUseCase
import com.devuloopers.knet.application.usecase.traffic.PrepareTrafficRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.PrepareCapturedNetworkRequestUseCase
import com.devuloopers.knet.application.usecase.traffic.PauseTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.QueryTrafficPageUseCase
import com.devuloopers.knet.application.usecase.traffic.ResumeTrafficCaptureUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
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
    single<TrafficMaintenancePort> { get<DesktopTrafficMaintenanceAdapter>() }
    single {
        DesktopTrafficQueryAdapter(
            dao = get<KNetDatabase>().canonicalCaptureDao(),
            bodyStore = get(),
        )
    }
    single<TrafficQueryPort> { get<DesktopTrafficQueryAdapter>() }
    single<TrafficSessionCatalogPort> { get<DesktopTrafficQueryAdapter>() }

    single { ClearTrafficHistoryUseCase(get(), get()) }
    factory { LoadTrafficExchangeDetailsUseCase(get()) }
    factory { ObserveLatestTrafficSessionUseCase(get()) }
    factory { QueryTrafficPageUseCase(get()) }
    factory { ObserveTrafficGenerationsUseCase(get()) }
    factory { PrepareTrafficRequestUseCase(get()) }
    factory { PrepareCapturedNetworkRequestUseCase(get()) }
    factory { PauseTrafficCaptureUseCase(get()) }
    factory { ResumeTrafficCaptureUseCase(get()) }
    factory { ObserveTrafficCaptureStateUseCase(get()) }

    viewModel {
        TrafficViewModel(
            observeLatestTrafficSessionUseCase = get(),
            queryTrafficPageUseCase = get(),
            observeTrafficGenerationsUseCase = get(),
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
            getWorkspaceLayoutUseCase = get(),
            updateWorkspaceLayoutUseCase = get(),
        )
    }
}
