package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.apistudio.usecase.DescribeRequestUseCase
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

/**
 * Owns collection-sidebar projections and collection CRUD dialogs.
 *
 * Active request hydration, editor auto-save, execution, and draft promotion intentionally belong to
 * [ApiStudioViewModel], leaving this ViewModel unable to write partial editor snapshots.
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
    private val describeRequestUseCase: DescribeRequestUseCase,
    private val deleteUnsavedRequestUseCase: DeleteUnsavedRequestUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val renameCollectionUseCase: RenameCollectionUseCase,
    private val updateRequestInCollectionUseCase: UpdateRequestInCollectionUseCase,
    private val deleteSavedSessionUseCase: DeleteSavedSessionUseCase,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsState())

    /** Immutable sidebar and collection-dialog state. */
    val uiState: StateFlow<CollectionsState> = _uiState.asStateFlow()

    init {
        combine(
            observeCollectionsUseCase.execute(),
            observeUnsavedRequestsUseCase.execute()
        ) { collections, drafts ->
            SidebarTreeMapper.toSidebarFolders(collections, describeRequestUseCase::execute) to
                SidebarTreeMapper.toSidebarUnsavedRequests(drafts, describeRequestUseCase::execute)
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
    fun deleteUnsavedRequest(requestId: String, onDeleted: () -> Unit = {}) = launchPersistence(onDeleted) {
        deleteUnsavedRequestUseCase.execute(requestId)
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
        val collectionId = request.collectionId ?: return
        val folderId = request.folderId ?: return
        val document = request.document
        launchPersistence(
            onSuccess = {
                closeRenameRequestDialog()
                onRenamed()
            }
        ) {
            updateRequestInCollectionUseCase.execute(
                collectionId = collectionId,
                folderId = folderId,
                request = document.copy(
                    name = newName.trim(),
                    nameOrigin = RequestNameOrigin.USER_DEFINED
                )
            )
        }
    }

    /** Deletes a saved request and invokes [onDeleted] only after persistence succeeds. */
    fun deleteSavedRequest(requestId: String, onDeleted: () -> Unit = {}) = launchPersistence(onDeleted) {
        deleteSavedSessionUseCase.execute(requestId)
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
