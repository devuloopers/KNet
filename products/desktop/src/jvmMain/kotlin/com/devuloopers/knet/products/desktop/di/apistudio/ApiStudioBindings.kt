package com.devuloopers.knet.products.desktop.di.apistudio

import com.devuloopers.knet.application.port.script.ScriptExecutionPort
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolAuthoringPort
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolAuthoringRegistry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutor
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutorRegistry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSchemaStore
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceDocumentStore
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolReflectionPort
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolReflectionRegistry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSessionExecutor
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSessionExecutorRegistry
import com.devuloopers.knet.application.usecase.apistudio.ExecuteApiStudioRequestUseCase
import com.devuloopers.knet.application.usecase.apistudio.CreateApiStudioProtocolDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.CreateApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.DeleteApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ExecuteApiStudioProtocolDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ImportApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.ListApiStudioProtocolOperationsUseCase
import com.devuloopers.knet.application.usecase.apistudio.LoadApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.GetApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ObserveApiStudioWorkspaceDocumentsUseCase
import com.devuloopers.knet.application.usecase.apistudio.PromoteApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ReadApiStudioProtocolDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.RenameApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.SaveApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.UpdateApiStudioWorkspaceContentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ReflectApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.OpenApiStudioProtocolSessionUseCase
import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.client.LocalProxyTlsTrust
import com.devuloopers.knet.data.desktop.apistudio.repository.CollectionsRepositoryImpl
import com.devuloopers.knet.data.desktop.apistudio.RoomApiStudioProtocolSchemaStore
import com.devuloopers.knet.data.desktop.apistudio.RoomApiStudioWorkspaceDocumentStore
import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.data.desktop.script.DesktopScriptExecutionAdapter
import com.devuloopers.knet.engine.grpc.GrpcApiStudioAuthoringAdapter
import com.devuloopers.knet.engine.grpc.GrpcApiStudioExecutor
import com.devuloopers.knet.engine.grpc.GrpcApiStudioReflectionAdapter
import com.devuloopers.knet.engine.grpc.GrpcClientChannelFactory
import com.devuloopers.knet.engine.grpc.GrpcRequestDraftCodec
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
import com.devuloopers.knet.ui.desktop.apistudio.protocol.ApiStudioWorkspaceContribution
import com.devuloopers.knet.ui.desktop.apistudio.grpc.GrpcApiStudioWorkspaceContribution
import com.devuloopers.knet.ui.desktop.apistudio.grpc.viewmodel.GrpcStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.grpc.persistence.GrpcWorkspaceDraftCodec
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.bind

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
    single<ApiStudioWorkspaceDocumentStore> {
        RoomApiStudioWorkspaceDocumentStore(get<KNetDatabase>().protocolDocumentDao())
    }
    single<ApiStudioProtocolSchemaStore> {
        RoomApiStudioProtocolSchemaStore(get<KNetDatabase>().protocolDocumentDao())
    }
    single { GrpcRequestDraftCodec() }
    single { GrpcApiStudioAuthoringAdapter(get(), get()) } bind ApiStudioProtocolAuthoringPort::class
    single {
        val certificates: CertificateRuntimeRepository = get()
        GrpcClientChannelFactory(certificates.rootCertificateDer())
    }
    single { GrpcApiStudioExecutor(get(), get(), get()) }
    single<ApiStudioProtocolExecutor> { get<GrpcApiStudioExecutor>() }
    single<ApiStudioProtocolSessionExecutor> { get<GrpcApiStudioExecutor>() }
    single { GrpcApiStudioReflectionAdapter(get(), get()) } bind ApiStudioProtocolReflectionPort::class
    single { ApiStudioProtocolAuthoringRegistry(getAll<ApiStudioProtocolAuthoringPort>()) }
    single { ApiStudioProtocolExecutorRegistry(getAll<ApiStudioProtocolExecutor>()) }
    single { ApiStudioProtocolSessionExecutorRegistry(getAll<ApiStudioProtocolSessionExecutor>()) }
    single { ApiStudioProtocolReflectionRegistry(getAll<ApiStudioProtocolReflectionPort>()) }
    single { GrpcWorkspaceDraftCodec() }
    single { GrpcApiStudioWorkspaceContribution(get()) } bind ApiStudioWorkspaceContribution::class

    factory { ExecuteClientApiRequestUseCase(get()) }
    factory { FormatResponseBodyUseCase() }
    factory { ExecuteApiStudioRequestUseCase(get(), get(), get(), Dispatchers.IO) }
    factory { ImportApiStudioProtocolSchemaUseCase(get()) }
    factory { ListApiStudioProtocolOperationsUseCase(get()) }
    factory { CreateApiStudioProtocolDocumentUseCase(get()) }
    factory { ReadApiStudioProtocolDocumentUseCase(get()) }
    factory { ExecuteApiStudioProtocolDocumentUseCase(get()) }
    factory { SaveApiStudioProtocolSchemaUseCase(get()) }
    factory { LoadApiStudioProtocolSchemaUseCase(get()) }
    factory { ObserveApiStudioWorkspaceDocumentsUseCase(get()) }
    factory { GetApiStudioWorkspaceDocumentUseCase(get()) }
    factory { CreateApiStudioWorkspaceDocumentUseCase(get()) }
    factory { UpdateApiStudioWorkspaceContentUseCase(get()) }
    factory { DeleteApiStudioWorkspaceDocumentUseCase(get()) }
    factory { RenameApiStudioWorkspaceDocumentUseCase(get()) }
    factory { PromoteApiStudioWorkspaceDocumentUseCase(get()) }
    factory { ReflectApiStudioProtocolSchemaUseCase(get()) }
    factory { OpenApiStudioProtocolSessionUseCase(get()) }
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
        GrpcStudioViewModel(
            importSchema = get(),
            listOperations = get(),
            createDocument = get(),
            executeDocument = get(),
            getWorkspaceDocument = get(),
            createWorkspaceDocument = get(),
            updateWorkspaceContent = get(),
            saveSchema = get(),
            loadSchema = get(),
            reflectSchema = get(),
            openSession = get(),
            observeProxyRuntimeState = get(),
            observeTrafficCaptureState = get(),
            draftCodec = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
    viewModel {
        ApiStudioViewModel(
            executeApiStudioRequestUseCase = get(),
            observeProxyRuntimeStateUseCase = get(),
            observeTrafficCaptureStateUseCase = get(),
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
            observeWorkspaceDocumentsUseCase = get(),
            describeRequestUseCase = get(),
            deleteUnsavedRequestUseCase = get(),
            createCollectionUseCase = get(),
            deleteCollectionUseCase = get(),
            renameCollectionUseCase = get(),
            updateRequestInCollectionUseCase = get(),
            deleteSavedSessionUseCase = get(),
            createWorkspaceDocumentUseCase = get(),
            deleteWorkspaceDocumentUseCase = get(),
            renameWorkspaceDocumentUseCase = get(),
            promoteWorkspaceDocumentUseCase = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
}
