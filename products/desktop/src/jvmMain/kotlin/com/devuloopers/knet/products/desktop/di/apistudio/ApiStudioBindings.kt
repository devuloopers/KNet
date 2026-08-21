package com.devuloopers.knet.products.desktop.di.apistudio

import com.devuloopers.knet.application.port.script.ScriptExecutionPort
import com.devuloopers.knet.application.usecase.apistudio.ExecuteApiStudioRequestUseCase
import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.client.LocalProxyTlsTrust
import com.devuloopers.knet.data.desktop.apistudio.repository.CollectionsRepositoryImpl
import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.data.desktop.script.DesktopScriptExecutionAdapter
import com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.domain.collection.usecase.CreateCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteSavedSessionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.GetSavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.ObserveCollectionsUseCase
import com.devuloopers.knet.domain.collection.usecase.ObserveUnsavedRequestsUseCase
import com.devuloopers.knet.domain.collection.usecase.RenameCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.SaveRequestToCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.SaveUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.UpdateRequestInCollectionUseCase
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.ui.desktop.apistudio.usecase.AutoSaveApiSessionUseCase
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.CollectionsViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** API Studio transport, scripting adapter, collections persistence, and promotion workflow. */
internal val apiStudioBindings: Module = module {
    single {
        val certificates: CertificateRuntimeRepository = get()
        KNetApiClient(
            localProxyTlsTrust = LocalProxyTlsTrust(certificates.rootCertificateDer()),
            captureOrigin = TrafficOrigin.ApiStudio,
        )
    }
    single<HttpExecutor> { get<KNetApiClient>() }
    single<ScriptExecutionPort> { DesktopScriptExecutionAdapter() }
    single<CollectionsRepository> {
        CollectionsRepositoryImpl(get<KNetDatabase>().collectionDao())
    }

    factory { ExecuteClientApiRequestUseCase(get()) }
    factory { FormatResponseBodyUseCase() }
    factory { ExecuteApiStudioRequestUseCase(get(), get(), get(), Dispatchers.IO) }
    factory { ImportRequestToStudioUseCase() }
    factory { ObserveCollectionsUseCase(get()) }
    factory { ObserveUnsavedRequestsUseCase(get()) }
    factory { SaveUnsavedRequestUseCase(get()) }
    factory { DeleteUnsavedRequestUseCase(get()) }
    factory { DeleteSavedSessionUseCase(get()) }
    factory { CreateCollectionUseCase(get()) }
    factory { DeleteCollectionUseCase(get()) }
    factory { RenameCollectionUseCase(get()) }
    factory { SaveRequestToCollectionUseCase(get()) }
    factory { UpdateRequestInCollectionUseCase(get()) }
    factory { GetSavedRequestUseCase(get()) }
    factory { AutoSaveApiSessionUseCase(get(), get()) }
    viewModel {
        ApiStudioViewModel(
            executeApiStudioRequestUseCase = get(),
            observeProxyRuntimeStateUseCase = get(),
            getWorkspaceLayoutUseCase = get(),
            updateWorkspaceLayoutUseCase = get(),
            observeApplicationSettingsUseCase = get(),
            updateApplicationSettingsUseCase = get(),
            importRequestToStudioUseCase = get(),
            describeRequestUseCase = get(),
            dropMatchingBreakpointsUseCase = get(),
            syncBodyStateUseCase = get(),
            autoSaveApiSessionUseCase = get(),
            getSavedRequestUseCase = get(),
            saveRequestToCollectionUseCase = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
    viewModel {
        CollectionsViewModel(
            observeCollectionsUseCase = get(),
            observeUnsavedRequestsUseCase = get(),
            describeRequestUseCase = get(),
            deleteUnsavedRequestUseCase = get(),
            createCollectionUseCase = get(),
            deleteCollectionUseCase = get(),
            renameCollectionUseCase = get(),
            updateRequestInCollectionUseCase = get(),
            deleteSavedSessionUseCase = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
}
