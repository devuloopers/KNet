package com.devuloopers.knet.products.desktop.di.apistudio

import com.devuloopers.knet.application.port.script.ScriptExecutionPort
import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.data.desktop.apistudio.repository.CollectionsRepositoryImpl
import com.devuloopers.knet.data.desktop.script.DesktopScriptExecutionAdapter
import com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase
import com.devuloopers.knet.domain.apistudio.usecase.ResolveUniqueSessionTitleUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.domain.collection.usecase.*
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.ui.desktop.apistudio.usecase.AutoSaveApiSessionUseCase
import com.devuloopers.knet.ui.desktop.apistudio.usecase.ExecuteScriptedApiRequestUseCase
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.CollectionsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor as DomainHttpExecutor

/** API Studio transport, scripting adapter, collections persistence, and promotion workflow. */
internal val apiStudioBindings: Module = module {
    single { KNetApiClient() }
    single<DomainHttpExecutor> { get<KNetApiClient>() }
    single<ScriptExecutionPort> { DesktopScriptExecutionAdapter() }
    single<CollectionsRepository> {
        CollectionsRepositoryImpl(get<KNetDatabase>().collectionDao())
    }

    factory { ExecuteClientApiRequestUseCase(get()) }
    factory { FormatResponseBodyUseCase() }
    factory { ExecuteScriptedApiRequestUseCase(get(), get(), get(), get()) }
    factory { ImportRequestToStudioUseCase() }
    factory { ResolveUniqueSessionTitleUseCase() }
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
    factory { AutoSaveApiSessionUseCase(get(), get(), get()) }
    viewModel {
        ApiStudioViewModel(
            executeScriptedUseCase = get(),
            observeProxyRuntimeStateUseCase = get(),
            getWorkspaceLayoutUseCase = get(),
            saveWorkspaceLayoutUseCase = get(),
            importRequestToStudioUseCase = get(),
            dropMatchingBreakpointsUseCase = get(),
            syncBodyStateUseCase = get(),
            autoSaveApiSessionUseCase = get(),
        )
    }
    viewModel {
        CollectionsViewModel(
            observeCollectionsUseCase = get(),
            observeUnsavedRequestsUseCase = get(),
            saveUnsavedRequestUseCase = get(),
            deleteUnsavedRequestUseCase = get(),
            createCollectionUseCase = get(),
            deleteCollectionUseCase = get(),
            renameCollectionUseCase = get(),
            saveRequestToCollectionUseCase = get(),
            updateRequestInCollectionUseCase = get(),
            deleteSavedSessionUseCase = get(),
            resolveUniqueSessionTitleUseCase = get(),
        )
    }
}
