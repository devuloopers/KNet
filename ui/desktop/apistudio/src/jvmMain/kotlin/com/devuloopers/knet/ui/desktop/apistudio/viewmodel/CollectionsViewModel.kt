package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
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
import com.devuloopers.knet.ui.desktop.apistudio.dialog.CollectionSaveMode
import com.devuloopers.knet.ui.desktop.apistudio.model.CollectionsState
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestDomainConverter.toDomainSavedRequest
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.apistudio.model.SidebarTreeMapper
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Dedicated ViewModel managing UDF state for API collections sidebar, unsaved request sessions,
 * and collection promotion modal dialogs.
 *
 * SRP: Strictly presentation UDF state management. Tree model mapping and DTO conversion
 * are delegated to [SidebarTreeMapper] and [RequestDomainConverter].
 *
 * @param observeCollectionsUseCase Use case for observing saved API collections from Room DB.
 * @param observeUnsavedRequestsUseCase Use case for observing active unsaved request sessions.
 * @param saveUnsavedRequestUseCase Use case for persisting unsaved session edits.
 * @param deleteUnsavedRequestUseCase Use case for deleting unsaved sessions.
 * @param createCollectionUseCase Use case for creating new API collections.
 * @param deleteCollectionUseCase Use case for deleting saved API collections.
 * @param renameCollectionUseCase Use case for renaming saved API collections.
 * @param saveRequestToCollectionUseCase Use case for promoting unsaved sessions into persistent collections.
 * @param updateRequestInCollectionUseCase Use case for updating saved requests.
 * @param deleteSavedSessionUseCase Use case for deleting saved sessions.
 * @param ioDispatcher Coroutine dispatcher for database storage I/O.
 */
@OptIn(FlowPreview::class)
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

    private sealed interface AutoSaveIntent {
        data class Unsaved(val savedReq: SavedApiRequest) : AutoSaveIntent
        data class Saved(val collectionId: String, val folderId: String, val savedReq: SavedApiRequest) : AutoSaveIntent
    }

    private val autoSaveFlow = MutableSharedFlow<AutoSaveIntent>(extraBufferCapacity = 64)

    init {
        // Observe persistent saved collections flow from Room DB via SidebarTreeMapper
        observeCollectionsUseCase?.execute()
            ?.map { SidebarTreeMapper.toSidebarFolders(it) }
            ?.flowOn(ioDispatcher)
            ?.onEach { sidebarFolders ->
                _uiState.update { it.copy(collections = sidebarFolders) }
            }
            ?.launchIn(viewModelScope)

        // Observe active unsaved request session flow from Room DB via SidebarTreeMapper
        observeUnsavedRequestsUseCase?.execute()
            ?.map { SidebarTreeMapper.toSidebarUnsavedRequests(it) }
            ?.flowOn(ioDispatcher)
            ?.onEach { sidebarItems ->
                _uiState.update { it.copy(unsavedRequests = sidebarItems) }
            }
            ?.launchIn(viewModelScope)

        // Debounced reactive auto-save pipeline
        autoSaveFlow
            .debounce(500.milliseconds)
            .onEach { intent ->
                when (intent) {
                    is AutoSaveIntent.Unsaved -> {
                        saveUnsavedRequestUseCase?.execute(intent.savedReq)
                    }
                    is AutoSaveIntent.Saved -> {
                        updateRequestInCollectionUseCase?.execute(
                            collectionId = intent.collectionId,
                            folderId = intent.folderId,
                            request = intent.savedReq
                        )
                    }
                }
            }
            .flowOn(ioDispatcher)
            .launchIn(viewModelScope)
    }

    /**
     * Auto-saves or updates an unsaved request draft in persistent storage.
     */
    fun triggerUnsavedAutoSave(
        editorState: RequestEditorState,
        onLinkedIdAssigned: (String, String) -> Unit
    ) {
        val currentUnsavedRequests = _uiState.value.unsavedRequests
        val effectiveLinkedId = editorState.linkedUnsavedId ?: "unsaved_${System.currentTimeMillis()}"
        val sessionName = if (editorState.linkedUnsavedId != null) {
            currentUnsavedRequests.find { it.id == effectiveLinkedId }?.name
                ?: "Unsaved Session ${currentUnsavedRequests.size + 1}"
        } else {
            val name = "Unsaved Session ${currentUnsavedRequests.size + 1}"
            onLinkedIdAssigned(effectiveLinkedId, name)
            name
        }

        val savedReq = editorState.toDomainSavedRequest(id = effectiveLinkedId, name = sessionName)
        autoSaveFlow.tryEmit(AutoSaveIntent.Unsaved(savedReq))
    }

    /**
     * Auto-saves edits to a saved collection request in-place.
     */
    fun triggerSavedRequestAutoSave(
        requestId: String,
        collectionId: String,
        folderId: String,
        editorState: RequestEditorState
    ) {
        val currentSavedName = _uiState.value.collections
            .flatMap { folder -> folder.requests }
            .find { request -> request.id == requestId }
            ?.name
            ?: editorState.linkedUnsavedId
            ?: requestId

        val savedReq = editorState.toDomainSavedRequest(id = requestId, name = currentSavedName)
        autoSaveFlow.tryEmit(AutoSaveIntent.Saved(collectionId = collectionId, folderId = folderId, savedReq = savedReq))
    }

    /**
     * Creates a new empty unsaved session.
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

    fun deleteUnsavedRequest(requestId: String) {
        viewModelScope.launch(ioDispatcher) {
            deleteUnsavedRequestUseCase?.execute(requestId)
        }
    }

    fun openSaveDialog() {
        _uiState.update { it.copy(isSaveDialogOpen = true) }
    }

    fun closeSaveDialog() {
        _uiState.update { it.copy(isSaveDialogOpen = false) }
    }

    fun openCreateCollectionDialog() {
        _uiState.update { it.copy(isCreateCollectionDialogOpen = true) }
    }

    fun closeCreateCollectionDialog() {
        _uiState.update { it.copy(isCreateCollectionDialogOpen = false) }
    }

    fun createCollection(collectionName: String) {
        viewModelScope.launch(ioDispatcher) {
            createCollectionUseCase?.execute(collectionName)
            _uiState.update { it.copy(isCreateCollectionDialogOpen = false) }
        }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch(ioDispatcher) {
            deleteCollectionUseCase?.execute(collectionId)
        }
    }

    fun openRenameDialog(collectionId: String, currentName: String) {
        _uiState.update {
            it.copy(
                isRenameDialogOpen = true,
                renamingCollectionId = collectionId,
                renamingCollectionName = currentName
            )
        }
    }

    fun closeRenameDialog() {
        _uiState.update {
            it.copy(
                isRenameDialogOpen = false,
                renamingCollectionId = null,
                renamingCollectionName = ""
            )
        }
    }

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

    fun openRenameRequestDialog(request: SidebarRequestItem) {
        _uiState.update {
            it.copy(
                isRenameRequestDialogOpen = true,
                renamingRequestItem = request
            )
        }
    }

    fun closeRenameRequestDialog() {
        _uiState.update {
            it.copy(
                isRenameRequestDialogOpen = false,
                renamingRequestItem = null
            )
        }
    }

    fun renameSavedRequest(request: SidebarRequestItem, newName: String) {
        val collectionId = request.collectionId ?: return
        val folderId = request.folderId ?: collectionId

        viewModelScope.launch(ioDispatcher) {
            val savedReq = request.toDomainSavedRequest(overrideName = newName)
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

    fun deleteSavedRequest(requestId: String) {
        viewModelScope.launch(ioDispatcher) {
            deleteSavedSessionUseCase?.execute(requestId)
        }
    }

    fun saveRequestToCollection(
        requestName: String,
        mode: CollectionSaveMode,
        selectedCollectionId: String?,
        newCollectionName: String,
        currentEditor: RequestEditorState,
        onSaved: (String) -> Unit = {}
    ) {
        val linkedId = currentEditor.linkedUnsavedId ?: "unsaved_${System.currentTimeMillis()}"
        val request = currentEditor.toDomainSavedRequest(id = "req_${System.currentTimeMillis()}", name = requestName)

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
