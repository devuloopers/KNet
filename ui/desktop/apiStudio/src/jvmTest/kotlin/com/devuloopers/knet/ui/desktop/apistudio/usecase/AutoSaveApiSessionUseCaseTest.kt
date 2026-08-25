package com.devuloopers.knet.ui.desktop.apistudio.usecase

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.domain.collection.usecase.SaveUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.UpdateRequestInCollectionUseCase
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.payload.StructuredPayloadState
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoSaveApiSessionUseCaseTest {

    private class FakeCollectionsRepository : CollectionsRepository {
        val savedUnsavedRequests = mutableListOf<SavedApiRequest>()
        val updatedSavedRequests = mutableListOf<Triple<String, String, SavedApiRequest>>()

        override fun observeCollections(): Flow<List<ApiCollection>> = emptyFlow()
        override suspend fun getCollectionById(id: String): ApiCollection? = null
        override suspend fun getRequestById(id: String): SavedApiRequest? = null
        override suspend fun saveCollection(collection: ApiCollection) {}
        override suspend fun deleteCollection(collectionId: String) {}
        override suspend fun saveFolder(collectionId: String, folder: CollectionFolder) {}
        override suspend fun deleteFolder(folderId: String) {}
        override suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest) {
            updatedSavedRequests.add(Triple(collectionId, folderId, request))
        }
        override suspend fun deleteRequest(requestId: String) {}
        override fun observeUnsavedRequests(): Flow<List<SavedApiRequest>> = emptyFlow()
        override suspend fun saveUnsavedRequest(request: SavedApiRequest) {
            savedUnsavedRequests.add(request)
        }
        override suspend fun deleteUnsavedRequest(requestId: String) {}
        override suspend fun saveUnsavedToNewCollectionTx(
            collection: ApiCollection,
            folder: CollectionFolder,
            request: SavedApiRequest,
            unsavedRequestIdToDelete: String
        ) {}
        override suspend fun saveUnsavedToExistingCollectionTx(
            collectionId: String,
            folderId: String,
            request: SavedApiRequest,
            unsavedRequestIdToDelete: String
        ) {}
    }

    @Test
    fun `auto-save unsaved GraphQL draft session persists GraphQL body type and payload`() = runTest {
        val repository = FakeCollectionsRepository()
        val saveUnsavedUseCase = SaveUnsavedRequestUseCase(repository)
        val updateSavedUseCase = UpdateRequestInCollectionUseCase(repository)

        val useCase = AutoSaveApiSessionUseCase(
            saveUnsavedRequestUseCase = saveUnsavedUseCase,
            updateRequestInCollectionUseCase = updateSavedUseCase
        )

        val graphQlState = GraphQlState(
            payload = StructuredPayloadState.GraphQL(
                queryText = "query GetUser { user { id name } }",
                operationName = "GetUser",
                variablesText = "{\n  \"id\": \"123\"\n}",
            ),
        )
        val bodyState = RequestBodyState(
            mode = RequestBodyMode.GRAPHQL,
            graphQlState = graphQlState,
            payloadText = "{\"query\":\"query GetUser { user { id name } }\",\"operationName\":\"GetUser\",\"variables\":{\"id\":\"123\"}}"
        )
        val editorState = RequestEditorState(
            url = "https://api.example.com/graphql",
            method = com.devuloopers.knet.traffic.model.http.HttpMethod.POST,
            bodyState = bodyState
        )

        useCase.execute(
            sessionContext = SessionContext.UnsavedDraft("draft_100"),
            documentTitle = "GraphQL request",
            nameOrigin = RequestNameOrigin.GENERATED,
            editorState = editorState
        )

        assertEquals(1, repository.savedUnsavedRequests.size)
        val savedReq = repository.savedUnsavedRequests.first()
        assertEquals("draft_100", savedReq.id)
        assertEquals(RequestNameOrigin.GENERATED, savedReq.nameOrigin)
        assertEquals(RequestBodyType.GRAPHQL, savedReq.body.type)
        assertTrue(savedReq.body.content.contains("query GetUser"))
    }

    @Test
    fun `auto-save saved collection request delegates to UpdateRequestInCollectionUseCase`() = runTest {
        val repository = FakeCollectionsRepository()
        val saveUnsavedUseCase = SaveUnsavedRequestUseCase(repository)
        val updateSavedUseCase = UpdateRequestInCollectionUseCase(repository)

        val useCase = AutoSaveApiSessionUseCase(
            saveUnsavedRequestUseCase = saveUnsavedUseCase,
            updateRequestInCollectionUseCase = updateSavedUseCase
        )

        val editorState = RequestEditorState(
            url = "https://api.example.com/users",
            method = com.devuloopers.knet.traffic.model.http.HttpMethod.GET
        )

        useCase.execute(
            sessionContext = SessionContext.SavedRequest(requestId = "req_1", collectionId = "coll_1", folderId = "fld_1"),
            documentTitle = "Get users",
            nameOrigin = RequestNameOrigin.USER_DEFINED,
            editorState = editorState
        )

        assertEquals(0, repository.savedUnsavedRequests.size)
        assertEquals(1, repository.updatedSavedRequests.size)
        val (collId, fldId, req) = repository.updatedSavedRequests.first()
        assertEquals("coll_1", collId)
        assertEquals("fld_1", fldId)
        assertEquals("req_1", req.id)
        assertEquals("Get users", req.name)
    }
}
