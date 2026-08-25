package com.devuloopers.knet.ui.desktop.apistudio.grpc.viewmodel

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolAuthoringRegistry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutorRegistry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolReflectionRegistry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSchemaSource
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSchemaStore
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSessionExecutorRegistry
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceContent
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceDocumentStore
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.proxy.ProxyStartResult
import com.devuloopers.knet.application.port.proxy.ProxyStopReason
import com.devuloopers.knet.application.port.proxy.ProxyStopResult
import com.devuloopers.knet.application.port.traffic.CaptureClearPreparation
import com.devuloopers.knet.application.port.traffic.CapturePauseResult
import com.devuloopers.knet.application.port.traffic.CaptureResumeResult
import com.devuloopers.knet.application.port.traffic.CaptureSessionControlPort
import com.devuloopers.knet.application.port.traffic.CaptureSessionState
import com.devuloopers.knet.application.usecase.apistudio.CreateApiStudioProtocolDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.CreateApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ExecuteApiStudioProtocolDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.GetApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ImportApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.ListApiStudioProtocolOperationsUseCase
import com.devuloopers.knet.application.usecase.apistudio.LoadApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.OpenApiStudioProtocolSessionUseCase
import com.devuloopers.knet.application.usecase.apistudio.ReflectApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.SaveApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.UpdateApiStudioWorkspaceContentUseCase
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.grpc.persistence.GrpcWorkspaceDraftCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GrpcStudioViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var workspaceStore: RecordingWorkspaceStore
    private lateinit var draftCodec: GrpcWorkspaceDraftCodec
    private lateinit var viewModel: GrpcStudioViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        workspaceStore = RecordingWorkspaceStore()
        draftCodec = GrpcWorkspaceDraftCodec()
        viewModel = createViewModel(workspaceStore, draftCodec)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openingTransientEditorDoesNotCreateWorkspaceDocument() = runTest(dispatcher) {
        viewModel.openWorkspaceDocument(null)

        advanceUntilIdle()

        assertTrue(viewModel.state.value.documentId.isBlank())
        assertTrue(workspaceStore.documents.value.isEmpty())
        assertEquals(0, workspaceStore.createCount)
    }

    @Test
    fun firstAuthoringEditCreatesOneUnsavedDocumentWithLatestContent() = runTest(dispatcher) {
        viewModel.updateTargetHost("grpc.example.test")
        viewModel.updateTargetPort("8443")

        advanceUntilIdle()

        val stored = workspaceStore.documents.value.single()
        val restored = draftCodec.decode(stored)
        assertEquals(1, workspaceStore.createCount)
        assertEquals(viewModel.state.value.documentId, stored.id)
        assertEquals("grpc.example.test", restored.targetHost)
        assertEquals("8443", restored.targetPort)
        assertFalse(viewModel.state.value.isDirty)
    }

    @Test
    fun presentationOnlyMessageSelectionDoesNotCreateWorkspaceDocument() = runTest(dispatcher) {
        viewModel.selectOutboundMessage(0)

        advanceUntilIdle()

        assertTrue(viewModel.state.value.documentId.isBlank())
        assertEquals(0, workspaceStore.createCount)
    }

    private fun createViewModel(
        workspaceStore: RecordingWorkspaceStore,
        codec: GrpcWorkspaceDraftCodec,
    ): GrpcStudioViewModel {
        val authoringRegistry = ApiStudioProtocolAuthoringRegistry()
        val schemaStore = EmptySchemaStore()
        return GrpcStudioViewModel(
            importSchema = ImportApiStudioProtocolSchemaUseCase(authoringRegistry),
            listOperations = ListApiStudioProtocolOperationsUseCase(authoringRegistry),
            createDocument = CreateApiStudioProtocolDocumentUseCase(authoringRegistry),
            executeDocument = ExecuteApiStudioProtocolDocumentUseCase(ApiStudioProtocolExecutorRegistry()),
            getWorkspaceDocument = GetApiStudioWorkspaceDocumentUseCase(workspaceStore),
            createWorkspaceDocument = CreateApiStudioWorkspaceDocumentUseCase(workspaceStore),
            updateWorkspaceContent = UpdateApiStudioWorkspaceContentUseCase(workspaceStore),
            saveSchema = SaveApiStudioProtocolSchemaUseCase(schemaStore),
            loadSchema = LoadApiStudioProtocolSchemaUseCase(schemaStore),
            reflectSchema = ReflectApiStudioProtocolSchemaUseCase(ApiStudioProtocolReflectionRegistry()),
            openSession = OpenApiStudioProtocolSessionUseCase(ApiStudioProtocolSessionExecutorRegistry()),
            observeProxyRuntimeState = ObserveProxyRuntimeStateUseCase(StoppedProxyRuntime()),
            observeTrafficCaptureState = ObserveTrafficCaptureStateUseCase(InactiveCaptureControl()),
            draftCodec = codec,
            ioDispatcher = dispatcher,
        )
    }
}

private class RecordingWorkspaceStore : ApiStudioWorkspaceDocumentStore {
    val documents = MutableStateFlow<List<ApiStudioWorkspaceDocument>>(emptyList())
    var createCount: Int = 0
        private set

    override fun observeDocuments(): Flow<List<ApiStudioWorkspaceDocument>> = documents

    override suspend fun document(id: String): ApiStudioWorkspaceDocument? =
        documents.value.firstOrNull { it.id == id }

    override suspend fun createDocument(document: ApiStudioWorkspaceDocument) {
        createCount++
        documents.value = documents.value + document
    }

    override suspend fun updateContent(id: String, content: ApiStudioWorkspaceContent) {
        documents.value = documents.value.map { document ->
            if (document.id != id) {
                document
            } else {
                ApiStudioWorkspaceDocument(
                    id = document.id,
                    editorId = content.editorId,
                    requestKind = content.requestKind,
                    name = content.suggestedName,
                    nameOrigin = document.nameOrigin,
                    badgeLabel = content.badgeLabel,
                    payloadVersion = content.payloadVersion,
                    payload = content.copyPayload(),
                    location = document.location,
                )
            }
        }
    }

    override suspend fun deleteDocument(id: String) {
        documents.value = documents.value.filterNot { it.id == id }
    }

    override suspend fun renameDocument(id: String, name: String) = Unit

    override suspend fun promoteToExistingCollection(
        id: String,
        name: String,
        nameOrigin: com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin,
        collectionId: String,
        folderId: String,
    ) = Unit

    override suspend fun promoteToNewCollection(
        id: String,
        name: String,
        nameOrigin: com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin,
        collection: ApiCollection,
        folder: CollectionFolder,
    ) = Unit
}

private class EmptySchemaStore : ApiStudioProtocolSchemaStore {
    override suspend fun saveSchema(source: ApiStudioProtocolSchemaSource) = Unit

    override suspend fun schema(kind: RequestKindId, sourceId: String): ApiStudioProtocolSchemaSource? = null
}

private class StoppedProxyRuntime : ProxyRuntimePort {
    override val state: StateFlow<ProxyRuntimeState> = MutableStateFlow(ProxyRuntimeState.Stopped)

    override suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult =
        ProxyStartResult.Failed("not-used")

    override suspend fun stop(reason: ProxyStopReason): ProxyStopResult = ProxyStopResult.Stopped
}

private class InactiveCaptureControl : CaptureSessionControlPort {
    override val captureState: StateFlow<CaptureSessionState> = MutableStateFlow(CaptureSessionState.Inactive)

    override suspend fun pause(): CapturePauseResult = CapturePauseResult.PROXY_INACTIVE

    override suspend fun resume(): CaptureResumeResult = CaptureResumeResult.ProxyInactive

    override suspend fun rotateForTrafficClear(): CaptureClearPreparation =
        CaptureClearPreparation.CANONICAL_SESSION_INACTIVE
}
