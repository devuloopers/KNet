package com.devuloopers.knet.products.desktop.di.traffic

import com.devuloopers.knet.application.contract.traffic.TrafficMaintenance
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.contract.traffic.TrafficFacetReader
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
import com.devuloopers.knet.application.usecase.traffic.QueryTrafficFacetsUseCase
import com.devuloopers.knet.application.usecase.traffic.ResumeTrafficCaptureUseCase
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficMaintenanceAdapter
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficQueryAdapter
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.data.desktop.capture.BodyDeletionReconciler
import com.devuloopers.knet.data.desktop.capture.BodyDeletionWorker
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficProtectionViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.onClose

/** Canonical traffic maintenance/query adapters and traffic application workflows. */
internal val trafficBindings: Module = module {
    factory<com.devuloopers.knet.application.contract.traffic.TrafficDetailsFormatting> { com.devuloopers.knet.data.desktop.traffic.DesktopTrafficDetailsFormatting() }
    factory { com.devuloopers.knet.application.usecase.traffic.CopyTrafficDetailsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory<com.devuloopers.knet.application.usecase.traffic.PrepareTrafficDetailsUseCase> { get<com.devuloopers.knet.application.usecase.traffic.CopyTrafficDetailsUseCase>() }
    viewModel { com.devuloopers.knet.ui.desktop.traffic.copy.TrafficDetailsCopyViewModel(get()) }

    single<com.devuloopers.knet.application.contract.importcapture.CaptureImportRepository> {
        com.devuloopers.knet.data.desktop.importcapture.DesktopCaptureImportRepository(
            get<com.devuloopers.knet.products.desktop.config.DesktopConfiguration>().appDirectory.resolve("imports/process").toFile())
    }
    single<com.devuloopers.knet.application.contract.importcapture.CaptureImportFilePicker> {
        com.devuloopers.knet.data.desktop.importcapture.DesktopCaptureImportFilePicker()
    }
    factory { com.devuloopers.knet.application.usecase.traffic.OpenCaptureImportUseCase(get(), get()) }
    factory { com.devuloopers.knet.application.usecase.traffic.ReadCaptureImportPageUseCase(get()) }
    factory { com.devuloopers.knet.application.usecase.traffic.ReadCaptureImportRecordUseCase(get()) }
    factory { com.devuloopers.knet.application.usecase.traffic.CloseCaptureImportUseCase(get()) }
    factory { com.devuloopers.knet.application.usecase.traffic.PrepareCaptureImportActionUseCase(get(), get(), get()) }
    viewModel { com.devuloopers.knet.ui.desktop.traffic.importcapture.CaptureImportDialogViewModel(get(), get(), get(), get(), get()) }

    single<com.devuloopers.knet.application.contract.export.CaptureExportRepository> {
        com.devuloopers.knet.data.desktop.export.DesktopCaptureExportRepository(get(), get(), get(), get(), get(),
            get<com.devuloopers.knet.products.desktop.config.DesktopConfiguration>().appDirectory.resolve("exports/snapshots").toFile())
    }
    single { com.devuloopers.knet.data.desktop.export.DesktopStudioExportRecorder(
        get<com.devuloopers.knet.products.desktop.config.DesktopConfiguration>().appDirectory.resolve("exports/studio").toFile()) }.onClose { it?.close() }
    single<com.devuloopers.knet.application.contract.export.StudioExportRecorder> { get<com.devuloopers.knet.data.desktop.export.DesktopStudioExportRecorder>() }
    single<com.devuloopers.knet.application.contract.copy.StudioCopySource> { get<com.devuloopers.knet.data.desktop.export.DesktopStudioExportRecorder>() }
    factory<com.devuloopers.knet.application.usecase.traffic.PrepareStudioCopyUseCase> { com.devuloopers.knet.application.usecase.traffic.CopyStudioExecutionUseCase(get(), get(), get(), get()) }
    viewModel { com.devuloopers.knet.ui.desktop.httppanel.copy.StudioDetailsCopyViewModel(get()) }
    single<com.devuloopers.knet.application.contract.export.CaptureExportFilePicker> { com.devuloopers.knet.data.desktop.export.DesktopCaptureExportFilePicker() }
    factory { com.devuloopers.knet.application.usecase.traffic.PrepareCaptureExportUseCase(get()) }
    factory { com.devuloopers.knet.application.usecase.traffic.SaveCaptureExportUseCase(get(), get()) }
    factory { com.devuloopers.knet.application.usecase.traffic.ReleaseCaptureExportUseCase(get()) }
    viewModel { com.devuloopers.knet.ui.desktop.httppanel.export.CaptureExportViewModel(get(), get(), get()) }

    factory { com.devuloopers.knet.application.usecase.traffic.ResolveCapturedTrafficSourceUseCase(get()) }
    viewModel { TrafficProtectionViewModel(get(), get(), get()) }
    single { BodyDeletionReconciler(get<KNetDatabase>().canonicalCaptureDao(), get()) }
    single { BodyDeletionWorker(get()) }
    single { DesktopTrafficMaintenanceAdapter(database = get()) }
    single<TrafficMaintenance> { get<DesktopTrafficMaintenanceAdapter>() }
    single {
        DesktopTrafficQueryAdapter(
            dao = get<KNetDatabase>().canonicalCaptureDao(),
            bodyStore = get(),
        )
    }
    single<TrafficQuery> { get<DesktopTrafficQueryAdapter>() }
    single<TrafficFacetReader> { get<DesktopTrafficQueryAdapter>() }
    single<ProtocolMessageQuery> { get<DesktopTrafficQueryAdapter>() }
    single<TrafficSessionCatalog> { get<DesktopTrafficQueryAdapter>() }

    single { ClearTrafficHistoryUseCase(get(), get()) }
    factory { LoadTrafficExchangeDetailsUseCase(get()) }
    factory { ObserveLatestTrafficSessionUseCase(get()) }
    factory { QueryTrafficPageUseCase(get()) }
    factory { QueryTrafficFacetsUseCase(get()) }
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
            observeGraphQLPersistedQueriesUseCase = get(),
            observeLatestTrafficSessionUseCase = get(),
            queryTrafficPageUseCase = get(),
            resolveTrafficSourceUseCase = get(),
            initialSource = com.devuloopers.knet.application.contract.traffic.TrafficSource.Other,
            queryTrafficFacetsUseCase = get(),
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
