package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceContent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocumentStore
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import kotlinx.coroutines.flow.Flow

public class ObserveApiStudioWorkspaceDocumentsUseCase(
    private val store: ApiStudioWorkspaceDocumentStore,
) {
    public fun execute(): Flow<List<ApiStudioWorkspaceDocument>> = store.observeDocuments()
}

public class GetApiStudioWorkspaceDocumentUseCase(
    private val store: ApiStudioWorkspaceDocumentStore,
) {
    public suspend fun execute(id: String): ApiStudioWorkspaceDocument? = store.document(id)
}

public class CreateApiStudioWorkspaceDocumentUseCase(
    private val store: ApiStudioWorkspaceDocumentStore,
) {
    public suspend fun execute(document: ApiStudioWorkspaceDocument): Unit = store.createDocument(document)
}

public class UpdateApiStudioWorkspaceContentUseCase(
    private val store: ApiStudioWorkspaceDocumentStore,
) {
    public suspend fun execute(id: String, content: ApiStudioWorkspaceContent): Unit =
        store.updateContent(id, content)
}

public class DeleteApiStudioWorkspaceDocumentUseCase(
    private val store: ApiStudioWorkspaceDocumentStore,
) {
    public suspend fun execute(id: String): Unit = store.deleteDocument(id)
}

public class RenameApiStudioWorkspaceDocumentUseCase(
    private val store: ApiStudioWorkspaceDocumentStore,
) {
    public suspend fun execute(id: String, name: String): Unit = store.renameDocument(id, name.trim())
}

public class PromoteApiStudioWorkspaceDocumentUseCase(
    private val store: ApiStudioWorkspaceDocumentStore,
) {
    public suspend fun executeExisting(
        id: String,
        name: String,
        nameOrigin: RequestNameOrigin,
        collectionId: String,
        folderId: String,
    ): Unit = store.promoteToExistingCollection(
        id = id,
        name = name.trim(),
        nameOrigin = nameOrigin,
        collectionId = collectionId,
        folderId = folderId,
    )

    public suspend fun executeNew(
        id: String,
        name: String,
        nameOrigin: RequestNameOrigin,
        collection: ApiCollection,
        folder: CollectionFolder,
    ): Unit = store.promoteToNewCollection(
        id = id,
        name = name.trim(),
        nameOrigin = nameOrigin,
        collection = collection,
        folder = folder,
    )
}
