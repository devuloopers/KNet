package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.domain.collection.usecase.ObserveCollectionsUseCase
import com.devuloopers.knet.domain.collection.usecase.UpdateRequestInCollectionUseCase
import com.devuloopers.knet.ui.desktop.apistudio.dialog.CollectionSaveMode
import com.devuloopers.knet.ui.desktop.apistudio.model.CollectionsState
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.CollectionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun `CollectionsState default values are set correctly`() {
        val state = CollectionsState()
        assertTrue(state.collections.isEmpty())
        assertTrue(state.unsavedRequests.isEmpty())
        assertFalse(state.isSaveDialogOpen)
    }

    @Test
    fun `openSaveDialog and closeSaveDialog update isSaveDialogOpen toggle`() {
        val viewModel = CollectionsViewModel(ioDispatcher = testDispatcher)
        assertFalse(viewModel.uiState.value.isSaveDialogOpen)

        viewModel.openSaveDialog()
        assertTrue(viewModel.uiState.value.isSaveDialogOpen)

        viewModel.closeSaveDialog()
        assertFalse(viewModel.uiState.value.isSaveDialogOpen)
    }

    @Test
    fun `triggerUnsavedAutoSave generates linked ID on initial draft save`() = runTest {
        val viewModel = CollectionsViewModel(ioDispatcher = testDispatcher)
        val editorState = RequestEditorState(url = "https://api.knet.dev/v1/ping", method = "GET")

        var assignedId: String? = null
        var assignedTitle: String? = null

        viewModel.triggerUnsavedAutoSave(
            editorState = editorState,
            onLinkedIdAssigned = { id, title ->
                assignedId = id
                assignedTitle = title
            }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(assignedId?.isNotBlank() == true)
        assertEquals("Untitled Request", assignedTitle)
    }

    @Test
    fun `saveRequestToCollection closes dialog and triggers onSaved callback`() = runTest {
        val viewModel = CollectionsViewModel(ioDispatcher = testDispatcher)
        viewModel.openSaveDialog()
        assertTrue(viewModel.uiState.value.isSaveDialogOpen)

        var savedRequestId: String? = null
        val editorState = RequestEditorState(url = "https://api.knet.dev/v1/users", method = "POST")

        viewModel.saveRequestToCollection(
            requestName = "Create User",
            mode = CollectionSaveMode.NEW_COLLECTION,
            selectedCollectionId = null,
            newCollectionName = "User API Suite",
            currentEditor = editorState,
            onSaved = { id -> savedRequestId = id }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaveDialogOpen)
        assertTrue(savedRequestId?.startsWith("req_") == true)
    }

    @Test
    fun `triggerSavedRequestAutoSave preserves existing saved request name`() = runTest {
        var updatedRequest: SavedApiRequest? = null
        val fakeRepo = object : CollectionsRepository {
            override fun observeCollections(): Flow<List<ApiCollection>> = flowOf(
                listOf(
                    ApiCollection(
                        id = "col_1",
                        name = "My Collection",
                        folders = listOf(
                            CollectionFolder(
                                id = "fld_1",
                                name = "Requests",
                                requests = listOf(
                                    SavedApiRequest(
                                        id = "req_100",
                                        name = "Unsaved Session 1",
                                        method = HttpMethod.GET,
                                        url = "https://api.knet.dev/v1/ping"
                                    )
                                )
                            )
                        )
                    )
                )
            )
            override suspend fun getCollectionById(id: String): ApiCollection? = null
            override suspend fun saveCollection(collection: ApiCollection) {}
            override suspend fun deleteCollection(collectionId: String) {}
            override suspend fun saveFolder(collectionId: String, folder: CollectionFolder) {}
            override suspend fun deleteFolder(folderId: String) {}
            override suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest) {
                updatedRequest = request
            }
            override suspend fun deleteRequest(requestId: String) {}
            override fun observeUnsavedRequests(): Flow<List<SavedApiRequest>> = emptyFlow()
            override suspend fun saveUnsavedRequest(request: SavedApiRequest) {}
            override suspend fun deleteUnsavedRequest(requestId: String) {}
            override suspend fun saveUnsavedToNewCollectionTx(
                collection: ApiCollection,
                folder: CollectionFolder,
                request: SavedApiRequest,
                unsavedRequestIdToDelete: String
            ) {}
        }

        val observeCollectionsUseCase = ObserveCollectionsUseCase(fakeRepo)
        val updateRequestInCollectionUseCase = UpdateRequestInCollectionUseCase(fakeRepo)

        val viewModel = CollectionsViewModel(
            observeCollectionsUseCase = observeCollectionsUseCase,
            updateRequestInCollectionUseCase = updateRequestInCollectionUseCase,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify initial state has the collection request with name "Unsaved Session 1"
        assertEquals("Unsaved Session 1", viewModel.uiState.value.collections.first().requests.first().name)

        // Trigger auto save with edited URL and method
        val editorState = RequestEditorState(url = "https://api.knet.dev/v1/updated", method = "POST")
        viewModel.triggerSavedRequestAutoSave(
            requestId = "req_100",
            collectionId = "col_1",
            folderId = "fld_1",
            editorState = editorState
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify updatedRequest retained original name "Unsaved Session 1" instead of "req_100"
        assertEquals("req_100", updatedRequest?.id)
        assertEquals("Unsaved Session 1", updatedRequest?.name)
        assertEquals("https://api.knet.dev/v1/updated", updatedRequest?.url)
        assertEquals(HttpMethod.POST, updatedRequest?.method)
    }

    @Test
    fun `observeCollections maps collection name directly without slash concatenation`() = runTest {
        val fakeRepo = object : CollectionsRepository {
            override fun observeCollections(): Flow<List<ApiCollection>> = flowOf(
                listOf(
                    ApiCollection(
                        id = "col_test",
                        name = "Test 1",
                        folders = listOf(
                            CollectionFolder(
                                id = "col_test",
                                name = "Test 1",
                                requests = emptyList()
                            )
                        )
                    )
                )
            )
            override suspend fun getCollectionById(id: String): ApiCollection? = null
            override suspend fun saveCollection(collection: ApiCollection) {}
            override suspend fun deleteCollection(collectionId: String) {}
            override suspend fun saveFolder(collectionId: String, folder: CollectionFolder) {}
            override suspend fun deleteFolder(folderId: String) {}
            override suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest) {}
            override suspend fun deleteRequest(requestId: String) {}
            override fun observeUnsavedRequests(): Flow<List<SavedApiRequest>> = emptyFlow()
            override suspend fun saveUnsavedRequest(request: SavedApiRequest) {}
            override suspend fun deleteUnsavedRequest(requestId: String) {}
            override suspend fun saveUnsavedToNewCollectionTx(
                collection: ApiCollection,
                folder: CollectionFolder,
                request: SavedApiRequest,
                unsavedRequestIdToDelete: String
            ) {}
        }

        val viewModel = CollectionsViewModel(
            observeCollectionsUseCase = ObserveCollectionsUseCase(fakeRepo),
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val folderItem = viewModel.uiState.value.collections.first()
        assertEquals("Test 1", folderItem.name)
    }

    @Test
    fun `renameSavedRequest updates request name in persistent collection`() = runTest {
        var updatedReq: SavedApiRequest? = null
        val fakeRepo = object : CollectionsRepository {
            override fun observeCollections(): Flow<List<ApiCollection>> = emptyFlow()
            override suspend fun getCollectionById(id: String): ApiCollection? = null
            override suspend fun saveCollection(collection: ApiCollection) {}
            override suspend fun deleteCollection(collectionId: String) {}
            override suspend fun saveFolder(collectionId: String, folder: CollectionFolder) {}
            override suspend fun deleteFolder(folderId: String) {}
            override suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest) {
                updatedReq = request
            }
            override suspend fun deleteRequest(requestId: String) {}
            override fun observeUnsavedRequests(): Flow<List<SavedApiRequest>> = emptyFlow()
            override suspend fun saveUnsavedRequest(request: SavedApiRequest) {}
            override suspend fun deleteUnsavedRequest(requestId: String) {}
            override suspend fun saveUnsavedToNewCollectionTx(
                collection: ApiCollection,
                folder: CollectionFolder,
                request: SavedApiRequest,
                unsavedRequestIdToDelete: String
            ) {}
        }

        val updateUseCase = UpdateRequestInCollectionUseCase(fakeRepo)
        val viewModel = CollectionsViewModel(
            updateRequestInCollectionUseCase = updateUseCase,
            ioDispatcher = testDispatcher
        )

        val requestItem = com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem(
            id = "req_123",
            name = "Old Name",
            method = "GET",
            collectionId = "col_1",
            folderId = "fld_1"
        )

        viewModel.openRenameRequestDialog(requestItem)
        assertTrue(viewModel.uiState.value.isRenameRequestDialogOpen)
        assertEquals("Old Name", viewModel.uiState.value.renamingRequestItem?.name)

        viewModel.renameSavedRequest(requestItem, "New Request Name")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRenameRequestDialogOpen)
        assertEquals("New Request Name", updatedReq?.name)
        assertEquals("req_123", updatedReq?.id)
    }

    @Test
    fun `deleteSavedRequest triggers deletion via DeleteSavedSessionUseCase`() = runTest {
        var deletedReqId: String? = null
        val fakeRepo = object : CollectionsRepository {
            override fun observeCollections(): Flow<List<ApiCollection>> = emptyFlow()
            override suspend fun getCollectionById(id: String): ApiCollection? = null
            override suspend fun saveCollection(collection: ApiCollection) {}
            override suspend fun deleteCollection(collectionId: String) {}
            override suspend fun saveFolder(collectionId: String, folder: CollectionFolder) {}
            override suspend fun deleteFolder(folderId: String) {}
            override suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest) {}
            override suspend fun deleteRequest(requestId: String) {
                deletedReqId = requestId
            }
            override fun observeUnsavedRequests(): Flow<List<SavedApiRequest>> = emptyFlow()
            override suspend fun saveUnsavedRequest(request: SavedApiRequest) {}
            override suspend fun deleteUnsavedRequest(requestId: String) {}
            override suspend fun saveUnsavedToNewCollectionTx(
                collection: ApiCollection,
                folder: CollectionFolder,
                request: SavedApiRequest,
                unsavedRequestIdToDelete: String
            ) {}
        }

        val deleteUseCase = com.devuloopers.knet.domain.collection.usecase.DeleteSavedSessionUseCase(fakeRepo)
        val viewModel = CollectionsViewModel(
            deleteSavedSessionUseCase = deleteUseCase,
            ioDispatcher = testDispatcher
        )

        viewModel.deleteSavedRequest("req_999")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("req_999", deletedReqId)
    }
}
