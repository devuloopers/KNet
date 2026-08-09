package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.collection.model.*
import com.devuloopers.knet.domain.collection.usecase.*
import com.devuloopers.knet.ui.desktop.apistudio.dialog.CollectionSaveMode
import com.devuloopers.knet.ui.desktop.apistudio.model.CollectionsState
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Dedicated ViewModel managing UDF state for API collections sidebar, unsaved request sessions,
 * and collection promotion modal dialogs.
 *
 * SRP: Completely decoupled from HTTP network execution and payload formatting.
 *
 * @param observeCollectionsUseCase Use case for observing saved API collections from Room DB.
 * @param observeUnsavedRequestsUseCase Use case for observing active unsaved request sessions.
 * @param saveUnsavedRequestUseCase Use case for persisting unsaved session edits.
 * @param deleteUnsavedRequestUseCase Use case for deleting unsaved sessions.
 * @param createCollectionUseCase Use case for creating new API collections.
 * @param deleteCollectionUseCase Use case for deleting saved API collections.
 * @param renameCollectionUseCase Use case for renaming saved API collections.
 * @param saveRequestToCollectionUseCase Use case for promoting unsaved sessions into persistent collections.
 * @param ioDispatcher Coroutine dispatcher for database storage I/O.
 */
class CollectionsViewModel(
    private val observeCollectionsUseCase: ObserveCollectionsUseCase? = null,
    private val observeUnsavedRequestsUseCase: ObserveUnsavedRequestsUseCase? = null,
    private val saveUnsavedRequestUseCase: SaveUnsavedRequestUseCase? = null,
    private val deleteUnsavedRequestUseCase: DeleteUnsavedRequestUseCase? = null,
    private val createCollectionUseCase: CreateCollectionUseCase? = null,
    private val deleteCollectionUseCase: DeleteCollectionUseCase? = null,
    private val renameCollectionUseCase: RenameCollectionUseCase? = null,
    private val saveRequestToCollectionUseCase: SaveRequestToCollectionUseCase? = null,
    private val updateRequestInCollectionUseCase: UpdateRequestInCollectionUseCase? = null,
    private val deleteSavedSessionUseCase: DeleteSavedSessionUseCase? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsState())
    val uiState: StateFlow<CollectionsState> = _uiState.asStateFlow()

    init {
        // Observe persistent saved collections flow from Room DB
        observeCollectionsUseCase?.execute()?.onEach { collectionList ->
            val sidebarFolders = collectionList.flatMap { collection ->
                if (collection.folders.isNotEmpty()) {
                    collection.folders.map { folder ->
                        val displayName =
                            if (collection.folders.size <= 1 || folder.name == "Requests" || folder.name == collection.name) {
                                collection.name
                            } else {
                                folder.name
                            }
                        SidebarFolderItem(
                            id = folder.id,
                            collectionId = collection.id,
                            name = displayName,
                            isExpanded = folder.isExpanded,
                            requests = folder.requests.map { req ->
                                SidebarRequestItem(
                                    id = req.id,
                                    name = req.name,
                                    method = req.methodString,
                                    url = req.url,
                                    headers = req.headers.map { it.key to it.value },
                                    bodyPayload = req.body.content,
                                    bodyType = req.body.type,
                                    preRequestScript = req.scripts.preRequest,
                                    testScript = req.scripts.test,
                                    collectionId = collection.id,
                                    folderId = folder.id
                                )
                            }
                        )
                    }
                } else {
                    listOf(
                        SidebarFolderItem(
                            id = collection.id,
                            collectionId = collection.id,
                            name = collection.name,
                            isExpanded = true,
                            requests = emptyList()
                        )
                    )
                }
            }
            _uiState.update { it.copy(collections = sidebarFolders) }
        }?.launchIn(viewModelScope)

        // Observe active unsaved request session flow from Room DB
        observeUnsavedRequestsUseCase?.execute()?.onEach { unsavedList ->
            val sidebarItems = unsavedList.map { req ->
                SidebarRequestItem(
                    id = req.id,
                    name = req.name,
                    method = req.methodString,
                    url = req.url,
                    headers = req.headers.map { it.key to it.value },
                    bodyPayload = req.body.content,
                    bodyType = req.body.type,
                    preRequestScript = req.scripts.preRequest,
                    testScript = req.scripts.test
                )
            }
            _uiState.update { it.copy(unsavedRequests = sidebarItems) }
        }?.launchIn(viewModelScope)
    }

    /**
     * Auto-saves or updates an unsaved request draft in persistent storage when the user edits request parameters.
     *
     * @param editorState Current active request editor state.
     * @param onLinkedIdAssigned Callback fired if a new unsaved session ID is generated.
     */
    fun triggerUnsavedAutoSave(
        editorState: RequestEditorState,
        onLinkedIdAssigned: (String, String) -> Unit
    ) {
        val currentUnsavedRequests = _uiState.value.unsavedRequests

        // Eagerly generate and assign the session ID to provide instant UI feedback
        val effectiveLinkedId = editorState.linkedUnsavedId ?: "unsaved_${System.currentTimeMillis()}"
        val sessionName = if (editorState.linkedUnsavedId != null) {
            currentUnsavedRequests.find { it.id == effectiveLinkedId }?.name
                ?: "Unsaved Session ${currentUnsavedRequests.size + 1}"
        } else {
            val name = "Unsaved Session ${currentUnsavedRequests.size + 1}"
            onLinkedIdAssigned(effectiveLinkedId, name) // Instantly update UI on first keystroke
            name
        }

        viewModelScope.launch(ioDispatcher) {
            val httpMethodEnum = try {
                HttpMethod.valueOf(editorState.method.uppercase())
            } catch (_: Exception) {
                HttpMethod.GET
            }

            val savedReq = SavedApiRequest(
                id = effectiveLinkedId,
                name = sessionName,
                method = httpMethodEnum,
                url = editorState.url,
                headers = editorState.headers.map { RequestHeader(it.first, it.second) },
                body = ApiRequestBody(content = editorState.bodyPayload, type = editorState.bodyType),
                scripts = ApiRequestScripts(preRequest = editorState.preRequestScript, test = editorState.testScript)
            )

            saveUnsavedRequestUseCase?.execute(savedReq)
        }
    }

    /**
     * Auto-saves edits to a **saved collection request** in-place directly to Room DB.
     *
     * This is invoked when the user edits a field while a [SessionContext.SavedRequest] is active.
     * It performs a targeted upsert of the existing request record and never creates any unsaved session.
     *
     * @param requestId The ID of the saved request being edited.
     * @param collectionId The ID of the parent collection.
     * @param folderId The ID of the parent folder.
     * @param editorState The current editor state to persist.
     */
    fun triggerSavedRequestAutoSave(
        requestId: String,
        collectionId: String,
        folderId: String,
        editorState: RequestEditorState
    ) {
        viewModelScope.launch(ioDispatcher) {
            val httpMethodEnum = try {
                HttpMethod.valueOf(editorState.method.uppercase())
            } catch (_: Exception) {
                HttpMethod.GET
            }

            // Look up existing request name from active UI state to preserve human-readable title
            val currentSavedName = _uiState.value.collections
                .flatMap { folder -> folder.requests }
                .find { request -> request.id == requestId }
                ?.name
                ?: editorState.linkedUnsavedId
                ?: requestId

            val savedReq = SavedApiRequest(
                id = requestId,
                name = currentSavedName,
                method = httpMethodEnum,
                url = editorState.url,
                headers = editorState.headers.map { RequestHeader(it.first, it.second) },
                body = ApiRequestBody(content = editorState.bodyPayload, type = editorState.bodyType),
                scripts = ApiRequestScripts(preRequest = editorState.preRequestScript, test = editorState.testScript)
            )

            updateRequestInCollectionUseCase?.execute(
                collectionId = collectionId,
                folderId = folderId,
                request = savedReq
            )
        }
    }

    /**
     * Creates a new empty unsaved session and persists it into storage.
     *
     * @param onSuccess Callback executed passing (newUnsavedId, sessionName).
     */
    fun createEmptyUnsavedSession(onSuccess: (String, String) -> Unit) {
        val currentUnsavedRequests = _uiState.value.unsavedRequests
        val sessionNumber = currentUnsavedRequests.size + 1
        val newId = "unsaved_${System.currentTimeMillis()}"
        val sessionName = "Unsaved Session $sessionNumber"

        val savedReq = SavedApiRequest(
            id = newId,
            name = sessionName,
            method = HttpMethod.GET,
            url = ""
        )

        viewModelScope.launch(ioDispatcher) {
            saveUnsavedRequestUseCase?.execute(savedReq)
            onSuccess(newId, sessionName)
        }
    }

    /**
     * Deletes an unsaved request session.
     */
    fun deleteUnsavedRequest(requestId: String) {
        viewModelScope.launch(ioDispatcher) {
            deleteUnsavedRequestUseCase?.execute(requestId)
        }
    }

    /**
     * Opens the Save Request Dialog modal.
     */
    fun openSaveDialog() {
        _uiState.update { it.copy(isSaveDialogOpen = true) }
    }

    /**
     * Closes the Save Request Dialog modal.
     */
    fun closeSaveDialog() {
        _uiState.update { it.copy(isSaveDialogOpen = false) }
    }

    /**
     * Opens the Create Collection Dialog modal.
     */
    fun openCreateCollectionDialog() {
        _uiState.update { it.copy(isCreateCollectionDialogOpen = true) }
    }

    /**
     * Closes the Create Collection Dialog modal.
     */
    fun closeCreateCollectionDialog() {
        _uiState.update { it.copy(isCreateCollectionDialogOpen = false) }
    }

    /**
     * Creates a new API collection suite.
     *
     * @param collectionName Name of the new collection.
     */
    fun createCollection(collectionName: String) {
        viewModelScope.launch(ioDispatcher) {
            createCollectionUseCase?.execute(collectionName)
            _uiState.update { it.copy(isCreateCollectionDialogOpen = false) }
        }
    }

    /**
     * Deletes a saved API collection by ID.
     */
    fun deleteCollection(collectionId: String) {
        viewModelScope.launch(ioDispatcher) {
            deleteCollectionUseCase?.execute(collectionId)
        }
    }

    /**
     * Opens the Rename Collection Dialog modal.
     */
    fun openRenameDialog(collectionId: String, currentName: String) {
        _uiState.update {
            it.copy(
                isRenameDialogOpen = true,
                renamingCollectionId = collectionId,
                renamingCollectionName = currentName
            )
        }
    }

    /**
     * Closes the Rename Collection Dialog modal.
     */
    fun closeRenameDialog() {
        _uiState.update {
            it.copy(
                isRenameDialogOpen = false,
                renamingCollectionId = null,
                renamingCollectionName = ""
            )
        }
    }

    /**
     * Renames a saved API collection suite.
     */
    fun renameCollection(collectionId: String, newName: String) {
        viewModelScope.launch(ioDispatcher) {
            renameCollectionUseCase?.execute(collectionId, newName)
            _uiState.update {
                it.copy(
                    isRenameDialogOpen = false,
                    renamingCollectionId = null,
                    renamingCollectionName = ""
                )
            }
        }
    }

    /**
     * Opens the Rename Request Dialog modal for a saved collection request.
     *
     * @param request The saved request item to rename.
     */
    fun openRenameRequestDialog(request: SidebarRequestItem) {
        _uiState.update {
            it.copy(
                isRenameRequestDialogOpen = true,
                renamingRequestItem = request
            )
        }
    }

    /**
     * Closes the Rename Request Dialog modal.
     */
    fun closeRenameRequestDialog() {
        _uiState.update {
            it.copy(
                isRenameRequestDialogOpen = false,
                renamingRequestItem = null
            )
        }
    }

    /**
     * Renames a saved API request item inside a collection folder.
     *
     * @param request The request item being renamed.
     * @param newName The new title to assign.
     */
    fun renameSavedRequest(request: SidebarRequestItem, newName: String) {
        val collectionId = request.collectionId ?: return
        val folderId = request.folderId ?: collectionId

        viewModelScope.launch(ioDispatcher) {
            val httpMethodEnum = try {
                HttpMethod.valueOf(request.method.uppercase())
            } catch (_: Exception) {
                HttpMethod.GET
            }

            val savedReq = SavedApiRequest(
                id = request.id,
                name = newName.trim(),
                method = httpMethodEnum,
                url = request.url,
                headers = request.headers.map { RequestHeader(it.first, it.second) },
                body = ApiRequestBody(content = request.bodyPayload, type = request.bodyType),
                scripts = ApiRequestScripts(preRequest = request.preRequestScript, test = request.testScript)
            )

            updateRequestInCollectionUseCase?.execute(
                collectionId = collectionId,
                folderId = folderId,
                request = savedReq
            )

            _uiState.update {
                it.copy(
                    isRenameRequestDialogOpen = false,
                    renamingRequestItem = null
                )
            }
        }
    }

    /**
     * Deletes a saved API request item from persistent storage.
     *
     * @param requestId The ID of the saved request record to delete.
     */
    fun deleteSavedRequest(requestId: String) {
        viewModelScope.launch(ioDispatcher) {
            deleteSavedSessionUseCase?.execute(requestId)
        }
    }

    /**
     * Saves or promotes the current active request session into a persistent collection.
     *
     * @param requestName Title of the saved request.
     * @param mode Target save mode (NEW_COLLECTION or EXISTING_COLLECTION).
     * @param selectedCollectionId Id of existing target collection folder if mode is EXISTING_COLLECTION.
     * @param newCollectionName Name of newly created collection if mode is NEW_COLLECTION.
     * @param currentEditor Current editor state containing URL, method, headers, etc.
     * @param onSaved Callback fired upon successful promotion passing saved request ID.
     */
    fun saveRequestToCollection(
        requestName: String,
        mode: CollectionSaveMode,
        selectedCollectionId: String?,
        newCollectionName: String,
        currentEditor: RequestEditorState,
        onSaved: (String) -> Unit = {}
    ) {
        val linkedId = currentEditor.linkedUnsavedId ?: "unsaved_${System.currentTimeMillis()}"

        val httpMethodEnum = try {
            HttpMethod.valueOf(currentEditor.method.uppercase())
        } catch (_: Exception) {
            HttpMethod.GET
        }

        val request = SavedApiRequest(
            id = "req_${System.currentTimeMillis()}",
            name = requestName,
            method = httpMethodEnum,
            url = currentEditor.url,
            headers = currentEditor.headers.map { RequestHeader(it.first, it.second) },
            body = ApiRequestBody(content = currentEditor.bodyPayload, type = currentEditor.bodyType),
            scripts = ApiRequestScripts(preRequest = currentEditor.preRequestScript, test = currentEditor.testScript)
        )

        viewModelScope.launch(ioDispatcher) {
            when (mode) {
                CollectionSaveMode.NEW_COLLECTION -> {
                    val colId = "col_${System.currentTimeMillis()}"
                    val folderId = "fld_${System.currentTimeMillis()}"
                    val collection = ApiCollection(id = colId, name = newCollectionName)
                    val folder = CollectionFolder(id = folderId, name = "Requests")

                    saveRequestToCollectionUseCase?.executeNew(
                        collection = collection,
                        folder = folder,
                        request = request,
                        unsavedRequestIdToDelete = linkedId
                    )
                }

                CollectionSaveMode.EXISTING_COLLECTION -> {
                    if (selectedCollectionId != null) {
                        saveRequestToCollectionUseCase?.executeExisting(
                            collectionId = selectedCollectionId,
                            folderId = selectedCollectionId,
                            request = request,
                            unsavedRequestIdToDelete = linkedId
                        )
                    }
                }
            }

            _uiState.update { state ->
                state.copy(isSaveDialogOpen = false)
            }
            onSaved(request.id)
        }
    }
}
