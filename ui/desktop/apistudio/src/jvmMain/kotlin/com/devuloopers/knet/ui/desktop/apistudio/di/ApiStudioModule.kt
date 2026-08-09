package com.devuloopers.knet.ui.desktop.apistudio.di

import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.usecase.CreateCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteSavedSessionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.ObserveCollectionsUseCase
import com.devuloopers.knet.domain.collection.usecase.ObserveUnsavedRequestsUseCase
import com.devuloopers.knet.domain.collection.usecase.RenameCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.SaveRequestToCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.SaveUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.UpdateRequestInCollectionUseCase
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.CollectionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:apistudio`.
 *
 * Provides [ApiStudioViewModel] (wired with client request execution, response formatting, proxy observation)
 * and [CollectionsViewModel] (wired with persistent collection streams & promotion use cases).
 */
public val apiStudioUiModule = module {
    factory { ExecuteClientApiRequestUseCase(get()) }
    factory { FormatResponseBodyUseCase() }
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

    viewModel {
        ApiStudioViewModel(
            executeUseCase = get(),
            formatResponseBodyUseCase = get(),
            observeProxyEngineStateUseCase = get(),
            widgetPreferencesRepository = get()
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
            deleteSavedSessionUseCase = get()
        )
    }
}
