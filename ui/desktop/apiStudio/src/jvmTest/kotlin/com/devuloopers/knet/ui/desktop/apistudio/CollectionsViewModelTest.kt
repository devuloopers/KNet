package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.application.contract.apistudio.ApiStudioDocumentLocation
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceContent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocumentStore
import com.devuloopers.knet.application.usecase.apistudio.CreateApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.DeleteApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ObserveApiStudioWorkspaceDocumentsUseCase
import com.devuloopers.knet.application.usecase.apistudio.PromoteApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.RenameApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.domain.request.descriptor.HttpRequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorContribution
import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorStrategy
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.domain.collection.usecase.CreateCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteSavedSessionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.ObserveCollectionsUseCase
import com.devuloopers.knet.domain.collection.usecase.ObserveUnsavedRequestsUseCase
import com.devuloopers.knet.domain.collection.usecase.RenameCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.UpdateRequestInCollectionUseCase
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.CollectionsViewModel
import com.devuloopers.knet.ui.desktop.apistudio.dialog.CollectionSaveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `combined Room streams publish one loaded sidebar state`() = runTest {
        val repository = SidebarRepository()
        val viewModel = createViewModel(repository)
        assertTrue(viewModel.uiState.value.isLoading)

        repository.collections.value = listOf(
            ApiCollection(
                id = "collection-1",
                name = "Users",
                folders = listOf(
                    CollectionFolder(
                        id = "folder-1",
                        name = "Requests",
                        requests = listOf(
                            SavedApiRequest(
                                id = "request-1",
                                name = "Get user",
                                method = HttpMethod.GET,
                                url = "https://api.example.com/users/1"
                            )
                        )
                    )
                )
            )
        )
        repository.drafts.value = listOf(
            SavedApiRequest(
                id = "draft-1",
                name = "Draft",
                method = HttpMethod.POST,
                url = "https://api.example.com/users"
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals("Get user", state.collections.single().requests.single().name)
        assertEquals("GET", state.collections.single().requests.single().descriptor.badgeLabel)
        assertEquals("Draft", state.unsavedRequests.single().name)
        assertEquals("POST", state.unsavedRequests.single().descriptor.badgeLabel)
    }

    @Test
    fun `protocol descriptor controls badge while retaining transport method`() = runTest {
        val repository = SidebarRepository()
        repository.drafts.value = listOf(
            SavedApiRequest(
                id = "graphql-draft",
                name = "Viewer",
                method = HttpMethod.POST,
                url = "https://api.example.com/graphql",
                body = ApiRequestBody(
                    content = "query Viewer { viewer { id } }",
                    type = RequestBodyType.GRAPHQL
                )
            )
        )
        val graphQlStrategy = RequestDescriptorStrategy { request ->
            if (request.semanticKindHint == RequestKindId.GRAPHQL) {
                RequestDescriptorContribution(
                    kind = RequestKindId.GRAPHQL,
                    badgeLabel = "GQL",
                    suggestedName = "Viewer"
                )
            } else {
                null
            }
        }
        val viewModel = createViewModel(
            repository = repository,
            describeRequestUseCase = DescribeRequestUseCase(
                listOf(graphQlStrategy, HttpRequestDescriptorStrategy())
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val descriptor = viewModel.uiState.value.unsavedRequests.single().descriptor
        assertEquals(RequestKindId.GRAPHQL, descriptor.kind)
        assertEquals("GQL", descriptor.badgeLabel)
        assertEquals(HttpMethod.POST, descriptor.transportMethod)
    }

    @Test
    fun `collection dialog state and creation are owned by sidebar ViewModel`() = runTest {
        val repository = SidebarRepository()
        val viewModel = createViewModel(repository)

        viewModel.openCreateCollectionDialog()
        assertTrue(viewModel.uiState.value.isCreateCollectionDialogOpen)
        viewModel.createCollection("Payments")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCreateCollectionDialogOpen)
        assertEquals("Payments", repository.lastSavedCollection?.name)
    }

    @Test
    fun `saved request rename uses complete document and retains its fields`() = runTest {
        val repository = SidebarRepository()
        val document = SavedApiRequest(
            id = "request-1",
            name = "Old name",
            method = HttpMethod.POST,
            url = "https://api.example.com/users",
            expectedStatus = 201
        )
        repository.collections.value = listOf(
            ApiCollection(
                id = "collection-1",
                name = "Users",
                folders = listOf(CollectionFolder("folder-1", "Requests", requests = listOf(document)))
            )
        )
        val viewModel = createViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()
        val request = viewModel.uiState.value.collections.single().requests.single()

        viewModel.renameSavedRequest(request, "Create user")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Create user", repository.lastSavedRequest?.name)
        assertEquals(RequestNameOrigin.USER_DEFINED, repository.lastSavedRequest?.nameOrigin)
        assertEquals(201, repository.lastSavedRequest?.expectedStatus)
        assertEquals("https://api.example.com/users", repository.lastSavedRequest?.url)
    }

    @Test
    fun `deletion intents target only their requested records`() = runTest {
        val repository = SidebarRepository()
        val viewModel = createViewModel(repository)
        repository.drafts.value = listOf(request("draft-9"))
        repository.collections.value = listOf(
            ApiCollection("collection-1", "Tests", listOf(CollectionFolder("folder-1", "Requests", requests = listOf(request("request-9"))))),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteUnsavedRequest(viewModel.uiState.value.unsavedRequests.single())
        viewModel.deleteSavedRequest(viewModel.uiState.value.collections.single().requests.single())
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("draft-9", repository.deletedDraftId)
        assertEquals("request-9", repository.deletedSavedId)
    }

    @Test
    fun `failed deletion exposes error and does not publish success callback`() = runTest {
        val repository = SidebarRepository().apply {
            deletionFailure = IllegalStateException("Database is unavailable")
        }
        val viewModel = createViewModel(repository)
        var deletionConfirmed = false
        repository.drafts.value = listOf(request("draft-9"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteUnsavedRequest(viewModel.uiState.value.unsavedRequests.single()) { deletionConfirmed = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(deletionConfirmed)
        assertEquals("Database is unavailable", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `contributed gRPC draft uses common sidebar and promotion destination`() = runTest {
        val repository = SidebarRepository().apply {
            collections.value = listOf(
                ApiCollection("collection-1", "Protocol Lab", listOf(CollectionFolder("folder-1", "Requests"))),
            )
        }
        val workspaceStore = WorkspaceStore(
            workspaceDocument("grpc-1", ApiStudioDocumentLocation.Unsaved),
        )
        val viewModel = createViewModel(repository, workspaceStore = workspaceStore)
        testDispatcher.scheduler.advanceUntilIdle()

        val draft = viewModel.uiState.value.unsavedRequests.single { it.id == "grpc-1" }
        assertEquals(ApiStudioEditorId.GRPC, draft.editorId)
        assertEquals("gRPC", draft.descriptor.badgeLabel)

        viewModel.promoteWorkspaceDocument(
            request = draft,
            requestName = "Lab/UnaryEcho",
            mode = CollectionSaveMode.EXISTING_COLLECTION,
            selectedFolder = viewModel.uiState.value.collections.single(),
            newCollectionName = "",
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.unsavedRequests.none { it.id == "grpc-1" })
        assertEquals("Lab/UnaryEcho", viewModel.uiState.value.collections.single().requests.single().name)
    }

    private fun createViewModel(
        repository: SidebarRepository,
        describeRequestUseCase: DescribeRequestUseCase = DescribeRequestUseCase(
            listOf(HttpRequestDescriptorStrategy())
        ),
        workspaceStore: WorkspaceStore = WorkspaceStore(),
    ): CollectionsViewModel = CollectionsViewModel(
        observeCollectionsUseCase = ObserveCollectionsUseCase(repository),
        observeUnsavedRequestsUseCase = ObserveUnsavedRequestsUseCase(repository),
        observeWorkspaceDocumentsUseCase = ObserveApiStudioWorkspaceDocumentsUseCase(workspaceStore),
        describeRequestUseCase = describeRequestUseCase,
        deleteUnsavedRequestUseCase = DeleteUnsavedRequestUseCase(repository),
        createCollectionUseCase = CreateCollectionUseCase(repository),
        deleteCollectionUseCase = DeleteCollectionUseCase(repository),
        renameCollectionUseCase = RenameCollectionUseCase(repository),
        updateRequestInCollectionUseCase = UpdateRequestInCollectionUseCase(repository),
        deleteSavedSessionUseCase = DeleteSavedSessionUseCase(repository),
        createWorkspaceDocumentUseCase = CreateApiStudioWorkspaceDocumentUseCase(workspaceStore),
        deleteWorkspaceDocumentUseCase = DeleteApiStudioWorkspaceDocumentUseCase(workspaceStore),
        renameWorkspaceDocumentUseCase = RenameApiStudioWorkspaceDocumentUseCase(workspaceStore),
        promoteWorkspaceDocumentUseCase = PromoteApiStudioWorkspaceDocumentUseCase(workspaceStore),
        ioDispatcher = testDispatcher
    )

    private fun request(id: String): SavedApiRequest = SavedApiRequest(
        id = id,
        name = id,
        method = HttpMethod.GET,
        url = "https://api.example.com/$id",
    )

    private class WorkspaceStore(vararg initial: ApiStudioWorkspaceDocument) : ApiStudioWorkspaceDocumentStore {
        private val documents = MutableStateFlow(initial.toList())

        override fun observeDocuments(): Flow<List<ApiStudioWorkspaceDocument>> = documents
        override suspend fun document(id: String): ApiStudioWorkspaceDocument? = documents.value.firstOrNull { it.id == id }
        override suspend fun createDocument(document: ApiStudioWorkspaceDocument) = replace(document)
        override suspend fun updateContent(id: String, content: ApiStudioWorkspaceContent) {
            val current = requireNotNull(document(id))
            require(current.editorId == content.editorId)
            replace(
                current.rebuild(
                    name = if (current.nameOrigin == RequestNameOrigin.USER_DEFINED) current.name else content.suggestedName,
                    requestKind = content.requestKind,
                    badgeLabel = content.badgeLabel,
                    payloadVersion = content.payloadVersion,
                    payload = content.copyPayload(),
                ),
            )
        }
        override suspend fun deleteDocument(id: String) {
            documents.value = documents.value.filterNot { it.id == id }
        }
        override suspend fun renameDocument(id: String, name: String) {
            replace(requireNotNull(document(id)).rebuild(name = name, nameOrigin = RequestNameOrigin.USER_DEFINED))
        }
        override suspend fun promoteToExistingCollection(
            id: String,
            name: String,
            nameOrigin: RequestNameOrigin,
            collectionId: String,
            folderId: String,
        ) {
            replace(
                requireNotNull(document(id)).rebuild(
                    name = name,
                    nameOrigin = nameOrigin,
                    location = ApiStudioDocumentLocation.Collection(collectionId, folderId),
                ),
            )
        }
        override suspend fun promoteToNewCollection(
            id: String,
            name: String,
            nameOrigin: RequestNameOrigin,
            collection: ApiCollection,
            folder: CollectionFolder,
        ) = promoteToExistingCollection(id, name, nameOrigin, collection.id, folder.id)

        private fun replace(document: ApiStudioWorkspaceDocument) {
            documents.value = documents.value.filterNot { it.id == document.id } + document
        }
    }

    private class SidebarRepository : CollectionsRepository {
        val collections = MutableStateFlow<List<ApiCollection>>(emptyList())
        val drafts = MutableStateFlow<List<SavedApiRequest>>(emptyList())
        var lastSavedCollection: ApiCollection? = null
        var lastSavedRequest: SavedApiRequest? = null
        var deletedDraftId: String? = null
        var deletedSavedId: String? = null
        var deletionFailure: Exception? = null

        override fun observeCollections(): Flow<List<ApiCollection>> = collections
        override suspend fun getCollectionById(id: String): ApiCollection? =
            collections.value.firstOrNull { it.id == id }
        override suspend fun getRequestById(id: String): SavedApiRequest? =
            drafts.value.firstOrNull { it.id == id } ?: collections.value
                .flatMap(ApiCollection::folders)
                .flatMap(CollectionFolder::requests)
                .firstOrNull { it.id == id }
        override suspend fun saveCollection(collection: ApiCollection) {
            lastSavedCollection = collection
        }
        override suspend fun deleteCollection(collectionId: String) = Unit
        override suspend fun saveFolder(collectionId: String, folder: CollectionFolder) = Unit
        override suspend fun deleteFolder(folderId: String) = Unit
        override suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest) {
            lastSavedRequest = request
        }
        override suspend fun deleteRequest(requestId: String) {
            deletedSavedId = requestId
        }
        override fun observeUnsavedRequests(): Flow<List<SavedApiRequest>> = drafts
        override suspend fun saveUnsavedRequest(request: SavedApiRequest) = Unit
        override suspend fun deleteUnsavedRequest(requestId: String) {
            deletionFailure?.let { throw it }
            deletedDraftId = requestId
        }
        override suspend fun saveUnsavedToNewCollectionTx(
            collection: ApiCollection,
            folder: CollectionFolder,
            request: SavedApiRequest,
            unsavedRequestIdToDelete: String
        ) = Unit
        override suspend fun saveUnsavedToExistingCollectionTx(
            collectionId: String,
            folderId: String,
            request: SavedApiRequest,
            unsavedRequestIdToDelete: String
        ) = Unit
    }
}

private fun workspaceDocument(id: String, location: ApiStudioDocumentLocation): ApiStudioWorkspaceDocument =
    ApiStudioWorkspaceDocument(
        id = id,
        editorId = ApiStudioEditorId.GRPC,
        requestKind = RequestKindId.GRPC,
        name = "Untitled gRPC Request",
        nameOrigin = RequestNameOrigin.GENERATED,
        badgeLabel = "gRPC",
        payloadVersion = 1,
        payload = byteArrayOf(),
        location = location,
    )

private fun ApiStudioWorkspaceDocument.rebuild(
    name: String = this.name,
    nameOrigin: RequestNameOrigin = this.nameOrigin,
    requestKind: RequestKindId = this.requestKind,
    badgeLabel: String = this.badgeLabel,
    payloadVersion: Int = this.payloadVersion,
    payload: ByteArray = copyPayload(),
    location: ApiStudioDocumentLocation = this.location,
): ApiStudioWorkspaceDocument = ApiStudioWorkspaceDocument(
    id = id,
    editorId = editorId,
    requestKind = requestKind,
    name = name,
    nameOrigin = nameOrigin,
    badgeLabel = badgeLabel,
    payloadVersion = payloadVersion,
    payload = payload,
    location = location,
)
