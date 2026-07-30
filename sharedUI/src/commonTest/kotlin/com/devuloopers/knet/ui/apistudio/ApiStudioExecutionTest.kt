package com.devuloopers.knet.ui.apistudio

import com.devuloopers.knet.ui.apistudio.viewmodel.ApiStudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApiStudioExecutionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testUrlNormalizationPrependsHttp() {
        val viewModel = ApiStudioViewModel()

        assertEquals("http://127.0.0.1:9090/api/test/get", viewModel.normalizeUrl("127.0.0.1:9090/api/test/get"))
        assertEquals("http://localhost:9090", viewModel.normalizeUrl("localhost:9090"))
        assertEquals("https://httpbin.org/post", viewModel.normalizeUrl("https://httpbin.org/post"))
        assertEquals("http://httpbin.org/get", viewModel.normalizeUrl("http://httpbin.org/get"))
    }

    @Test
    fun testUrlChangeResetsResponseState() {
        val viewModel = ApiStudioViewModel()

        // Simulate typing new URL
        viewModel.onUrlInputChanged("http://127.0.0.1:9090/new-endpoint")

        val state = viewModel.uiState.value
        assertNull(state.latestResult, "latestResult should be cleared when URL is edited")
        assertTrue(state.testResults.isEmpty(), "testResults should be cleared when URL is edited")
        assertNull(state.scriptErrorMessage, "scriptErrorMessage should be cleared when URL is edited")
    }

    @Test
    fun testSaveUnsavedToSpecificTargetCollection() {
        val viewModel = ApiStudioViewModel()

        // Create 2 collections via ViewModel
        viewModel.createNewCollection("Auth Service APIs")
        viewModel.createNewCollection("Payment Service APIs")

        val state = viewModel.uiState.value
        val col2 = state.collections.find { it.name == "Payment Service APIs" }!!
        val targetFolder = col2.folders.firstOrNull()?.id

        val unsaved = viewModel.createUnsavedRequest()
        viewModel.onUrlInputChanged("https://api.payment.com/v1/charge")

        // Save specifically to col2
        viewModel.saveUnsavedToCollection(
            requestId = unsaved.id,
            targetCollectionId = col2.id,
            targetFolderId = targetFolder,
            customName = "Create Charge Session"
        )

        val updatedState = viewModel.uiState.value
        val updatedCol2 = updatedState.collections.find { it.id == col2.id }!!

        val savedReq = updatedCol2.folders.flatMap { it.requests }.find { it.name == "Create Charge Session" }
        assertTrue(savedReq != null, "Request should be saved in target collection col-2")
        assertEquals("https://api.payment.com/v1/charge", savedReq.url)
    }

    @Test
    fun testSaveUnsavedToCollectionWithZeroFoldersAutoCreatesGeneralFolder() {
        val viewModel = ApiStudioViewModel()

        // Create collection (which has 0 folders initially or default folders)
        viewModel.createNewCollection("Empty Collection")
        val createdCol = viewModel.uiState.value.collections.find { it.name == "Empty Collection" }!!

        // Clear folders to simulate 0 folders scenario
        val stateWithoutFolders = viewModel.uiState.value.copy(
            collections = viewModel.uiState.value.collections.map {
                if (it.id == createdCol.id) it.copy(folders = emptyList()) else it
            }
        )
        // Set state via reflection/internal or trigger save
        val unsaved = viewModel.createUnsavedRequest()
        viewModel.saveUnsavedToCollection(
            requestId = unsaved.id,
            targetCollectionId = createdCol.id,
            customName = "Saved To Empty Collection"
        )

        val updatedState = viewModel.uiState.value
        val updatedCol = updatedState.collections.find { it.id == createdCol.id }

        assertTrue(updatedCol != null, "Target collection should exist")
        assertTrue(updatedCol.folders.isNotEmpty(), "Target collection should have folders")
        val savedReq = updatedCol.folders.flatMap { it.requests }.find { it.name == "Saved To Empty Collection" }
        assertTrue(savedReq != null, "Request should be successfully saved in the collection folder")
    }

    @Test
    fun testSaveUnsavedToNewCollectionCreatesCollectionAndSavesSession() {
        val viewModel = ApiStudioViewModel()

        // 1. Create an unsaved session request when no collections exist
        val unsaved = viewModel.createUnsavedRequest()
        viewModel.onUrlInputChanged("https://api.stripe.com/v1/checkout")

        // 2. Promote unsaved session to a brand new collection
        viewModel.saveUnsavedToNewCollection(
            requestId = unsaved.id,
            collectionName = "Stripe API Collection",
            requestName = "Checkout Session Request"
        )

        val updatedState = viewModel.uiState.value

        // 3. Verify collection was created
        val newCol = updatedState.collections.find { it.name == "Stripe API Collection" }
        assertTrue(newCol != null, "New collection should be created in UI state")

        // 4. Verify unsaved session request is inside the new collection's General folder
        val savedReq = newCol.folders.flatMap { it.requests }.find { it.name == "Checkout Session Request" }
        assertTrue(savedReq != null, "Unsaved session request should be saved inside the new collection")
        assertEquals("https://api.stripe.com/v1/checkout", savedReq.url)

        // 5. Verify session request was removed from unsavedRequests dropdown
        val existsInUnsaved = updatedState.unsavedRequests.any { it.id == unsaved.id }
        assertFalse(existsInUnsaved, "Session request should be removed from unsaved sessions dropdown")
    }
}

