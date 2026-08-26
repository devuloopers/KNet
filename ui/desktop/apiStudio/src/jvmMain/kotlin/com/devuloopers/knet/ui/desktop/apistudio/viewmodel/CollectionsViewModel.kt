package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.application.usecase.apistudio.CreateApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.DeleteApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ObserveApiStudioWorkspaceDocumentsUseCase
import com.devuloopers.knet.application.usecase.apistudio.PromoteApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.RenameApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.CreateCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteSavedSessionUseCase
import com.devuloopers.knet.domain.collection.usecase.DeleteUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.ObserveCollectionsUseCase
import com.devuloopers.knet.domain.collection.usecase.ObserveUnsavedRequestsUseCase
import com.devuloopers.knet.domain.collection.usecase.RenameCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.UpdateRequestInCollectionUseCase
import com.devuloopers.knet.ui.desktop.apistudio.model.CollectionsState
import com.devuloopers.knet.ui.desktop.apistudio.model.SidebarTreeMapper
import com.devuloopers.knet.ui.desktop.apistudio.dialog.CollectionSaveMode
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * Owns the protocol-neutral collection-sidebar projection plus collection/document lifecycle dialogs.
 *
 * HTTP requests retain their canonical typed repository. Contributed editors publish opaque, versioned workspace
 * documents through application use cases. This ViewModel merges both sources but never decodes editor payloads;
 * hydration, auto-save, validation, and execution remain owned by the selected editor.
 *
 * @param observeCollectionsUseCase Observes saved collection trees.
 * @param observeUnsavedRequestsUseCase Observes durable draft summaries.
 * @param describeRequestUseCase Resolves semantic badge metadata from canonical requests.
 * @param deleteUnsavedRequestUseCase Deletes a draft selected by the user.
 * @param createCollectionUseCase Creates an empty saved collection.
 * @param deleteCollectionUseCase Deletes a saved collection.
 * @param renameCollectionUseCase Renames a saved collection.
 * @param updateRequestInCollectionUseCase Updates a saved request during sidebar rename.
 * @param deleteSavedSessionUseCase Deletes a saved request.
 * @param ioDispatcher Dispatcher used for Room-backed operations.
 */
class CollectionsViewModel(
    observeCollectionsUseCase: ObserveCollectionsUseCase,
    observeUnsavedRequestsUseCase: ObserveUnsavedRequestsUseCase,
    observeWorkspaceDocumentsUseCase: ObserveApiStudioWorkspaceDocumentsUseCase,
    private val describeRequestUseCase: DescribeRequestUseCase,
    private val deleteUnsavedRequestUseCase: DeleteUnsavedRequestUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val renameCollectionUseCase: RenameCollectionUseCase,
    private val updateRequestInCollectionUseCase: UpdateRequestInCollectionUseCase,
    private val deleteSavedSessionUseCase: DeleteSavedSessionUseCase,
    private val createWorkspaceDocumentUseCase: CreateApiStudioWorkspaceDocumentUseCase,
    private val deleteWorkspaceDocumentUseCase: DeleteApiStudioWorkspaceDocumentUseCase,
    private val renameWorkspaceDocumentUseCase: RenameApiStudioWorkspaceDocumentUseCase,
    private val promoteWorkspaceDocumentUseCase: PromoteApiStudioWorkspaceDocumentUseCase,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsState())

    /** Immutable sidebar and collection-dialog state. */
    val uiState: StateFlow<CollectionsState> = _uiState.asStateFlow()

    init {
        combine(
            observeCollectionsUseCase.execute(),
            observeUnsavedRequestsUseCase.execute(),
            observeWorkspaceDocumentsUseCase.execute(),
        ) { collections, drafts, workspaceDocuments ->
            SidebarTreeMapper.mergeWorkspaceDocuments(
                folders = SidebarTreeMapper.toSidebarFolders(collections, describeRequestUseCase::execute),
                httpDrafts = SidebarTreeMapper.toSidebarUnsavedRequests(drafts, describeRequestUseCase::execute),
                workspaceDocuments = workspaceDocuments,
            )
        }
            .flowOn(ioDispatcher)
            .onEach { (collections, drafts) ->
                _uiState.update {
                    it.copy(
                        collections = collections,
                        unsavedRequests = drafts,
                        isLoading = false
                    )
                }
            }
            .catch { failure ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = failure.message ?: "Collections could not be loaded."
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Deletes one durable unsaved draft and invokes [onDeleted] only after persistence succeeds. */
    fun deleteUnsavedRequest(request: SidebarRequestItem, onDeleted: () -> Unit = {}) = launchPersistence(onDeleted) {
        if (request.workspaceDocument == null) {
            deleteUnsavedRequestUseCase.execute(request.id)
        } else {
            deleteWorkspaceDocumentUseCase.execute(request.id)
        }
    }

    /** Persists an editor-owned incomplete document before selecting it in the workspace. */
    fun createWorkspaceDraft(document: ApiStudioWorkspaceDocument, onCreated: () -> Unit = {}) =
        launchPersistence(onCreated) {
            createWorkspaceDocumentUseCase.execute(document)
        }

    /** Opens the create-collection dialog. */
    fun openCreateCollectionDialog() {
        _uiState.update { it.copy(isCreateCollectionDialogOpen = true) }
    }

    /** Closes the create-collection dialog. */
    fun closeCreateCollectionDialog() {
        _uiState.update { it.copy(isCreateCollectionDialogOpen = false) }
    }

    /** Creates a collection and closes its dialog after successful persistence. */
    fun createCollection(collectionName: String) = launchPersistence(
        onSuccess = { _uiState.update { it.copy(isCreateCollectionDialogOpen = false) } }
    ) {
        createCollectionUseCase.execute(collectionName)
    }

    /** Deletes a collection and invokes [onDeleted] only after persistence succeeds. */
    fun deleteCollection(collectionId: String, onDeleted: () -> Unit = {}) = launchPersistence(onDeleted) {
        deleteCollectionUseCase.execute(collectionId)
    }

    /** Opens collection rename state for the supplied collection. */
    fun openRenameDialog(collectionId: String, currentName: String) {
        _uiState.update {
            it.copy(
                isRenameDialogOpen = true,
                renamingCollectionId = collectionId,
                renamingCollectionName = currentName
            )
        }
    }

    /** Clears collection rename state. */
    fun closeRenameDialog() {
        _uiState.update {
            it.copy(
                isRenameDialogOpen = false,
                renamingCollectionId = null,
                renamingCollectionName = ""
            )
        }
    }

    /** Persists a collection rename and closes the dialog. */
    fun renameCollection(collectionId: String, newName: String) = launchPersistence(
        onSuccess = ::closeRenameDialog
    ) {
            renameCollectionUseCase.execute(collectionId, newName)
    }

    /** Opens request rename state for a saved request. */
    fun openRenameRequestDialog(request: SidebarRequestItem) {
        _uiState.update { it.copy(isRenameRequestDialogOpen = true, renamingRequestItem = request) }
    }

    /** Clears saved-request rename state. */
    fun closeRenameRequestDialog() {
        _uiState.update { it.copy(isRenameRequestDialogOpen = false, renamingRequestItem = null) }
    }

    /** Renames a saved request using its complete persisted document rather than a partial sidebar snapshot. */
    fun renameSavedRequest(
        request: SidebarRequestItem,
        newName: String,
        onRenamed: () -> Unit = {}
    ) {
        launchPersistence(
            onSuccess = {
                closeRenameRequestDialog()
                onRenamed()
            }
        ) {
            val workspaceDocument = request.workspaceDocument
            if (workspaceDocument == null) {
                updateRequestInCollectionUseCase.execute(
                    collectionId = requireNotNull(request.collectionId),
                    folderId = requireNotNull(request.folderId),
                    request = requireNotNull(request.document).copy(
                        name = newName.trim(),
                        nameOrigin = RequestNameOrigin.USER_DEFINED,
                    ),
                )
            } else {
                renameWorkspaceDocumentUseCase.execute(workspaceDocument.id, newName)
            }
        }
    }

    /** Deletes a saved request and invokes [onDeleted] only after persistence succeeds. */
    fun deleteSavedRequest(request: SidebarRequestItem, onDeleted: () -> Unit = {}) = launchPersistence(onDeleted) {
        if (request.workspaceDocument == null) {
            deleteSavedSessionUseCase.execute(request.id)
        } else {
            deleteWorkspaceDocumentUseCase.execute(request.id)
        }
    }

    /** Promotes any contributed editor draft into the same collection hierarchy used by HTTP requests. */
    fun promoteWorkspaceDocument(
        request: SidebarRequestItem,
        requestName: String,
        mode: CollectionSaveMode,
        selectedFolder: SidebarFolderItem?,
        newCollectionName: String,
        onSaved: () -> Unit = {},
    ) {
        val document = request.workspaceDocument ?: return
        val submittedName = requestName.trim()
        val nameOrigin = if (submittedName == document.name) document.nameOrigin else RequestNameOrigin.USER_DEFINED
        launchPersistence(onSaved) {
            when (mode) {
                CollectionSaveMode.EXISTING_COLLECTION -> {
                    val folder = requireNotNull(selectedFolder) { "Choose a collection before saving the request." }
                    promoteWorkspaceDocumentUseCase.executeExisting(
                        id = document.id,
                        name = submittedName,
                        nameOrigin = nameOrigin,
                        collectionId = folder.collectionId,
                        folderId = folder.id,
                    )
                }

                CollectionSaveMode.NEW_COLLECTION -> {
                    val collectionId = "col_${Uuid.random()}"
                    val folderId = "fld_${Uuid.random()}"
                    promoteWorkspaceDocumentUseCase.executeNew(
                        id = document.id,
                        name = submittedName,
                        nameOrigin = nameOrigin,
                        collection = ApiCollection(collectionId, newCollectionName.trim()),
                        folder = CollectionFolder(folderId, "Requests"),
                    )
                }
            }
        }
    }

    private fun launchPersistence(
        onSuccess: () -> Unit = {},
        operation: suspend () -> Unit
    ) {
        _uiState.update { it.copy(errorMessage = null) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { operation() }
                onSuccess()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(errorMessage = failure.message ?: "Collection changes could not be saved.")
                }
            }
        }
    }
}
